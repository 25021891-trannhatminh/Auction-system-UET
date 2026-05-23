package server.service;

import server.common.entity.Auction;
import server.common.entity.BidTransaction;
import server.common.entity.User;
import server.common.enums.AuctionStatus;
import server.common.entity.exception.AuctionClosedException;
import server.common.entity.exception.InvalidBidException;
import server.common.entity.manager.AuctionManager;
import server.common.model.BidHistoryDTO;
import server.database.DBConnection;
import server.repository.AccountDAO;
import server.repository.BidTransactionDAO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class BidTransactionService {

  private static final Logger logger = LoggerFactory.getLogger(BidTransactionService.class);

  private final BidTransactionDAO bidTransactionDAO = new BidTransactionDAO();
  private final AccountDAO accountDAO = new AccountDAO();

  /**
   * Quy trình xử lý luồng đặt giá.
   * Đồng bộ hóa và thao tác dữ liệu qua lại sử dụng trực tiếp thực thể gốc BidHistoryDTO.
   */
  public BidTransaction executePlaceBidFlow(int auctionId, int bidderId, BigDecimal amount, boolean isAutoBid
  ) throws AuctionClosedException, InvalidBidException {

    // SỬA LỖI: Cập nhật chuẩn xác 3 tham số cho InvalidBidException theo đúng file Exception gốc
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new InvalidBidException("Số tiền đặt giá phải lớn hơn 0", amount, BigDecimal.ZERO);
    }

    Auction auction = AuctionManager.getInstance()
        .getAuction(String.valueOf(auctionId))
        .orElse(null);

    User bidder = AuctionManager.getInstance()
        .findUserById(String.valueOf(bidderId))
        .orElse(null);

    if (auction == null || bidder == null) {
      logger.warn("executePlaceBidFlow() - auction or bidder not found");
      throw new InvalidBidException("Không tìm thấy phiên đấu giá hoặc người dùng trên hệ thống RAM", amount, BigDecimal.ZERO);
    }

    // SỬA LỖI SCOPE: Khai báo biến Snapshot bên ngoài khối try-catch để tầng catch có thể nhìn thấy và thực hiện Rollback RAM
    BigDecimal previousPrice = auction.getCurrentPrice();
    User previousLeader = auction.getCurrentLeader();

    try (Connection conn = DBConnection.getConnection()) {
      conn.setAutoCommit(false);

      try {
        // Khóa dòng vật lý và lấy trạng thái thực tế dưới Database hạ tầng
        String dbStatus = bidTransactionDAO.lockAuctionRowAndGetStatus(conn, auctionId);
        if (dbStatus == null) {
          conn.rollback();
          logger.warn("executePlaceBidFlow() - auction {} not found under DB", auctionId);
          throw new InvalidBidException("Phiên đấu giá không tồn tại dưới Database hạ tầng", amount, previousPrice);
        }

        // Check trạng thái đóng từ DB hạ tầng trước khi nạp cược
        if ("FINISHED".equals(dbStatus) || "ENDED".equals(dbStatus)) {
          conn.rollback();
          throw new AuctionClosedException(String.valueOf(auctionId), AuctionStatus.FINISHED);
        }

        // 1. Thực thi nghiệp vụ đặt giá chính thống trên Core RAM nhằm sinh Entity nguyên bản
        // Lưu ý: Nếu bước placeBid này ném ra InvalidBidException, nó sẽ trùng khớp với catch phía dưới
        BidTransaction tx = auction.placeBid(bidder, amount, isAutoBid);
        if (tx == null) {
          conn.rollback();
          return null;
        }

        // 2. Đồng bộ các thông số State cốt lõi của Auction xuống bảng 'auctions' dưới DB
        bidTransactionDAO.updateAuctionState(
            conn,
            auctionId,
            bidderId,
            tx.getAmount(),
            auction.getEndTime()
        );

        // 3. Sử dụng cấu trúc DTO độc lập của bạn để ghi vết lịch sử giao dịch vào DB an toàn
        BidHistoryDTO txDTO = toDTO(tx);
        bidTransactionDAO.insertBidTransaction(conn, txDTO);

        conn.commit();
        logger.info("executePlaceBidFlow() - success auctionId={} bidderId={} amount={}", auctionId, bidderId, amount);

        return tx;

      } catch (AuctionClosedException | InvalidBidException e) {
        conn.rollback();
        throw e;
      } catch (Exception e) {
        try { conn.rollback(); } catch (SQLException ignored) {}
        logger.error("executePlaceBidFlow() - rollback RAM state due to unexpected error", e);

        // SỬA LỖI: Các biến snapshot 'previousPrice' và 'previousLeader' hiện đã hợp lệ và gọi thành công tại đây
        auction.rollbackLastBid(null, previousPrice, previousLeader);
        return null;
      } finally {
        conn.setAutoCommit(true);
      }
    } catch (SQLException e) {
      logger.error("executePlaceBidFlow() - DB network connection error", e);
      return null;
    }
  }

  /**
   * Lấy lịch sử đặt giá. Đọc danh sách DTO thô từ DAO lên và dùng hàm toEntity
   * để ánh xạ ngược lại thành List Entity nguyên bản trả cho Auction phục dựng lịch sử trên RAM Core.
   */
  public List<BidTransaction> getBidHistory(int auctionId) {
    List<BidHistoryDTO> dtos = bidTransactionDAO.getBidHistory(auctionId);
    List<BidTransaction> results = new ArrayList<>();

    for (BidHistoryDTO dto : dtos) {
      results.add(toEntity(dto));
    }
    return results;
  }

  /**
   * Hàm map ngược từ BidHistoryDTO (sử dụng BidStatus gốc) thành Entity nguyên bản trên RAM
   */
  private BidTransaction toEntity(BidHistoryDTO dto) {
    String bidderName = "Ẩn danh";
    try {
      var account = accountDAO.getUserById(dto.getBidderId());
      if (account != null) {
        bidderName = account.getFullName();
      }
    } catch (Exception e) {
      logger.warn("Không tìm thấy thông tin chi tiết tên hiển thị của người dùng ID {}", dto.getBidderId());
    }

    return new BidTransaction(
        String.valueOf(dto.getBidId()),
        String.valueOf(dto.getAuctionId()),
        String.valueOf(dto.getBidderId()),
        bidderName,
        dto.getAmount(),
        dto.getBidTime().toLocalDateTime(),
        dto.isAutoBid(),
        dto.getStatus() // Đồng nhất Enum BidStatus từ file DTO gốc của bạn
    );
  }

  /**
   * Hàm chuyển đổi từ Entity RAM sang BidHistoryDTO để lưu trữ xuống DB hạ tầng
   */
  private BidHistoryDTO toDTO(BidTransaction tx) {
    BidHistoryDTO dto = new BidHistoryDTO();
    dto.setBidId(tx.getId() != null ? Integer.parseInt(tx.getId()) : 0);
    dto.setAuctionId(Integer.parseInt(tx.getAuctionId()));
    dto.setBidderId(Integer.parseInt(tx.getBidderId()));
    dto.setAmount(tx.getAmount());
    dto.setAutoBid(tx.isAutoBid());
    dto.setStatus(tx.getStatus()); // Gán trực tiếp Enum BidStatus từ Entity sang DTO gốc của bạn
    dto.setBidTime(Timestamp.valueOf(tx.getBidTime()));
    return dto;
  }
}
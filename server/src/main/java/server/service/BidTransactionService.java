package server.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import server.common.entity.Auction;
import server.common.entity.Auction.PlaceBidResult;
import server.common.entity.BidTransaction;
import server.common.entity.User;
import server.common.entity.exception.AuctionClosedException;
import server.common.entity.exception.InvalidBidException;
import server.common.entity.manager.AuctionManager;
import server.common.enums.AuctionStatus;
import server.common.model.BidHistoryDTO;
import server.database.DBConnection;
import server.network.NotificationDispatcher;
import server.repository.AccountDAO;
import server.repository.BidTransactionDAO;
import server.repository.BidTransactionDAO.AuctionLockInfo;

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
        .getAuction(auctionId)
        .orElse(null);

    User bidder = AuctionManager.getInstance()
        .findUserById(bidderId)
        .orElse(null);

    if (auction == null || bidder == null) {
      logger.warn("executePlaceBidFlow() - auction or bidder not found");
      throw new InvalidBidException("Không tìm thấy phiên đấu giá hoặc người dùng trên hệ thống RAM", amount, BigDecimal.ZERO);
    }

    try (Connection conn = DBConnection.getConnection()) {
      conn.setAutoCommit(false);
      BidTransaction tx = null;
      PlaceBidResult result = null;
      try {
        // ── Bước 1: Khóa hàng DB và lấy cả status lẫn endTime trong một lần SELECT FOR UPDATE ──
        AuctionLockInfo lockInfo = bidTransactionDAO.lockAuctionRowAndGetInfo(conn, auctionId);
        if (lockInfo == null) {
          conn.rollback();
          logger.warn("executePlaceBidFlow() - auction {} not found under DB", auctionId);
          throw new InvalidBidException("Phiên đấu giá không tồn tại dưới Database hạ tầng", amount, auction.getCurrentPrice());
        }

        // ── Bước 2: Guard status từ DB ────────────────────────────────────────────────────────
        if ("FINISHED".equals(lockInfo.status()) || "ENDED".equals(lockInfo.status()) || "CANCELED".equals(lockInfo.status())) {
          conn.rollback();
          throw new AuctionClosedException(auctionId, AuctionStatus.FINISHED);
        }

        // ── Bước 3: Guard endTime từ DB — lớp phòng thủ cuối, bắt scheduler trễ ─────────────
        // Auction.placeBid() cũng check endTime trong RAM (trong lock), nhưng DB là nguồn
        // sự thật duy nhất: nếu end_time trong DB đã qua thì từ chối ngay, không xuống RAM.
        if (LocalDateTime.now().isAfter(lockInfo.endTime())) {
          conn.rollback();
          logger.warn("executePlaceBidFlow() - auction {} đã quá endTime ({}) nhưng status vẫn RUNNING — scheduler trễ",
              auctionId, lockInfo.endTime());
          throw new AuctionClosedException(auctionId, AuctionStatus.FINISHED);
        }

        // ── Bước 4: Thực thi nghiệp vụ đặt giá trên RAM ─────────────────────────────────────
        result = auction.placeBid(bidder, amount, isAutoBid);
        tx = result.tx();
        if (tx == null) {
          conn.rollback();
          return null;
        }

        // ── Bước 5: Đồng bộ state auction xuống DB ───────────────────────────────────────────
        bidTransactionDAO.updateAuctionState(
            conn,
            auctionId,
            bidderId,
            tx.getAmount(),
            auction.getEndTime()
        );

        // ── Bước 6: Ghi lịch sử giao dịch
        BidHistoryDTO txDTO = toDTO(tx);
        bidTransactionDAO.insertBidTransaction(conn, txDTO);

        conn.commit();

        // ── Bước 7: Notify sau khi DB commit thành công ───────────────────────────────────────
        // Truyền toàn bộ result để notifyBidCommitted() lấy previousLeader đúng snapshot,
        // tránh bug gửi onOutbid nhầm cho người vừa thắng.
        auction.notifyBidCommitted(result);

        // Gọi rescheduleClose() trực tiếp từ result — không dùng checkAndResetExtensionFlag()
        // để tránh miss khi nhiều bid anti-snipe đến liên tiếp.
        if (result.timeExtended()) {
          AuctionManager.getInstance().rescheduleClose(auction);
        }
        logger.info("executePlaceBidFlow() - success auctionId={} bidderId={} amount={}", auctionId, bidderId, amount);
        return tx;

      } catch (AuctionClosedException | InvalidBidException e) {
        conn.rollback();
        throw e;
      } catch (Exception e) {
        try { conn.rollback(); } catch (SQLException ignored) {}
        logger.error("executePlaceBidFlow() - rollback RAM state due to unexpected error", e);

        if (result != null) {
          auction.rollbackLastBid(
              tx,
              result.outbidTx(),
              result.previousPrice(),
              result.previousLeader(),
              result.previousEndTime(),
              result.previousLastBid()
          );
        }
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
   * Phát payload realtime riêng cho UI đang mở đúng phòng đấu giá.
   * Gói tin này chỉ được gửi sau khi transaction đã commit thành công xuống DB.
   */
  private void publishRealtimeBidUpdate(Auction auction, BidTransaction tx, boolean timeExtended) {
    String leaderName = auction.getCurrentLeader() != null
        ? auction.getCurrentLeader().getUsername()
        : "";

    String bidUpdateMessage = String.format(
        "BID_UPDATE|%d|%d|%s|%d|%s|%d|%s|%s",
        auction.getId(),
        tx.getBidderId(),
        tx.getAmount().toPlainString(),
        auction.getTotalBids(),
        encodeRealtimeField(leaderName),
        auction.getSecondsRemaining(),
        auction.getEndTime().toString(),
        tx.isAutoBid() ? "true" : "false"
    );
    NotificationDispatcher.getInstance().pushRawToAuctionWatchers(auction.getId(), bidUpdateMessage);

    if (timeExtended) {
      String timeExtendedMessage = String.format(
          "TIME_EXTENDED|%d|%d|%s|%d",
          auction.getId(),
          auction.getSecondsRemaining(),
          auction.getEndTime().toString(),
          auction.getSnipeExtensionSeconds()
      );
      NotificationDispatcher.getInstance().pushRawToAuctionWatchers(auction.getId(), timeExtendedMessage);
    }
  }

  private String encodeRealtimeField(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\")
        .replace("|", "\\p")
        .replace("\n", "\\n")
        .replace("\r", "\\r");
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
        dto.getBidId(),
        dto.getAuctionId(),
        dto.getBidderId(),
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
    dto.setBidId(tx.getId());
    dto.setAuctionId(tx.getAuctionId());
    dto.setBidderId(tx.getBidderId());
    dto.setAmount(tx.getAmount());
    dto.setAutoBid(tx.isAutoBid());
    dto.setStatus(tx.getStatus()); // Gán trực tiếp Enum BidStatus từ Entity sang DTO gốc của bạn
    dto.setBidTime(Timestamp.valueOf(tx.getBidTime()));
    return dto;
  }
}
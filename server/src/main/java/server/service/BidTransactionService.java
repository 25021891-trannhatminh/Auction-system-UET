package server.service;

import server.common.entity.Auction;
import server.common.entity.AutoBidEngine;
import server.common.entity.BidTransaction;
import server.common.entity.User;
import server.common.entity.exception.AuctionClosedException;
import server.common.entity.exception.InvalidBidException;
import server.common.entity.manager.AuctionManager;
import server.database.DBConnection;
import server.repository.BidTransactionDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * BidTransactionService - Lớp điều phối giao dịch đặt giá đa luồng.
 * Đảm bảo thứ tự khóa an toàn: Khóa DB row trước, thực thi RAM logic sau, đồng bộ State chính xác.
 */
public class BidTransactionService {

  private static final Logger logger = LoggerFactory.getLogger(BidTransactionService.class);
  private final BidTransactionDAO bidTransactionDAO = new BidTransactionDAO();

  /**
   * Điều phối quy trình đặt giá tích hợp đồng bộ giữa RAM và Cơ sở dữ liệu.
   * CHỈ GỌI CORE RAM 1 LẦN DUY NHẤT TRONG VÙNG TRANSACTIONS.
   * * @return BidTransaction đối tượng transaction vừa tạo, hoặc null nếu thất bại
   */
  public BidTransaction executePlaceBidFlow(int auctionId, int bidderId, BigDecimal amount, boolean isAutoBid)
      throws AuctionClosedException, InvalidBidException {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      return null;
    }

    Auction auction = AuctionManager.getInstance().getAuction(String.valueOf(auctionId)).orElse(null);
    User bidder = AuctionManager.getInstance().findUserById(String.valueOf(bidderId)).orElse(null);

    if (auction == null || bidder == null) {
      logger.error("executePlaceBidFlow() - Thất bại: Không tìm thấy Auction hoặc User trên RAM Cache.");
      return null;
    }

    try (Connection conn = DBConnection.getConnection()) {
      conn.setAutoCommit(false); // Mở Transaction để cô lập dữ liệu

      // 1. Khóa cứng bản ghi dưới DB bằng SELECT FOR UPDATE
      if (!bidTransactionDAO.lockAuctionRow(conn, auctionId)) {
        logger.warn("executePlaceBidFlow() - Không thể lock hàng dưới DB cho Auction {}", auctionId);
        conn.rollback();
        return null;
      }

      // Chụp snapshot RAM TRƯỚC khi vào try — catch cần đọc được khi DB fail
      BigDecimal previousPrice  = auction.getCurrentPrice();
      User       previousLeader = auction.getCurrentLeader();

      try {
        // 2. [QUAN TRỌNG] Đã an toàn -> Gọi CORE LOGIC trên RAM tại đây (Duy nhất 1 lần)
        BidTransaction tx = auction.placeBid(bidder, amount, isAutoBid);
        if (tx == null) {
          conn.rollback();
          return null;
        }

        // 3. Cập nhật State từ RAM đồng bộ xuống DB vật lý
        bidTransactionDAO.updateAuctionState(conn, auctionId, bidderId, tx.getAmount(), auction.getEndTime());
        bidTransactionDAO.insertBidTransaction(conn, auctionId, bidderId, tx);

        // Commit toàn bộ thay đổi xuống DB một cách an toàn
        conn.commit();
        logger.info("executePlaceBidFlow() - Giao dịch đặt giá thành công hoàn toàn: Auction {}, Price {}", auctionId, tx.getAmount());

        return tx; // Trả transaction ra ngoài cho Manager dùng

      } catch (AuctionClosedException | InvalidBidException e) {
        // Lỗi nghiệp vụ xảy ra BÊN TRONG placeBid() — RAM chưa thay đổi, chỉ cần rollback DB
        logger.warn("executePlaceBidFlow() - Lỗi nghiệp vụ: {}", e.getMessage());
        conn.rollback();
        throw e; // KHÔNG nuốt — re-throw để BidHandler nhận được reason thật
      } catch (Exception e) {
        // Lỗi DB hoặc hệ thống xảy ra SAU placeBid() — RAM đã thay đổi, phải rollback cả RAM
        logger.error("executePlaceBidFlow() - Lỗi DB sau khi RAM đã thay đổi, rollback cả RAM: {}", e.getMessage());
        try { conn.rollback(); } catch (SQLException ignored) {}
        // Rollback RAM về snapshot trước khi placeBid() chạy
        auction.rollbackLastBid(null, previousPrice, previousLeader);
        return null;
      }
    } catch (SQLException e) {
      logger.error("executePlaceBidFlow() - Lỗi kết nối hoặc thực thi SQL hạ tầng: {}", e.getMessage());
      return null;
    }
  }
}
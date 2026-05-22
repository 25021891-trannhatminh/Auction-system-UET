package server.service;

import server.common.entity.Auction;
import server.common.entity.AutoBidEngine;
import server.common.entity.BidTransaction;
import server.common.entity.User;
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
  public BidTransaction executePlaceBidFlow(int auctionId, int bidderId, BigDecimal amount, boolean isAutoBid) {
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

      } catch (Exception e) {
        logger.error("executePlaceBidFlow() - Lỗi nghiệp vụ logic đấu giá, tiến hành rollback DB: {}", e.getMessage());
        conn.rollback();
        return null;
      }
    } catch (SQLException e) {
      logger.error("executePlaceBidFlow() - Lỗi kết nối hoặc thực thi SQL hạ tầng: {}", e.getMessage());
      return null;
    }
  }
}
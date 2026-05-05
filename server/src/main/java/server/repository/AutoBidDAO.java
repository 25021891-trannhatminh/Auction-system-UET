package server.repository;

import server.common.enums.AutoBidStatus;
import server.common.model.AutoBidDTO;
import server.database.DBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object (DAO) cho bảng {@code AUTO_BIDS}.
 *
 * <p>Cung cấp các thao tác CRUD và truy vấn cấu hình auto bid. Mọi lỗi
 * {@link SQLException} đều được bắt, ghi log ở mức {@code SEVERE}, và
 * phương thức trả về giá trị mặc định an toàn thay vì ném ngoại lệ ra ngoài.</p>
 *
 * <p>Ví dụ sử dụng:
 * <pre>{@code
 * AutoBidDAO dao = new AutoBidDAO();
 * AutoBidDTO highest = dao.getHighestAutoBid(10);
 * if (highest != null) {
 *     dao.cancel(highest.getAutoBidId());
 * }
 * }</pre>
 * </p>
 */
public class AutoBidDAO {

  private static final Logger logger = LoggerFactory.getLogger(AutoBidDAO.class);

  // ============================================================
  // SQL constants
  // ============================================================

  private static final String SQL_BASE_SELECT = """
      SELECT auto_bid_id, auction_id, bidder_id,
             max_bid, increment, status,
             created_at, updated_at
      FROM auto_bids
      """;

  private static final String SQL_INSERT = """
      INSERT INTO auto_bids (
          auction_id, bidder_id, max_bid,
          increment, status
      ) VALUES (?, ?, ?, ?, ?)
      """;

  private static final String SQL_SELECT_BY_AUCTION =
      SQL_BASE_SELECT + "WHERE auction_id = ? AND status = 'ACTIVE'";

  private static final String SQL_SELECT_HIGHEST = SQL_BASE_SELECT + """
      WHERE auction_id = ? AND status = 'ACTIVE'
      ORDER BY max_bid DESC
      LIMIT 1
      """;

  private static final String SQL_UPDATE_MAX_BID = """
      UPDATE auto_bids
      SET max_bid = ?, updated_at = NOW()
      WHERE auto_bid_id = ?
      """;

  private static final String SQL_CANCEL = """
      UPDATE auto_bids
      SET status = ?, updated_at = NOW()
      WHERE auto_bid_id = ?
      """;

  private static final String SQL_DELETE =
      "DELETE FROM auto_bids WHERE auto_bid_id = ?";

  // ============================================================
  // Public methods
  // ============================================================

  /**
   * Tạo một cấu hình auto bid mới. Status mặc định là {@code ACTIVE}.
   *
   * @param dto thông tin auto bid cần tạo; không được {@code null}
   * @return {@code true} nếu insert thành công, {@code false} nếu thất bại
   */
  public boolean create(AutoBidDTO dto) {
    logger.debug("create() – auctionId={}, bidderId={}, maxBid={}, increment={}",
        dto.getAuctionId(), dto.getBidderId(), dto.getMaxBid(), dto.getIncrement());

    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {

      ps.setInt(1, dto.getAuctionId());
      ps.setInt(2, dto.getBidderId());
      ps.setBigDecimal(3, dto.getMaxBid());
      ps.setBigDecimal(4, dto.getIncrement());
      ps.setString(5, AutoBidStatus.ACTIVE.name());

      boolean created = ps.executeUpdate() > 0;
      if (created) {
        logger.info("create() – AutoBid created for auctionId={}, bidderId={}",
            dto.getAuctionId(), dto.getBidderId());
      } else {
        logger.warn("create() – No rows affected for auctionId={}, bidderId={}",
            dto.getAuctionId(), dto.getBidderId());
      }
      return created;

    } catch (SQLException e) {
      logger.error("create() – DB error for auctionId={}, bidderId={}",
          dto.getAuctionId(), dto.getBidderId(), e);
      return false;
    }
  }

  /**
   * Lấy danh sách auto bid đang {@code ACTIVE} của một phiên đấu giá.
   *
   * @param auctionId id phiên đấu giá cần truy vấn
   * @return danh sách {@link AutoBidDTO} đang active; list rỗng nếu không có hoặc lỗi
   */
  public List<AutoBidDTO> getByAuction(int auctionId) {
    logger.debug("getByAuction() – auctionId={}", auctionId);
    List<AutoBidDTO> results = new ArrayList<>();

    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_AUCTION)) {

      ps.setInt(1, auctionId);

      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          results.add(mapRow(rs));
        }
      }

      logger.debug("getByAuction() – Found {} active auto bid(s) for auctionId={}",
          results.size(), auctionId);

    } catch (SQLException e) {
      logger.error("getByAuction() – DB error for auctionId={}", auctionId, e);
    }

    return results;
  }

  /**
   * Lấy auto bid có {@code max_bid} cao nhất đang {@code ACTIVE} trong phiên đấu giá.
   *
   * <p>Dùng để xác định auto bid ưu tiên khi hệ thống tự động đặt giá.</p>
   *
   * @param auctionId id phiên đấu giá
   * @return {@link AutoBidDTO} có max_bid cao nhất, hoặc {@code null} nếu không có
   */
  public AutoBidDTO getHighestAutoBid(int auctionId) {
    logger.debug("getHighestAutoBid() – auctionId={}", auctionId);

    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_SELECT_HIGHEST)) {

      ps.setInt(1, auctionId);

      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          AutoBidDTO dto = mapRow(rs);
          logger.debug("getHighestAutoBid() – Found autoBidId={} with maxBid={} for auctionId={}",
              dto.getAutoBidId(), dto.getMaxBid(), auctionId);
          return dto;
        }
      }

      logger.debug("getHighestAutoBid() – No active auto bid for auctionId={}", auctionId);
      return null;

    } catch (SQLException e) {
      logger.error("getHighestAutoBid() – DB error for auctionId={}", auctionId, e);
      return null;
    }
  }

  /**
   * Cập nhật giá tối đa ({@code max_bid}) của một auto bid.
   *
   * @param autoBidId id auto bid cần cập nhật
   * @param newMax    giá tối đa mới; không được {@code null}
   * @return {@code true} nếu cập nhật thành công
   */
  public boolean updateMaxBid(int autoBidId, BigDecimal newMax) {
    logger.debug("updateMaxBid() – autoBidId={}, newMax={}", autoBidId, newMax);

    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_MAX_BID)) {

      ps.setBigDecimal(1, newMax);
      ps.setInt(2, autoBidId);

      boolean updated = ps.executeUpdate() > 0;
      if (updated) {
        logger.info("updateMaxBid() – autoBidId={} updated maxBid to {}", autoBidId, newMax);
      } else {
        logger.warn("updateMaxBid() – No rows affected for autoBidId={}", autoBidId);
      }
      return updated;

    } catch (SQLException e) {
      logger.error("updateMaxBid() – DB error for autoBidId={}", autoBidId, e);
      return false;
    }
  }

  /**
   * Hủy một cấu hình auto bid (chuyển status sang {@code CANCELED}).
   *
   * <p>Không xóa bản ghi để bảo toàn lịch sử. Dùng {@link #delete(int)}
   * nếu cần xóa hẳn.</p>
   *
   * @param autoBidId id auto bid cần hủy
   * @return {@code true} nếu hủy thành công
   */
  public boolean cancel(int autoBidId) {
    logger.debug("cancel() – autoBidId={}", autoBidId);

    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_CANCEL)) {

      ps.setString(1, AutoBidStatus.CANCELED.name());
      ps.setInt(2, autoBidId);

      boolean canceled = ps.executeUpdate() > 0;
      if (canceled) {
        logger.info("cancel() – autoBidId={} canceled successfully", autoBidId);
      } else {
        logger.warn("cancel() – No rows affected for autoBidId={}", autoBidId);
      }
      return canceled;

    } catch (SQLException e) {
      logger.error("cancel() – DB error for autoBidId={}", autoBidId, e);
      return false;
    }
  }

  /**
   * Xóa vĩnh viễn một cấu hình auto bid khỏi cơ sở dữ liệu.
   *
   * <p>Chỉ dùng khi thực sự cần xóa. Với hủy thông thường, hãy dùng
   * {@link #cancel(int)} để giữ lại lịch sử.</p>
   *
   * @param autoBidId id auto bid cần xóa
   * @return {@code true} nếu xóa thành công
   */
  public boolean delete(int autoBidId) {
    logger.debug("delete() – autoBidId={}", autoBidId);

    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_DELETE)) {

      ps.setInt(1, autoBidId);

      boolean deleted = ps.executeUpdate() > 0;
      if (deleted) {
        logger.info("delete() – autoBidId={} deleted successfully", autoBidId);
      } else {
        logger.warn("delete() – No rows affected for autoBidId={}", autoBidId);
      }
      return deleted;

    } catch (SQLException e) {
      logger.error("delete() – DB error for autoBidId={}", autoBidId, e);
      return false;
    }
  }

  // ============================================================
  // Private helpers
  // ============================================================

  /**
   * Map một hàng {@link ResultSet} thành {@link AutoBidDTO}.
   *
   * @param rs result set đã được định vị tại hàng cần đọc
   * @return {@link AutoBidDTO} được điền đầy đủ
   * @throws SQLException nếu tên cột không tồn tại hoặc lỗi đọc dữ liệu
   */
  private AutoBidDTO mapRow(ResultSet rs) throws SQLException {
    return new AutoBidDTO(
        rs.getInt("auto_bid_id"),
        rs.getInt("auction_id"),
        rs.getInt("bidder_id"),
        rs.getBigDecimal("max_bid"),
        rs.getBigDecimal("increment"),
        AutoBidStatus.valueOf(rs.getString("status")),
        rs.getTimestamp("created_at"),
        rs.getTimestamp("updated_at")
    );
  }
}
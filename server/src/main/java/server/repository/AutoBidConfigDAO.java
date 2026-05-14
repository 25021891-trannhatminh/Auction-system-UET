//package server.repository;
//
//import server.common.enums.AutoBidStatus;
//import server.common.model.AutoBidDTO;
//import server.database.DBConnection;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.math.BigDecimal;
//import java.sql.Connection;
//import java.sql.PreparedStatement;
//import java.sql.ResultSet;
//import java.sql.SQLException;
//import java.util.ArrayList;
//import java.util.List;
//
///**
// * Data Access Object (DAO) cho bảng {@code AUTO_BIDS}.
// *
// * <p>Cung cấp các thao tác CRUD và truy vấn cấu hình auto bid. Mọi lỗi
// * {@link SQLException} đều được bắt, ghi log ở mức {@code SEVERE}, và
// * phương thức trả về giá trị mặc định an toàn thay vì ném ngoại lệ ra ngoài.</p>
// *
// * <p>Ví dụ sử dụng:
// * <pre>{@code
// * AutoBidDAO dao = new AutoBidDAO();
// * AutoBidDTO highest = dao.getHighestAutoBid(10);
// * if (highest != null) {
// *     dao.cancel(highest.getAutoBidId());
// * }
// * }</pre>
// * </p>
// */
//public class AutoBidDAO {
//
//  private static final Logger logger = LoggerFactory.getLogger(AutoBidDAO.class);
//
//  // ============================================================
//  // SQL constants
//  // ============================================================
//
//  private static final String SQL_BASE_SELECT = """
//      SELECT auto_bid_id, auction_id, bidder_id,
//             max_bid, increment, status,
//             created_at, updated_at
//      FROM auto_bids
//      """;
//
//  private static final String SQL_INSERT = """
//      INSERT INTO auto_bids (
//          auction_id, bidder_id, max_bid,
//          increment, status
//      ) VALUES (?, ?, ?, ?, ?)
//      """;
//
//  private static final String SQL_SELECT_BY_AUCTION =
//      SQL_BASE_SELECT + "WHERE auction_id = ? AND status = 'ACTIVE'";
//
//  private static final String SQL_SELECT_HIGHEST = SQL_BASE_SELECT + """
//      WHERE auction_id = ? AND status = 'ACTIVE'
//      ORDER BY max_bid DESC
//      LIMIT 1
//      """;
//
//  private static final String SQL_UPDATE_MAX_BID = """
//      UPDATE auto_bids
//      SET max_bid = ?, updated_at = NOW()
//      WHERE auto_bid_id = ?
//      """;
//
//  private static final String SQL_CANCEL = """
//      UPDATE auto_bids
//      SET status = ?, updated_at = NOW()
//      WHERE auto_bid_id = ?
//      """;
//
//  private static final String SQL_DELETE =
//      "DELETE FROM auto_bids WHERE auto_bid_id = ?";
//
//  // ============================================================
//  // Public methods
//  // ============================================================
//
//  /**
//   * Tạo một cấu hình auto bid mới. Status mặc định là {@code ACTIVE}.
//   *
//   * @param dto thông tin auto bid cần tạo; không được {@code null}
//   * @return {@code true} nếu insert thành công, {@code false} nếu thất bại
//   */
//  public boolean create(AutoBidDTO dto) {
//    logger.debug("create() – auctionId={}, bidderId={}, maxBid={}, increment={}",
//        dto.getAuctionId(), dto.getBidderId(), dto.getMaxBid(), dto.getIncrement());
//
//    try (Connection conn = DBConnection.getConnection();
//        PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {
//
//      ps.setInt(1, dto.getAuctionId());
//      ps.setInt(2, dto.getBidderId());
//      ps.setBigDecimal(3, dto.getMaxBid());
//      ps.setBigDecimal(4, dto.getIncrement());
//      ps.setString(5, AutoBidStatus.ACTIVE.name());
//
//      boolean created = ps.executeUpdate() > 0;
//      if (created) {
//        logger.info("create() – AutoBid created for auctionId={}, bidderId={}",
//            dto.getAuctionId(), dto.getBidderId());
//      } else {
//        logger.warn("create() – No rows affected for auctionId={}, bidderId={}",
//            dto.getAuctionId(), dto.getBidderId());
//      }
//      return created;
//
//    } catch (SQLException e) {
//      logger.error("create() – DB error for auctionId={}, bidderId={}",
//          dto.getAuctionId(), dto.getBidderId(), e);
//      return false;
//    }
//  }
//
//  /**
//   * Lấy danh sách auto bid đang {@code ACTIVE} của một phiên đấu giá.
//   *
//   * @param auctionId id phiên đấu giá cần truy vấn
//   * @return danh sách {@link AutoBidDTO} đang active; list rỗng nếu không có hoặc lỗi
//   */
//  public List<AutoBidDTO> getByAuction(int auctionId) {
//    logger.debug("getByAuction() – auctionId={}", auctionId);
//    List<AutoBidDTO> results = new ArrayList<>();
//
//    try (Connection conn = DBConnection.getConnection();
//        PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_AUCTION)) {
//
//      ps.setInt(1, auctionId);
//
//      try (ResultSet rs = ps.executeQuery()) {
//        while (rs.next()) {
//          results.add(mapRow(rs));
//        }
//      }
//
//      logger.debug("getByAuction() – Found {} active auto bid(s) for auctionId={}",
//          results.size(), auctionId);
//
//    } catch (SQLException e) {
//      logger.error("getByAuction() – DB error for auctionId={}", auctionId, e);
//    }
//
//    return results;
//  }
//
//  /**
//   * Lấy auto bid có {@code max_bid} cao nhất đang {@code ACTIVE} trong phiên đấu giá.
//   *
//   * <p>Dùng để xác định auto bid ưu tiên khi hệ thống tự động đặt giá.</p>
//   *
//   * @param auctionId id phiên đấu giá
//   * @return {@link AutoBidDTO} có max_bid cao nhất, hoặc {@code null} nếu không có
//   */
//  public AutoBidDTO getHighestAutoBid(int auctionId) {
//    logger.debug("getHighestAutoBid() – auctionId={}", auctionId);
//
//    try (Connection conn = DBConnection.getConnection();
//        PreparedStatement ps = conn.prepareStatement(SQL_SELECT_HIGHEST)) {
//
//      ps.setInt(1, auctionId);
//
//      try (ResultSet rs = ps.executeQuery()) {
//        if (rs.next()) {
//          AutoBidDTO dto = mapRow(rs);
//          logger.debug("getHighestAutoBid() – Found autoBidId={} with maxBid={} for auctionId={}",
//              dto.getAutoBidId(), dto.getMaxBid(), auctionId);
//          return dto;
//        }
//      }
//
//      logger.debug("getHighestAutoBid() – No active auto bid for auctionId={}", auctionId);
//      return null;
//
//    } catch (SQLException e) {
//      logger.error("getHighestAutoBid() – DB error for auctionId={}", auctionId, e);
//      return null;
//    }
//  }
//
//  /**
//   * Cập nhật giá tối đa ({@code max_bid}) của một auto bid.
//   *
//   * @param autoBidId id auto bid cần cập nhật
//   * @param newMax    giá tối đa mới; không được {@code null}
//   * @return {@code true} nếu cập nhật thành công
//   */
//  public boolean updateMaxBid(int autoBidId, BigDecimal newMax) {
//    logger.debug("updateMaxBid() – autoBidId={}, newMax={}", autoBidId, newMax);
//
//    try (Connection conn = DBConnection.getConnection();
//        PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_MAX_BID)) {
//
//      ps.setBigDecimal(1, newMax);
//      ps.setInt(2, autoBidId);
//
//      boolean updated = ps.executeUpdate() > 0;
//      if (updated) {
//        logger.info("updateMaxBid() – autoBidId={} updated maxBid to {}", autoBidId, newMax);
//      } else {
//        logger.warn("updateMaxBid() – No rows affected for autoBidId={}", autoBidId);
//      }
//      return updated;
//
//    } catch (SQLException e) {
//      logger.error("updateMaxBid() – DB error for autoBidId={}", autoBidId, e);
//      return false;
//    }
//  }
//
//  /**
//   * Hủy một cấu hình auto bid (chuyển status sang {@code CANCELED}).
//   *
//   * <p>Không xóa bản ghi để bảo toàn lịch sử. Dùng {@link #delete(int)}
//   * nếu cần xóa hẳn.</p>
//   *
//   * @param autoBidId id auto bid cần hủy
//   * @return {@code true} nếu hủy thành công
//   */
//  public boolean cancel(int autoBidId) {
//    logger.debug("cancel() – autoBidId={}", autoBidId);
//
//    try (Connection conn = DBConnection.getConnection();
//        PreparedStatement ps = conn.prepareStatement(SQL_CANCEL)) {
//
//      ps.setString(1, AutoBidStatus.CANCELED.name());
//      ps.setInt(2, autoBidId);
//
//      boolean canceled = ps.executeUpdate() > 0;
//      if (canceled) {
//        logger.info("cancel() – autoBidId={} canceled successfully", autoBidId);
//      } else {
//        logger.warn("cancel() – No rows affected for autoBidId={}", autoBidId);
//      }
//      return canceled;
//
//    } catch (SQLException e) {
//      logger.error("cancel() – DB error for autoBidId={}", autoBidId, e);
//      return false;
//    }
//  }
//
//  /**
//   * Xóa vĩnh viễn một cấu hình auto bid khỏi cơ sở dữ liệu.
//   *
//   * <p>Chỉ dùng khi thực sự cần xóa. Với hủy thông thường, hãy dùng
//   * {@link #cancel(int)} để giữ lại lịch sử.</p>
//   *
//   * @param autoBidId id auto bid cần xóa
//   * @return {@code true} nếu xóa thành công
//   */
//  public boolean delete(int autoBidId) {
//    logger.debug("delete() – autoBidId={}", autoBidId);
//
//    try (Connection conn = DBConnection.getConnection();
//        PreparedStatement ps = conn.prepareStatement(SQL_DELETE)) {
//
//      ps.setInt(1, autoBidId);
//
//      boolean deleted = ps.executeUpdate() > 0;
//      if (deleted) {
//        logger.info("delete() – autoBidId={} deleted successfully", autoBidId);
//      } else {
//        logger.warn("delete() – No rows affected for autoBidId={}", autoBidId);
//      }
//      return deleted;
//
//    } catch (SQLException e) {
//      logger.error("delete() – DB error for autoBidId={}", autoBidId, e);
//      return false;
//    }
//  }
//
//  // ============================================================
//  // Private helpers
//  // ============================================================
//
//  /**
//   * Map một hàng {@link ResultSet} thành {@link AutoBidDTO}.
//   *
//   * @param rs result set đã được định vị tại hàng cần đọc
//   * @return {@link AutoBidDTO} được điền đầy đủ
//   * @throws SQLException nếu tên cột không tồn tại hoặc lỗi đọc dữ liệu
//   */
//  private AutoBidDTO mapRow(ResultSet rs) throws SQLException {
//    return new AutoBidDTO(
//        rs.getInt("auto_bid_id"),
//        rs.getInt("auction_id"),
//        rs.getInt("bidder_id"),
//        rs.getBigDecimal("max_bid"),
//        rs.getBigDecimal("increment"),
//        AutoBidStatus.valueOf(rs.getString("status")),
//        rs.getTimestamp("created_at"),
//        rs.getTimestamp("updated_at")
//    );
//  }
//}
package server.repository;

import server.common.enums.AutoBidStatus;
import server.common.model.AutoBidConfigDTO;
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
 * <p>Quản lý toàn bộ vòng đời của cấu hình đặt giá tự động:
 * <ul>
 *   <li>Tạo mới, cập nhật, hủy và xóa auto bid.</li>
 *   <li>Truy vấn auto bid theo phiên đấu giá hoặc người dùng.</li>
 *   <li>Xác định auto bid ưu tiên (max_bid cao nhất) để hệ thống tự động đặt giá.</li>
 *   <li>Hỗ trợ Transaction thông qua overload nhận {@link Connection} từ ngoài.</li>
 * </ul>
 * </p>
 *
 * <p>Mọi lỗi {@link SQLException} được bắt tại chỗ, ghi log đầy đủ và trả về
 * giá trị mặc định an toàn. Các thao tác quan trọng cần atomic có thể truyền
 * {@link Connection} vào để Service layer quản lý transaction.</p>
 *
 * <p>Ví dụ sử dụng đơn giản:
 * <pre>{@code
 * AutoBidDAO dao = new AutoBidDAO();
 * AutoBidDTO highest = dao.getHighestAutoBid(10);
 * if (highest != null) {
 *     dao.cancel(highest.getAutoBidId());
 * }
 * }</pre>
 * </p>
 *
 * <p>Ví dụ sử dụng với Transaction:
 * <pre>{@code
 * try (Connection conn = DBConnection.getConnection()) {
 *     conn.setAutoCommit(false);
 *     try {
 *         autoBidDAO.create(conn, dto);
 *         bidDAO.placeBid(conn, auctionId, bidderId, amount);
 *         conn.commit();
 *     } catch (Exception e) {
 *         conn.rollback();
 *         throw e;
 *     }
 * }
 * }</pre>
 * </p>
 */
public class AutoBidConfigDAO {

  private static final Logger logger = LoggerFactory.getLogger(AutoBidConfigDAO.class);

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

  // Lấy tất cả auto bid ACTIVE của một phiên đấu giá
  private static final String SQL_SELECT_BY_AUCTION =
      SQL_BASE_SELECT + "WHERE auction_id = ? AND status = 'ACTIVE' ORDER BY max_bid DESC";

  // Lấy tất cả auto bid của một người dùng (mọi trạng thái)
  private static final String SQL_SELECT_BY_BIDDER =
      SQL_BASE_SELECT + "WHERE bidder_id = ? ORDER BY created_at DESC";

  // Lấy auto bid của một người dùng trong một phiên đấu giá cụ thể
  private static final String SQL_SELECT_BY_AUCTION_AND_BIDDER =
      SQL_BASE_SELECT + "WHERE auction_id = ? AND bidder_id = ? LIMIT 1";

  // Lấy auto bid có max_bid cao nhất đang ACTIVE
  private static final String SQL_SELECT_HIGHEST = SQL_BASE_SELECT + """
        WHERE auction_id = ? AND status = 'ACTIVE'
        ORDER BY max_bid DESC
        LIMIT 1
        """;

  // Lấy auto bid theo ID
  private static final String SQL_SELECT_BY_ID =
      SQL_BASE_SELECT + "WHERE auto_bid_id = ?";

  // Đếm số auto bid ACTIVE trong một phiên đấu giá
  private static final String SQL_COUNT_ACTIVE =
      "SELECT COUNT(*) FROM auto_bids WHERE auction_id = ? AND status = 'ACTIVE'";

  // Kiểm tra người dùng đã có auto bid ACTIVE trong phiên chưa
  private static final String SQL_EXISTS_ACTIVE =
      "SELECT COUNT(*) FROM auto_bids WHERE auction_id = ? AND bidder_id = ? AND status = 'ACTIVE'";

  private static final String SQL_UPDATE_MAX_BID = """
        UPDATE auto_bids
        SET max_bid = ?, updated_at = NOW()
        WHERE auto_bid_id = ?
        """;

  private static final String SQL_UPDATE_STATUS = """
        UPDATE auto_bids
        SET status = ?, updated_at = NOW()
        WHERE auto_bid_id = ?
        """;

  // Hủy tất cả auto bid ACTIVE của một phiên (khi phiên kết thúc)
  private static final String SQL_CANCEL_ALL_BY_AUCTION = """
        UPDATE auto_bids
        SET status = 'CANCELED', updated_at = NOW()
        WHERE auction_id = ? AND status = 'ACTIVE'
        """;

  private static final String SQL_DELETE =
      "DELETE FROM auto_bids WHERE auto_bid_id = ?";

  // ============================================================
  // CREATE Methods
  // ============================================================

  /**
   * Tạo một cấu hình auto bid mới. Status mặc định là {@code ACTIVE}.
   *
   * <p>Trước khi tạo, nên kiểm tra {@link #hasActiveBid(int, int)} để tránh
   * tạo trùng auto bid cho cùng một người trong cùng phiên đấu giá.</p>
   *
   * @param dto thông tin auto bid cần tạo; không được {@code null}
   * @return {@code true} nếu insert thành công, {@code false} nếu thất bại
   */
  public boolean create(AutoBidConfigDTO dto) {
    logger.debug("create() – auctionId={}, bidderId={}, maxBid={}, increment={}",
        dto.getAuctionId(), dto.getBidderId(), dto.getMaxBid(), dto.getIncrement());

    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {

      return executeCreate(ps, dto);

    } catch (SQLException e) {
      logger.error("create() – DB error for auctionId={}, bidderId={}",
          dto.getAuctionId(), dto.getBidderId(), e);
      return false;
    }
  }

  /**
   * Overload hỗ trợ Transaction: tạo auto bid trong {@link Connection} cho trước.
   *
   * @param conn connection đang trong transaction; không được {@code null}
   * @param dto  thông tin auto bid cần tạo
   * @return {@code true} nếu insert thành công
   * @throws SQLException để Service layer có thể rollback
   */
  public boolean create(Connection conn, AutoBidConfigDTO dto) throws SQLException {
    try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {
      return executeCreate(ps, dto);
    }
  }

  // ============================================================
  // READ Methods
  // ============================================================

  /**
   * Lấy thông tin auto bid theo ID.
   *
   * @param autoBidId id auto bid cần tìm
   * @return {@link AutoBidConfigDTO} tương ứng, hoặc {@code null} nếu không tìm thấy
   */
  public AutoBidConfigDTO getById(int autoBidId) {
    logger.debug("getById() – autoBidId={}", autoBidId);

    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_ID)) {

      ps.setInt(1, autoBidId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) return mapRow(rs);
      }

    } catch (SQLException e) {
      logger.error("getById() – DB error for autoBidId={}", autoBidId, e);
    }
    return null;
  }

  /**
   * Lấy danh sách auto bid đang {@code ACTIVE} của một phiên đấu giá,
   * sắp xếp theo {@code max_bid} giảm dần.
   *
   * @param auctionId id phiên đấu giá cần truy vấn
   * @return danh sách {@link AutoBidConfigDTO} đang active; list rỗng nếu không có hoặc lỗi
   */
  public List<AutoBidConfigDTO> getByAuction(int auctionId) {
    logger.debug("getByAuction() – auctionId={}", auctionId);
    List<AutoBidConfigDTO> results = new ArrayList<>();

    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_AUCTION)) {

      ps.setInt(1, auctionId);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) results.add(mapRow(rs));
      }

      logger.debug("getByAuction() – Found {} active auto bid(s) for auctionId={}",
          results.size(), auctionId);

    } catch (SQLException e) {
      logger.error("getByAuction() – DB error for auctionId={}", auctionId, e);
    }
    return results;
  }

  /**
   * Lấy toàn bộ lịch sử auto bid của một người dùng (mọi trạng thái).
   *
   * <p>Dùng để hiển thị lịch sử đặt giá tự động trong trang cá nhân.</p>
   *
   * @param bidderId id người dùng cần truy vấn
   * @return danh sách {@link AutoBidConfigDTO} của người dùng đó
   */
  public List<AutoBidConfigDTO> getByBidder(int bidderId) {
    logger.debug("getByBidder() – bidderId={}", bidderId);
    List<AutoBidConfigDTO> results = new ArrayList<>();

    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_BIDDER)) {

      ps.setInt(1, bidderId);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) results.add(mapRow(rs));
      }

      logger.debug("getByBidder() – Found {} auto bid(s) for bidderId={}",
          results.size(), bidderId);

    } catch (SQLException e) {
      logger.error("getByBidder() – DB error for bidderId={}", bidderId, e);
    }
    return results;
  }

  /**
   * Lấy auto bid của một người dùng trong một phiên đấu giá cụ thể.
   *
   * <p>Dùng để kiểm tra hoặc cập nhật auto bid hiện tại của người dùng
   * trước khi tạo mới.</p>
   *
   * @param auctionId id phiên đấu giá
   * @param bidderId  id người dùng
   * @return {@link AutoBidConfigDTO} nếu tồn tại, hoặc {@code null}
   */
  public AutoBidConfigDTO getByAuctionAndBidder(int auctionId, int bidderId) {
    logger.debug("getByAuctionAndBidder() – auctionId={}, bidderId={}", auctionId, bidderId);

    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_AUCTION_AND_BIDDER)) {

      ps.setInt(1, auctionId);
      ps.setInt(2, bidderId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) return mapRow(rs);
      }

    } catch (SQLException e) {
      logger.error("getByAuctionAndBidder() – DB error for auctionId={}, bidderId={}",
          auctionId, bidderId, e);
    }
    return null;
  }

  /**
   * Lấy auto bid có {@code max_bid} cao nhất đang {@code ACTIVE} trong phiên đấu giá.
   *
   * <p>Đây là method cốt lõi của tính năng auto bid — hệ thống gọi method này
   * mỗi khi có bid mới để xác định xem có auto bid nào cần kích hoạt không.</p>
   *
   * @param auctionId id phiên đấu giá
   * @return {@link AutoBidConfigDTO} có max_bid cao nhất, hoặc {@code null} nếu không có
   */
  public AutoBidConfigDTO getHighestAutoBid(int auctionId) {
    logger.debug("getHighestAutoBid() – auctionId={}", auctionId);

    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_SELECT_HIGHEST)) {

      ps.setInt(1, auctionId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          AutoBidConfigDTO dto = mapRow(rs);
          logger.debug("getHighestAutoBid() – Found autoBidId={}, maxBid={} for auctionId={}",
              dto.getAutoBidId(), dto.getMaxBid(), auctionId);
          return dto;
        }
      }

      logger.debug("getHighestAutoBid() – No active auto bid for auctionId={}", auctionId);

    } catch (SQLException e) {
      logger.error("getHighestAutoBid() – DB error for auctionId={}", auctionId, e);
    }
    return null;
  }

  /**
   * Đếm số lượng auto bid đang {@code ACTIVE} trong một phiên đấu giá.
   *
   * @param auctionId id phiên đấu giá
   * @return số lượng auto bid active; {@code 0} nếu không có hoặc lỗi
   */
  public int countActive(int auctionId) {
    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_COUNT_ACTIVE)) {

      ps.setInt(1, auctionId);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getInt(1) : 0;
      }

    } catch (SQLException e) {
      logger.error("countActive() – DB error for auctionId={}", auctionId, e);
    }
    return 0;
  }

  /**
   * Kiểm tra người dùng đã có auto bid {@code ACTIVE} trong phiên đấu giá chưa.
   *
   * <p>Dùng trước khi tạo mới để tránh tạo trùng. Nếu đã có thì nên
   * dùng {@link #updateMaxBid(int, BigDecimal)} thay vì tạo mới.</p>
   *
   * @param auctionId id phiên đấu giá
   * @param bidderId  id người dùng
   * @return {@code true} nếu đã có auto bid active
   */
  public boolean hasActiveBid(int auctionId, int bidderId) {
    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_EXISTS_ACTIVE)) {

      ps.setInt(1, auctionId);
      ps.setInt(2, bidderId);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() && rs.getInt(1) > 0;
      }

    } catch (SQLException e) {
      logger.error("hasActiveBid() – DB error for auctionId={}, bidderId={}",
          auctionId, bidderId, e);
    }
    return false;
  }

  // ============================================================
  // UPDATE Methods
  // ============================================================

  /**
   * Cập nhật giá tối đa ({@code max_bid}) của một auto bid.
   *
   * <p>Dùng khi người dùng muốn nâng giới hạn tự động thay vì tạo
   * một auto bid mới.</p>
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
        logger.info("updateMaxBid() – autoBidId={} maxBid updated to {}", autoBidId, newMax);
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
   * nếu thực sự cần xóa hẳn khỏi DB.</p>
   *
   * @param autoBidId id auto bid cần hủy
   * @return {@code true} nếu hủy thành công
   */
  public boolean cancel(int autoBidId) {
    logger.debug("cancel() – autoBidId={}", autoBidId);
    return updateStatusInternal(autoBidId, AutoBidStatus.CANCELED);
  }

  /**
   * Đánh dấu auto bid là hoàn thành ({@code COMPLETED}) khi phiên đấu giá kết thúc
   * và người dùng thắng cuộc.
   *
   * @param autoBidId id auto bid cần cập nhật
   * @return {@code true} nếu cập nhật thành công
   */
  public boolean complete(int autoBidId) {
    logger.debug("complete() – autoBidId={}", autoBidId);
    return updateStatusInternal(autoBidId, AutoBidStatus.COMPLETED);
  }

  /**
   * Hủy toàn bộ auto bid đang {@code ACTIVE} của một phiên đấu giá.
   *
   * <p>Gọi khi phiên đấu giá kết thúc ({@code FINISHED}) để dọn dẹp
   * tất cả auto bid còn lại, tránh chúng tiếp tục được kích hoạt.</p>
   *
   * @param auctionId id phiên đấu giá cần hủy tất cả auto bid
   * @return số lượng auto bid bị hủy
   */
  public int cancelAllByAuction(int auctionId) {
    logger.debug("cancelAllByAuction() – auctionId={}", auctionId);

    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_CANCEL_ALL_BY_AUCTION)) {

      ps.setInt(1, auctionId);
      int count = ps.executeUpdate();
      logger.info("cancelAllByAuction() – Canceled {} auto bid(s) for auctionId={}",
          count, auctionId);
      return count;

    } catch (SQLException e) {
      logger.error("cancelAllByAuction() – DB error for auctionId={}", auctionId, e);
      return 0;
    }
  }

  // ============================================================
  // DELETE Methods
  // ============================================================

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
   * Tách logic insert ra helper để tái sử dụng giữa 2 overload {@code create()}.
   */
  private boolean executeCreate(PreparedStatement ps, AutoBidConfigDTO dto) throws SQLException {
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
  }

  /**
   * Helper dùng chung để cập nhật status, tránh lặp code giữa
   * {@link #cancel(int)} và {@link #complete(int)}.
   */
  private boolean updateStatusInternal(int autoBidId, AutoBidStatus status) {
    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_STATUS)) {

      ps.setString(1, status.name());
      ps.setInt(2, autoBidId);

      boolean updated = ps.executeUpdate() > 0;
      if (updated) {
        logger.info("updateStatus() – autoBidId={} -> {}", autoBidId, status);
      } else {
        logger.warn("updateStatus() – No rows affected for autoBidId={}", autoBidId);
      }
      return updated;

    } catch (SQLException e) {
      logger.error("updateStatus() – DB error for autoBidId={}", autoBidId, e);
      return false;
    }
  }

  /**
   * Map một hàng {@link ResultSet} thành {@link AutoBidConfigDTO}.
   *
   * @param rs result set đã được định vị tại hàng cần đọc
   * @return {@link AutoBidConfigDTO} được điền đầy đủ
   * @throws SQLException nếu tên cột không tồn tại hoặc lỗi đọc dữ liệu
   */
  private AutoBidConfigDTO mapRow(ResultSet rs) throws SQLException {
    return new AutoBidConfigDTO(
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
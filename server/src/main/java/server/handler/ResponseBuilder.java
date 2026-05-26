package server.handler;

import java.time.LocalDateTime;
import server.common.ProtocolConstants;
import java.math.BigDecimal;
import server.common.enums.AuctionStatus;

/**
 * Builder tạo response message theo protocol chuẩn.
 */
public final class ResponseBuilder {
    
    private ResponseBuilder() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    // ==================== LOGIN ====================
    
    /**
     * Tạo response login thành công.
     *
     * @param userId   ID người dùng
     * @param username Tên đăng nhập
     * @param role     Vai trò (USER/ADMIN)
     * @param fullName Họ tên
     * @return "LOGIN_SUCCESS userId|username|role|fullName"
     */
    public static String loginSuccess(int userId, String username, String role, String fullName) {
        return String.format("%s %d|%s|%s|%s",
            ProtocolConstants.LOGIN_SUCCESS, userId, username, role, fullName);
    }
    
    /**
     * Tạo response login thất bại.
     *
     * @param reason Lý do thất bại
     * @return "LOGIN_FAIL reason"
     */
    public static String loginFail(String reason) {
        return String.format("%s %s", ProtocolConstants.LOGIN_FAIL, reason);
    }
    
    // ==================== REGISTER ====================
    
    /**
     * Tạo response register thành công.
     *
     * @param userId   ID người dùng mới
     * @param username Tên đăng nhập
     * @return "REGISTER_SUCCESS userId|username"
     */
    public static String registerSuccess(int userId, String username) {
        return String.format("%s %d|%s", ProtocolConstants.REGISTER_SUCCESS, userId, username);
    }
    
    /**
     * Tạo response register thất bại.
     *
     * @param reason Lý do thất bại
     * @return "REGISTER_FAIL reason"
     */
    public static String registerFail(String reason) {
        return String.format("%s %s", ProtocolConstants.REGISTER_FAIL, reason);
    }
    
    // ==================== BID ====================
    
    /**
     * Tạo response đặt giá thành công.
     *
     * @param auctionId ID phiên đấu giá
     * @param amount    Số tiền đặt
     * @return "BID_SUCCESS auctionId amount"
     */
    public static String bidSuccess(int auctionId, BigDecimal amount) {
        return String.format("%s %d %s", ProtocolConstants.BID_SUCCESS, auctionId, amount);
    }

    /**
     * Tạo response đặt giá thất bại.
     * Payload: "BID_FAIL auctionId reason"
     *
     * <p>auctionId được đưa vào payload để client biết phiên nào bị lỗi —
     * cần thiết khi user mở nhiều cửa sổ auction cùng lúc.
     * Dùng {@code -1} khi auctionId chưa parse được (lỗi format).</p>
     *
     * @param auctionId ID phiên đấu giá; -1 nếu chưa xác định được
     * @param reason    Mã lý do từ {@link ProtocolConstants} BID_REASON_*
     * @return "BID_FAIL auctionId reason"
     */
    public static String bidFail(int auctionId, String reason) {
        return String.format("%s %d %s", ProtocolConstants.BID_FAIL, auctionId, reason);
    }

    /**
     * Overload tiện dụng khi auctionId chưa parse được (lỗi format đầu vào).
     * Payload: "BID_FAIL -1 reason"
     *
     * @param reason Mã lý do từ {@link ProtocolConstants} BID_REASON_*
     * @return "BID_FAIL -1 reason"
     */
    public static String bidFail(String reason) {
        return bidFail(-1, reason);
    }
    
    // ==================== LIST ====================
    
    /**
     * Bắt đầu danh sách.
     *
     * @return "LIST_BEGIN"
     */
    public static String listBegin() {
        return ProtocolConstants.LIST_BEGIN;
    }
    
    /**
     * Kết thúc danh sách.
     *
     * @return "LIST_END"
     */
    public static String listEnd() {
        return ProtocolConstants.LIST_END;
    }
    
    /**
     * Một item trong danh sách.
     *
     * @param auctionId ID phiên
     * @param itemName  Tên vật phẩm
     * @param price     Giá hiện tại
     * @param seller    Người bán
     * @return "ITEM auctionId|itemName|price|seller"
     */
    public static String listItem(String auctionId, String itemName, String price, String seller) {
        return String.format("%s %s|%s|%s|%s",
            ProtocolConstants.ITEM, auctionId, itemName, price, seller);
    }
    
    // ==================== BROADCAST ====================
    
    /**
     * Broadcast có giá đặt mới.
     *
     * @param username  Tên người đặt
     * @param auctionId ID phiên
     * @param amount    Số tiền đặt
     * @return "NEW_BID username auctionId amount"
     */
    public static String newBid(String username, int auctionId, BigDecimal amount) {
        return String.format("%s %s %d %s",
            ProtocolConstants.NEW_BID, username, auctionId, amount);
    }

    // ==================== REALTIME AUCTION STATE ====================

    /**
     * Push trạng thái phiên sau mỗi bid thành công — broadcast tới auction watchers.
     *
     * <p>Client nhận message này để cập nhật toàn bộ state liên quan đến giá mà không cần
     * gọi lại JOIN_AUCTION: currentPrice, leader, countdown timer và bid count đều có đủ.</p>
     *
     * <p>Format: {@code AUCTION_BID_UPDATE|auctionId|currentPrice|leaderId|leaderName|endTime|totalBids|isAutoBid}</p>
     *
     * @param auctionId    ID phiên đấu giá
     * @param currentPrice giá hiện tại sau bid (plain decimal, không ký tự khoa học)
     * @param leaderId     ID người đang dẫn đầu; -1 nếu chưa có
     * @param leaderName   username của leader; "None" nếu chưa có
     * @param endTime      thời điểm kết thúc, có thể đã bị anti-sniping extend
     * @param totalBids    tổng số bid của phiên tính đến thời điểm này
     * @param isAutoBid    true nếu bid vừa đặt là auto-bid
     * @return chuỗi protocol AUCTION_BID_UPDATE
     */
    public static String auctionBidUpdate(int auctionId, BigDecimal currentPrice, int leaderId, String leaderName, LocalDateTime endTime, int totalBids, boolean isAutoBid) {return String.format("%s|%d|%s|%d|%s|%s|%d|%s",
            ProtocolConstants.AUCTION_BID_UPDATE,
            auctionId,
            currentPrice.toPlainString(),
            leaderId,
            leaderName != null ? leaderName : "None",
            endTime.toString(),
            totalBids,
            isAutoBid ? "AUTO" : "MANUAL");
    }

    /**
     * Push trạng thái đóng phiên — broadcast tới auction watchers.
     *
     * <p>Client nhận message này để render màn hình kết quả: hiển thị winner,
     * giá cuối, và chuyển trạng thái UI sang FINISHED/CANCELED mà không cần poll lại.</p>
     *
     * <p>Format: {@code AUCTION_CLOSED_UPDATE|auctionId|finalStatus|finalPrice|winnerId|winnerName}</p>
     *
     * @param auctionId   ID phiên đấu giá
     * @param finalStatus FINISHED hoặc CANCELED
     * @param finalPrice  giá cuối cùng
     * @param winnerId    ID người thắng; -1 nếu CANCELED hoặc không có winner
     * @param winnerName  username của người thắng; "None" nếu không có
     * @return chuỗi protocol AUCTION_CLOSED_UPDATE
     */
    public static String auctionClosedUpdate(int auctionId, AuctionStatus finalStatus, BigDecimal finalPrice, int winnerId, String winnerName) {
        return String.format("%s|%d|%s|%s|%d|%s",
            ProtocolConstants.AUCTION_CLOSED_UPDATE,
            auctionId,
            finalStatus.name(),
            finalPrice.toPlainString(),
            winnerId,
            winnerName != null ? winnerName : "None");
    }
    
    // ==================== ADMIN ====================
    
    /**
     * Response admin ban user thành công.
     *
     * @param targetUserId ID user bị ban
     * @return "ADMIN_BAN_SUCCESS targetUserId"
     */
    public static String adminBanSuccess(int targetUserId) {
        return String.format("%s %d", ProtocolConstants.ADMIN_BAN_SUCCESS, targetUserId);
    }
    
    /**
     * Response admin ban user thất bại.
     *
     * @param reason Lý do
     * @return "ADMIN_BAN_FAIL reason"
     */
    public static String adminBanFail(String reason) {
        return String.format("%s %s", ProtocolConstants.ADMIN_BAN_FAIL, reason);
    }
    
    // ==================== HEARTBEAT ====================
    
    /**
     * Response PONG cho heartbeat.
     *
     * @return "PONG"
     */
    public static String pong() {
        return ProtocolConstants.PONG;
    }

    /**
     * Tạo chuỗi thông báo bắt đầu đồng bộ dữ liệu biểu đồ.
     * @return chuỗi lệnh HISTORY_START
     */
    public static String historyStart() {
        return ProtocolConstants.HISTORY_START;
    }

    /**
     * Tạo chuỗi phẳng gói gọn một điểm tọa độ biểu đồ quá khứ.
     * @param bidTimeStr chuỗi thời gian định dạng HH:mm:ss
     * @param amount mức giá tiền cụ thể
     * @return chuỗi định dạng phẳng khớp cấu trúc hệ thống
     */
    public static String historyItem(final String bidTimeStr, final java.math.BigDecimal amount) {
        return String.format("%s %s|%s", ProtocolConstants.HISTORY_ITEM, bidTimeStr, amount.toPlainString());
    }

    /**
     * Tạo chuỗi thông báo kết thúc luồng truyền dữ liệu biểu đồ.
     * @return chuỗi lệnh HISTORY_END
     */
    public static String historyEnd() {
        return ProtocolConstants.HISTORY_END;
    }

}
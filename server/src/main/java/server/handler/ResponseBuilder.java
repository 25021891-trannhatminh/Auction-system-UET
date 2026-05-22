package server.handler;

import server.common.ProtocolConstants;
import java.math.BigDecimal;

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
     *
     * @param reason Lý do thất bại
     * @return "FAIL reason"
     */
    public static String bidFail(String reason) {
        return String.format("%s %s", ProtocolConstants.BID_FAIL, reason);
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
}
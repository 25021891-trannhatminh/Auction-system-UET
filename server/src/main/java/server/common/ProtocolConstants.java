package server.common;

/**
 * Hằng số giao thức giữa Client và Server.
 * Dùng chung cho cả Backend và Frontend.
 */
public final class ProtocolConstants {
    
    private ProtocolConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    // ========== USER_ID ==========
    public static final int NOTIFICATION_GLOBAL_USER_ID = -1;
    public static final int NOTIFICATION_AUCTION_USER_ID = 0;

    // ========== LOGIN ==========
    public static final String LOGIN = "LOGIN";
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAIL = "LOGIN_FAIL";
    
    // ========== REGISTER ==========
    public static final String REGISTER = "REGISTER";
    public static final String REGISTER_SUCCESS = "REGISTER_SUCCESS";
    public static final String REGISTER_FAIL = "REGISTER_FAIL";

    // ========== JOIN_AUCTION ==========
    public static final String JOIN_AUCTION =  "JOIN_AUCTION";
    public static final String JOIN_AUCTION_SUCCESS =  "JOIN_AUCTION_SUCCESS";
    public static final String JOIN_AUCTION_FAIL = "JOIN_AUCTION_FAIL";

    // ========== LEAVE_AUCTION ==========
    public static final String LEAVE_AUCTION =  "LEAVE_AUCTION";
    public static final String LEAVE_AUCTION_SUCCESS =  "LEAVE_AUCTION_SUCCESS";
    public static final String LEAVE_AUCTION_FAIL = "LEAVE_AUCTION_FAIL";

    // --- Reason codes cho BID_FAIL (client dùng để hiển thị message đúng) ---
    public static final String BID_REASON_NOT_LOGGED_IN      = "NOT_LOGGED_IN";
    public static final String BID_REASON_INVALID_FORMAT     = "INVALID_FORMAT";
    public static final String BID_REASON_AMOUNT_NOT_POSITIVE = "AMOUNT_NOT_POSITIVE";
    public static final String BID_REASON_USER_OFFLINE        = "USER_OFFLINE";
    public static final String BID_REASON_USER_NOT_FOUND      = "USER_NOT_FOUND";
    public static final String BID_REASON_AUCTION_CLOSED      = "AUCTION_CLOSED";
    public static final String BID_REASON_AUCTION_NOT_FOUND   = "AUCTION_NOT_FOUND";
    public static final String BID_REASON_BELOW_MIN_INCREMENT = "BELOW_MIN_INCREMENT";
    public static final String BID_REASON_BELOW_CURRENT_PRICE = "BELOW_CURRENT_PRICE";
    public static final String BID_REASON_OWN_AUCTION         = "OWN_AUCTION";
    public static final String BID_REASON_DB_FAILED            = "DB_PERSIST_FAILED";
    public static final String BID_REASON_SYSTEM_ERROR         = "SYSTEM_ERROR";

    // ========== BID ==========
    public static final String BID = "BID";
    public static final String BID_SUCCESS = "BID_SUCCESS";
    public static final String BID_FAIL = "BID_FAIL";
    
    // ========== LIST ==========
    public static final String LIST = "LIST";
    public static final String LIST_BEGIN = "LIST_BEGIN";
    public static final String LIST_END = "LIST_END";
    public static final String ITEM = "ITEM";
    
    // ========== AUTO-BID ==========
    public static final String AUTOBID_SUCCESS = "AUTOBID_SUCCESS";
    public static final String AUTOBID_FAIL = "AUTOBID_FAIL";
    public static final String AUTOBID_REGISTER = "AUTOBID_REGISTER";
    public static final String AUTOBID_CANCEL = "AUTOBID_CANCEL";
    
    // ========== ADMIN ==========
    public static final String ADMIN_BAN_USER = "ADMIN_BAN_USER";
    public static final String ADMIN_BAN_SUCCESS = "ADMIN_BAN_SUCCESS";
    public static final String ADMIN_BAN_FAIL = "ADMIN_BAN_FAIL";

    public static final String ADMIN_UNBAN_USER = "ADMIN_UNBAN_USER";
    public static final String ADMIN_UNBAN_SUCCESS = "ADMIN_UNBAN_SUCCESS";
    public static final String ADMIN_UNBAN_FAIL = "ADMIN_UNBAN_FAIL";
    
    // ========== BROADCAST ==========
    public static final String NEW_BID = "NEW_BID";
    public static final String AUCTION_CLOSED = "AUCTION_CLOSED";

    // ========== REALTIME AUCTION STATE ==========
    public static final String AUCTION_BID_UPDATE = "AUCTION_BID_UPDATE";
    public static final String AUCTION_CLOSED_UPDATE = "AUCTION_CLOSED_UPDATE";
    
    // ========== HEARTBEAT ==========
    public static final String PING = "PING";
    public static final String PONG = "PONG";
    
    // ========== COMMON ==========
    public static final String FAIL_NOT_LOGGED_IN = "NOT_LOGGED_IN";
    public static final String FAIL_INVALID_FORMAT = "INVALID_FORMAT";
}
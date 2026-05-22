package server.common;

/**
 * Hằng số giao thức giữa Client và Server.
 * Dùng chung cho cả Backend và Frontend.
 */
public final class ProtocolConstants {
    
    private ProtocolConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    // ========== LOGIN ==========
    public static final String LOGIN = "LOGIN";
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAIL = "LOGIN_FAIL";
    
    // ========== REGISTER ==========
    public static final String REGISTER = "REGISTER";
    public static final String REGISTER_SUCCESS = "REGISTER_SUCCESS";
    public static final String REGISTER_FAIL = "REGISTER_FAIL";
    
    // ========== BID ==========
    public static final String BID = "BID";
    public static final String BID_SUCCESS = "BID_SUCCESS";
    public static final String BID_FAIL = "FAIL";
    
    // ========== LIST ==========
    public static final String LIST = "LIST";
    public static final String LIST_BEGIN = "LIST_BEGIN";
    public static final String LIST_END = "LIST_END";
    public static final String ITEM = "ITEM";
    
    // ========== AUTO-BID ==========
    public static final String AUTOBID = "AUTOBID";
    public static final String AUTOBID_SUCCESS = "AUTOBID_SUCCESS";
    public static final String CANCEL_AUTOBID = "CANCEL_AUTOBID";
    public static final String CANCEL_AUTOBID_SUCCESS = "CANCEL_AUTOBID_SUCCESS";
    
    // ========== ADMIN ==========
    public static final String ADMIN_BAN_USER = "ADMIN_BAN_USER";
    public static final String ADMIN_BAN_SUCCESS = "ADMIN_BAN_SUCCESS";
    public static final String ADMIN_BAN_FAIL = "ADMIN_BAN_FAIL";
    
    // ========== BROADCAST ==========
    public static final String NEW_BID = "NEW_BID";
    public static final String AUCTION_CLOSED = "AUCTION_CLOSED";
    
    // ========== HEARTBEAT ==========
    public static final String PING = "PING";
    public static final String PONG = "PONG";
    
    // ========== COMMON ==========
    public static final String FAIL_NOT_LOGGED_IN = "NOT_LOGGED_IN";
    public static final String FAIL_INVALID_FORMAT = "INVALID_FORMAT";
}
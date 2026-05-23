package server.common;

/**
 * Hằng số phản hồi đấu giá - Dùng chung cho Backend và Frontend
 * UI dựa vào các hằng số này để xử lý hiển thị giao diện
 */
public final class AuctionConstants {
    
    private AuctionConstants() {} // Không cho khởi tạo

    /** Tham gia phòng đấu giá */
    public static final String JOIN_AUCTION = "JOIN_AUCTION";
    
    // ========== THÀNH CÔNG ==========
    /** Đặt giá thành công - Format: BID_SUCCESS auctionId amount */
    public static final String BID_SUCCESS = "BID_SUCCESS";
    
    // ========== THẤT BẠI ==========
    /** Chưa đăng nhập */
    public static final String FAIL_NOT_LOGGED_IN = "FAIL NOT_LOGGED_IN";
    
    /** Số tiền phải lớn hơn 0 */
    public static final String FAIL_AMOUNT_MUST_BE_POSITIVE = "FAIL AMOUNT_MUST_BE_POSITIVE";
    
    /** Không tìm thấy người dùng */
    public static final String FAIL_USER_NOT_FOUND = "FAIL USER_NOT_FOUND";
    
    /** Phiên đấu giá đã đóng */
    public static final String FAIL_AUCTION_CLOSED = "FAIL AUCTION_CLOSED";
    
    /** Số tiền không hợp lệ */
    public static final String FAIL_INVALID_AMOUNT = "FAIL INVALID_AMOUNT";
    
    /** Format lệnh sai */
    public static final String FAIL_INVALID_BID_FORMAT = "FAIL INVALID_BID_FORMAT";
    
    /** Đặt giá thất bại (lý do khác) */
    public static final String FAIL_BID_FAILED = "FAIL BID_FAILED";
}
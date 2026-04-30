package enums;

public enum AutoBidStatus {
    ACTIVE,     /* Đăng ký thành công, đang chạy */
    COMPLETED,  /* Chạy xong (Hết Auction hoặc > maxAutoBid) */
    CANCELED    /* Bidder hủy */
}

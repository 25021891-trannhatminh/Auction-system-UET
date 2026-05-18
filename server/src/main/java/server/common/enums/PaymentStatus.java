package server.common.enums;

public enum PaymentStatus {
    PENDING,    /* Đang xử lý */
    COMPLETED,  /* Hoàn thành */
    FAILED,     /* Thất bại */
    REFUNDED    /* Hoàn tiền */
}
package server.common.enums;

public enum AuctionStatus {

    OPEN,       /* Mở (seller push lên) + chưa bắt đầu (chờ admin accept) */
    RUNNING,    /* Sẵn sàng (Admin accept) */
    FINISHED,   /* Hết time — chờ thanh toán */
    PAID,       /* Đã thanh toán */
    CANCELED    /* Bị hủy (không ai bid, hoặc admin force) */
}
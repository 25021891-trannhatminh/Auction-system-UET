package server.common.model;

import server.common.enums.PaymentStatus;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * DTO đại diện cho bảng PAYMENTS.
 *
 * Dùng để truyền dữ liệu thanh toán giữa các layer.
 * Mỗi bản ghi thể hiện một giao dịch thanh toán của auction.
 */
public class PaymentDTO {

    /** ID thanh toán (PK). */
    private int paymentId;

    /** ID auction liên quan (FK -> AUCTIONS.auction_id). */
    private int auctionId;

    /** ID người mua (winner). */
    private int buyerId;

    /** ID người bán (seller). */
    private int sellerId;

    /** Số tiền thanh toán. */
    private BigDecimal amount;

    /** Trạng thái thanh toán (PENDING, COMPLETED, FAILED, REFUNDED). */
    private PaymentStatus status;

    /** Thời điểm thanh toán hoàn tất (có thể null nếu chưa trả). */
    private Timestamp paidAt;

    /** Thời điểm tạo bản ghi. */
    private Timestamp createdAt;

    /** Constructor rỗng. */
    public PaymentDTO() {}

    /**
     * Constructor dùng khi load dữ liệu từ DB.
     */
    public PaymentDTO(int paymentId, int auctionId, int buyerId, int sellerId,
        BigDecimal amount, PaymentStatus status,
        Timestamp paidAt, Timestamp createdAt) {
        this.paymentId = paymentId;
        this.auctionId = auctionId;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.amount = amount;
        this.status = status;
        this.paidAt = paidAt;
        this.createdAt = createdAt;
    }

    public int getPaymentId() { return paymentId; }
    public void setPaymentId(int paymentId) { this.paymentId = paymentId; }

    public int getAuctionId() { return auctionId; }
    public void setAuctionId(int auctionId) { this.auctionId = auctionId; }

    public int getBuyerId() { return buyerId; }
    public void setBuyerId(int buyerId) { this.buyerId = buyerId; }

    public int getSellerId() { return sellerId; }
    public void setSellerId(int sellerId) { this.sellerId = sellerId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public Timestamp getPaidAt() { return paidAt; }
    public void setPaidAt(Timestamp paidAt) { this.paidAt = paidAt; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
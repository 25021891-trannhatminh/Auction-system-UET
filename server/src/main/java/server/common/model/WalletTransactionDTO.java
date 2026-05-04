package server.common.model;

import server.common.enums.WalletTransactionType;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * DTO đại diện cho bảng WALLET_TRANSACTIONS.
 *
 * Dùng để lưu và truyền dữ liệu lịch sử giao dịch ví.
 * Mỗi bản ghi thể hiện một thay đổi số dư (deposit, withdraw, hold, release...).
 */
public class WalletTransactionDTO {

    /** ID giao dịch (PK). */
    private int txId;

    /** ID ví (FK -> WALLETS.wallet_id). */
    private int walletId;

    /** Loại giao dịch (DEPOSIT, WITHDRAW, HOLD, RELEASE). */
    private WalletTransactionType type;

    /** Số tiền giao dịch. */
    private BigDecimal amount;

    /**
     * ID auction liên quan (nullable).
     * Dùng cho các giao dịch đấu giá như HOLD/RELEASE.
     */
    private Integer refAuctionId;

    /** Ghi chú bổ sung. */
    private String note;

    /** Thời điểm tạo giao dịch. */
    private Timestamp createdAt;

    /** Constructor rỗng. */
    public WalletTransactionDTO() {}

    /**
     * Constructor dùng khi load dữ liệu từ DB.
     */
    public WalletTransactionDTO(int txId, int walletId,
        WalletTransactionType type, BigDecimal amount,
        Integer refAuctionId, String note,
        Timestamp createdAt) {

        this.txId = txId;
        this.walletId = walletId;
        this.type = type;
        this.amount = amount;
        this.refAuctionId = refAuctionId;
        this.note = note;
        this.createdAt = createdAt;
    }

    public int getTxId() { return txId; }
    public void setTxId(int txId) { this.txId = txId; }

    public int getWalletId() { return walletId; }
    public void setWalletId(int walletId) { this.walletId = walletId; }

    public WalletTransactionType getType() { return type; }
    public void setType(WalletTransactionType type) { this.type = type; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Integer getRefAuctionId() { return refAuctionId; }
    public void setRefAuctionId(Integer refAuctionId) { this.refAuctionId = refAuctionId; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
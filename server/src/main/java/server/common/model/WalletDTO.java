package server.common.model;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * DTO đại diện cho bảng WALLETS.
 *
 * Dùng để truyền dữ liệu ví của user giữa các layer.
 * Mỗi user có một ví lưu số dư hiện tại.
 */
public class WalletDTO {

    /** ID ví (PK). */
    private int walletId;

    /** ID người dùng (FK -> USERS.user_id). */
    private int userId;

    /** Số dư hiện tại của ví. */
    private BigDecimal balance;

    /** Thời điểm cập nhật số dư gần nhất. */
    private Timestamp updatedAt;

    /** Constructor rỗng. */
    public WalletDTO() {}

    /**
     * Constructor dùng khi load dữ liệu từ DB.
     */
    public WalletDTO(int walletId, int userId,
        BigDecimal balance, Timestamp updatedAt) {
        this.walletId = walletId;
        this.userId = userId;
        this.balance = balance;
        this.updatedAt = updatedAt;
    }

    public int getWalletId() { return walletId; }
    public void setWalletId(int walletId) { this.walletId = walletId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
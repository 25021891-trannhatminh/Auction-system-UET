package server.common.entity;

import server.common.enums.AccountRole;
import server.common.enums.UserStatus;
import server.common.entity.exception.InsufficientBalanceException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Tài khoản người dùng thông thường — đóng vai trò Bidder, Seller.
 *
 * <p>DB: {@code listedItemIds} ánh xạ sang cột {@code items.seller_id}.
 * DAO sẽ lazy-load danh sách item khi cần.
 * UI: hiển thị trên trang profile của Seller.</p>
 */
public class User extends Account {
    private BigDecimal balance;
    private final Map<Integer, AutoBidConfig> autoBidMap;    // <AuctionID, AutoBidConfig>
    private double rating;            // 0.0 – 5.0
    private final List<String> itemIDs; // FK sang items.item_id

    public User(String username, String email, String passwordHash,
                String fullName, String phone) {
        super(username, email, passwordHash, fullName, phone, AccountRole.USER);
        this.rating        = 0.0;
        this.itemIDs = new ArrayList<>();
        this.balance    = BigDecimal.ZERO;
        this.autoBidMap = new HashMap<>();
    }

    public User (User other){
        super(other);
        this.rating        = other.rating;
        this.itemIDs = new ArrayList<>(other.itemIDs);
        this.balance    = other.balance;
        this.autoBidMap = new HashMap<>(other.autoBidMap);
    }

    /** Constructor dùng khi load từ DB (không có balance). */
    public User(int id, LocalDateTime createdAt,
                String username, String email, String passwordHash,
                String fullName, String phone, AccountRole role,
                UserStatus status, LocalDateTime lastLogin) {
        super(id, createdAt, username, email, passwordHash, fullName, phone,
            role, status, lastLogin);
        this.rating        = 0.0;
        this.itemIDs = new ArrayList<>();
        this.balance    = BigDecimal.ZERO;
        this.autoBidMap = new HashMap<>();
    }

    /** Constructor dùng khi load từ DB (có đủ rating và balance). */
    public User(int id, LocalDateTime createdAt,
                String username, String email, String passwordHash,
                String fullName, String phone, AccountRole role,
                UserStatus status, LocalDateTime lastLogin,double rating, BigDecimal balance) {
        super(id, createdAt, username, email, passwordHash, fullName, phone,
            role, status, lastLogin);
        this.rating        = rating;
        this.itemIDs = new ArrayList<>();
        this.balance    = balance;
        this.autoBidMap = new HashMap<>();
    }

    // ── Item management ───────────────────────────────────────────────────────

    public void addItem(String itemId)    { itemIDs.add(itemId); }
    public void removeItem(String itemId) { itemIDs.remove(itemId); }

    public double          getRating()       { return rating; }

    /** Trả về view bất biến của danh sách item ID — caller không thể sửa trực tiếp. */
    public List<String>    getItemIds(){ return Collections.unmodifiableList(itemIDs); }

    public void updateRating(double newRating) {
        if (newRating < 0 || newRating > 5)
            throw new IllegalArgumentException("Rating must be between 0 and 5");
        this.rating = newRating;
    }

    // ── Balance management ────────────────────────────────────────────────────

    public BigDecimal getBalance() { return balance; }

    /**
     * Nạp tiền vào ví người dùng.
     *
     * @param amount số tiền nạp, phải dương
     * @throws IllegalArgumentException nếu {@code amount <= 0}
     */
    public void deposit(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Deposit amount must be positive");
        this.balance = balance.add(BigDecimal.valueOf(amount));
    }

    /**
     * Trừ tiền từ ví người dùng (thanh toán).
     *
     * @param amount số tiền cần trừ, phải dương
     * @throws IllegalArgumentException      nếu {@code amount <= 0}
     * @throws InsufficientBalanceException  nếu số dư không đủ
     */
    public void debit(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Debit amount must be positive");
        if (this.balance.compareTo(BigDecimal.valueOf(amount)) < 0)
            throw new InsufficientBalanceException(BigDecimal.valueOf(amount), this.balance);
        this.balance = balance.subtract(BigDecimal.valueOf(amount));
    }


    // ── Auto-Bid management ───────────────────────────────────────────────────

    /**
     * Thêm hoặc ghi đè {@link AutoBidConfig} cho một phiên đấu giá.
     *
     * <p>Nếu đã có config cũ cho {@code auctionId} → ghi đè bằng config mới.</p>
     *
     * @param config config auto-bid mới cần lưu
     */
    public void setAutoBid(AutoBidConfig config) {
        autoBidMap.put(config.getAuctionId(), config);
    }

    /**
     * Hủy auto-bid cho một phiên cụ thể và xóa khỏi map.
     *
     * @param auctionId ID phiên cần hủy auto-bid
     */
    public void cancelAutoBid(int auctionId) {
        AutoBidConfig config = autoBidMap.get(auctionId);
        if (config != null) {
            config.cancel();
            autoBidMap.remove(auctionId);
        }
    }

    /** Lấy config auto-bid đang active trong một phiên, hoặc {@code null} nếu không có. */
    public AutoBidConfig getAutoBidConfig(int auctionId) {
        return autoBidMap.getOrDefault(auctionId, null);
    }

    public boolean hasAutoBid(int auctionId) {
        return autoBidMap.containsKey(auctionId);
    }
}

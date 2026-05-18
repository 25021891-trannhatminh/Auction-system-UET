package server.common.entity;

import server.common.enums.AccountRole;
import server.common.enums.UserStatus;
import server.common.entity.exception.InsufficientBalanceException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/*
    User: Bidder, Seller nói chung
        DB: listedItemIds ánh xạ sang items.seller_id.
        DAO sẽ lazy-load danh sách item khi cần.
        UI: hiển thị trên profile trang Seller.
 */
public class User extends Account {
    private BigDecimal balance;
    private final Map<String, AutoBidConfig> autoBidMap;    // <AuctionID, AutoBidConfig>
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
    /* Constructor dùng khi load từ DB */
    public User(String id, LocalDateTime createdAt,
                String username, String email, String passwordHash,
                String fullName, String phone,
                UserStatus status, LocalDateTime lastLogin) {
        super(id, createdAt, username, email, passwordHash, fullName, phone,
            AccountRole.USER, status, lastLogin);
        this.rating        = 0.0;
        this.itemIDs = new ArrayList<>();
        this.balance    = BigDecimal.ZERO;
        this.autoBidMap = new HashMap<>();
    }
    public User(String id, LocalDateTime createdAt,
                   String username, String email, String passwordHash,
                   String fullName, String phone,
                   UserStatus status, LocalDateTime lastLogin,double rating, BigDecimal balance) {
        super(id, createdAt, username, email, passwordHash, fullName, phone,
            AccountRole.USER, status, lastLogin);
        this.rating        = rating;
        this.itemIDs = new ArrayList<>();
        this.balance    = balance;
        this.autoBidMap = new HashMap<>();
    }


    // == ITEM ==
    public void addItem(String itemId)    { itemIDs.add(itemId); }
    public void removeItem(String itemId) { itemIDs.remove(itemId); }

    public double          getRating()       { return rating; }
    public List<String>    getItemIds(){ return Collections.unmodifiableList(itemIDs); }    // Return 1 tham chiếu (view) đến List (nhưng đã Override các phương thức để không thể sửa đổi)

    public void updateRating(double newRating) {
        if (newRating < 0 || newRating > 5)
            throw new IllegalArgumentException("Rating must be between 0 and 5");
        this.rating = newRating;
    }


    // == BID, BALANCE ==
    public BigDecimal getBalance() { return balance; }

    // Nạp tiền
    public void deposit(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Deposit amount must be positive");
        this.balance = balance.add(BigDecimal.valueOf(amount));
    }

    // Thanh toán
    //Ném InsufficientBalanceException nếu không đủ số dư.

    public void debit(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Debit amount must be positive");
        if (this.balance.compareTo(BigDecimal.valueOf(amount)) < 0)
            throw new InsufficientBalanceException(BigDecimal.valueOf(amount), this.balance);
        this.balance = balance.subtract(BigDecimal.valueOf(amount));
    }

    /* Kiểm tra nhanh mà không thay đổi state */
    public boolean canAfford(double amount) {
        return this.balance.compareTo(BigDecimal.valueOf(amount)) >= 0;
    }

    // __ Auto-Bid __

    /*
        Add hoặc update AutoBidConfig cho 1 Auction.
        Nếu đã có config cũ cho auctionId này → ghi đè .
     */
    public void setAutoBid(AutoBidConfig config) {
        autoBidMap.put(config.getAuctionId(), config);
    }

    /* Hủy auto-bid cho một phiên cụ thể */
    public void cancelAutoBid(String auctionId) {
        AutoBidConfig config = autoBidMap.get(auctionId);
        if (config != null) {
            config.cancel();
            autoBidMap.remove(auctionId);
        }
    }

    /* Lấy config auto-bid đang active trong 1 auction */
    public AutoBidConfig getAutoBidConfig(String auctionId) {
        return autoBidMap.getOrDefault(auctionId, null);
    }

    public boolean hasAutoBid(String auctionId) {
        return autoBidMap.containsKey(auctionId);
    }

}

package model.user;

import enums.UserRole;
import enums.UserStatus;
import exception.InsufficientBalanceException;
import model.AutoBidConfig;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/*
 Bidder 

    Quản lý số dư (balance) cục bộ trong core logic.
    DB: balance ánh xạ sang bảng wallets (wallet.balance).

    Lưu AutoBidConfig theo từng phiên đấu giá (auctionId → config).
    DB: config ánh xạ sang bảng auto_bids.

    UI hiển thị balance từ getter getBalance().
    UI gọi setAutoBid() khi người dùng điền form auto-bid.
    UI gọi cancelAutoBid() khi hủy.
 */
public class Bidder extends User {

    private double balance;

    /*
        Map auctionId → AutoBidConfig.
        Có thể auto-bid ở nhiều phiên khác nhau cùng lúc. Chỉ 1 config đang ACTIVE trong 1 Auction (UNIQUE constraint trong DB).
     */
    private final Map<String, AutoBidConfig> autoBidMap;    // <AuctionID, AutoBidConfig>

    public Bidder(String username, String email, String passwordHash,
                  String fullName, String phone) {
        super(username, email, passwordHash, fullName, phone, UserRole.BIDDER);
        this.balance    = 0.0;
        this.autoBidMap = new HashMap<>();
    }

    /** Constructor load từ DB */
    public Bidder(String id, LocalDateTime createdAt,
                  String username, String email, String passwordHash,
                  String fullName, String phone, UserStatus status,
                  LocalDateTime lastLogin, double balance) {
        super(id, createdAt, username, email, passwordHash, fullName, phone,
              UserRole.BIDDER, status, lastLogin);
        this.balance    = balance;
        this.autoBidMap = new HashMap<>();
    }

    // __ Balance __

    public double getBalance() { return balance; }


        // Nạp tiền
    public void deposit(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Deposit amount must be positive");
        this.balance += amount;
    }

    
        // Thanh toán
            //Ném InsufficientBalanceException nếu không đủ số dư.
    
    public void debit(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Debit amount must be positive");
        if (this.balance < amount)
            throw new InsufficientBalanceException(amount, this.balance);
        this.balance -= amount;
    }

    /* Kiểm tra nhanh mà không thay đổi state */
    public boolean canAfford(double amount) {
        return this.balance >= amount;
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

    /* Lấy config auto-bid đang active cho 1 auction */
    public AutoBidConfig getAutoBidConfig(String auctionId) {
        return autoBidMap.getOrDefault(auctionId, null);
    }

    public boolean hasAutoBid(String auctionId) {
        return autoBidMap.containsKey(auctionId);
    }

    @Override
    public void printInfo() {
        super.printInfo();
        System.out.printf("  Balance: %.2f | Auto-bid auctions: %d%n",
            balance, autoBidMap.size());
    }
}

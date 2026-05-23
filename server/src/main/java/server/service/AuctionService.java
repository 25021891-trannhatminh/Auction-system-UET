package server.service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import server.common.entity.Auction;
import server.common.entity.Item;
import server.common.entity.User;
import server.common.entity.AutoBidConfig;
import server.common.entity.BidTransaction;
import server.common.entity.exception.AuctionClosedException;
import server.common.entity.exception.InvalidBidException;
import server.common.entity.manager.AuctionManager;
import server.common.enums.AuctionStatus;
import server.common.enums.ItemStatus;
import server.common.model.AuctionDTO;
import server.common.model.BidHistoryDTO;
import server.common.model.BidResultDTO;
import server.common.model.PaymentDTO;
import server.database.DBConnection;
import server.repository.*;
import server.service.listeners.AuctionEventListener;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * AuctionService xử lý business logic cho đấu giá:
 * - Quản lý đặt giá, bắt đầu/kết thúc phiên, auto‑bid.
 * - Điều phối thông báo qua Observer Pattern.
 * - KHÔNG can thiệp vào luồng tài chính (thanh toán do PaymentService đảm nhiệm).
 */
public class AuctionService {

  private static final Logger logger = LoggerFactory.getLogger(AuctionService.class);

  // Observer listeners (thread‑safe)
  private final List<AuctionEventListener> listeners = new CopyOnWriteArrayList<>();

  // DAOs
  private final AuctionDAO         auctionDAO         = new AuctionDAO();
  private final BidTransactionDAO  bidTransactionDAO  = new BidTransactionDAO();
  private final AutoBidConfigDAO   autoBidConfigDAO   = new AutoBidConfigDAO();
  private final ItemDAO            itemDAO            = new ItemDAO();
  private final AccountDAO         accountDAO         = new AccountDAO();

  // Domain manager (singleton)
  private final AuctionManager auctionManager = AuctionManager.getInstance();
  /**
   * Chuyển đổi AuctionDTO (từ DB) thành Auction domain entity.
   * Yêu cầu ItemDAO và AccountDAO đã có sẵn.
   */
  private Auction toAuctionEntity(AuctionDTO dto) {
    Item item = itemDAO.getById(dto.getItemId());
    User winner = (dto.getCurrentWinnerId() != null)
        ? accountDAO.getUserById(dto.getCurrentWinnerId())
        : null;

    LocalDateTime lastBidTime = (dto.getLastBidTime() != null)
        ? dto.getLastBidTime().toLocalDateTime()
        : null;

    Auction auction = new Auction(
        String.valueOf(dto.getAuctionId()),
        dto.getCreatedAt().toLocalDateTime(),
        item,
        String.valueOf(dto.getSellerId()),
        dto.getStartTime().toLocalDateTime(),
        dto.getEndTime().toLocalDateTime(),
        lastBidTime,
        dto.getCurrentPrice(),
        dto.getMinBidIncrement(),
        dto.getReservePrice(),
        dto.getSnipeWindowSeconds(),
        dto.getSnipeExtensionSeconds(),
        dto.getStatus(),
        winner
    );
    // Khôi phục lịch sử bid từ DB (nếu cần)
    auction.restoreBidHistory(toBidTransactionList(bidTransactionDAO.getBidHistory(dto.getAuctionId())));
    return auction;
  }

  /**
   * Khởi tạo service, gắn callback vòng đời auction vào AuctionManager.
   * Callback giúp đồng bộ trạng thái RAM → DB và kích hoạt thông báo.
   */
  public AuctionService() {
    auctionManager.setOnAuctionStartedCallback(this::onAuctionStarted);
    auctionManager.setOnAuctionClosedCallback(this::onAuctionClosed);
    logger.info("AuctionService initialized – ready to manage auctions.");
  }

  // ==================== KHỞI ĐỘNG SERVER ====================

  /**
   * Load toàn bộ auction đang RUNNING/OPEN từ DB vào AuctionManager khi server khởi động.
   * Đồng thời load AutoBidConfig và đăng ký vào engine.
   */
  public void loadAllFromDatabase() {
    logger.info("Loading active auctions from database...");
    List<AuctionDTO> runningDTOs = auctionDAO.getByStatus(AuctionStatus.RUNNING);
    List<AuctionDTO> openDTOs    = auctionDAO.getByStatus(AuctionStatus.OPEN);
    runningDTOs.addAll(openDTOs);

    List<Auction> auctions = runningDTOs.stream()
        .map(this::toAuctionEntity)
        .toList();

    for (Auction auction : auctions) {
      loadAutoBidsForAuction(auction);
      auctionManager.loadAuction(auction);
      logger.info("Loaded auction {} with status {}", auction.getId(), auction.getStatus());
    }
    logger.info("Finished loading {} auction(s) into memory.", auctions.size());
  }

  private void loadAutoBidsForAuction(Auction auction) {
    int auctionId = Integer.parseInt(auction.getId());
    List<AutoBidConfig> configs = autoBidConfigDAO.getByAuction(auctionId);
    for (AutoBidConfig config : configs) {
      User bidder = accountDAO.getUserById(Integer.parseInt(config.getBidderId()));
      if (bidder == null) {
        logger.warn("Bidder {} not found, skipping auto‑bid config.", config.getBidderId());
        continue;
      }
      auctionManager.getAutoBidEngine().register(config, bidder);
    }
  }

  // ==================== TẠO AUCTION ====================

  public Auction createAuction(Item item, String sellerId,
      LocalDateTime startTime, LocalDateTime endTime,
      BigDecimal minBidIncrement, BigDecimal reservePrice,
      int snipeWindowSeconds, int snipeExtensionSeconds) {
    // 1. Validate nghiệp vụ (service layer)
    if (item == null || sellerId == null || startTime == null || endTime == null) {
      logger.error("createAuction() – invalid null parameters");
      return null;
    }
    if (item.getStatus() != ItemStatus.AVAILABLE) {
      logger.error("createAuction() – item {} is not AVAILABLE", item.getId());
      return null;
    }
    if (!item.getSellerId().equals(sellerId)) {
      logger.error("createAuction() – seller mismatch for item {}", item.getId());
      return null;
    }
    LocalDateTime now = LocalDateTime.now();
    if (!endTime.isAfter(startTime)) {
      logger.error("createAuction() – endTime must be after startTime");
      return null;
    }
    if (!endTime.isAfter(now)) {
      logger.error("createAuction() – endTime must be in the future");
      return null;
    }

    int itemIdInt = Integer.parseInt(item.getId());
    int sellerIdInt = Integer.parseInt(sellerId);

    // 2. Tính toán initial status
    AuctionStatus initialStatus = startTime.isAfter(now) ? AuctionStatus.OPEN : AuctionStatus.RUNNING;

    // 3. Gọi DAO thuần (không có business rule)
    AuctionDTO dto = auctionDAO.createAuction(
        itemIdInt, sellerIdInt,
        startTime, endTime,
        minBidIncrement, reservePrice,
        snipeWindowSeconds, snipeExtensionSeconds,
        initialStatus
    );

    if (dto == null) {
      logger.error("createAuction() – DB persistence failed for item {}", item.getId());
      return null;
    }

    // 4. Chuyển DTO thành entity và nạp vào RAM
    Auction saved = toAuctionEntity(dto);
    if (saved.getItem() != null) {
      saved.getItem().setStatus(ItemStatus.IN_AUCTION);
    }
    auctionManager.loadAuction(saved);
    logger.info("Auction {} created and loaded into manager.", saved.getId());
    return saved;
  }

  // ==================== ĐẶT GIÁ ====================
  /**
   * Entry point duy nhất cho đặt giá từ BidHandler.
   *
   * Flow cố định:
   *   1. BidTransactionService.executePlaceBidFlow()
   *        → DB lock (SELECT FOR UPDATE)
   *        → auction.placeBid() — core RAM logic
   *        → UPDATE auctions + INSERT bid_transactions
   *        → commit / rollback
   *   2. AutoBidEngine.trigger() — cascade auto-bid ngoài DB lock
   *   3. Persist auto-bid transaction xuống DB nếu engine vừa đặt
   *   4. Notify listener onBidPlaced
   *
   * Throws AuctionClosedException / InvalidBidException để BidHandler
   * trả reason cụ thể cho client thay vì BID_FAILED chung chung.
   */
  public boolean placeBid(int auctionId, int userId, BigDecimal amount, boolean isAutoBid) throws AuctionClosedException, InvalidBidException {
    Auction auction = findAuctionById(String.valueOf(auctionId));
    User bidder = findUserById(String.valueOf(userId));

    if (auction == null || bidder == null) {
      return false;
    }

    // ── Bước 1: DB lock + RAM core + DB sync ────────────────────────────────
    // executePlaceBidFlow tự quản lý snapshot + rollback RAM bên trong nếu DB fail.
    // Nếu ném exception nghiệp vụ → re-throw thẳng lên BidHandler.
    // Nếu trả null → lỗi hạ tầng (DB/lock), không rollback thêm gì ở đây.
    BidTransactionService txService = new BidTransactionService();
    BidTransaction manualTx = txService.executePlaceBidFlow(auctionId, userId, amount, isAutoBid);
    if (manualTx == null) {
      logger.error("placeBid() – infrastructure failure for auction {} by user {}", auctionId, userId);
      return false;
    }

    // ── Bước 2: Cascade AutoBid sau khi manual bid đã committed xuống DB ────
    // Snapshot trạng thái AUTO-BID trước trigger — dùng để rollback RAM auto-bid nếu persist fail.
    // Lưu ý: KHÔNG snapshot cho manual bid vì nó đã committed, không rollback.

    BigDecimal priceAfterManual = auction.getCurrentPrice();
    User leaderAfterManual = auction.getCurrentLeader();

    BidTransaction autoBidTx = null;
    try {
      autoBidTx = auctionManager.getAutoBidEngine().trigger(auction, bidder);
    } catch (InvalidBidException e) {
      logger.error("placeBid() – AutoBidEngine.trigger() threw exception for auction {}", auctionId, e);
      // trigger fail or autoBidTx = null → không persist, không rollback manual bid
    }

    // ── Bước 3: Persist auto-bid nếu engine vừa đặt ─────────────────────────
    if (autoBidTx != null) {
      boolean persisted = persistAutoBidTransaction(auction, autoBidTx);

      if (!persisted) {
        // DB fail khi ghi auto-bid → RAM đã bị engine thay đổi, cần rollback RAM về sau manual bid.
        // Manual bid KHÔNG bị rollback vì đã committed DB ở Bước 1.
        logger.warn("placeBid() – auto-bid persist failed, rolling back RAM to post-manual state " +
            "for auction {}", auctionId);
        auction.rollbackLastBid(autoBidTx, priceAfterManual, leaderAfterManual);
        // Không return false — manual bid vẫn thành công, chỉ auto-bid bị bỏ qua lần này.
      }
    }

      // Reschedule đóng nếu anti-snipe đã gia hạn endTime trong lúc bid
      if (auction.checkAndResetExtensionFlag()) {
        auctionManager.rescheduleClose(auction);
      }

    // ── Bước 4: Notify listener ───────────────────────────────────────────────
    String itemName = (auction.getItem() != null) ? auction.getItem().getName() : "Unknown";
    notifyBidPlaced(userId, auctionId, itemName, amount);
    return true;
  }

  // ==================== AUTO BID ====================

  public void registerAutoBid(AutoBidConfig config, User bidder) {
    int auctionId = Integer.parseInt(config.getAuctionId());
    int bidderId  = Integer.parseInt(config.getBidderId());

    // Persist config
    if (autoBidConfigDAO.hasActiveBid(auctionId, bidderId)) {
      autoBidConfigDAO.updateMaxBid(auctionId, bidderId, config.getMaxBid());
    } else {
      autoBidConfigDAO.create(config);
    }

    // Register in engine (may trigger immediate bid)
    Auction auction = findAuctionById(config.getAuctionId());
    auctionManager.registerAutoBid(config, bidder);
    triggerAutoBids(auction, auction.getCurrentLeader());
    logger.info("Auto‑bid registered for auction {} by user {}", auctionId, bidderId);
  }

  public void cancelAutoBid(String auctionId, User bidder) {
    // Trigger lại để tìm winner mới
    Auction auction = findAuctionById(auctionId);
    triggerAutoBids(auction, bidder);
    auctionManager.cancelAutoBid(auctionId, bidder);
    int auctionInt = Integer.parseInt(auctionId);
    int bidderInt  = Integer.parseInt(bidder.getId());
    autoBidConfigDAO.cancelByAuctionAndBidder(auctionInt, bidderInt);


  }

  // ==================== QUẢN LÝ USER TRONG AUCTION MANAGER ====================

  public void registerConnectedUser(User user) {
    auctionManager.registerUser(user);
  }

  public User registerOrGetUser(User user) {
    return auctionManager.registerOrGetUser(user);
  }

  public User findUserById(String userId) {
    return auctionManager.findUserById(userId).orElse(null);
  }

  public Auction findAuctionById(String auctionId) {
    return auctionManager.getAuction(auctionId).orElse(null);
  }

  // ==================== VÒNG ĐỜI AUCTION (CALLBACK TỪ MANAGER) ====================

  public void onAuctionStarted(Auction auction) {
    int auctionId = Integer.parseInt(auction.getId());
    auctionDAO.updateStatus(auctionId, AuctionStatus.RUNNING);
    String itemName = auction.getItem() != null ? auction.getItem().getName() : "Unknown";
    notifyAuctionStarted(Integer.parseInt(auction.getSellerId()), auctionId, itemName);
    triggerAutoBids(auction, null);   // Trigger khi auction bắt đầu
    logger.info("Auction {} started – item: {}", auctionId, itemName);
  }

  public void onAuctionClosed(Auction auction) {
    int auctionId = Integer.parseInt(auction.getId());
    AuctionStatus status = auction.getStatus();

    // 1. Persist trạng thái auction và item
    if (status == AuctionStatus.FINISHED) {
      Integer winnerId = auction.getCurrentLeader() != null ?
          Integer.parseInt(auction.getCurrentLeader().getId()) : null;
      auctionDAO.finishAuction(auctionId, winnerId);
      if (auction.getItem() != null) {
        auction.getItem().setStatus(ItemStatus.SOLD);
        itemDAO.updateStatus(Integer.parseInt(auction.getItem().getId()), ItemStatus.SOLD);
      }
    } else { // CANCELED
      auctionDAO.updateStatus(auctionId, AuctionStatus.CANCELED);
      if (auction.getItem() != null) {
        auction.getItem().setStatus(ItemStatus.AVAILABLE);
        itemDAO.updateStatus(Integer.parseInt(auction.getItem().getId()), ItemStatus.AVAILABLE);
      }
    }

    // 2. Huỷ auto‑bid trong DB
    int cancelled = autoBidConfigDAO.cancelAllByAuction(auctionId);
    logger.info("Auction {} closed ({}), cancelled {} auto‑bids.", auctionId, status, cancelled);

    // 3. Gửi thông báo phân nhánh
    String itemName = auction.getItem() != null ? auction.getItem().getName() : "Unknown";
    BigDecimal finalPrice = auction.getCurrentPrice();
    int sellerId = Integer.parseInt(auction.getSellerId());

    if (status == AuctionStatus.FINISHED) {
      dispatchFinishedNotifications(auction, auctionId, sellerId, itemName, finalPrice);
    } else {
      dispatchCanceledNotifications(auctionId, sellerId, itemName, finalPrice);
    }
  }

  // ==================== ADMIN / CƯỠNG CHẾ ====================

  public void forceCloseAuction(String auctionId, String reason) {
    auctionManager.forceCloseAuction(auctionId, reason);
  }

  public void approveItem(int sellerId, int itemId, String itemName) {
    notifyItemApproved(sellerId, itemId, itemName);
  }

  public void rejectItem(int sellerId, int itemId, String itemName) {
    notifyItemRejected(sellerId, itemId, itemName);
  }

  // ==================== KIỂM TRA THANH TOÁN (READ-ONLY) ====================

  /**
   * Kiểm tra xem auction đã được thanh toán thành công hay chưa.
   * Phương thức này chỉ đọc trạng thái, không gây bất kỳ thay đổi nào.
   *
   * @param auctionId ID phiên đấu giá
   * @return true nếu trạng thái auction là PAID
   */
  public boolean isPaymentConfirmed(int auctionId) {
    Optional<Auction> auctionOpt = auctionManager.getAuction(String.valueOf(auctionId));
    return auctionOpt.map(a -> a.getStatus() == AuctionStatus.PAID).orElse(false);
  }

  // ==================== BROADCAST HỆ THỐNG ====================

  public void broadcastSystemNotif(String title, String message) {
    notifySystemNotification(-1, title, message);
  }

  // ==================== OBSERVER PATTERN – NOTIFY METHODS ====================

  public void addListener(AuctionEventListener listener) {
    if (listener != null && !listeners.contains(listener)) {
      listeners.add(listener);
      logger.info("Listener registered: {}", listener.getClass().getSimpleName());
    }
  }

  public void removeListener(AuctionEventListener listener) {
    if (listeners.remove(listener)) {
      logger.info("Listener unregistered: {}", listener.getClass().getSimpleName());
    }
  }

  // Các phương thức gửi sự kiện đến tất cả listener
  private void dispatchFinishedNotifications(Auction auction, int auctionId,
      int sellerId, String itemName, BigDecimal finalPrice) {
    if (auction.getCurrentLeader() == null) {
      logger.warn("Finished auction {} without leader – notifying seller only", auctionId);
      notifyAuctionEnded(sellerId, auctionId, itemName, finalPrice);
      return;
    }

    int winnerId = Integer.parseInt(auction.getCurrentLeader().getId());
    // Notify winner
    try {
      notifyAuctionWon(winnerId, auctionId, itemName, finalPrice);
      notifyPaymentDue(winnerId, auctionId, itemName, finalPrice);
    } catch (Exception e) {
      logger.error("Failed to notify winner {} for auction {}", winnerId, auctionId, e);
    }

    // Notify từng người thua (riêng biệt để 1 lỗi không ảnh hưởng phần còn lại)
    List<Integer> allBidders = bidTransactionDAO.getBiddersByAuctionId(auctionId);
    for (int bidderId : allBidders) {
      if (bidderId != winnerId) {
        try {
          notifyAuctionLost(bidderId, auctionId, itemName);
        } catch (Exception e) {
          logger.error("Failed to notify loser {} for auction {}", bidderId, auctionId, e);
        }
      }
    }

    // Notify seller
    try {
      notifyAuctionEnded(sellerId, auctionId, itemName, finalPrice);
    } catch (Exception e) {
      logger.error("Failed to notify seller {} for auction {}", sellerId, auctionId, e);
    }
  }

  private void dispatchCanceledNotifications(int auctionId, int sellerId,
      String itemName, BigDecimal finalPrice) {
    try {
      notifyAuctionEnded(sellerId, auctionId, itemName, finalPrice);
    } catch (Exception e) {
      logger.error("Failed to notify seller {} about cancellation", sellerId, e);
    }
  }

  // Helper
  /**
   * Method trung tâm gọi AutoBidEngine.trigger() + persist DB
   */
  private void triggerAutoBids(Auction auction, User previousWinner) {
    if (auction == null || auction.getStatus() != AuctionStatus.RUNNING) {
      return;
    }

    try {
      BidTransaction autoBidTx = auctionManager.getAutoBidEngine()
          .trigger(auction, previousWinner);

      if (autoBidTx != null) {
        persistAutoBidTransaction(auction, autoBidTx);
      }
    } catch (Exception e) {
      logger.error("Auto-bid trigger failed for auction {}", auction.getId(), e);
    }
  }

  private boolean persistAutoBidTransaction(Auction auction, BidTransaction autoBidTx) {
    try (Connection conn = DBConnection.getConnection()) {
      conn.setAutoCommit(false);
      try {
        int autoBidderId = Integer.parseInt(autoBidTx.getBidderId());
        bidTransactionDAO.updateAuctionState(conn,
            Integer.parseInt(auction.getId()),
            autoBidderId,
            autoBidTx.getAmount(),
            auction.getEndTime());

        bidTransactionDAO.insertBidTransaction(conn, toBidHistoryDTO(autoBidTx));
        conn.commit();
        logger.info("Auto-bid persisted for auction {}", auction.getId());
        return true;
      } catch (Exception e) {
        conn.rollback();
        logger.error("Failed to persist auto-bid", e);
        return false;
      }
    } catch (SQLException e) {
      logger.error("DB connection error when persisting auto-bid", e);
      return false;
    }
  }

  private List<BidTransaction> toBidTransactionList(List<BidHistoryDTO> dtos) {
    List<BidTransaction> transactions = new ArrayList<>();
    for (BidHistoryDTO dto : dtos) {
      // Reuse the mapping logic from BidTransactionService (or replicate here)
      String bidderName = "Unknown";
      try {
        User user = accountDAO.getUserById(dto.getBidderId());
        if (user != null) bidderName = user.getFullName();
      } catch (Exception ignored) {}
      transactions.add(new BidTransaction(
          String.valueOf(dto.getBidId()),
          String.valueOf(dto.getAuctionId()),
          String.valueOf(dto.getBidderId()),
          bidderName,
          dto.getAmount(),
          dto.getBidTime().toLocalDateTime(),
          dto.isAutoBid(),
          dto.getStatus()
      ));
    }
    return transactions;
  }

  private BidHistoryDTO toBidHistoryDTO(BidTransaction tx) {
    BidHistoryDTO dto = new BidHistoryDTO();
    dto.setBidId(tx.getId() != null ? Integer.parseInt(tx.getId()) : 0);
    dto.setAuctionId(Integer.parseInt(tx.getAuctionId()));
    dto.setBidderId(Integer.parseInt(tx.getBidderId()));
    dto.setAmount(tx.getAmount());
    dto.setAutoBid(tx.isAutoBid());
    dto.setStatus(tx.getStatus());
    dto.setBidTime(Timestamp.valueOf(tx.getBidTime()));
    return dto;
  }

  // Các hàm notify cụ thể – gọi listener tương ứng
  public void notifyBidPlaced(int bidderId, int auctionId, String itemName, BigDecimal amount) {
    listeners.forEach(l -> l.onBidPlaced(bidderId, auctionId, itemName, amount));
  }

  public void notifyOutbid(int userId, int auctionId, String itemName, BigDecimal newPrice) {
    listeners.forEach(l -> l.onOutbid(userId, auctionId, itemName, newPrice));
  }

  public void notifyAuctionStarted(int userId, int auctionId, String itemName) {
    listeners.forEach(l -> l.onAuctionStarted(userId, auctionId, itemName));
  }

  public void notifyAuctionEnded(int userId, int auctionId, String itemName, BigDecimal finalPrice) {
    listeners.forEach(l -> l.onAuctionEnded(userId, auctionId, itemName, finalPrice));
  }

  public void notifyAuctionWon(int winnerId, int auctionId, String itemName, BigDecimal finalPrice) {
    listeners.forEach(l -> l.onAuctionWon(winnerId, auctionId, itemName, finalPrice));
  }

  public void notifyAuctionLost(int loserId, int auctionId, String itemName) {
    listeners.forEach(l -> l.onAuctionLost(loserId, auctionId, itemName));
  }

  public void notifyPaymentDue(int buyerId, int auctionId, String itemName, BigDecimal amount) {
    listeners.forEach(l -> l.onPaymentDue(buyerId, auctionId, itemName, amount));
  }

  public void notifyPaymentReceived(int sellerId, int auctionId, String itemName, BigDecimal amount) {
    listeners.forEach(l -> l.onPaymentReceived(sellerId, auctionId, itemName, amount));
  }

  public void notifyItemApproved(int sellerId, int itemId, String itemName) {
    listeners.forEach(l -> l.onItemApproved(sellerId, itemId, itemName));
  }

  public void notifyItemRejected(int sellerId, int itemId, String itemName) {
    listeners.forEach(l -> l.onItemRejected(sellerId, itemId, itemName));
  }

  public void notifySystemNotification(int userId, String title, String message) {
    listeners.forEach(l -> l.onSystemNotification(userId, title, message));
  }
}
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
import server.service.listeners.BusinessEventListener;
import server.service.listeners.NotificationEventHandler;
import server.service.listeners.PaymentTriggerObserver;

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
  private final List<BusinessEventListener> listeners = new CopyOnWriteArrayList<>();

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
        dto.getAuctionId(),
        dto.getCreatedAt().toLocalDateTime(),
        item,
        dto.getSellerId(),
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
    int auctionId = auction.getId();
    List<AutoBidConfig> configs = autoBidConfigDAO.getByAuction(auctionId);
    for (AutoBidConfig config : configs) {
      User bidder = accountDAO.getUserById(config.getBidderId());
      if (bidder == null) {
        logger.warn("Bidder {} not found, skipping auto‑bid config.", config.getBidderId());
        continue;
      }
      auctionManager.getAutoBidEngine().register(config, bidder);
    }
  }

  // ==================== TẠO AUCTION ====================

  public Auction createAuction(Item item, int sellerId,
      LocalDateTime startTime, LocalDateTime endTime,
      BigDecimal minBidIncrement, BigDecimal reservePrice,
      int snipeWindowSeconds, int snipeExtensionSeconds) {
    // 1. Validate nghiệp vụ (service layer)
    if (item == null || startTime == null || endTime == null) {
      logger.error("createAuction() – invalid null parameters");
      return null;
    }
    if (item.getStatus() != ItemStatus.AVAILABLE) {
      logger.error("createAuction() – item {} is not AVAILABLE", item.getId());
      return null;
    }
    if (! (item.getSellerId() == sellerId)) {
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


    // 2. Tính toán initial status
    AuctionStatus initialStatus = startTime.isAfter(now) ? AuctionStatus.OPEN : AuctionStatus.RUNNING;

    // 3. Gọi DAO thuần (không có business rule)
    AuctionDTO dto = auctionDAO.createAuction(
        item.getId(), sellerId,
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
    Auction auction = findAuctionById(auctionId);
    User bidder = findUserById(userId);

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
    // Snapshot SAU manual bid commit — dùng để rollback RAM nếu auto-bid persist fail
    BigDecimal priceAfterManual    = auction.getCurrentPrice();
    User leaderAfterManual         = auction.getCurrentLeader();
    LocalDateTime endTimeAfterManual  = auction.getEndTime();
    LocalDateTime lastBidAfterManual  = auction.getLastBidTime();

    Auction.PlaceBidResult autoBidResult = null;
    try {
      autoBidResult = auctionManager.getAutoBidEngine().trigger(auction, bidder);
    } catch (InvalidBidException e) {
      logger.error("placeBid() – AutoBidEngine.trigger() threw exception for auction {}", auctionId, e);
      // trigger fail hoặc autoBidResult = null → không persist, không rollback manual bid
    }

    // ── Bước 3: Persist auto-bid nếu engine vừa đặt ─────────────────────────
    if (autoBidResult != null) {
      BidTransaction autoBidTx = autoBidResult.tx();
      boolean persisted = persistAutoBidTransaction(auction, autoBidTx);

      if (!persisted) {
        // DB fail khi ghi auto-bid → RAM đã bị engine thay đổi, cần rollback RAM về sau manual bid.
        // Manual bid KHÔNG bị rollback vì đã committed DB ở Bước 1.
        logger.warn("placeBid() – auto-bid persist failed, rolling back RAM to post-manual state " +
            "for auction {}", auctionId);
        auction.rollbackLastBid(autoBidTx, autoBidResult.outbidTx(), priceAfterManual, leaderAfterManual, endTimeAfterManual, lastBidAfterManual);
        // Không return false — manual bid vẫn thành công, chỉ auto-bid bị bỏ qua lần này.
      }else if(autoBidResult.timeExtended()){
        auctionManager.rescheduleClose(auction);
      }
    }

    // ── Bước 4: Notify listener ───────────────────────────────────────────────
    String itemName = (auction.getItem() != null) ? auction.getItem().getName() : "Unknown";
    notifyBidPlaced(userId, auctionId, itemName, amount);
    return true;
  }

  // ==================== AUTO BID ====================

  public void registerAutoBid(AutoBidConfig config, User bidder) {
    int auctionId = config.getAuctionId();
    int bidderId  = config.getBidderId();

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

  public void cancelAutoBid(int auctionId, User bidder) {
    // Trigger lại để tìm winner mới
    Auction auction = findAuctionById(auctionId);
    triggerAutoBids(auction, bidder);
    auctionManager.cancelAutoBid(auctionId, bidder);
    autoBidConfigDAO.cancelByAuctionAndBidder(auctionId, bidder.getId());
  }

  // ==================== QUẢN LÝ USER TRONG AUCTION MANAGER ====================

  public void registerConnectedUser(User user) {
    auctionManager.registerUser(user);
  }

  public User registerOrGetUser(User user) {
    return auctionManager.registerOrGetUser(user);
  }

  public User findUserById(int userId) {
    return auctionManager.findUserById(userId).orElse(null);
  }

  public Auction findAuctionById(int auctionId) {
    return auctionManager.getAuction(auctionId).orElse(null);
  }

  // ==================== VÒNG ĐỜI AUCTION (CALLBACK TỪ MANAGER) ====================

  public void onAuctionStarted(Auction auction) {
    int auctionId = auction.getId();
    auctionDAO.updateStatus(auctionId, AuctionStatus.RUNNING);
    String itemName = auction.getItem() != null ? auction.getItem().getName() : "Unknown";
    notifyAuctionStarted(auction.getSellerId(), auctionId, itemName);
    triggerAutoBids(auction, null);   // Trigger khi auction bắt đầu
    logger.info("Auction {} started – item: {}", auctionId, itemName);
  }

  public void onAuctionClosed(Auction auction) {
    int auctionId = auction.getId();
    AuctionStatus targetStatus = auction.getStatus(); // Trạng thái FINISHED/CANCELED đã được đổi tạm thời trên RAM
    String itemName = auction.getItem() != null ? auction.getItem().getName() : "Unknown";
    BigDecimal finalPrice = auction.getCurrentPrice();
    int sellerId = auction.getSellerId();

    // ── BƯỚC 1 + 2: DB PERSIST (ỦY THÁC HOÀN TOÀN CHO CÁC PHƯƠNG THỨC DAO SẴN CÓ) ───
    try {
      if (targetStatus == AuctionStatus.FINISHED) {
        // 1. Ghi nhận trạng thái kết thúc phiên và lưu Winner ID xuống DB (Sử dụng hàm gốc của bạn)
        Integer winnerId = (auction.getCurrentLeader() != null) ? auction.getCurrentLeader().getId() : null;
        auctionDAO.finishAuction(auctionId, winnerId);

        // 2. Cập nhật trạng thái Vật phẩm sang SOLD (Đã bán)
        if (auction.getItem() != null) {
          auction.getItem().setStatus(ItemStatus.SOLD);
          itemDAO.updateStatus(auction.getItem().getId(), ItemStatus.SOLD);
        }
      } else { // Trạng thái CANCELED (Hủy phiên)
        auctionDAO.updateStatus(auctionId, AuctionStatus.CANCELED);

        // Trả vật phẩm về trạng thái AVAILABLE để có thể tạo phiên đấu giá khác
        if (auction.getItem() != null) {
          auction.getItem().setStatus(ItemStatus.AVAILABLE);
          itemDAO.updateStatus(auction.getItem().getId(), ItemStatus.AVAILABLE);
        }
      }

      // 3. Hủy/Xóa toàn bộ các cấu hình Auto‑bid liên quan đến phiên này dưới DB (Dùng hàm gốc của bạn)
      int cancelled = autoBidConfigDAO.cancelAllByAuction(auctionId);
      logger.info("DB: Đóng phiên {} thành công ({}), đã hủy {} auto-bids.", auctionId, targetStatus, cancelled);

    } catch (Exception e) {
      logger.error("DB: Lỗi nghiêm trọng khi thực thi đóng phiên {}. Tiến hành HOÀN TÁC RAM về RUNNING.", auctionId, e);

      // 🛠️ CƠ CHẾ CỨU NGUY RAM: Nếu DB fail do nghẽn mạng/rớt kết nối, bắt buộc phải trả trạng thái RAM
      // từ FINISHED/CANCELED ngược về RUNNING để Scheduler quét lại ở chu kỳ sau, tránh kẹt bộ nhớ.
      try {
        java.lang.reflect.Field statusField = Auction.class.getDeclaredField("status");
        statusField.setAccessible(true);
        statusField.set(auction, AuctionStatus.RUNNING);
      } catch (Exception ex) {
        logger.error("Không thể hoàn tác trạng thái RAM qua Reflection", ex);
      }
      return; // NGẮT LUỒNG TUYỆT ĐỐI, không phát bất kỳ sự kiện hay thông báo sai lệch nào ra ngoài!
    }

    // ── BƯỚC 3: GỬI LỆNH REALTIME CHO UI BIẾT ĐỂ CẬP NHẬT (SOCKET) ─────────────────
    int winnerId = (auction.getCurrentLeader() != null) ? auction.getCurrentLeader().getId() : -1;
    auction.notifyRealTimeAuctionClosed(winnerId, itemName, finalPrice);
    logger.info("Realtime: Đã phát tín hiệu kết thúc phiên lên Socket mạng.");

    // ── BƯỚC 4: KÍCH HOẠT TẠO PENDING PAYMENT (PaymentTriggerObserver) ───────────
    // Lọc đích danh duy nhất lớp xử lý tài chính chạy trước để sinh hóa đơn công nợ
    listeners.stream()
        .filter(l -> l instanceof PaymentTriggerObserver)
        .forEach(l -> {
          try {
            l.onAuctionSessionClosed(auctionId, itemName, finalPrice, targetStatus);
            logger.info("Payment: Đã kích hoạt sinh hóa đơn chờ thanh toán cho Winner ID: {}", winnerId);
          } catch (Exception e) {
            logger.error("Lỗi xử lý tạo công nợ tại PaymentTriggerObserver", e);
          }
        });

    // ── BƯỚC 5: ĐẨY PUSH NOTIFICATION RA APP (NotificationEventHandler) ──────────
    // Đảm bảo thông báo chỉ bắn đi khi Hóa đơn ở Bước 4 đã an toàn nằm trong Queue xử lý công nợ
    listeners.stream()
        .filter(l -> l instanceof NotificationEventHandler)
        .forEach(l -> {
          try {
            l.onAuctionSessionClosed(auctionId, itemName, finalPrice, targetStatus);
          } catch (Exception e) {
            logger.error("Lỗi xử lý thông báo tổng quan tại NotificationEventHandler", e);
          }
        });

    // Phân nhánh đẩy thông báo chi tiết độc lập sang duy nhất NotificationEventHandler
    if (targetStatus == AuctionStatus.FINISHED) {
      dispatchBusinessNotificationsAfterClosed(auction, auctionId, sellerId, itemName, finalPrice, winnerId);
    } else {
      try {
        listeners.stream()
            .filter(l -> l instanceof NotificationEventHandler)
            .forEach(l -> l.onAuctionEnded(sellerId, auctionId, itemName, finalPrice));
      } catch (Exception e) {
        logger.error("Lỗi gửi thông báo hủy phiên cho seller {}", sellerId, e);
      }
    }
  }

  // ==================== ADMIN / CƯỠNG CHẾ ====================

  public void forceCloseAuction(int auctionId, String reason) {
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
    Optional<Auction> auctionOpt = auctionManager.getAuction(auctionId);
    return auctionOpt.map(a -> a.getStatus() == AuctionStatus.PAID).orElse(false);
  }

  // ==================== BROADCAST HỆ THỐNG ====================

  public void broadcastSystemNotif(String title, String message) {
    notifySystemNotification(-1, title, message);
  }

  // ==================== OBSERVER PATTERN – NOTIFY METHODS ====================

  public void addListener(BusinessEventListener listener) {
    if (listener != null && !listeners.contains(listener)) {
      listeners.add(listener);
      logger.info("Listener registered: {}", listener.getClass().getSimpleName());
    }
  }

  public void removeListener(BusinessEventListener listener) {
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

    int winnerId = auction.getCurrentLeader().getId();
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
   * Helper điều phối thông báo chi tiết độc lập sang duy nhất NotificationEventHandler
   */
  private void dispatchBusinessNotificationsAfterClosed(Auction auction, int auctionId,
      int sellerId, String itemName, BigDecimal finalPrice, int winnerId) {

    // 1. Gửi thông báo đến Winner (Mở app lên là hóa đơn từ bước 4 đã sẵn sàng)
    if (winnerId != -1) {
      try {
        listeners.stream()
            .filter(l -> l instanceof NotificationEventHandler)
            .forEach(l -> {
              l.onAuctionWon(winnerId, auctionId, itemName, finalPrice);
              l.onPaymentDue(winnerId, auctionId, itemName, finalPrice);
            });
      } catch (Exception e) {
        logger.error("Lỗi gửi thông báo trúng giải cho user {}", winnerId, e);
      }
    }

    // 2. Gửi thông báo trượt giải cho những người cùng phòng cược
    List<Integer> allBidders = bidTransactionDAO.getBiddersByAuctionId(auctionId);
    for (int bidderId : allBidders) {
      if (bidderId != winnerId) {
        try {
          listeners.stream()
              .filter(l -> l instanceof NotificationEventHandler)
              .forEach(l -> l.onAuctionLost(bidderId, auctionId, itemName));
        } catch (Exception e) {
          logger.error("Lỗi gửi thông báo trượt giải cho user {}", bidderId, e);
        }
      }
    }

    // 3. Gửi thông báo hoàn tất phiên cho Chủ tài sản (Seller)
    try {
      listeners.stream()
          .filter(l -> l instanceof NotificationEventHandler)
          .forEach(l -> l.onAuctionEnded(sellerId, auctionId, itemName, finalPrice));
    } catch (Exception e) {
      logger.error("Lỗi gửi thông báo kết thúc đến seller {}", sellerId, e);
    }
  }
  /**
   * Method trung tâm gọi AutoBidEngine.trigger() + persist DB
   */
  private void triggerAutoBids(Auction auction, User previousWinner) {
    if (auction == null || auction.getStatus() != AuctionStatus.RUNNING) {
      return;
    }

    try {
      Auction.PlaceBidResult autoBidResult = auctionManager.getAutoBidEngine().trigger(auction, previousWinner);  // ← đổi nhận PlaceBidResult

      if (autoBidResult != null) {
        persistAutoBidTransaction(auction, autoBidResult.tx());  // ← lấy tx từ result
      }
    } catch (Exception e) {
      logger.error("Auto-bid trigger failed for auction {}", auction.getId(), e);
    }
  }

  private boolean persistAutoBidTransaction(Auction auction, BidTransaction autoBidTx) {
    try (Connection conn = DBConnection.getConnection()) {
      conn.setAutoCommit(false);
      try {
        int autoBidderId = autoBidTx.getBidderId();
        bidTransactionDAO.updateAuctionState(conn,
            auction.getId(),
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
          dto.getBidId(),
          dto.getAuctionId(),
          dto.getBidderId(),
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
    dto.setBidId(tx.getId());
    dto.setAuctionId(tx.getAuctionId());
    dto.setBidderId(tx.getBidderId());
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

  public void notifyAuctionEnded(int winnerId, int auctionId, String itemName, BigDecimal finalPrice) {
    listeners.forEach(l -> {
      l.onAuctionEnded(winnerId, auctionId, itemName, finalPrice);
      // Phải gọi thêm hàm này để báo đóng phiên nghiệp vụ:
      l.onAuctionSessionClosed(auctionId, itemName, finalPrice, AuctionStatus.FINISHED);
    });
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
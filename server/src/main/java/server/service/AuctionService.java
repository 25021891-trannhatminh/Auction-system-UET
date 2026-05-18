package server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.common.entity.*;
import server.common.entity.manager.AuctionManager;
import server.common.enums.AuctionStatus;
import server.common.enums.ItemStatus;
import server.repository.*;
import server.service.listeners.AuctionEventListener;
import server.common.model.BidResultDTO;
import server.common.entity.exception.AuctionClosedException;
import server.common.entity.exception.InvalidBidException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * AuctionService xử lý business logic chính cho hệ thống đấu giá:
 * - Quản lý đặt giá, bắt đầu/kết thúc phiên và điều phối thông báo.
 * - Áp dụng Observer Pattern để tách biệt logic nghiệp vụ và thông báo.
 */
public class AuctionService {

  private static final Logger logger = LoggerFactory.getLogger(AuctionService.class);

  // Sử dụng CopyOnWriteArrayList để an toàn khi đa luồng đăng ký/hủy listener
  private final List<AuctionEventListener> listeners = new CopyOnWriteArrayList<>();

  // DAOs
  private final AuctionDAO         auctionDAO         = new AuctionDAO();
  private final BidTransactionDAO  bidTransactionDAO  = new BidTransactionDAO();
  private final AutoBidConfigDAO   autoBidConfigDAO   = new AutoBidConfigDAO();
  private final ItemDAO            itemDAO            = new ItemDAO();
  private final AccountDAO         accountDAO         = new AccountDAO();

  // Singleton domain manager
  private final AuctionManager auctionManager = AuctionManager.getInstance();


  // =========================================================================
  //  KHỞI ĐỘNG SERVER — Load DB vào memory

  /**
   * Load toàn bộ trạng thái từ DB vào AuctionManager khi server khởi động.
   *
   * Thứ tự load :
   *   Load các Auction đang RUNNING hoặc OPEN từ DB.
   *   Với mỗi Auction, load AutoBidConfig đang ACTIVE và đăng ký vào
   *       AutoBidEngine — engine cần có config trước khi trigger được gọi.
   *   Gọi {@link AuctionManager#loadAuction} — scheduleClose() sẽ tự lên lịch
   *       đóng auction theo endTime còn lại.
   *
   * Gọi method này một lần duy nhất trong {@code AuctionServer.start()} trước khi
   * bắt đầu nhận request từ client.
   */
  public void loadAllFromDatabase() {
    logger.info("loadAllFromDatabase() — Loading active auctions from DB...");

    // 1. Load auction đang chạy hoặc chờ mở
    List<Auction> runningAuctions = auctionDAO.getByStatus(AuctionStatus.RUNNING);
    List<Auction> openAuctions   = auctionDAO.getByStatus(AuctionStatus.OPEN);
    runningAuctions.addAll(openAuctions);

    for (Auction auction : runningAuctions) {
      loadAutoBidsForAuction(auction);
      auctionManager.loadAuction(auction); // scheduleClose() được gọi bên trong
      logger.info("loadAllFromDatabase() — Loaded auction id={}, status={}",
          auction.getId(), auction.getStatus());
    }

    logger.info("loadAllFromDatabase() — Done. Loaded {} auction(s).", runningAuctions.size());
  }

  /**
   * Helper: load AutoBidConfig từ DB và đăng ký vào AutoBidEngine cho 1 auction.
   * Tách ra để có thể gọi lại khi cần reload config của 1 phiên cụ thể.
   */
  private void loadAutoBidsForAuction(Auction auction) {
    int auctionId = Integer.parseInt(auction.getId());
    List<AutoBidConfig> configs = autoBidConfigDAO.getByAuction(auctionId);

    for (AutoBidConfig config : configs) {
      // Load User object để engine có thể gọi auction.placeBid(user, ...)
      User bidder = accountDAO.getById(Integer.parseInt(config.getBidderId()));
      if (bidder == null) {
        logger.warn("_loadAutoBidsForAuction() — Bidder {} not found, skipping config",
            config.getBidderId());
        continue;
      }
      // Đăng ký vào engine (không trigger vì auction chưa hoàn toàn load xong)
      auctionManager.getAutoBidEngine().register(config, bidder);
      logger.debug("_loadAutoBidsForAuction() — Registered AutoBid bidderId={}, auctionId={}",
          config.getBidderId(), auction.getId());
    }
  }

  // =========================================================================
  //  TẠO AUCTION

  /**
   * Tạo phiên đấu giá mới: gọi AuctionManager tạo in-memory → persist DB.
   *
   * Sau khi gọi method này, item_status sẽ chuyển sang IN_AUCTION
   * và auction sẽ tự mở/đóng theo lịch.
   *
   * @return Auction vừa tạo, hoặc null nếu DB write thất bại.
   */
  public Auction createAuction(Item item, String sellerId,
                               LocalDateTime startTime, LocalDateTime endTime,
                               BigDecimal minBidIncrement, BigDecimal reservePrice,
                               int snipeWindowSeconds, int snipeExtensionSeconds) {

    // Bước 1: Tạo in-memory (lên lịch scheduleOpen/Close ngay)
    Auction auction = auctionManager.createAuction(
        item, sellerId, startTime, endTime,
        minBidIncrement, reservePrice,
        snipeWindowSeconds, snipeExtensionSeconds
    );

    // Bước 2: Persist DB
    boolean savedAuction = auctionDAO.create(auction);
    if (!savedAuction) {
      logger.error("createAuction() — DB write failed for itemId={}", item.getId());
      // Rollback memory: xóa khỏi manager vì DB thất bại
      // (AuctionManager cần expose removeAuction — nếu chưa có thì log warning)
      logger.warn("createAuction() — Auction {} created in memory but NOT in DB. " +
          "Manual cleanup required.", auction.getId());
      return null;
    }

    // Bước 3: Cập nhật trạng thái item → IN_AUCTION
    itemDAO.updateStatus(Integer.parseInt(item.getId()), ItemStatus.IN_AUCTION);

    logger.info("createAuction() — Auction {} created. Item {} → IN_AUCTION",
        auction.getId(), item.getId());
    return auction;
  }

  // =========================================================================
  //  ĐẶT GIÁ (MANUAL BID)

  /**
   * Flow đặt giá đầy đủ — kết hợp Java logic và DB persistence.
   *
   * Thứ tự xử lý (QUAN TRỌNG — đọc kỹ trước khi sửa):
   *
   *   Java lock (ReentrantLock) — (Auction.placeBid()) acquire lock ->
   *       validate giá -> cập nhật currentPrice in-memory -> release lock.
   *   Notify observers (ngoài lock) — UI nhận update realtime ngay sau step 1,
   *       trước khi DB được ghi. Đây là đánh đổi có chủ đích:
   *       realtime nhanh hơn, DB theo sau. Nếu DB fail, auction vẫn tiếp tục
   *       trong memory nhưng log error để reconcile sau.
   *   DB lock (SELECT FOR UPDATE) — (BidTransactionDAO.placeBid())
   *       lock row trong DB, validate lại lần nữa (bảo vệ khi horizontal scale),
   *       ghi bid record và cập nhật current_price trong transaction.
   *   Trigger AutoBidEngine — engine đặt auto-bid nếu có config phù hợp.
   *       Mỗi auto-bid lặp lại step 1-3.
   *   Cập nhật AuctionDAO — đồng bộ trạng thái auction tổng thể.
   *
   *
   * Về "double lock": Java lock và DB lock bảo vệ hai tầng khác nhau.
   * Java lock: bảo vệ in-memory state trong một JVM process.
   * DB lock: bảo vệ persistence layer, đảm bảo consistency khi có nhiều server.
   * Không thể bỏ một trong hai nếu muốn đảm bảo cả realtime lẫn data integrity.
   *
   *
   * @return {@link BidResultDTO} chứa kết quả bid thủ công và auto-bid (nếu có).
   * @throws AuctionClosedException nếu auction không RUNNING.
   * @throws InvalidBidException    nếu amount không hợp lệ.
   */
  public BidResultDTO placeBid(String auctionId, User bidder, BigDecimal amount) {

    // ── STEP 1 & 2: Java lock + notify observers (trong AuctionManager) ──
    // placeBidFull gọi Auction.placeBid() (lock) rồi trigger AutoBidEngine
    BidResultDTO bidResult = auctionManager.placeBidFull(auctionId, bidder, amount);

    int auctionID = Integer.parseInt(auctionId);
    int bidderId  = Integer.parseInt(bidder.getId());

    // ── STEP 3: Persist manual bid vào DB (DB-level lock bên trong DAO) ──
    boolean manualSaved = bidTransactionDAO.placeBid(
        auctionID, bidderId, amount, false
    );
    if (!manualSaved) {
      // DB thất bại nhưng memory đã cập nhật → log để reconcile
      logger.error("placeBid() — WARN: Manual bid NOT saved to DB. " +
          "auctionId={}, bidderId={}, amount={}", auctionId, bidder.getId(), amount);
    }

    // ── STEP 4: Persist auto-bid nếu có ──
    if (bidResult.getAutoBidTransaction() != null) {
      BidTransaction autoBidTransaction = bidResult.getAutoBidTransaction();
      boolean savedAutoBidTx = bidTransactionDAO.placeBid(
          Integer.parseInt(autoBidTransaction.getAuctionId()),
          Integer.parseInt(autoBidTransaction.getBidderId()),
          autoBidTransaction.getAmount(),
          true // isAutoBid = true
      );
      if (!savedAutoBidTx) {
        logger.error("placeBid() — WARN: Auto-bid NOT saved to DB. " +
            "bidderId={}, amount={}", autoBidTransaction.getBidderId(), autoBidTransaction.getAmount());
      }
    }

    // ── STEP 5: Đồng bộ currentPrice trong bảng auctions ──
    // BidTransactionDAO đã UPDATE auctions.current_price bên trong transaction của nó.
    // Không cần gọi AuctionDAO.updateCurrentPrice() riêng để tránh double-write.
    // Nếu muốn explicit update (ví dụ khi BidTxDAO không update auction table):
     auctionDAO.updateCurrentPrice(auctionID,
         Integer.parseInt(bidResult.getCurrentLeaderName()), bidResult.getFinalPrice());

    logger.info("placeBid() — Done. auctionId={}, finalPrice={}, leader={}",
        auctionId, bidResult.getFinalPrice(), bidResult.getCurrentLeaderName());

    return bidResult;
  }


  // =========================================================================
  //  ĐĂNG KÝ AUTO-BID
  // =========================================================================

  /**
   * Đăng ký auto-bid: persist DB → cập nhật engine → trigger nếu cần.
   *
   * Logic DB:
   *    Nếu bidder chưa có config trong phiên → INSERT mới.
   *    Nếu đã có config ACTIVE → UPDATE maxBid (không tạo trùng).
   *
   * Thứ tự: persist DB TRƯỚC để nếu engine trigger fail,
   * config vẫn được lưu và load lại khi server restart.
   *
   * @return {@code Optional<BidTransaction>} nếu engine đặt bid ngay sau đăng ký.
   */
  public Optional<BidTransaction> registerAutoBid(AutoBidConfig config, User bidder) {
    int auctionId = Integer.parseInt(config.getAuctionId());
    int bidderId  = Integer.parseInt(config.getBidderId());

    // ── STEP 1: Persist config vào DB ──
    if (autoBidConfigDAO.hasActiveBid(auctionId, bidderId)) {
      // Bidder đã có config → update maxBid
      AutoBidConfig existing = autoBidConfigDAO.getByAuctionAndBidder(auctionId, bidderId);
      if (existing != null) {
        boolean updated = autoBidConfigDAO.updateMaxBid(
            Integer.parseInt(existing.getAuctionId()), config.getMaxBid()
        );
        if (!updated) {
          logger.error("registerAutoBid() — Failed to update maxBid in DB. " +
              "auctionId={}, bidderId={}", auctionId, bidderId);
        }
      }
    } else {
      // Chưa có → tạo mới
      boolean created = autoBidConfigDAO.create(config);
      if (!created) {
        logger.error("registerAutoBid() — Failed to create AutoBidConfig in DB. " +
            "auctionId={}, bidderId={}", auctionId, bidderId);
      }
    }

    // ── STEP 2: Cập nhật engine + trigger (trong AuctionManager) ──
    Optional<BidTransaction> autoBidTx = auctionManager.registerAutoBid(config, bidder);

    // ── STEP 3: Nếu engine trigger ngay (trigger kích hoạt return BidTx) → persist bid đó vào DB ──
    autoBidTx.ifPresent(tx -> {
      boolean saved = bidTransactionDAO.placeBid(
          Integer.parseInt(tx.getAuctionId()),
          Integer.parseInt(tx.getBidderId()),
          tx.getAmount(),
          true
      );
      if (!saved) {
        logger.error("registerAutoBid() — Immediate auto-bid NOT saved to DB. " +
            "bidderId={}, amount={}", tx.getBidderId(), tx.getAmount());
      }
    });

    logger.info("registerAutoBid() — Done. auctionId={}, bidderId={}, maxBid={}",
        auctionId, bidderId, config.getMaxBid());

    return autoBidTx;
  }

  /**
   * Hủy auto-bid: cập nhật engine → cập nhật DB.
   */
  public void cancelAutoBid(String auctionId, User bidder) {
    // Hủy trong engine (memory)
    auctionManager.cancelAutoBid(auctionId, bidder);

    // Hủy trong DB
    int auctionIdInt = Integer.parseInt(auctionId);
    int bidderIdInt  = Integer.parseInt(bidder.getId());
    AutoBidConfig existing = autoBidConfigDAO.getByAuctionAndBidder(auctionIdInt, bidderIdInt);
    if (existing != null) {
      // getByAuctionAndBidder trả về config có id = auto_bid_id
      // Dùng cancelAllByAuction nếu không lưu auto_bid_id trực tiếp:
      // Hoặc update status = CANCELED cho đúng record
      autoBidConfigDAO.cancelAllByAuction(auctionIdInt); // ← nếu chỉ 1 config per bidder
      logger.info("cancelAutoBid() — Canceled in DB. auctionId={}, bidderId={}",
          auctionId, bidder.getId());
    }
  }

  // =========================================================================
  //  ĐÓNG PHIÊN (gọi từ Scheduler trong AuctionManager)
  // =========================================================================

  /**
   * Callback được gọi sau khi {@link Auction#closeSession()} hoàn tất.
   *
   * AuctionManager cần gọi method này trong {@code scheduleClose()} sau khi
   * {@code auction.closeSession()} thành công. Tách ra khỏi domain class để
   * không kéo DAO dependency vào Auction.
   *
   * Cách tích hợp vào AuctionManager.scheduleClose():
   * if (auction.getStatus() == AuctionStatus.RUNNING) {
   *     auction.closeSession();
   *     autoBidEngine.cleanupAutoBids(auction.getId());
   *     // Gọi service để persist:
   *     auctionService.onAuctionClosed(auction);
   * }
   * }
   *
   * @param auction phiên vừa được đóng (status đã là FINISHED hoặc CANCELED)
   */
  public void onAuctionClosed(Auction auction) {
    int auctionId = Integer.parseInt(auction.getId());

    // Persist trạng thái auction
    if (auction.getStatus() == AuctionStatus.FINISHED) {
      Integer winnerId = auction.getCurrentLeader() != null
          ? Integer.parseInt(auction.getCurrentLeader().getId()) : null;
      boolean finished = auctionDAO.finishAuction(auctionId, winnerId);
      if (!finished) {
        logger.error("onAuctionClosed() — Failed to persist FINISHED for auctionId={}",
            auctionId);
      }
      // Cập nhật item → SOLD
      itemDAO.updateStatus(
          Integer.parseInt(auction.getItem().getId()), ItemStatus.SOLD
      );
    } else {
      // CANCELED
      boolean canceled = auctionDAO.updateStatus(auctionId, AuctionStatus.CANCELED);
      if (!canceled) {
        logger.error("onAuctionClosed() — Failed to persist CANCELED for auctionId={}",
            auctionId);
      }
      // Cập nhật item → AVAILABLE (quay lại chờ đấu giá tiếp)
      itemDAO.updateStatus(
          Integer.parseInt(auction.getItem().getId()), ItemStatus.AVAILABLE
      );
    }

    // Hủy toàn bộ auto-bid còn active trong DB
    int canceledCount = autoBidConfigDAO.cancelAllByAuction(auctionId);
    logger.info("onAuctionClosed() — auctionId={}, status={}, canceledAutoBids={}",
        auctionId, auction.getStatus(), canceledCount);
  }

  // =========================================================================
  //  ADMIN OPERATIONS
  // =========================================================================

  /**
   * Admin force-close phiên: domain logic → persist DB.
   */
  public void forceCloseAuction(String auctionId, String reason) {
    auctionManager.forceCloseAuction(auctionId, reason);

    // Lấy trạng thái mới sau khi force close
    Optional<Auction> auctionOpt = auctionManager.getAuction(auctionId);
    auctionOpt.ifPresent(this::onAuctionClosed);
  }

  /**
   * Đăng ký người nghe sự kiện (vd: NotificationHandler)
   */
  public void addListener(AuctionEventListener listener) {
    if (listener != null && !listeners.contains(listener)) {
      listeners.add(listener);
      logger.info("addListener() - Registered: {}", listener.getClass().getSimpleName());
    }
  }

  /**
   * Gỡ bỏ người nghe sự kiện
   */
  public void removeListener(AuctionEventListener listener) {
    if (listeners.remove(listener)) {
      logger.info("removeListener() - Unregistered: {}", listener.getClass().getSimpleName());
    }
  }

  /**
   * Gửi thông báo toàn hệ thống (Broadcast).
   * Thường dùng khi bảo trì hoặc có thông báo chung.
   */
  public void broadcastSystemNotif(String title, String message) {
    logger.info("Broadcasting system notification to all active users");
    // ID -1 hoặc 0 thường được quy ước là Global/System
    notifySystemNotification(-1, title, message);
  }

  /**
   * Kích hoạt bắt đầu phiên đấu giá
   */
  public boolean startAuction(int auctionId) {
    Auction auction = auctionDAO.getById(auctionId);
    if (auction == null) return false;

    boolean updated = auctionDAO.updateStatus(auctionId, AuctionStatus.RUNNING);
    if (updated) {
      notifyAuctionStarted(Integer.parseInt(auction.getSellerId()), auctionId, "Auction #" + auctionId);
      logger.info("startAuction() - Auction {} is LIVE", auctionId);
    }
    return updated;
  }

  /**
   * Kết thúc phiên đấu giá và xác định kết quả
   */
  public boolean endAuction(int auctionId) {
    Auction auction = auctionDAO.getById(auctionId);
    if (auction == null) return false;

    Integer winnerId = Integer.parseInt(auction.getCurrentLeader().getId());
    boolean finished = auctionDAO.finishAuction(auctionId, winnerId);

    if (!finished) {
      logger.error("endAuction() - Failed to finalize auction {}", auctionId);
      return false;
    }

    double finalPrice = auction.getCurrentPrice().doubleValue();
    String itemName = "Auction #" + auctionId;

    // 1. Thông báo cho người bán
    notifyAuctionEnded(Integer.parseInt(auction.getSellerId()), auctionId, itemName, finalPrice);

    // 2. Xử lý thông báo thắng/thua
      // Thông báo thắng cuộc và yêu cầu thanh toán
      notifyAuctionWon(winnerId, auctionId, itemName, finalPrice);
      notifyPaymentDue(winnerId, auctionId, itemName, finalPrice);

      // Gửi thông báo cho những người tham gia nhưng không thắng (Auction Lost)
      List<Integer> allBidders = bidTransactionDAO.getBiddersByAuctionId(auctionId);
      for (Integer bidderId : allBidders) {
        if (!bidderId.equals(winnerId)) {
          notifyAuctionLost(bidderId, auctionId, itemName);
        }
      }


    logger.info("endAuction() - Auction {} closed successfully", auctionId);
    return true;
  }

  /**
   * Xác nhận thanh toán thành công và thông báo cho các bên liên quan.
   * @param auctionId ID của phiên đấu giá
   * @return true nếu xử lý thành công
   */
  public boolean confirmPayment(int auctionId) {
    try {
      // 1. Lấy thông tin phiên đấu giá
      Auction auction = auctionDAO.getById(auctionId);
      if (auction == null) {
        logger.warn("confirmPayment() - Auction {} not found", auctionId);
        return false;
      }

      // 2. Kiểm tra trạng thái (Chỉ cho phép thanh toán khi phiên đã kết thúc và có người thắng)
      if (auction.getStatus() != AuctionStatus.FINISHED || auction.getCurrentLeader().getId() == null) {
        logger.warn("confirmPayment() - Auction {} is not in a payable state", auctionId);
        return false;
      }

      // 3. Cập nhật trạng thái thanh toán trong Database (giả sử qua auctionDAO)
      // Lưu ý: Bạn nên có thêm trường paymentStatus trong DB
      boolean isUpdated = auctionDAO.updatePaymentStatus(auctionId, true);

      if (isUpdated) {
        int sellerId = Integer.parseInt(auction.getSellerId());
        int buyerId = Integer.parseInt(auction.getCurrentLeader().getId());
        String itemName = "Auction #" + auctionId;
        double finalPrice = auction.getCurrentPrice().doubleValue();

        logger.info("confirmPayment() - Payment SUCCESS [Auction:{}, Buyer:{}, Seller:{}]",
            auctionId, buyerId, sellerId);

        // 4. Thông báo cho người bán: "Tiền đã về, hãy giao hàng"
        notifyPaymentReceived(sellerId, auctionId, itemName, finalPrice);

        // 5. Thông báo cho người mua: "Xác nhận bạn đã thanh toán thành công"
        notifySystemNotification(buyerId, "Thanh toán thành công",
            String.format("Bạn đã thanh toán thành công %.2f cho vật phẩm: %s", finalPrice, itemName));

        return true;
      }

      return false;
    } catch (Exception e) {
      logger.error("confirmPayment() - Critical error during payment confirmation", e);
      return false;
    }
  }

  /**
   * Duyệt vật phẩm (Gọi từ Admin)
   */
  public void approveItem(int sellerId, int itemId, String itemName) {
    notifyItemApproved(sellerId, itemId, itemName);
  }

  /**
   * Từ chối vật phẩm (Gọi từ Admin)
   */
  public void rejectItem(int sellerId, int itemId, String itemName) {
    notifyItemRejected(sellerId, itemId, itemName);
  }

  // ============================================================
  // Notification Dispatchers (Observer Pattern Implementation)
  // ============================================================

  private void notifyBidPlaced(int bidderId, int auctionId, String itemName, double amount) {
    listeners.forEach(l -> l.onBidPlaced(bidderId, auctionId, itemName, amount));
  }

  private void notifyOutbid(int userId, int auctionId, String itemName, double newPrice) {
    listeners.forEach(l -> l.onOutbid(userId, auctionId, itemName, newPrice));
  }

  private void notifyAuctionStarted(int userId, int auctionId, String itemName) {
    listeners.forEach(l -> l.onAuctionStarted(userId, auctionId, itemName));
  }

  private void notifyAuctionEnded(int userId, int auctionId, String itemName, double finalPrice) {
    listeners.forEach(l -> l.onAuctionEnded(userId, auctionId, itemName, finalPrice));
  }

  private void notifyAuctionWon(int winnerId, int auctionId, String itemName, double finalPrice) {
    listeners.forEach(l -> l.onAuctionWon(winnerId, auctionId, itemName, finalPrice));
  }

  private void notifyAuctionLost(int loserId, int auctionId, String itemName) {
    listeners.forEach(l -> l.onAuctionLost(loserId, auctionId, itemName));
  }

  private void notifyPaymentDue(int buyerId, int auctionId, String itemName, double amount) {
    listeners.forEach(l -> l.onPaymentDue(buyerId, auctionId, itemName, amount));
  }

  public void notifyPaymentReceived(int sellerId, int auctionId, String itemName, double amount) {
    listeners.forEach(l -> l.onPaymentReceived(sellerId, auctionId, itemName, amount));
  }

  private void notifyItemApproved(int sellerId, int itemId, String itemName) {
    listeners.forEach(l -> l.onItemApproved(sellerId, itemId, itemName));
  }

  private void notifyItemRejected(int sellerId, int itemId, String itemName) {
    listeners.forEach(l -> l.onItemRejected(sellerId, itemId, itemName));
  }

  public void notifySystemNotification(int userId, String title, String message) {
    listeners.forEach(l -> l.onSystemNotification(userId, title, message));
  }
}
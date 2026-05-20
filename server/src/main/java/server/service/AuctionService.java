package server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.common.entity.*;
import server.common.entity.manager.AuctionManager;
import server.common.enums.AuctionStatus;
import server.common.enums.ItemStatus;
import server.common.model.PaymentDTO;
import server.repository.*;
import server.service.listeners.AuctionEventListener;
import server.common.model.BidResultDTO;
import server.common.entity.exception.AuctionClosedException;
import server.common.entity.exception.InvalidBidException;
import server.service.listeners.ObserverToNotificationEventAdapter;

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
  private final PaymentDAO         paymentDAO         = new PaymentDAO();
  // Singleton domain manager
  private final AuctionManager auctionManager = AuctionManager.getInstance();
  private final PaymentService paymentService = new PaymentService();
  // Support
  private final ObserverToNotificationEventAdapter domainAdapter;
  private final AdminService adminService;

  public AuctionService() {
    this.domainAdapter = new ObserverToNotificationEventAdapter(this);

    // Đăng ký adapter vào AuctionManager ngay khi tạo service
    AuctionManager.getInstance().addGlobalObserver(domainAdapter);
    this.adminService = new AdminService(this);

    logger.info("AuctionService initialized with ObserverToNotificationEventAdapter");
  }

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

    List<Auction> runningAuctions = auctionDAO.getByStatus(AuctionStatus.RUNNING);
    List<Auction> openAuctions   = auctionDAO.getByStatus(AuctionStatus.OPEN);
    runningAuctions.addAll(openAuctions);

    for (Auction auction : runningAuctions) {
      loadAutoBidsForAuction(auction);
      auctionManager.loadAuction(auction);
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
      User bidder = accountDAO.getUserById(Integer.parseInt(config.getBidderId()));
      if (bidder == null) {
        logger.warn("loadAutoBidsForAuction() — Bidder {} not found, skipping config",
            config.getBidderId());
        continue;
      }
      auctionManager.getAutoBidEngine().register(config, bidder);
      logger.debug("loadAutoBidsForAuction() — Registered AutoBid bidderId={}, auctionId={}",
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
    // Cập nhật trạng thái item → IN_AUCTION trên cả 2 tầng
    if (item != null) {
      item.setStatus(ItemStatus.IN_AUCTION); // Khóa trạng thái trên RAM luôn
      itemDAO.updateStatus(Integer.parseInt(item.getId()), ItemStatus.IN_AUCTION);
    }

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
   * Về "BigDecimal lock": Java lock và DB lock bảo vệ hai tầng khác nhau.
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
    // Không cần gọi AuctionDAO.updateCurrentPrice() riêng để tránh Double-write.
    // Nếu muốn explicit update (ví dụ khi BidTxDAO không update auction table):
    // Đúng logic: người dẫn đầu là người đặt auto-bid (nếu wasOutbidByAutoBid() = true),
    //   ngược lại là manual bidder.
    int currentLeaderId;
    if (bidResult.wasOutbidByAutoBid()) {
      // Auto-bid của người khác đã vượt manual bid → leader là auto-bidder
      currentLeaderId = Integer.parseInt(bidResult.getAutoBidTransaction().getBidderId());
    } else {
      // Manual bidder đang dẫn đầu (hoặc auto-bid của chính họ)
      currentLeaderId = Integer.parseInt(bidResult.getManualTransaction().getBidderId());
    }

//    auctionDAO.updateCurrentPrice(auctionID, currentLeaderId, bidResult.getFinalPrice());

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
    int auctionId = Integer.parseInt(config.getAuctionId()); // cite: AutoBidConfig, AuctionService
    int bidderId  = Integer.parseInt(config.getBidderId()); // cite: AutoBidConfig, AuctionService

    // ── STEP 1: Persist hoặc Update cấu hình vào DB ──
    if (autoBidConfigDAO.hasActiveBid(auctionId, bidderId)) { // cite: AuctionService
      // Người dùng đã có cấu hình ACTIVE trong phiên này -> Tiến hành cập nhật giá trần mới
      // FIXca: Sử dụng hàm update mới với cả auctionId và bidderId thay vì truyền nhầm khóa chính
      boolean updated = autoBidConfigDAO.updateMaxBid(auctionId, bidderId, config.getMaxBid()); // cite: AutoBidConfig

      if (!updated) {
        logger.error("registerAutoBid() — Failed to update maxBid in DB. auctionId={}, bidderId={}",
            auctionId, bidderId);
      }
    } else {
      // Chưa có cấu hình nào -> Tạo mới một bản ghi
      boolean created = autoBidConfigDAO.create(config); // cite: AuctionService
      if (!created) {
        logger.error("registerAutoBid() — Failed to create AutoBidConfig in DB. auctionId={}, bidderId={}",
            auctionId, bidderId);
      }
    }

    // ── STEP 2: Cập nhật engine + trigger (giữ nguyên logic cũ của bạn) ──
    Optional<BidTransaction> autoBidTx = auctionManager.registerAutoBid(config, bidder); // cite: AuctionService

    // ── STEP 3: Nếu engine trigger ngay lập tức -> Lưu giao dịch đặt giá tự động đó vào DB ──
    autoBidTx.ifPresent(tx -> {
      boolean saved = bidTransactionDAO.placeBid(
          Integer.parseInt(tx.getAuctionId()),
          Integer.parseInt(tx.getBidderId()),
          tx.getAmount(),
          true
      );
      if (!saved) {
        logger.error("registerAutoBid() — Immediate auto-bid NOT saved to DB. bidderId={}, amount={}",
            tx.getBidderId(), tx.getAmount());
      }
    });

    logger.info("registerAutoBid() — Done. auctionId={}, bidderId={}, maxBid={}",
        auctionId, bidderId, config.getMaxBid()); // cite: AutoBidConfig

    return autoBidTx; // cite: AuctionService
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

    //Chỉ gọi lệnh hủy chính xác cho cấu hình của đúng đối tượng bidder yêu cầu
    boolean dbUpdated = autoBidConfigDAO.cancelByAuctionAndBidder(auctionIdInt, bidderIdInt);

    if (dbUpdated) {
      logger.info("cancelAutoBid() — Successfully synchronized CANCELED state to DB for auctionId={}, bidderId={}",
          auctionId, bidder.getId());
    } else {
      logger.warn("cancelAutoBid() — DB state was not changed (possibly already canceled or none existed). auctionId={}, bidderId={}",
          auctionId, bidder.getId());
    }
  }

  // =========================================================================
  //  MỞ PHIÊN
  // =========================================================================

  /**
   * Gọi bởi Scheduler trong AuctionManager khi đến startTime của phiên đấu giá.
   *
   * Trách nhiệm của method này (Service layer — KHÔNG phải domain logic):
   *   1. Đồng bộ trạng thái RUNNING xuống DB (domain đã cập nhật RAM trước đó).
   *   2. Phát notify "Auction Started" tới seller.
   *
   * Lưu ý: {@code auction.startRunning()} được gọi bởi {@code AuctionManager.scheduleOpen()}
   * — Service KHÔNG gọi lại để tránh double-transition. Method này chỉ nhận auction đã
   * ở trạng thái RUNNING.
   *
   * @param auction phiên vừa được chuyển sang RUNNING bởi Scheduler
   */
  public void onAuctionStarted(Auction auction) {
    int auctionIdInt = Integer.parseInt(auction.getId());

    // Persist RUNNING xuống DB
    boolean dbUpdated = auctionDAO.updateStatus(auctionIdInt, AuctionStatus.RUNNING);
    if (!dbUpdated) {
      logger.error("onAuctionStarted() — Failed to persist RUNNING status for auctionId={}", auctionIdInt);
    }

    // Notify seller
    String itemName = auction.getItem() != null ? auction.getItem().getName() : "Unknown Item";
    notifyAuctionStarted(Integer.parseInt(auction.getSellerId()), auctionIdInt, itemName);

    logger.info("onAuctionStarted() — auctionId={}, item='{}'", auctionIdInt, itemName);
  }

  // =========================================================================
  //  ĐÓNG PHIÊN
  // =========================================================================

  /**
   * Single entry-point xử lý mọi việc sau khi một phiên đấu giá kết thúc.
   *
   * Được gọi bởi {@code AuctionManager.onAuctionClosedCallback} — tức là từ:
   *   - {@code scheduleClose()}   : kết thúc tự động theo thời gian
   *   - {@code forceCloseAuction()}: Admin force-close
   *
   * Thứ tự xử lý (quan trọng — không thay đổi thứ tự):
   *   1. Persist trạng thái auction và item xuống DB
   *   2. Hủy toàn bộ auto-bid còn active trong DB
   *   3. Gửi notify phân nhánh theo kết quả (FINISHED / CANCELED)
   *
   * Quy ước:
   *   - FINISHED + có leader  : Auction kết thúc hợp lệ, có người thắng
   *   - FINISHED + null leader: Không xảy ra (closeSession() guard), nhưng được phòng thủ
   *   - CANCELED              : Không đủ bid hoặc không đạt reserve price
   *
   * @param auction phiên vừa đóng, status đã là FINISHED hoặc CANCELED
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
      // Thêm null-check trước khi cho item lấy ID
      if (auction.getItem() != null) {
        // STEP 1: Cập nhật thực thể Item trên RAM trước
        auction.getItem().setStatus(ItemStatus.SOLD);
        // STEP 2: Đồng bộ xuống Database
        itemDAO.updateStatus(
            Integer.parseInt(auction.getItem().getId()), ItemStatus.SOLD
        );
      } else {
        logger.error("onAuctionClosed() — Auction FINISHED but item is NULL for auctionId={}", auctionId);
      }
    } else {
      // CANCELED
      boolean canceled = auctionDAO.updateStatus(auctionId, AuctionStatus.CANCELED);
      if (!canceled) {
        logger.error("onAuctionClosed() — Failed to persist CANCELED for auctionId={}",
            auctionId);
      }
      // Cập nhật item → AVAILABLE (quay lại chờ đấu giá tiếp)
      // Thêm null-check trước khi cho item lấy ID
      if (auction.getItem() != null) {
        // STEP 1: Trả trạng thái Item trên RAM về trống trải, tự do
        auction.getItem().setStatus(ItemStatus.AVAILABLE);
        // STEP 2: Đồng bộ xuống Database
        itemDAO.updateStatus(
            Integer.parseInt(auction.getItem().getId()), ItemStatus.AVAILABLE
        );
      } else {
        logger.error("onAuctionClosed() — Auction CANCELED but item is NULL for auctionId={}", auctionId);
      }
    }

    // Hủy toàn bộ auto-bid còn active trong DB
    int canceledCount = autoBidConfigDAO.cancelAllByAuction(auctionId);
    logger.info("onAuctionClosed() — auctionId={}, status={}, canceledAutoBids={}",
        auctionId, auction.getStatus(), canceledCount);

    // Notify AUCTION.FINISHED hoặc AUCTION.CANCELED
    int sellerId    = Integer.parseInt(auction.getSellerId());
    String itemName = auction.getItem() != null ? auction.getItem().getName() : "Unknown Item";
    BigDecimal finalPrice = auction.getCurrentPrice();
    if (auction.getStatus() == AuctionStatus.FINISHED) {
      dispatchFinishedNotifications(auction, auctionId, sellerId, itemName, finalPrice);
    } else {
      dispatchCanceledNotifications(auctionId, sellerId, itemName, finalPrice);
    }
  }

  // =========================================================================
  //  ADMIN OPERATIONS
  // =========================================================================

  /**
   * Admin force-close phiên: uỷ quyền cho AuctionManager.
   * AuctionManager.forceCloseAuction() đã fire onAuctionClosedCallback → onAuctionClosed()
   * nên Service không cần gọi thêm gì.
   */
  public void forceCloseAuction(String auctionId, String reason) {
    auctionManager.forceCloseAuction(auctionId, reason);

    Optional<Auction> auctionOpt = auctionManager.getAuction(auctionId);
    auctionOpt.ifPresent(auction -> {
      if (auction.getStatus() == AuctionStatus.FINISHED || auction.getStatus() == AuctionStatus.CANCELED) {
        onAuctionClosed(auction);
      } else {
        logger.warn("forceCloseAuction() — Auction {} still in status {} after force close, skipping onAuctionClosed.", auctionId, auction.getStatus());
      }
    });
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


  /**
   * Xác nhận thanh toán thành công và thông báo cho các bên liên quan.
   * Đồng bộ dữ liệu trong RAM với DB
   * @param auctionId ID của phiên đấu giá
   * @return true nếu xử lý thành công
   */
  public boolean confirmPayment(int auctionId) {
    try {
      String auctionIdStr = String.valueOf(auctionId);

      // ── SỬA ĐỒNG BỘ: Ưu tiên lấy thực thể phiên đấu giá từ bộ nhớ RAM (Engine) trước thay vì lấy thẳng từ DB ──
      Optional<Auction> auctionOpt = auctionManager.getAuction(auctionIdStr);
      Auction auction;

      if (auctionOpt.isPresent()) {
        auction = auctionOpt.get();
      } else {
        // Fallback: Nếu RAM chưa quản lý (vd: server vừa restart), tải từ DB rồi đưa ngược lại lên RAM
        auction = auctionDAO.getById(auctionId);
        if (auction == null) {
          logger.warn("confirmPayment() — Auction {} not found in database", auctionId);
          return false;
        }
        auctionManager.loadAuction(auction);
      }

      // ── FIX BUG-8 (Lỗi 1): Null-check phòng thủ nghiêm ngặt để triệt tiêu hoàn toàn nguy cơ sập luồng NullPointerException ──
      if (auction.getCurrentLeader() == null) {
        logger.warn("confirmPayment() — Auction {} ended with NO LEADER or NO BIDS. Payment confirmation aborted.", auctionId);
        return false;
      }

      // Lấy ID người thắng cuộc một cách an toàn sau khi đã qua bước kiểm tra đối tượng tồn tại
      String buyerIdStr = auction.getCurrentLeader().getId();

      // ── FIX BUG-8 (Lỗi 2): Loại bỏ toán tử ==, sử dụng hàm so sánh giá trị chuỗi an toàn (.trim().isEmpty()) ──
      if (buyerIdStr == null || buyerIdStr.trim().isEmpty()) {
        logger.error("confirmPayment() — Critical: Current leader ID value is null or blank for auctionId={}", auctionId);
        return false;
      }

      // Kiểm tra trạng thái vòng đời trên RAM (Chỉ cho phép thanh toán khi phiên đã kết thúc thành công)
      if (auction.getStatus() != AuctionStatus.FINISHED) {
        logger.warn("confirmPayment() — Auction {} is currently in status {}, payment conversion skipped.", auctionId, auction.getStatus());
        return false;
      }

      // 3. Truy vấn thông tin bản ghi hóa đơn từ DB
      PaymentDTO payment = paymentDAO.getPaymentByAuctionId(auctionId);
      if (payment == null) {
        logger.warn("confirmPayment() — Payment entity not found for auctionId={}", auctionId);
        return false;
      }

      // 4. Tiến hành cập nhật trạng thái hóa đơn xuống DB thành hoàn tất (completePayment)
      boolean isUpdated = paymentDAO.completePayment(payment.getPaymentId());
      if (isUpdated) {

        // ── SỬA ĐỒNG BỘ: Gọi phương thức đóng gói chuẩn để đồng bộ chuyển trạng thái của đối tượng trên RAM sang PAID ──
        try {
          auction.markPaid();
        } catch (IllegalStateException e) {
          logger.error("confirmPayment() — Lifecycle error when trying to mark auction ID {} as PAID on RAM", auctionId, e);
          return false;
        }

        // Đồng bộ cập nhật cột trạng thái trong bảng đấu giá (auctions) dưới Database thành PAID
        auctionDAO.updateStatus(auctionId, AuctionStatus.PAID);
        List<PaymentDTO> pendingList = paymentDAO.getPendingPayments();
        for (PaymentDTO p : pendingList) {
          if (p.getAuctionId() == auctionId) {
            paymentDAO.completePayment(p.getPaymentId()); // Chuyển từ PENDING -> COMPLETED
            break;
          }
        }

        int sellerId = Integer.parseInt(auction.getSellerId());
        int buyerId = Integer.parseInt(buyerIdStr);
        String itemName = "Auction #" + auctionId;
        BigDecimal finalPrice = auction.getCurrentPrice();

        logger.info("confirmPayment() — Payment SUCCESS. [Auction:{}, Buyer:{}, Seller:{}]. Status synchronized to PAID on both RAM and DB layers.",
            auctionId, buyerId, sellerId);

        // 5. Phát tán các thông báo Realtime cho các bên liên quan (Dữ liệu tiếng Anh sạch sẽ)
        notifyPaymentReceived(sellerId, auctionId, itemName, finalPrice);
        notifySystemNotification(buyerId, "Payment Successful",
            String.format("You have successfully paid %s for item: %s", finalPrice.toString(), itemName));

        return true;
      }

      return false;
    } catch (Exception e) {
      logger.error("confirmPayment() — Critical failure caught during payment confirmation workflow execution.", e);
      return false;
    }
  }

  // ============================================================
  // Notification Dispatchers (Observer Pattern Implementation)
  // ============================================================

  /**
   * Gửi toàn bộ notify khi phiên kết thúc FINISHED:
   *   - Winner  : onAuctionWon + onPaymentDue
   *   - Losers  : onAuctionLost (từng người, try-catch độc lập)
   *   - Seller  : onAuctionEnded
   */
  private void dispatchFinishedNotifications(Auction auction, int auctionId,
      int sellerId, String itemName,
      BigDecimal finalPrice) {
    if (auction.getCurrentLeader() == null) {
      // Không xảy ra theo logic closeSession(), nhưng phòng thủ
      logger.warn("dispatchFinishedNotifications() — FINISHED but no leader for auctionId={}", auctionId);
      notifyAuctionEnded(sellerId, auctionId, itemName, finalPrice);
      return;
    }

    int winnerId = Integer.parseInt(auction.getCurrentLeader().getId());

    // Notify winner
    try {
      notifyAuctionWon(winnerId, auctionId, itemName, finalPrice);
      notifyPaymentDue(winnerId, auctionId, itemName, finalPrice);
    } catch (Exception e) {
      logger.error("dispatchFinishedNotifications() — Failed to notify winner={}", winnerId, e);
    }

    // Notify từng loser — try-catch độc lập để 1 người lỗi không làm mất notify của người khác
    List<Integer> allBidders = bidTransactionDAO.getBiddersByAuctionId(auctionId);
    for (Integer bidderId : allBidders) {
      if (!bidderId.equals(winnerId)) {
        try {
          notifyAuctionLost(bidderId, auctionId, itemName);
        } catch (Exception e) {
          logger.error("dispatchFinishedNotifications() — Failed to notify loser={}", bidderId, e);
        }
      }
    }

    // Notify seller
    try {
      notifyAuctionEnded(sellerId, auctionId, itemName, finalPrice);
    } catch (Exception e) {
      logger.error("dispatchFinishedNotifications() — Failed to notify seller={}", sellerId, e);
    }
  }

  /**
   * Gửi notify khi phiên bị hủy (CANCELED):
   *   - Seller: onAuctionEnded với finalPrice = startingPrice (không có bid hợp lệ)
   */
  private void dispatchCanceledNotifications(int auctionId, int sellerId,
      String itemName, BigDecimal finalPrice) {
    try {
      notifyAuctionEnded(sellerId, auctionId, itemName, finalPrice);
    } catch (Exception e) {
      logger.error("dispatchCanceledNotifications() — Failed to notify seller={}", sellerId, e);
    }
  }

  /**
   * Kết thúc phiên đấu giá (Thành công hoặc Không ai mua): Persist trạng thái và phân phối thông báo.
   */
  public void endAuction(String auctionId) {
    int auctionIdInt = Integer.parseInt(auctionId);

    // Lấy thông tin phiên đấu giá từ bộ nhớ RAM (Engine quản lý)
    Optional<Auction> auctionOpt = auctionManager.getAuction(auctionId);
    if (auctionOpt.isEmpty()) {
      logger.error("endAuction() — Critical: Auction ID {} not found in memory engine.", auctionId);
      return;
    }
    Auction auction = auctionOpt.get();

    // Lấy tên Item theo cấu trúc yêu cầu kèm null-check phòng thủ (Tránh lỗi NullPointerException)
    String itemName = auction.getItem() != null
        ? "Auction #" + auctionId + ": " + auction.getItem().getName()
        : "Auction #" + auctionId + ": Unknown Item";

    // ── Bước 1: Gọi hàm đóng gói sẵn có của lớp Auction để cập nhật trạng thái trên RAM an toàn ──
    try {
      auction.closeSession();
    } catch (IllegalStateException e) {
      logger.error("endAuction() — Closing session aborted due to invalid state for auctionId={}", auctionId, e);
      return;
    }

    // Kiểm tra an toàn xem có người tham gia đặt giá hay không (Null-check Leader) ──
    if (auction.getCurrentLeader() != null) {
      // Trường hợp 1: Có người chiến thắng cuộc đua giá
      int winnerId = Integer.parseInt(auction.getCurrentLeader().getId());
      BigDecimal finalPrice = auction.getCurrentPrice();

      logger.info("endAuction() — Auction {} concluded. winnerId={}, finalPrice={}",
          auctionId, winnerId, finalPrice);

      // Gọi hàm để đồng bộ kết quả (Cập nhật trạng thái FINISHED và đổi Item sang SOLD) xuống DB
      onAuctionClosed(auction);

      // Gửi thông báo kết quả cho người thắng cuộc
      try {
        notifyAuctionWon(winnerId, auctionIdInt, itemName, finalPrice);
        notifyPaymentDue(winnerId, auctionIdInt, itemName, finalPrice);
      } catch (Exception e) {
        logger.error("endAuction() — Failed to notify winner user {}", winnerId, e);
      }

      // Gửi thông báo cho toàn bộ những người còn lại (những người đã thua cuộc)
      // Dựa theo lớp BidTransactionDAO bạn cung cấp, hàm truy vấn id người dùng là getBiddersByAuctionId
      List<Integer> allBidders = bidTransactionDAO.getBiddersByAuctionId(auctionIdInt);
      for (Integer bidderId : allBidders) {
        if (!bidderId.equals(winnerId)) {
          // Bọc try-catch độc lập cho từng người nhận thông báo để không bẻ gãy vòng lặp ── tránh trường hợp 1 bidder lỗi connection, các bidder khác mất notify
          try {
            notifyAuctionLost(bidderId, auctionIdInt, itemName);
          } catch (Exception e) {
            logger.error("endAuction() — Failed to send lost notification to user {} but continuing loop iteration.", bidderId, e);
          }
        }
      }

    } else {
      // Trường hợp 2: Phiên kết thúc mà không một ai đặt giá (Leader bị null)
      logger.warn("endAuction() — Auction {} ended with NO BIDS. Rolling back item status.", auctionId);

      // Đồng bộ DB (onAuctionClosed sẽ nhận diện không leader và tự động rollback Item status về AVAILABLE)
      onAuctionClosed(auction);
    }

    // Phát thông báo sự kiện đóng phòng chung cho toàn bộ hệ thống
    try {
      notifyAuctionEnded(Integer.parseInt(auction.getSellerId()), auctionIdInt, itemName, auction.getCurrentPrice());
    } catch (Exception e) {
      logger.error("endAuction() — Exception caught during auction ended global broadcast event.", e);
    }
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
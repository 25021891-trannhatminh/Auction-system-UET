package server.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.common.ProtocolConstants;
import server.common.model.NotificationEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import server.common.model.WalletUpdateEvent;

public class NotificationDispatcher implements Runnable {

  private static final Logger logger = LoggerFactory.getLogger(NotificationDispatcher.class);

  // Singleton
  private static final NotificationDispatcher instance = new NotificationDispatcher();

  // ── Registry: auctionId → Set<userId> đang xem phiên đó ─────────────────
  private final Map<Integer, Set<Integer>> auctionWatchers = new ConcurrentHashMap<>();


  private NotificationDispatcher() {}

  public static NotificationDispatcher getInstance() {
    return instance;
  }

  // BlockingQueue: Thread-safe tuyệt đối
  private final BlockingQueue<NotificationEvent> queue = new LinkedBlockingQueue<>();

  /**
   * Đưa event vào hàng đợi.
   */
  public void submit(NotificationEvent event) {
    if (event == null) {
      logger.warn("Dispatcher – Attempted to submit a null event, ignoring.");
      return;
    }

    // offer() trả về false ngay lập tức nếu queue đầy (nếu giới hạn capacity)
    if (!queue.offer(event)) {
      logger.warn("Dispatcher – Could not queue event for userId={}, queue might be full.", event.getUserId());
    }
  }

  @Override
  public void run() {
    logger.info("Dispatcher – Worker thread started: {}", Thread.currentThread().getName());

    while (!Thread.currentThread().isInterrupted()) {
      try {
        // take() sẽ block thread này lại nếu queue trống, KHÔNG tốn CPU.
        NotificationEvent event = queue.take();

        // Gửi thông báo
        dispatch(event);

      } catch (InterruptedException e) {
        // Khi Server tắt hoặc thread bị stop
        Thread.currentThread().interrupt();
        logger.info("Dispatcher – Thread interrupted, stopping...");
        break;
      } catch (Exception e) {
        // Cực kỳ quan trọng: Bắt mọi Exception để Worker Thread không bao giờ bị chết
        logger.error("Dispatcher – Error dispatching event, but thread will continue", e);
      }
    }
  }

  /**
   * Logic gửi tin nhắn thực tế
   */
  private void dispatch(NotificationEvent event) {
    try {
      // ── Nhánh 1: WalletUpdateEvent → gửi WALLET_UPDATE thay vì PUSH_NOTIF ──
      if (event instanceof WalletUpdateEvent walletEvent) {
        String msg = String.format("WALLET_UPDATE|%d|%s",
            walletEvent.getUserId(),
            walletEvent.getNewBalance().toPlainString());

        if (walletEvent.getUserId() == -1) {
          ClientManager.broadcast(msg);
        } else {
          ClientManager.sendToUser(walletEvent.getUserId(), msg);
        }

        logger.debug("Dispatcher – WALLET_UPDATE sent to userId={}, balance={}",
            walletEvent.getUserId(), walletEvent.getNewBalance());
        return;
      }

      // ── Nhánh 2: NotificationEvent thông thường → PUSH_NOTIF ──────────────
      String type = event.getType() != null ? event.getType().name() : "INFO";
      String title = event.getTitle() != null ? event.getTitle() : "";
      String content = event.getMessage() != null ? event.getMessage() : "";

      // 2. Format chuỗi theo giao thức (nên tự format để giảm phụ thuộc ClientHandler)
      String rawProtocolMessage = String.format("PUSH_NOTIF|%s|%s|%s", type, title, content);

      // 3. Đẩy sang ClientManager
      // Nếu user offline, sendToUser sẽ không tìm thấy handler và bỏ qua (Hợp lý)
      if (event.getUserId() == ProtocolConstants.NOTIFICATION_GLOBAL_USER_ID) {
        // Nếu ID là -1, gọi hàm broadcast trong ClientManager
        ClientManager.broadcast(rawProtocolMessage);
      } else if (event.getUserId() == ProtocolConstants.NOTIFICATION_AUCTION_USER_ID) {
        // Nếu ID = 0, thông báo cho Users trong Auction
        pushToAuctionWatchers(event.getRelatedId(), rawProtocolMessage);
      } else {
        // Nếu là ID cụ thể, gửi cho đúng người đó
        ClientManager.sendToUser(event.getUserId(), rawProtocolMessage);
      }
      logger.debug("Dispatcher – Successfully sent notif to userId={}", event.getUserId());
    } catch (Exception e) {
      logger.error("Dispatcher – Failed to format or send message for userId={}", event.getUserId(), e);
    }
  }


  // ─────────────────────────────────────────────────────────────────────────
  //  Registry management
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Đăng ký user vào danh sách watcher của một phiên.
   */
  public void subscribeAuction(int auctionId, int userId) {
    auctionWatchers
        .computeIfAbsent(auctionId, k -> ConcurrentHashMap.newKeySet())
        .add(userId);
    logger.debug("Dispatcher – userId={} subscribed to auctionId={}", userId, auctionId);
  }

  /**
   * Hủy đăng ký user khỏi một phiên cụ thể.
   */
  public void unsubscribeAuction(int auctionId, int userId) {
    Set<Integer> watchers = auctionWatchers.get(auctionId);
    if (watchers != null) {
      watchers.remove(userId);
      // Dọn map nếu phiên không còn ai xem — tránh memory leak khi nhiều phiên đóng
      if (watchers.isEmpty()) {
        auctionWatchers.remove(auctionId, watchers);
      }
    }
    logger.debug("Dispatcher – userId={} unsubscribed from auctionId={}", userId, auctionId);
  }

  /**
   * Hủy đăng ký user khỏi tất cả phiên — gọi khi client disconnect.
   */
  public void unsubscribeAll(int userId) {
    auctionWatchers.values().forEach(watchers -> watchers.remove(userId));
    logger.debug("Dispatcher – userId={} unsubscribed from all auctions", userId);
  }

  /**
   * Dọn toàn bộ watcher của một phiên khi phiên đóng.
   * Gọi bởi AuctionService.onAuctionClosed() sau khi DB persist xong.
   */
  public void clearAuction(int auctionId) {
    auctionWatchers.remove(auctionId);
    logger.debug("Dispatcher – Cleared all watchers for auctionId={}", auctionId);
  }


  /**
   * Push message đến tất cả userId đang xem phiên auctionId.
   * Gọi từ dispatch() trong worker thread — không cần sync thêm vì
   * auctionWatchers là ConcurrentHashMap và Set là ConcurrentHashSet.
   */
  private void pushToAuctionWatchers(int auctionId, String message) {
    Set<Integer> watchers = auctionWatchers.get(auctionId);
    if (watchers == null || watchers.isEmpty()) {
      logger.debug("Dispatcher – No watchers for auctionId={}, skip broadcast", auctionId);
      return;
    }
    // Snapshot set để tránh ConcurrentModificationException nếu
    // có subscribe/unsubscribe xảy ra đồng thời trong khi đang iterate
    Set<Integer> snapshotUserList = Set.copyOf(watchers);
    int counter = 0;
    for (int userId : snapshotUserList) {
      if (ClientManager.sendToUser(userId, message)) counter++;
    }
    logger.debug("Dispatcher – Broadcast auctionId={} → {}/{} watchers reached",
        auctionId, counter, snapshotUserList.size());
  }
  public int getQueueSize() {
    return queue.size();
  }
}
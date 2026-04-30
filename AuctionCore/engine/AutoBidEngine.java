package engine;

import enums.AutoBidStatus;
import exception.InvalidBidException;
import model.Auction;
import model.AutoBidConfig;
import model.BidTransaction;
import model.user.Bidder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

/*

  AutoBidEngine — Xử lý đấu giá tự động (Auto-Bidding).

 Cách hoạt động :
   1. Sau mỗi bid thủ công được chấp nhận → Auction gọi AutoBidEngine.trigger()
   2. Engine lấy PriorityQueue của phiên đó
   3. peek() → xem config ưu tiên cao nhất (root Heap)
   4. Nếu config đó thuộc bidder vừa bid → bỏ qua (không tự outbid mình)
   5. Nếu canBid(currentPrice) → đặt giá tự động (cascade)
   6. Lặp lại cho đến khi không còn ai có thể bid cao hơn maxBid của mình

 Thứ tự ưu tiên trong PriorityQueue :
   - maxBid cao hơn → được xử lý trước
   - maxBid bằng nhau → đăng ký sớm hơn → được xử lý trước (FIFO tie-breaking)

 Cascade example:
   BidderA bid thủ công 500k
   → Engine trigger: BidderB có auto max=700k, inc=50k → bid 550k
   → Engine trigger: BidderC có auto max=650k, inc=50k → bid 600k
   → Engine trigger: BidderB auto → bid 650k
   → Engine trigger: BidderC max=650k đã đạt → không thể bid nữa → dừng
   → Kết quả: BidderB dẫn đầu với 650k

 DB:
   Sau mỗi auto-bid → BidDAO.insert(BidTransaction) với is_auto_bid = true
   Sau khi cascade kết thúc → AutoBidDAO.updateStatus() những config đã COMPLETED

 Thread safety:
   Engine chạy trong vùng sau lock của Auction (lock đã được release).
   Mỗi auto-bid gọi lại auction.placeBid() → tự acquire lock lại.
   Do ReentrantLock(fair=true), các auto-bid được xếp hàng fair.

 UI:
   Sau cascade xong, Auction đã notify Observer cho từng bid.
   UI sẽ nhận được các update lần lượt qua Observer.
 */
public class AutoBidEngine {

    /*
     Map auctionId → PriorityQueue<AutoBidConfig>

     Mỗi phiên có một PriorityQueue riêng lưu tất cả config auto-bid đang ACTIVE.
     ConcurrentHashMap để nhiều thread có thể register/trigger đồng thời.
     */
    private final Map<String, PriorityQueue<AutoBidConfig>> AutoBidQueues
        = new ConcurrentHashMap<>();

    /*
      Map auctionId + bidderId → Bidder object
      Cần để gọi auction.placeBid(bidder, ...) — engine cần Bidder object thực sự.
        => Lưu thông tin chi tiết về Bidder

     DB: khi load auction từ DB, tầng DAO phải
          gọi registerBidder() cho mỗi bidder có auto-bid trong phiên.
     */
    private final Map<String, Map<String, Bidder>> auctionBidderMap
        = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    //  Registration

    /*
     Đăng ký AutoBidConfig cho 1 Bidder trong 1 Auction = khi Bidder kích hoạt auto-bid trên UI.
     Synchronized -> tránh Race Condition, 2 Thread cùng lúc đăng ký cho User A

     Nếu bidder đã có config cũ trong queue → xóa cái cũ, thêm cái mới.
     (Đảm bảo UNIQUE per (auction, bidder) — DB constraint)
     */
    public synchronized void register(AutoBidConfig config, Bidder bidder) {
        String auctionId = config.getAuctionId();
        String bidderId  = config.getBidderId();

        // Tạo PriorityQueue nếu chưa có cho Auction
        AutoBidQueues.computeIfAbsent(auctionId, k -> new PriorityQueue<>());
        auctionBidderMap.computeIfAbsent(auctionId, k -> new ConcurrentHashMap<>());

        // Xóa config cũ nếu bidder đã đăng ký trước đó
        PriorityQueue<AutoBidConfig> currentQueue = AutoBidQueues.get(auctionId);
        currentQueue.removeIf(currentConfig -> currentConfig.getBidderId().equals(bidderId));

        // Thêm config mới
        currentQueue.add(config);
        auctionBidderMap.get(auctionId).put(bidderId, bidder);

        System.out.printf("[AutoBidEngine] Registered: bidder=%s, auction=%s, max=%.2f, inc=%.2f%n",
            bidder.getUsername(), auctionId.substring(0, 8),
            config.getMaxBid(), config.getIncrement());
    }

    /*
     Hủy đăng ký auto-bid khi bidder cancel hoặc phiên kết thúc.
     */
    public synchronized void unregister(String auctionId, String bidderId) {
        PriorityQueue<AutoBidConfig> currentQueue = AutoBidQueues.get(auctionId);
        if (currentQueue != null) {
            currentQueue.removeIf(currentConfig -> currentConfig.getBidderId().equals(bidderId));
        }
    }

    /*
     Dọn dẹp toàn bộ data của một phiên khi phiên kết thúc.
     Gọi bởi AuctionManager sau khi closeSession().
     */
    public synchronized void cleanupAutoBids(String auctionId) {
        AutoBidQueues.remove(auctionId);
        auctionBidderMap.remove(auctionId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Core trigger — gọi sau mỗi bid thành công

    /**
     Kích hoạt auto-bidding cascade sau một bid thủ công.
     @param auction         Phiên đấu giá
     @param triggeringBidder Bidder vừa đặt bid (thủ công hoặc auto)
     @return Danh sách các auto-bid đã được thực hiện trong cascade này
     */
    public List<BidTransaction> trigger(Auction auction, Bidder triggeringBidder) {
        String auctionId = auction.getId();
        List<BidTransaction> autoBids = new ArrayList<>();  // Lưu các bidTransaction trong Cascade

        // Không ai đăng ký AutoBid -> không cần Trigger
        PriorityQueue<AutoBidConfig> currentQueue = AutoBidQueues.get(auctionId);
        if (currentQueue == null || currentQueue.isEmpty()) return autoBids;

        // Giới hạn cascade để tránh vòng lặp vô hạn
        // Thực tế cascade sẽ kết thúc tự nhiên khi không ai còn canBid()
            // Lý do: Mỗi bidder có thể:
                // 1. Được chọn để auto-bid (khi không phải triggering)
                // 2. Bị skip (khi là triggering)
        int maxIterations = currentQueue.size() * 2 + 1;
        int iterations    = 0;

        while (!currentQueue.isEmpty() && auction.isRunning() && iterations < maxIterations) {
            iterations++;

            AutoBidConfig topConfig = currentQueue.peek();
            if (topConfig == null) break;

            // Bỏ qua bidder vừa đặt giá 
            if (topConfig.getBidderId().equals(triggeringBidder.getId())) {
                // Kiểm tra config thứ 2
                if (currentQueue.size() < 2) break;
                // Tạm thời lấy config đầu ra, xét config tiếp theo
                AutoBidConfig firstConfig = currentQueue.poll();
                AutoBidConfig secondConfig    = currentQueue.peek();
                currentQueue.add(firstConfig); // đưa lại vào queue

                if (secondConfig == null || secondConfig.getBidderId().equals(triggeringBidder.getId())) break;
            }

            // Kiểm tra điều kiện auto-bid
            if (!topConfig.canBid(auction.getCurrentPrice())) {
                // Config đạt maxBid → đánh dấu COMPLETED và loại khỏi queue
                currentQueue.removeIf(c -> c.getBidderId().equals(topConfig.getBidderId()));
                topConfig.complete();
                System.out.printf("[AutoBidEngine] Config COMPLETED for bidder=%s (maxBid=%.2f reached)%n",
                    topConfig.getBidderId(), topConfig.getMaxBid());
                continue;
            }

            // Lấy Bidder object để đặt giá
            Bidder autoBidder = getBidder(auctionId, topConfig.getBidderId());
            if (autoBidder == null) {
                currentQueue.poll(); // Bidder không còn trong system (bị ban, thread xóa auction) → loại bỏ
                continue;
            }

            double nextAmount = topConfig.nextBidAmount(auction.getCurrentPrice());

            // Thực hiện auto-bid — gọi lại auction.placeBid()
            try {
                BidTransaction bidTransaction = auction.placeBid(autoBidder, nextAmount, true);
                autoBids.add(bidTransaction);

                System.out.printf("[AutoBidEngine] Auto-bid: %s bid %.2f for auction %s%n",
                    autoBidder.getUsername(), nextAmount, auctionId.substring(0, 8));

                // Sau auto-bid, bidder này là triggering → vòng tiếp theo skip họ
                triggeringBidder = autoBidder;

            } catch (InvalidBidException e) {
                // Bid bị reject (giá không hợp lệ) → loại config này
                System.err.println("[AutoBidEngine] Auto-bid rejected: " + e.getMessage());
                currentQueue.removeIf(c -> c.getBidderId().equals(topConfig.getBidderId()));
                break;
            } catch (Exception e) {
                System.err.println("[AutoBidEngine] Unexpected error: " + e.getMessage());
                break;
            }
        }

        return autoBids;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Bidder getBidder(String auctionId, String bidderId) {
        Map<String, Bidder> bidderMap = auctionBidderMap.get(auctionId);
        return bidderMap != null ? bidderMap.get(bidderId) : null;
    }

    /** Xem danh sách configs đang active trong một phiên (debug/admin) */
    public List<AutoBidConfig> getActiveConfigs(String auctionId) {
        PriorityQueue<AutoBidConfig> queue = AutoBidQueues.get(auctionId);
        if (queue == null) return new ArrayList<>();
        // Tạo sorted list từ PriorityQueue (không phá vỡ queue)
        List<AutoBidConfig> sorted = new ArrayList<>(queue);
        sorted.sort(AutoBidConfig::compareTo);
        return sorted;
    }

    public int getRegisteredCount(String auctionId) {
        PriorityQueue<AutoBidConfig> queue = AutoBidQueues.get(auctionId);
        return queue == null ? 0 : queue.size();
    }
}

package server.common.entity.BidModel;


import server.common.entity.Auction;
import server.common.entity.Auction.PlaceBidResult;
import server.common.entity.User;
import server.common.exception.InvalidBidException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

/*
  AutoBidEngine — Xử lý đấu giá tự động (Auto-Bidding).

 Cách hoạt động :
   1. Sau mỗi bid thủ công đặt → Auction gọi AutoBidEngine.trigger()
   2. Engine lấy PriorityQueue của phiên đó
   3. peek() → xem AutoBidConfig ưu tiên cao nhất (root Heap)
   4. Nếu config đó thuộc bidder vừa bid → bỏ qua (không tự outbid mình)
   5. Nếu canBid(currentPrice) → đặt giá tự động
   6. Lặp lại cho đến khi không còn ai có thể bid cao hơn maxBid của mình

 Thứ tự ưu tiên trong PriorityQueue :
   - maxBid cao hơn → được xử lý trước
   - maxBid bằng nhau → đăng ký sớm hơn → được xử lý trước (FIFO tie-breaking)

 Cascade example:
   UserA bid thủ công 500k
   → Engine trigger: UserB có auto max=700k, inc=50k → bid 550k


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
    private final Map<Integer, PriorityQueue<AutoBidConfig>> AutoBidManager
        = new ConcurrentHashMap<>();        // Quản lý AutoBid cho tất cả Auction

    /*
      Map auctionId + userId → User object : Tìm Bidder thông qua AuctionID + userID
      Cần để gọi auction.placeBid(bidder, ...) — engine cần User object thực sự.
        => Lưu thông tin chi tiết về User

     DB: khi load auction từ DB, tầng DAO phải
          gọi registerUser() cho mỗi bidder có auto-bid trong phiên.
     */
    private final Map<Integer, Map<Integer, User>> AutoBidBidderManager
        = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    //  Registration

    /*
     Đăng ký AutoBidConfig cho 1 User trong 1 Auction = khi User kích hoạt auto-bid trên UI.
     Synchronized -> tránh Race Condition, 2 Thread cùng lúc đăng ký cho User A

     Nếu bidder đã có config cũ trong queue → xóa cái cũ, thêm cái mới.
     (Đảm bảo UNIQUE per (auction, bidder) — DB constraint)
     */
    public synchronized void register(AutoBidConfig config, User bidder) {
        int auctionId = config.getAuctionId();
        int bidderId  = config.getBidderId();

        // Tạo PriorityQueue nếu chưa có cho Auction
        AutoBidManager.computeIfAbsent(auctionId, pq -> new PriorityQueue<>());
        AutoBidBidderManager.computeIfAbsent(auctionId, map -> new ConcurrentHashMap<>());

        // Xóa config cũ nếu bidder đã đăng ký trước đó
        PriorityQueue<AutoBidConfig> auctionAutoBidQueue = AutoBidManager.get(auctionId);
        auctionAutoBidQueue.removeIf(currentConfig -> currentConfig.getBidderId() == bidderId);

        // Thêm config mới
        auctionAutoBidQueue.add(config);
        AutoBidBidderManager.get(auctionId).put(bidderId, bidder);

        System.out.printf("[AutoBidEngine] Registered: bidder=%s, auction=%s, max=%.2f, inc=%.2f%n",
            bidder.getUsername(), auctionId,
            config.getMaxBid(), config.getIncrement());
    }

    /*
     Hủy đăng ký auto-bid khi bidder cancel hoặc phiên kết thúc.
     */
    public synchronized void unregister(int auctionId, int bidderId) {
        // Remove config nếu config là của Bidder
        PriorityQueue<AutoBidConfig> auctionAutoBidQueue = AutoBidManager.get(auctionId);
        if (auctionAutoBidQueue != null) {
            auctionAutoBidQueue.removeIf(currentConfig -> currentConfig.getBidderId() == bidderId);
        }
        // Remove Bidder ra khỏi BidderManager
        Map<Integer, User> bidderMap = AutoBidBidderManager.get(auctionId);
        if (bidderMap != null) {
            bidderMap.remove(bidderId);
        }
    }

    /*
     Dọn dẹp toàn bộ data của một phiên khi phiên kết thúc.
     Gọi bởi AuctionManager sau khi closeSession().
     */
    public synchronized void cleanupAutoBids(int auctionId) {
        AutoBidManager.remove(auctionId);
        AutoBidBidderManager.remove(auctionId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Core trigger — gọi sau mỗi bid thành công,openAuction, registerAutoBid
        // scheduleOpen() → trigger(auction, null)
        // placeBid() → trigger(auction, manualBidder.getId())
        // registerAutoBid() trên RUNNING Auction → trigger(auction, null)
    /*      Xác định amountBid:
     *
     *     Có ≥ 2 config:
     *      Nếu 2 maxBid của 2 config >= currentPrice
     *          secondWinner = config đứng thứ 2 (maxBid cao nhì)
     *          amountBid   = secondWinner.maxBid + winner.increment
     *          (if amountBid > winner.maxBid -> amountBid = winner.maxBid)
     *      Nếu maxBidA =< currentPrice < maxBidB
     *          amountBid = currentPrice + winner.increment
     *          (if amountBid > winner.maxBid -> amountBid = winner.maxBid)
     *
     *     Chỉ có 1 config :
     *       amount = currentPrice + winner.increment
     *       Condition: amount ≤ winner.maxBid
     *
     *     amount ≤ currentPrice → không đủ điều kiện → không bid.
    */

    // Amount mà winner có thể trả
    BigDecimal calculateWinnerAmount(BigDecimal currentPrice,
                                     AutoBidConfig winner,
                                     AutoBidConfig secondWinner) {
        BigDecimal baseAmount = winner.getMaxBid();
        if (secondWinner != null) {
            if (secondWinner.getMaxBid().compareTo(currentPrice) < 0 && currentPrice.compareTo(winner.getMaxBid()) < 0){
                baseAmount = currentPrice.add(winner.getIncrement());
            }else if (secondWinner.getMaxBid().compareTo(currentPrice) > 0) {
                baseAmount = secondWinner.getMaxBid().add(winner.getIncrement());
            }
        } else {
            baseAmount = currentPrice.add(winner.getIncrement());
        }
        BigDecimal winnerCanBidAmount = baseAmount.min(winner.getMaxBid());
        return winnerCanBidAmount;
    }

    /* Xử lý return BidTransaction */
    private PlaceBidResult executeWinnerBidTransaction(Auction auction, AutoBidConfig winner, AutoBidConfig secondWinner) {
        BigDecimal amountBidWinner = calculateWinnerAmount(auction.getCurrentPrice(), winner, secondWinner);

        if (amountBidWinner.compareTo(auction.getCurrentPrice()) <= 0) {
            winner.complete();
            return null;
        }
        // Lấy User object để đặt giá
        User winnerBidder = getUser(auction.getId(), winner.getBidderId());
        User previousWinner = auction.getCurrentLeader() != null ? auction.getCurrentLeader() : winnerBidder;
        BigDecimal previousPrice = auction.getCurrentPrice();
        if (winnerBidder == null) {
            System.err.printf("[AutoBidEngine] Winner bidder object not found for id=%s%n",
                winner.getBidderId());
            return null;
        }
        try {
            // placeBid() có trong executePlaceBidFlow rồi
//            Auction.PlaceBidResult result = auction.placeBid(winnerBidder, amountBidWinner, true);
            BidTransaction tx = new BidTransaction(
                auction.getId(), winnerBidder.getId(), winnerBidder.getUsername(), amountBidWinner, true
            );
            BidTransaction outBidTx = new BidTransaction(
                auction.getId(), previousWinner.getId(), previousWinner.getUsername(), previousPrice, false
            );
            boolean timeExtend = (auction.getSecondsRemaining() > 0 && auction.getSecondsRemaining() <= auction.getSnipeWindowSeconds());
            PlaceBidResult result = new PlaceBidResult(tx,outBidTx, timeExtend,previousPrice,previousWinner,auction.getEndTime(),auction.getLastBidTime());
            if (amountBidWinner.compareTo(winner.getMaxBid()) >= 0) winner.complete();
            return result;

        } catch (InvalidBidException e) {
            System.err.printf("[AutoBidEngine] Auto-bid failed for %s: %s%n",
                winnerBidder.getUsername(), e.getMessage());
            return null;
        }
    }

    /**
     Kích hoạt auto-bidding trigger sau một bid thành công, 1 autobidConfig đăng ký hoặc hủy trong queue, hoặc .

     /*
     *   Bước 1 — Xác định winner (top 1 queue):
     *     Config có maxBid cao nhất trong queue = winner.
     *     Nếu winner chính là người vừa trigger → dừng (null).
     *
     *   Bước 2 — Xác định amountBid
     *   Bước 3 — return BidTransaction
     *
     */
    public Auction.PlaceBidResult trigger(Auction auction, User triggeringBidder) {
        int auctionId = auction.getId();
        // Xác định winner
        AutoBidConfig winner;
        AutoBidConfig secondWinner;
        synchronized (this) {
            PriorityQueue<AutoBidConfig> auctionQueue = AutoBidManager.get(auctionId);
            if (auctionQueue == null || auctionQueue.isEmpty()) return null;

            // Lấy tham chiếu cho top1 ,top2
            winner = auctionQueue.peek();
            auctionQueue.poll();
            secondWinner = auctionQueue.peek();
            auctionQueue.add(winner);
        }

        // Nếu triggerBidder != null -> Check winner có phải Bidder vừa đặt Bid không?
        if (triggeringBidder != null && winner.getBidderId() == triggeringBidder.getId()) {
          // Nếu manual Bid của winner lớn hơn maxAutoBid của winner đó -> AutoBidConfig complete
          if (auction.getCurrentPrice().compareTo(winner.getMaxBid()) >= 0){
              winner.complete();
          }
          return null;
        }

        return executeWinnerBidTransaction(auction, winner, secondWinner);
    }


    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers

    // Lấy User Object thông qua AuctionID, UserID
    private User getUser(int auctionId, int bidderId) {
        Map<Integer, User> bidderMap = AutoBidBidderManager.get(auctionId);
        return bidderMap != null ? bidderMap.get(bidderId) : null;
    }

    /**
     * Lấy winner hiện tại mà không trigger bid.
     * Kiểm tra winner trong auto-bid.
     */
    public synchronized AutoBidConfig peekWinner(int auctionId) {
        PriorityQueue<AutoBidConfig> queue = AutoBidManager.get(auctionId);
        return queue == null ? null : queue.peek();
    }

    public synchronized int getWinnerId(int auctionId){
        return peekWinner(auctionId).getBidderId();
    }
    /** Số lượng AutoBid đăng ký trong 1 Auction */
    public int getRegisteredCount(int auctionId) {
        PriorityQueue<AutoBidConfig> queue = AutoBidManager.get(auctionId);
        return queue == null ? 0 : queue.size();
    }
}

package observer;

import model.Auction;
import model.BidTransaction;

/**
 * Interface Observer — pattern Observer cho hệ thống đấu giá.
 *
 * Ai implement interface này?
 *   - ConsoleObserver     (test/debug — in ra console)
 *   - ClientHandler       (server-side — push JSON qua Socket tới client)
 *   - NotificationService (lưu notification vào DB)
 *
 * Mỗi khi có sự kiện trong Auction, auction gọi notifyObservers()
 * → lần lượt gọi các method này trên tất cả observer đã đăng ký.
 *
 * Kết nối UI/Server:
 *   ClientHandler (tầng network) sẽ implement interface này.
 *   Khi onBidUpdated() được gọi, ClientHandler serialize Auction state
 *   thành JSON và push qua Socket đến đúng client.
 */
public interface AuctionObserver {

    /**
     * Được gọi ngay sau khi một bid được chấp nhận thành công.
     * @param auction Phiên đấu giá với state đã cập nhật (giá mới, leader mới)
     * @param tx      BidTransaction vừa được tạo
     */
    void onBidUpdated(Auction auction, BidTransaction tx);

    /**
     * Được gọi khi phiên đấu giá kết thúc (FINISHED hoặc CANCELED).
     * @param auction Phiên đấu giá với winner và final price
     */
    void onAuctionClosed(Auction auction);

    /**
     * Được gọi khi thời gian phiên được gia hạn (anti-sniping).
     * @param auction  Phiên đấu giá
     * @param addedSeconds  Số giây được thêm vào
     */
    void onTimeExtended(Auction auction, int addedSeconds);
}

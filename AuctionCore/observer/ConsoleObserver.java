package observer;

import model.Auction;
import model.BidTransaction;

import java.time.format.DateTimeFormatter;

/**
 * ConsoleObserver — Implementation mặc định của AuctionObserver.
 *
 * Mục đích:
 *   - Dùng trong môi trường test/debug để in event ra console
 *   - Làm ví dụ tham khảo để implement các Observer thực tế:
 *       - ClientSocketObserver  : push JSON qua Socket đến client (Server layer)
 *       - DatabaseObserver      : lưu notification vào DB (DAO layer)
 *       - JavaFXObserver        : cập nhật UI trên JavaFX thread (UI layer)
 *
 * ⚠️  Kết nối UI (JavaFX):
 *   Khi tích hợp JavaFX, tạo JavaFXObserver implements AuctionObserver:
 *
 *   @Override
 *   public void onBidUpdated(Auction auction, BidTransaction tx) {
 *       Platform.runLater(() -> {
 *           // Cập nhật Label giá hiện tại
 *         +  currentPriceLabel.setText(String.format("%.2f", auction.getCurrentPrice()));
 *           // Thêm row vào TableView lịch sử bid
 *           bidHistoryTable.getItems().add(0, tx);
 *           // Highlight người dẫn đầu
 *           leaderLabel.setText(auction.getCurrentLeader().getUsername());
 *       });
 *   }
 *
 * ⚠️  Kết nối Server (Socket):
 *   ClientSocketObserver implements AuctionObserver:
 *
 *   @Override
 *   public void onBidUpdated(Auction auction, BidTransaction tx) {
 *       String json = gson.toJson(new BidUpdateDTO(auction, tx));
 *       clientSocket.sendMessage(json); // push realtime
 *   }
 */
public class ConsoleObserver implements AuctionObserver {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final String observerName; // để phân biệt nhiều observer khi test

    public ConsoleObserver() {
        this("Console");
    }

    public ConsoleObserver(String name) {
        this.observerName = name;
    }

    @Override
    public void onBidUpdated(Auction auction, BidTransaction tx) {
        System.out.printf("[%s | %s] BID UPDATE — Auction: %s | %s bid %.2f%s | New price: %.2f%n",
            FMT.format(tx.getBidTime()),
            observerName,
            auction.getItem().getName(),
            tx.getBidderName(),
            tx.getAmount(),
            tx.isAutoBid() ? " (AUTO)" : "",
            auction.getCurrentPrice()
        );
    }

    @Override
    public void onAuctionClosed(Auction auction) {
        System.out.println("─".repeat(60));
        System.out.printf("[%s] AUCTION CLOSED — %s%n",
            observerName, auction.getItem().getName());
        System.out.printf("  Status : %s%n", auction.getStatus());
        System.out.printf("  Winner : %s%n",
            auction.getCurrentLeader() != null
                ? auction.getCurrentLeader().getUsername() : "No winner");
        System.out.printf("  Price  : %.2f%n", auction.getCurrentPrice());
        System.out.printf("  Bids   : %d total%n", auction.getTotalBids());
        System.out.println("─".repeat(60));
    }

    @Override
    public void onTimeExtended(Auction auction, int addedSeconds) {
        System.out.printf("[%s | Anti-snipe] Auction '%s' extended by %ds. New end: %s | %ds remaining%n",
            observerName,
            auction.getItem().getName(),
            addedSeconds,
            FMT.format(auction.getEndTime()),
            auction.getSecondsRemaining()
        );
    }
}

package server.service.listeners;

import server.common.entity.Auction;
import server.common.entity.AuctionObserver;
import server.common.entity.BidTransaction;
import server.service.AuctionService;

import java.math.BigDecimal;

public class ObserverToNotificationEventAdapter implements AuctionObserver {

  /**
   * Adapter chuyển Domain Events (từ Auction) sang Business Events (cho Notification, Wallet, v.v.)
   *
   * Đây là cầu nối giữa hai hệ thống Observer.
   */

    private final AuctionService auctionService;

    public ObserverToNotificationEventAdapter(AuctionService auctionService) {
      this.auctionService = auctionService;
    }

    @Override
    public void onBidUpdated(Auction auction, BidTransaction tx) {
      String itemName = auction.getItem().getName();
      int auctionId = Integer.parseInt(auction.getId());
      int bidderId = Integer.parseInt(tx.getBidderId());
      BigDecimal amount = tx.getAmount();

      // 1. Notify bid placed cho người vừa bid
      auctionService.notifyBidPlaced(bidderId, auctionId, itemName, amount);

      // 2. Notify outbid cho người bị vượt giá (nếu có)
      if (auction.getCurrentLeader() != null &&
          !auction.getCurrentLeader().getId().equals(tx.getBidderId())) {

        auctionService.notifyOutbid(
            Integer.parseInt(tx.getBidderId()),
            auctionId,
            itemName,
            auction.getCurrentPrice()
        );
      }
    }

    @Override
    public void onAuctionClosed(Auction auction) {
      String itemName = auction.getItem().getName();
      int auctionId = Integer.parseInt(auction.getId());
      BigDecimal finalPrice = auction.getCurrentPrice();
      int sellerId = Integer.parseInt(auction.getSellerId());

      if (auction.getStatus() == server.common.enums.AuctionStatus.FINISHED) {
        Integer winnerId = auction.getCurrentLeader() != null
            ? Integer.parseInt(auction.getCurrentLeader().getId()) : null;

        if (winnerId != null) {
          auctionService.notifyAuctionWon(winnerId, auctionId, itemName, finalPrice);
          auctionService.notifyPaymentDue(winnerId, auctionId, itemName, finalPrice);
        }

        auctionService.notifyAuctionEnded(sellerId, auctionId, itemName, finalPrice);
      }
      // Có thể thêm notify cho CANCELED nếu cần
    }

    @Override
    public void onTimeExtended(Auction auction, int addedSeconds) {
      // Có thể notify cho tất cả bidder đang theo dõi nếu cần
      // Hiện tại chỉ log
      System.out.printf("[Anti-Snipe] Auction %s extended by %d seconds%n",
          auction.getId().substring(0, 8), addedSeconds);
    }
  }

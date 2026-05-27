package server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.common.entity.Item;
import server.common.model.AuctionDTO;
import server.common.model.AuctionVisualisationDTO;
import server.common.model.BidPointDTO;
import server.repository.AuctionDAO;
import server.repository.BidTransactionDAO;
import server.repository.ItemDAO;

import java.math.BigDecimal;
import java.util.List;

public class AuctionVisualisationService {

  private static final Logger logger = LoggerFactory.getLogger(AuctionVisualisationService.class);

  private final AuctionDAO auctionDAO = new AuctionDAO();
  private final ItemDAO itemDAO = new ItemDAO();
  private final BidTransactionDAO bidTransactionDAO = new BidTransactionDAO();

  /**
   * Builds the price timeline DTO for an auction from persisted database state.
   *
   * <p>The visualisation flow intentionally uses {@code auctions}, {@code items}, and
   * {@code bid_transactions} as read-only sources. It does not join an auction room and
   * does not change any core domain bidding/payment logic.</p>
   *
   * @param auctionId auction id requested by the UI
   * @return visualisation DTO, or {@code null} when the auction does not exist
   */
  public AuctionVisualisationDTO getVisualisation(int auctionId) {
    AuctionDTO auction = auctionDAO.getById(auctionId);
    if (auction == null) {
      logger.warn("Auction visualisation requested for missing auctionId={}", auctionId);
      return null;
    }

    Item item = itemDAO.getById(auction.getItemId());
    String itemName = item == null ? "Auction #" + auctionId : item.getName();
    BigDecimal startingPrice = item == null ? auction.getCurrentPrice() : item.getStartingPrice();
    List<BidPointDTO> points = bidTransactionDAO.getBidPointHistory(auctionId);

    return new AuctionVisualisationDTO(
        auction.getAuctionId(),
        itemName,
        startingPrice,
        auction.getCurrentPrice(),
        points
    );
  }
}

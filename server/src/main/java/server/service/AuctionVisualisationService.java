package server.service;

import server.common.entity.Auction;
import server.common.entity.manager.AuctionManager;
import server.common.model.AuctionVisualisationDTO;
import server.common.model.BidPointDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AuctionVisualisationService {

  private static final Logger logger = LoggerFactory.getLogger(AuctionVisualisationService.class);
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  /**
   * Trả về DTO đầy đủ để render biểu đồ giá.
   * Dữ liệu được lấy trực tiếp từ RAM (Auction entity trong AuctionManager).
   * Hỗ trợ mọi trạng thái: OPEN, RUNNING, FINISHED, CANCELED, PAID.
   *
   * @param auctionId ID phiên đấu giá
   * @return AuctionVisualisationDTO hoặc null nếu không tìm thấy phiên
   */
  public AuctionVisualisationDTO getVisualisation(int auctionId) {
    // Lấy auction từ RAM (đã được load khi khởi động server hoặc tạo mới)
    Auction auction = AuctionManager.getInstance()
        .getAuction(auctionId)
        .orElse(null);

    if (auction == null) {
      logger.warn("Auction {} not found in AuctionManager (not loaded into RAM)", auctionId);
      return null;
    }

    // Chuyển lịch sử bid thành các điểm (bidTime, amount)
    List<BidPointDTO> points = auction.getBidHistory().stream()
        .map(tx -> new BidPointDTO(
            tx.getBidTime().format(TIME_FORMATTER),   // định dạng thời gian
            tx.getAmount()
        ))
        .sorted(Comparator.comparing(BidPointDTO::getBidTime)) // sắp xếp tăng dần theo thời gian
        .collect(Collectors.toList());

    // Tạo DTO trả về
    return new AuctionVisualisationDTO(
        auction.getId(),
        auction.getItem().getName(),
        auction.getStartingPrice(),
        auction.getCurrentPrice(),
        points
    );
  }
}
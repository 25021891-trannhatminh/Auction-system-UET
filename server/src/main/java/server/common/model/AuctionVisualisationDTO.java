package server.common.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class AuctionVisualisationDTO {
  private final int auctionId;
  private final String itemName;
  private final BigDecimal startingPrice;
  private final BigDecimal currentPrice;
  private final List<BidPointDTO> points;   // sorted ASC by bidTime, empty [] nếu chưa có bid

  public AuctionVisualisationDTO(int auctionId,
      String itemName,
      BigDecimal startingPrice,
      BigDecimal currentPrice,
      List<BidPointDTO> points) {
    this.auctionId = auctionId;
    this.itemName = itemName;
    this.startingPrice = startingPrice;
    this.currentPrice = currentPrice;
    // Tạo bản sao không thể sửa đổi và sắp xếp theo bidTime (giả sử BidPointDTO có phương thức bidTime())
    List<BidPointDTO> sortedCopy = new ArrayList<>(points != null ? points : Collections.emptyList());
    sortedCopy.sort(Comparator.comparing(BidPointDTO::getBidTime, Comparator.nullsLast(Comparator.naturalOrder())));
    this.points = Collections.unmodifiableList(sortedCopy);
  }

  public int getAuctionId() {
    return auctionId;
  }

  public String getItemName() {
    return itemName;
  }

  public BigDecimal getStartingPrice() {
    return startingPrice;
  }

  public BigDecimal getCurrentPrice() {
    return currentPrice;
  }

  public List<BidPointDTO> getPoints() {
    return points;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AuctionVisualisationDTO that = (AuctionVisualisationDTO) o;
    return auctionId == that.auctionId &&
        Objects.equals(itemName, that.itemName) &&
        Objects.equals(startingPrice, that.startingPrice) &&
        Objects.equals(currentPrice, that.currentPrice) &&
        Objects.equals(points, that.points);
  }

  @Override
  public int hashCode() {
    return Objects.hash(auctionId, itemName, startingPrice, currentPrice, points);
  }

  @Override
  public String toString() {
    return "AuctionVisualisationDTO[" +
        "auctionId=" + auctionId +
        ", itemName=" + itemName +
        ", startingPrice=" + startingPrice +
        ", currentPrice=" + currentPrice +
        ", points=" + points +
        ']';
  }
}
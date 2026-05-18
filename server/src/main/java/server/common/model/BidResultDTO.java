package server.common.model;

import server.common.entity.BidTransaction;

import java.math.BigDecimal;

public class BidResultDTO {
  // ─────────────────────────────────────────────────────────────────────────
  //  Result DTO — trả về toàn bộ thông tin sau một lần bid
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Kết quả đầy đủ sau một lần placeBid.
   *
   * ⚠️  Kết nối Server:
   *   Server serialize object này thành JSON trả về client.
   *   Client cần biết cả manualTransaction (bid thủ công) lẫn autoBidTransaction để hiển thị đúng:
   */

    public final BidTransaction manualTransaction;     // Bid thủ công của người dùng
    public final BidTransaction autoBidTransaction;    // Auto-bid của engine (null nếu không có)
    public final BigDecimal     finalPrice;   // Giá cuối sau tất cả
    public final String         currentLeaderName;  // Username người đang dẫn đầu

    public BidResultDTO(BidTransaction manualTransaction, BidTransaction autoBidTransaction,
              BigDecimal finalPrice, String currentLeaderName) {
      this.manualTransaction   = manualTransaction;
      this.autoBidTransaction  = autoBidTransaction;
      this.finalPrice = finalPrice;
      this.currentLeaderName = currentLeaderName;
    }

    /** True nếu manual bidder bị auto-bid của người khác vượt ngay sau khi bid */
    public boolean wasOutbidByAutoBid() {
      return autoBidTransaction != null
          && !autoBidTransaction.getBidderId().equals(manualTransaction.getBidderId());
    }

  public BidTransaction getManualTransaction()  { return manualTransaction; }
  public BidTransaction getAutoBidTransaction() { return autoBidTransaction; }
  public BigDecimal     getFinalPrice()          { return finalPrice; }
  public String         getCurrentLeaderName()   { return currentLeaderName; }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(String.format("BidResult: manual=%.0f by %s",
          manualTransaction.getAmount(), manualTransaction.getBidderName()));
      if (autoBidTransaction != null) {
        sb.append(String.format(" | auto=%.0f by %s",
            autoBidTransaction.getAmount(), autoBidTransaction.getBidderName()));
      }
      sb.append(String.format(" | final=%.0f leader=%s", finalPrice, currentLeaderName));
      return sb.toString();
    }

}

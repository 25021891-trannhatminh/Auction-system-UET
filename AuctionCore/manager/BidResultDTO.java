package manager;

import model.BidTransaction;

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

    public final BidTransaction manualTx;     // Bid thủ công của người dùng
    public final BidTransaction autoBidTx;    // Auto-bid của engine (null nếu không có)
    public final double         finalPrice;   // Giá cuối sau tất cả
    public final String         finalLeader;  // Username người đang dẫn đầu

    public BidResultDTO(BidTransaction manualTx, BidTransaction autoBidTx,
              double finalPrice, String finalLeader) {
      this.manualTx   = manualTx;
      this.autoBidTx  = autoBidTx;
      this.finalPrice = finalPrice;
      this.finalLeader = finalLeader;
    }

    /** True nếu manual bidder bị auto-bid của người khác vượt ngay sau khi bid */
    public boolean wasOutbidByAutoBid() {
      return autoBidTx != null
          && !autoBidTx.getBidderId().equals(manualTx.getBidderId());
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(String.format("BidResult: manual=%.0f by %s",
          manualTx.getAmount(), manualTx.getBidderName()));
      if (autoBidTx != null) {
        sb.append(String.format(" | auto=%.0f by %s",
            autoBidTx.getAmount(), autoBidTx.getBidderName()));
      }
      sb.append(String.format(" | final=%.0f leader=%s", finalPrice, finalLeader));
      return sb.toString();
    }

}

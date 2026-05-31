package client.controller;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static section copy for the user dashboard.
 *
 * <p>Moving dashboard metadata out of {@link UserDashboardController} keeps the main controller
 * focused on live data, auction rendering, bidding, and create-listing actions.</p>
 */
final class UserDashboardSections {

  private UserDashboardSections() {
  }

  static Map<String, BaseDashboardController.SectionContent> buildSections() {
    Map<String, BaseDashboardController.SectionContent> map = new LinkedHashMap<>();

    map.put("dashboard", page(
        "Dashboard",
        "Track bids, browse live auctions, manage listings, and follow transactions.",
        "User Auction Workspace",
        "A compact workspace for bids, auctions, seller items, and wallet transactions.",
        new String[0],
        new String[0],
        new String[]{"Action needed", "Bid tracking", "Seller follow-up"},
        new String[]{
            "Rows use statuses defined by the core domain enums.",
            "My Bids keeps current price, your bid, status, and next action visible.",
            "Sold items and won auctions move into Transactions after bidding ends."
        },
        new String[]{
            "Auction activity loads from database.",
            "Countdowns use live auction end_time without creating a separate auction status.",
            "Seller follow-up rows are hidden until wired to DB.",
            "Auto-bid rows are hidden until wired to DB."
        }
    ));

    map.put("auctions", page(
        "Auctions",
        "Browse by category, filter live auctions, preview items, and place bids quickly.",
        "Auction Browse",
        "Marketplace-style browsing with product images, core status filters, category filters, " +
            "and paginated auction cards.",
        new String[0],
        new String[0],
        new String[]{"Shop by Category", "Auction cards", "Pagination"},
        new String[]{
            "Category cards work as practical browse shortcuts instead of decorative sections.",
            "Each auction card shows image, category, current bid, bid count, countdown, " +
                "badge, and actions.",
            "Large result sets stay manageable with page 1, page 2, and Next/Previous controls."
        },
        new String[]{
            "Live auction cards come from the auctions table.",
            "Auction filters mirror AuctionStatus and ItemCategory from the core domain.",
            "Reserve price displays only from the stored auction row.",
            "Category cards are generated from live auction categories."
        }
    ));

    map.put("myBids", page(
        "My Bids",
        "Track every bid you placed and act when you are winning, outbid, won, or lost.",
        "Bid Tracking Board",
        "A management table is best here: item thumbnail, current price, your bid, status, " +
            "countdown, and quick action.",
        new String[0],
        new String[0],
        new String[]{"Bid history", "Status badges", "Quick re-bid"},
        new String[]{
            "Rows should compare current price, your latest bid, and closing pressure.",
            "Winning, Outbid, Won, and Lost statuses stay visible as badges.",
            "Outbid rows expose Bid Again without forcing a full detail page first."
        },
        new String[]{
            "Bid history is hidden until user bid queries are added.",
            "Outbid rows are not mocked.",
            "Countdown text uses seconds_left from the server without adding a new status.",
            "Bid filters mirror WINNING, OUTBID, WON, and LOST from the core domain."
        }
    ));

    map.put("autoBids", page(
        "Auto Bids",
        "Manage automated bidding rules, maximum limits, increments, and cancellation controls.",
        "Auto Bid Controls",
        "Auto bids are bidding rules, not just history. Keep max limit, current price, " +
            "increment, core status, and controls visible.",
        new String[0],
        new String[0],
        new String[]{"Rule table", "Core status", "Cancel rule"},
        new String[]{
            "Show each automated rule beside the related auction item.",
            "Statuses stay aligned with ACTIVE, COMPLETED, and CANCELED.",
            "Allow quick viewing and cancellation without inventing extra rule states."
        },
        new String[]{
            "Auto-bid rules are hidden until loaded from DB.",
            "Only ACTIVE, COMPLETED, and CANCELED are exposed as filters.",
            "Ended auto-bid rows are rendered as COMPLETED from DB.",
            "Auto-bid rows stay hidden until loaded from DB."
        }
    ));

    map.put("myItems", page(
        "My Items",
        "Manage seller listings, drafts, active auctions, sold items, and relist actions.",
        "Seller Workspace",
        "Seller management stays practical with item thumbnails, listing status, bids, " +
            "watchers, countdown, winner, and context actions.",
        new String[0],
        new String[0],
        new String[]{"Listing status", "Auction linkage", "Seller actions"},
        new String[]{
            "Draft, Pending Review, Available, In Auction, Sold, and Removed map to ItemStatus.",
            "Each item shows whether it has bids, watchers, or a winner follow-up task.",
            "Actions change by state: Edit, Publish, View Bids, Contact Winner, Mark Shipped, " +
                "or Relist."
        },
        new String[]{
            "Seller item counts load from DB.",
            "Sold item rows are not mocked.",
            "Watcher data is hidden until a real query exists.",
            "Relist rows are hidden until a real query exists."
        }
    ));

    map.put("winners", page(
        "Transactions",
        "Follow wallet deposits plus auctions you won or sold after bidding ends.",
        "Wallet & Auction Transactions",
        "Transactions combines wallet top-ups with bidder payment and seller payout follow-up.",
        new String[0],
        new String[0],
        new String[]{"Wallet deposits", "Won auctions", "Sold auctions"},
        new String[]{
            "Wallet Deposit rows show the amount, timestamp, and wallet transaction reference.",
            "Auction payments track final price, counterparty, payment status, and Pay Now action.",
            "Transaction filters mirror PaymentStatus and WalletTransactionType."
        },
        new String[]{
            "Transaction rows load from payments and wallet_transactions.",
            "PENDING buyer rows call the existing CONFIRM_PAYMENT flow.",
            "Deposit rows call the wallet top-up endpoint and are logged as DEPOSIT.",
            "Payment, refund, and deposit references are shown from wallet transaction logs."
        }
    ));

    return map;
  }

  private static BaseDashboardController.SectionContent page(
      String title,
      String subtitle,
      String surfaceTitle,
      String surfaceDescription,
      String[] statValues,
      String[] statLabels,
      String[] featureTitles,
      String[] featureDescriptions,
      String[] activityLines) {
    return new BaseDashboardController.SectionContent(
        title,
        subtitle,
        surfaceTitle,
        surfaceDescription,
        "",
        "",
        statValues,
        statLabels,
        featureTitles,
        featureDescriptions,
        activityLines,
        new String[0],
        new String[0],
        new String[0]
    );
  }

}

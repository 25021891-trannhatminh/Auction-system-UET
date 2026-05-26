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
        "A compact workspace for outbid alerts, ending-soon auctions, unpaid wins, seller " +
            "tasks, and auto-bid warnings.",
        new String[]{"00", "00", "00", "00"},
        new String[]{"Active Bids", "Winning Now", "Outbid", "Ending Soon"},
        new String[]{"Action needed", "Bid tracking", "Seller follow-up"},
        new String[]{
            "Outbid rows and ending-soon auctions are pushed to the top.",
            "My Bids keeps current price, your bid, status, and next action visible.",
            "Sold items and won auctions move into Transactions after bidding ends."
        },
        new String[]{
            "Auction activity loads from database.",
            "Countdowns use live auction end_time.",
            "Seller follow-up rows are hidden until wired to DB.",
            "Auto-bid rows are hidden until wired to DB."
        }
    ));

    map.put("auctions", page(
        "Auctions",
        "Browse by category, filter live auctions, preview items, and place bids quickly.",
        "Auction Browse",
        "Marketplace-style browsing with product images, category cards, status filters, and " +
            "paginated auction cards.",
        new String[]{"00", "00", "00", "00"},
        new String[]{"Live Auctions", "Ending Soon", "Hot Items", "Watched"},
        new String[]{"Shop by Category", "Auction cards", "Pagination"},
        new String[]{
            "Category cards work as practical browse shortcuts instead of decorative sections.",
            "Each auction card shows image, category, current bid, bid count, countdown, " +
                "badge, and actions.",
            "Large result sets stay manageable with page 1, page 2, and Next/Previous controls."
        },
        new String[]{
            "Live auction cards come from the auctions table.",
            "Auction stats refresh after database responses.",
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
        new String[]{"00", "00", "00", "00"},
        new String[]{"Total Bids", "Winning", "Outbid", "Completed"},
        new String[]{"Bid history", "Status badges", "Quick re-bid"},
        new String[]{
            "Rows should compare current price, your latest bid, and closing pressure.",
            "Winning, Outbid, Won, and Lost statuses stay visible as badges.",
            "Outbid rows expose Bid Again without forcing a full detail page first."
        },
        new String[]{
            "Bid history is hidden until user bid queries are added.",
            "Outbid rows are not mocked.",
            "Ending-soon counts use seconds_left from the server.",
            "Completed bid rows need a real transaction query before rendering."
        }
    ));

    map.put("autoBids", page(
        "Auto Bids",
        "Manage automated bidding rules, maximum limits, increments, and pause or resume controls.",
        "Auto Bid Controls",
        "Auto bids are bidding rules, not just history. Keep max limit, current price, " +
            "increment, warning threshold, and controls visible.",
        new String[]{"00", "00", "00", "00"},
        new String[]{"Active Rules", "Paused", "Near Limit", "Limit Reached"},
        new String[]{"Rule table", "Limit safety", "Pause / resume"},
        new String[]{
            "Show each automated rule beside the related auction item.",
            "Warn when current price approaches the configured max bid.",
            "Allow quick Edit, Pause, Resume, or Delete actions."
        },
        new String[]{
            "Auto-bid rules are hidden until loaded from DB.",
            "Paused auto-bid rows are not mocked.",
            "Ended auto-bid rows are not mocked.",
            "Auto-bid safety data will render only after DB wiring."
        }
    ));

    map.put("myItems", page(
        "My Items",
        "Manage seller listings, drafts, active auctions, sold items, and relist actions.",
        "Seller Workspace",
        "Seller management stays practical with item thumbnails, listing status, bids, " +
            "watchers, countdown, winner, and context actions.",
        new String[]{"00", "00", "00", "00"},
        new String[]{"Items", "Drafts", "Active Sales", "Sold"},
        new String[]{"Listing status", "Auction linkage", "Seller actions"},
        new String[]{
            "Draft, Pending, Active, Sold, and Unsold items are separated by filter chips.",
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
        "Follow auctions you won and auctions you sold after bidding ends.",
        "Post-Auction Transactions",
        "Transactions split bidder and seller follow-up: Won Auctions for purchases, Sold " +
            "Auctions for winners of your listings.",
        new String[]{"00", "00", "00", "00"},
        new String[]{"Won Auctions", "Payment Due", "Sold Auctions", "To Ship"},
        new String[]{"Won auctions", "Sold auctions", "Fulfilment"},
        new String[]{
            "Won Auctions track final price, seller, payment, and pickup or shipping status.",
            "Sold Auctions track winner, winning bid, payment status, and seller fulfilment.",
            "Actions stay clear: Pay Now, Contact Seller, Contact Winner, Mark Shipped, or " +
                "Leave Review."
        },
        new String[]{
            "Transaction rows load from payments joined with auctions, items, and accounts.",
            "Payment Due rows call the existing CONFIRM_PAYMENT flow.",
            "Completed, failed, and refunded states come from the payments table.",
            "Wallet transaction references are shown when the payment flow writes them."
        }
    ));

    map.put("settings", page(
        "Settings",
        "Manage account details, notifications, bidding preferences, seller profile, payments, " +
            "and app preferences.",
        "Settings Workspace",
        "Settings should be grouped forms, not auction tables: account, notifications, " +
            "bidding, seller profile, payment, privacy, and preferences.",
        new String[]{"07", "08", "04", "03"},
        new String[]{"Groups", "Alerts", "Bid Rules", "Preferences"},
        new String[]{"Account", "Notifications", "Bidding preferences"},
        new String[]{
            "Profile, email, phone number, avatar, and password change belong in Account.",
            "Outbid, ending soon, won auction, payment, new bid, and auto-bid limit alerts " +
                "belong in Notifications.",
            "Default increment, quick bid confirmation, auto-bid threshold, currency, " +
                "timezone, and default view belong in Preferences."
        },
        new String[]{
            "Outbid and ending-soon alerts are enabled.",
            "Confirm before placing bid is enabled.",
            "Default auction view is Grid/List hybrid.",
            "Currency is set to VND."
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

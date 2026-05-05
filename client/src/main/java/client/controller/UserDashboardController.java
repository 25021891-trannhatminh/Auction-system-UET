package client.controller;

import java.util.LinkedHashMap;
import java.util.Map;

public class UserDashboardController extends BaseDashboardController {

    private final Map<String, SectionContent> sections = buildSections();

    @Override
    protected Map<String, SectionContent> createSections() {
        return sections;
    }

    @Override
    protected String getDefaultSectionKey() {
        return "dashboard";
    }

    @Override
    protected String getRoleTitle() {
        return "BIDDER / SELLER";
    }

    private Map<String, SectionContent> buildSections() {
        Map<String, SectionContent> map = new LinkedHashMap<>();

        map.put("dashboard", new SectionContent(
                "Dashboard",
                "Track ongoing auctions, monitor your activity, and jump back into the flow instantly.",
                "Command Center Overview",
                "This area is ready to host your real dashboard widgets later. For now it shows a polished placeholder structure based on the approved visual direction.",
                "Today at a Glance",
                "A compact side panel for quick insights, alerts, or countdown widgets once the real data is wired in.",
                new String[]{"12", "04", "03", "08"},
                new String[]{"Active Auctions", "Winning Now", "Auto Bids", "Saved Items"},
                new String[]{"Browse auctions", "Monitor bids", "Review selling items"},
                new String[]{
                        "Main list area prepared for auction cards.",
                        "Placeholder block ready for bid history or live activity.",
                        "Use this block later for seller-side shortcuts or recent drafts."
                },
                new String[]{
                        "Newest auctions appear here",
                        "Expiring lots can be listed here",
                        "Outbid alerts can be surfaced here",
                        "Recent winners can be summarized here"
                },
                new String[]{"Live auctions", "Fast bidding", "Seller mode"},
                new String[]{"Focus", "Priority", "Mode"},
                new String[]{"Ending soon", "Keep bidding", "Hybrid"}
        ));

        map.put("auctions", new SectionContent(
                "Auctions",
                "Browse, search, and filter all auction sessions from a single hub.",
                "Auction Listing Placeholder",
                "Reserve this large content area for filters, auction cards, and detail modals. The overall layout is already in place.",
                "Quick Filters",
                "Perfect spot for status filters like running, upcoming, and finished, together with category shortcuts.",
                new String[]{"25", "09", "06", "02"},
                new String[]{"Running", "Ending Soon", "Followed", "Recommended"},
                new String[]{"Search and filter", "Auction card grid", "Auction details panel"},
                new String[]{
                        "Top utility row can host keyword search and categories.",
                        "Middle section fits repeated auction result cards nicely.",
                        "Right side can later preview selected auction detail."
                },
                new String[]{
                        "Result list placeholder",
                        "Sorting controls placeholder",
                        "Mini statistics placeholder",
                        "CTA buttons placeholder"
                },
                new String[]{"Running", "Upcoming", "Top value"},
                new String[]{"Default view", "Best sort", "Attention"},
                new String[]{"Card list", "Ending soon", "High demand"}
        ));

        map.put("myBids", new SectionContent(
                "My Bids",
                "Keep track of all bids you placed and whether you are currently leading or outbid.",
                "Bid Tracking Board",
                "Later you can drop in a table showing auction name, your latest bid, current price, and status.",
                "Bid Status Summary",
                "Use this area for a compact summary of winning, outbid, lost, or completed sessions.",
                new String[]{"18", "07", "05", "06"},
                new String[]{"Total Bids", "Winning", "Outbid", "Completed"},
                new String[]{"Bid history table", "Current status chips", "Quick re-bid action"},
                new String[]{
                        "Ideal for a wide data table layout.",
                        "Perfect place for row status or colored flags.",
                        "Can later connect directly to bid detail or place-bid dialog."
                },
                new String[]{
                        "Recent bid log placeholder",
                        "Outbid notifications placeholder",
                        "Winning sessions placeholder",
                        "Closed auctions placeholder"
                },
                new String[]{"Winning", "Outbid", "History"},
                new String[]{"Main signal", "Watchlist", "Refresh"},
                new String[]{"Leading in 7", "Need action", "Realtime"}
        ));

        map.put("autoBids", new SectionContent(
                "Auto Bids",
                "Manage your automation strategy and maximum bid limits from one place.",
                "Auto Bid Manager",
                "The content canvas can later contain a table of max bid, increment, status, and control actions.",
                "Automation Snapshot",
                "This side panel is ready for summaries such as active configurations, triggers, and recent executions.",
                new String[]{"03", "01", "02", "11"},
                new String[]{"Active Rules", "Paused", "Triggered Today", "Saved Limits"},
                new String[]{"Rule list", "Edit max bid", "Pause / resume"},
                new String[]{
                        "Large surface area designed for a clean management table.",
                        "Card placeholder fits form or edit drawer behaviour.",
                        "Supplemental card works for rule explanations or guide text."
                },
                new String[]{
                        "Auto bid items placeholder",
                        "Triggered events placeholder",
                        "Paused rules placeholder",
                        "Create new rule placeholder"
                },
                new String[]{"Automation", "Safe limits", "Quick edit"},
                new String[]{"Health", "Usage", "Control"},
                new String[]{"Stable", "3 active", "Manual"}
        ));

        map.put("myItems", new SectionContent(
                "My Items",
                "View the items you are selling, draft new listings, and launch auctions later.",
                "Seller Workspace",
                "This placeholder section can later host item cards, create-item forms, and your seller-side action buttons.",
                "Seller Snapshot",
                "A natural place for item counts, listing drafts, and auction publishing shortcuts.",
                new String[]{"14", "05", "03", "06"},
                new String[]{"Items", "Draft Auctions", "Running Sales", "Archived"},
                new String[]{"Item grid", "Create listing", "Launch auction"},
                new String[]{
                        "Use the main card for item list or tile view.",
                        "Secondary block suits an add-item form or quick editor.",
                        "Third block can show selling tips or analytics later."
                },
                new String[]{
                        "My items placeholder",
                        "Draft items placeholder",
                        "Published auctions placeholder",
                        "Archived items placeholder"
                },
                new String[]{"Listing", "Drafts", "Seller tools"},
                new String[]{"Inventory", "Workflow", "Status"},
                new String[]{"Healthy", "In progress", "Ready"}
        ));

        map.put("winners", new SectionContent(
                "Winners",
                "Review auctions you won and keep the follow-up flow visible in a dedicated space.",
                "Winning Results Board",
                "Later you can place result cards, payment status, and next-step actions right inside this board.",
                "Fulfilment Notes",
                "Use the side panel for payment reminders, contact actions, or shipping milestones.",
                new String[]{"06", "02", "03", "01"},
                new String[]{"Won Auctions", "Pending Payment", "Ready to Close", "Flagged"},
                new String[]{"Won auction cards", "Payment actions", "Delivery follow-up"},
                new String[]{
                        "Main content block supports a post-auction result grid.",
                        "Second block fits invoice or payment prompt cards.",
                        "Third block can be used for seller/buyer communication status."
                },
                new String[]{
                        "Won item list placeholder",
                        "Payment reminder placeholder",
                        "Collection details placeholder",
                        "Completed results placeholder"
                },
                new String[]{"Results", "Payments", "Delivery"},
                new String[]{"Next step", "Urgent", "Progress"},
                new String[]{"Pay 2 items", "1 reminder", "On track"}
        ));

        map.put("settings", new SectionContent(
                "Settings",
                "Manage your profile, preferences, and display behaviour for the dashboard later.",
                "Preferences Shell",
                "This layout block can later host account settings, password changes, and notification toggles.",
                "Personalization",
                "Side space prepared for theme controls, saved filters, or help content.",
                new String[]{"05", "03", "02", "01"},
                new String[]{"Profile Blocks", "Notification Rules", "Saved Filters", "Theme Presets"},
                new String[]{"Account settings", "Notification panel", "Display options"},
                new String[]{
                        "Main placeholder reserved for form sections.",
                        "Useful for toggle groups or permission settings.",
                        "Can later host support resources and FAQs."
                },
                new String[]{
                        "Profile form placeholder",
                        "Password section placeholder",
                        "Notification settings placeholder",
                        "Preferences summary placeholder"
                },
                new String[]{"Profile", "Alerts", "Theme"},
                new String[]{"Editable", "Sync", "Palette"},
                new String[]{"Ready", "Enabled", "Login tone"}
        ));

        return map;
    }
}
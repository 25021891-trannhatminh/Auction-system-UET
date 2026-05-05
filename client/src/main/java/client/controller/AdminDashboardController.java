package client.controller;

import java.util.LinkedHashMap;
import java.util.Map;

public class AdminDashboardController extends BaseDashboardController {

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
        return "SYSTEM ADMIN";
    }

    private Map<String, SectionContent> buildSections() {
        Map<String, SectionContent> map = new LinkedHashMap<>();

        map.put("dashboard", new SectionContent(
                "Admin Dashboard",
                "Monitor the platform health, manage operations, and move quickly across the system.",
                "Operations Overview",
                "This large surface is ready for core admin widgets such as system summaries, moderation queues, and live auction health.",
                "Control Summary",
                "The right rail is a good fit for quick insights, alerts, and moderation counters.",
                new String[]{"248", "31", "142", "912"},
                new String[]{"Users", "Active Auctions", "Items", "Total Bids"},
                new String[]{"System health", "Operational queue", "Recent actions"},
                new String[]{
                        "Primary panel reserved for top-level admin insights.",
                        "Secondary panel can later show flagged records or approvals.",
                        "Third panel works well for audit events or admin tools."
                },
                new String[]{
                        "User growth placeholder",
                        "Auction health placeholder",
                        "Moderation queue placeholder",
                        "Recent actions placeholder"
                },
                new String[]{"Overview", "Queue", "Moderation"},
                new String[]{"System", "Urgent", "Load"},
                new String[]{"Healthy", "3 items", "Normal"}
        ));

        map.put("users", new SectionContent(
                "Users",
                "Manage user accounts, statuses, and role-specific operations from one screen.",
                "User Management Table",
                "This section is prepared for an admin table with username, role, account status, and action buttons.",
                "Account Health",
                "Use the side panel for summaries like active, suspended, and banned accounts.",
                new String[]{"248", "229", "11", "08"},
                new String[]{"Total Users", "Active", "Suspended", "Banned"},
                new String[]{"User table", "Status controls", "Role overview"},
                new String[]{
                        "Wide table placeholder ready for management use.",
                        "Second content block is suitable for batch actions or filters.",
                        "Third block can explain statuses or role scopes."
                },
                new String[]{
                        "All users placeholder",
                        "Suspended accounts placeholder",
                        "Role distribution placeholder",
                        "Batch action tools placeholder"
                },
                new String[]{"Accounts", "Roles", "Review"},
                new String[]{"Priority", "Next step", "Trend"},
                new String[]{"Suspended 11", "Audit queue", "Stable"}
        ));

        map.put("auctions", new SectionContent(
                "Auctions",
                "Supervise all auction sessions, watch statuses, and intervene when necessary.",
                "Auction Administration",
                "This canvas fits a system-wide auction table with status, seller, item, timing, and moderation actions.",
                "Live Auction Pulse",
                "The side panel can later show active counts, abnormal sessions, or ending-soon alerts.",
                new String[]{"31", "18", "09", "04"},
                new String[]{"Active", "Scheduled", "Finished Today", "Flagged"},
                new String[]{"Auction list", "Status controls", "Session detail"},
                new String[]{
                        "Primary block ready for operations table or cards.",
                        "Secondary block can host detail preview or moderation reasons.",
                        "Third block can store helper guides or audit log summary."
                },
                new String[]{
                        "Live auctions placeholder",
                        "Flagged sessions placeholder",
                        "Ending soon placeholder",
                        "Auction status filters placeholder"
                },
                new String[]{"Activity", "Flags", "Timing"},
                new String[]{"Live feed", "Action", "Window"},
                new String[]{"Busy", "Review now", "Next 2h"}
        ));

        map.put("items", new SectionContent(
                "Items",
                "Inspect listed items, categories, and seller-linked assets before or during auction use.",
                "Item Catalog Control",
                "This section is ready for item list views, category filters, and item moderation actions.",
                "Catalog Snapshot",
                "Use the side panel for category mix, pending reviews, or content quality notes.",
                new String[]{"142", "06", "11", "03"},
                new String[]{"Items", "Pending Review", "Categories", "Flagged"},
                new String[]{"Catalog grid", "Item detail", "Moderation tools"},
                new String[]{
                        "Main area accommodates a table or visual card grid.",
                        "Secondary surface suits detail preview and seller info.",
                        "Third block can later display validation rules or warnings."
                },
                new String[]{
                        "Catalog placeholder",
                        "Item review placeholder",
                        "Category summary placeholder",
                        "Flagged item placeholder"
                },
                new String[]{"Catalog", "Review", "Quality"},
                new String[]{"Coverage", "Need action", "Signal"},
                new String[]{"Wide", "6 pending", "Good"}
        ));

        map.put("reports", new SectionContent(
                "Reports",
                "Reserve this section for platform analytics, KPIs, and downloadable summaries.",
                "Reporting Workspace",
                "The layout is ready for charts, statistic cards, and export controls once the reporting logic is implemented.",
                "Insight Feed",
                "Side panel can display date range quick picks, trend notes, and notable events.",
                new String[]{"7d", "92%", "+14%", "03"},
                new String[]{"Range", "Completion", "Growth", "Alerts"},
                new String[]{"KPI cards", "Chart zone", "Export tools"},
                new String[]{
                        "Main content is intentionally spacious for visual analytics.",
                        "Secondary block can be used for trend breakdown charts.",
                        "Third block fits export or comparison widgets."
                },
                new String[]{
                        "Revenue chart placeholder",
                        "Status chart placeholder",
                        "Top sellers placeholder",
                        "Export summary placeholder"
                },
                new String[]{"KPIs", "Charts", "Exports"},
                new String[]{"Best fit", "Priority", "Signal"},
                new String[]{"Analytics", "Weekly", "Upward"}
        ));

        map.put("settings", new SectionContent(
                "Settings",
                "Manage admin preferences, access defaults, and future system configuration blocks.",
                "Admin Settings Shell",
                "This is a prepared placeholder for configuration forms, feature switches, and profile controls.",
                "Configuration Notes",
                "Great location for environment notes, warning messages, or access summaries.",
                new String[]{"04", "02", "03", "01"},
                new String[]{"Config Groups", "Access Levels", "Saved Views", "Warnings"},
                new String[]{"Profile config", "Permission blocks", "System preferences"},
                new String[]{
                        "Main area ready for setting forms and save actions.",
                        "Secondary block can show access tiers or role notes.",
                        "Third block can host theme or layout presets later."
                },
                new String[]{
                        "Profile config placeholder",
                        "Access control placeholder",
                        "System preference placeholder",
                        "Environment note placeholder"
                },
                new String[]{"Config", "Access", "Theme"},
                new String[]{"Editable", "Level", "Palette"},
                new String[]{"Ready", "Admin", "Login tone"}
        ));

        return map;
    }
}
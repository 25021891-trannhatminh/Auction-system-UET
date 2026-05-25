package client.controller;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static section copy for the administrator dashboard.
 *
 * <p>Keeping this text outside {@link AdminDashboardController} makes the controller focus on
 * data loading, rendering, and user actions instead of long page metadata blocks.</p>
 */
final class AdminDashboardSections {

    private AdminDashboardSections() {
    }

    static Map<String, BaseDashboardController.SectionContent> buildSections() {
        Map<String, BaseDashboardController.SectionContent> map = new LinkedHashMap<>();

        map.put("dashboard", page(
            "Admin Dashboard",
            "Monitor live auctions, pending item reviews, user accounts, and platform activity.",
            "Control Center",
            "A practical admin workspace for auction health, review queues, " +
                "and recent operational events.",
            new String[]{"0", "0", "0", "0"},
            new String[]{"Users", "Running Auctions", "Listed Items", "Total Bids"},
            new String[]{"Live auction health", "Moderation queue", "Recent activity"},
            new String[]{
                "Track RUNNING auctions, current prices, bid counts, and ending-soon sessions.",
                "Review AVAILABLE items, flagged auctions, suspended accounts, " +
                    "and payment follow-ups.",
                "Surface audit events, bid events, auction status changes, and item updates."
            },
            new String[]{
                "Admin tables now prefer shared database data when the server is available.",
                "Item Review and Auction Sessions stay linked by ITEM/AUC IDs.",
                "User Accounts are requested from the shared cloud database.",
                "Fallback demo rows stay available when the server is offline."
            }
        ));

        map.put("users", page(
            "Users",
            "Manage user accounts, role scopes, and status actions from one screen.",
            "User Management",
            "Inspect accounts from the shared cloud database with ACTIVE, SUSPENDED, and BANNED " +
                "states.",
            new String[]{"0", "0", "0", "0"},
            new String[]{"Total Users", "Active", "Suspended", "Banned"},
            new String[]{"Account table", "Role controls", "Risk review"},
            new String[]{
                "Search users by username, email, role, or account status.",
                "Rows are populated from the server database instead of static fake accounts.",
                "Keep SUSPENDED and BANNED accounts visible for audit and restore workflows."
            },
            new String[]{
                "Filter by ACTIVE, SUSPENDED, or BANNED before batch review.",
                "Open a user row to inspect wallet, auctions, bids, and account history.",
                "Use Suspend, Restore, or Ban only after checking recent activity.",
                "Role changes stay inside the user detail flow, not as a quick row action."
            }
        ));

        map.put("auctions", page(
            "Auctions",
            "Supervise auction sessions, timing, bid activity, and intervention actions.",
            "Auction Monitoring",
            "The list is for quick monitoring. Use View to open a focused auction detail screen " +
                "with edit, payment, bids, and logs.",
            new String[]{"0", "0", "0", "0"},
            new String[]{"Auctions", "Running", "Open", "Finished/Paid"},
            new String[]{"Auction table", "Linked item", "Bid history"},
            new String[]{
                "Auction rows are linked to item rows by ITEM ID.",
                "Every auction row keeps item ID and seller visible so admin can trace ownership.",
                "Bid count and current price are surfaced for abnormal bidding checks."
            },
            new String[]{
                "RUNNING auctions show current price, bid count, and end-time pressure.",
                "OPEN auctions are scheduled or ready before the bidding window starts.",
                "FINISHED and PAID auctions need winner/payment follow-up.",
                "CANCELED auctions remain visible for audit and dispute review."
            }
        ));

        map.put("items", page(
            "Item Review",
            "Review listed items, item statuses, categories, seller links, and auction readiness.",
            "Item Review & Catalog",
            "Items are seller listings. Auctions are bidding sessions created from approved items.",
            new String[]{"0", "0", "0", "0"},
            new String[]{"Items", "Available", "In Auction", "Sold/Removed"},
            new String[]{"Item table", "Auction linkage", "Catalog quality"},
            new String[]{
                "Manage DRAFT, AVAILABLE, IN_AUCTION, SOLD, and REMOVED item states.",
                "Approved AVAILABLE items can be converted into OPEN auction sessions.",
                "Flagged or REMOVED items stay visible for moderation notes and seller review."
            },
            new String[]{
                "AVAILABLE items are ready for Create Auction after admin inspection.",
                "IN_AUCTION items should link back to their active auction session.",
                "SOLD items should match a FINISHED or PAID auction record.",
                "REMOVED items should keep a reason for audit history."
            }
        ));

        map.put("reports", page(
            "Reports",
            "Track auction KPIs, bidding activity, growth signals, and export-ready summaries.",
            "Reporting Workspace",
            "Reports focus on analytics: date range, KPIs, trends, top records, and exports.",
            new String[]{"7d", "912", "92%", "+14%"},
            new String[]{"Range", "Bid Volume", "Completion", "Growth"},
            new String[]{"KPI review", "Trend breakdown", "Export tools"},
            new String[]{
                "Use weekly and monthly ranges to compare auction volume and closing health.",
                "Summarize bids, completed auctions, revenue proxy, and flagged cases.",
                "Prepare CSV/PDF export hooks once backend report endpoints are available."
            },
            new String[]{
                "Bid volume increased by 14% compared with the previous 7-day range.",
                "92% of ending auctions reached FINISHED, PAID, or payment follow-up states.",
                "Top sellers and top categories should sit below KPI cards for quick review.",
                "Exports should use the selected date range and current filters."
            }
        ));

        map.put("settings", page(
            "Settings",
            "Configure auction rules, access controls, item rules, moderation, " +
                "and admin preferences.",
            "System Settings",
            "Settings are configuration groups with clear Edit/Save workflows instead of " +
                "dashboard-style row menus.",
            new String[]{"05", "05", "03", "04"},
            new String[]{"Auction States", "Item States", "User Roles", "Bid States"},
            new String[]{"Auction rules", "Access control", "Moderation rules"},
            new String[]{
                "Minimum bid increment, reserve price, anti-sniping window, " +
                    "and auto-close behaviour.",
                "ADMIN and USER scopes should be explicit and easy to audit.",
                "Flag suspicious bidding, removed items, and suspended accounts for review."
            },
            new String[]{
                "Auction status flow: OPEN -> RUNNING -> FINISHED -> PAID or CANCELED.",
                "Item status flow: DRAFT -> AVAILABLE -> IN_AUCTION -> SOLD or REMOVED.",
                "User statuses remain ACTIVE, SUSPENDED, and BANNED for account control.",
                "Bid statuses remain WINNING, OUTBID, WON, and LOST."
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
            "",
            surfaceTitle,
            "",
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

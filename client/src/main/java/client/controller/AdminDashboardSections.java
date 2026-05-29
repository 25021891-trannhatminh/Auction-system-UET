package client.controller;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static section copy for the administrator operation console.
 */
final class AdminDashboardSections {

    private AdminDashboardSections() {
    }

    static Map<String, BaseDashboardController.SectionContent> buildSections() {
        Map<String, BaseDashboardController.SectionContent> map = new LinkedHashMap<>();

        map.put("dashboard", page(
            "Admin Dashboard",
            "Monitor the queues that really need admin action.",
            "Operation Console",
            new String[]{"0", "0", "0", "0"},
            new String[]{"Pending Review", "Running Auctions", "Payment Due", "Risk Accounts"},
            new String[]{"Item review", "Auction supervision", "Account moderation"},
            new String[]{
                "Approve or reject seller listings before they can become auction rooms.",
                "Watch OPEN/RUNNING auctions and force close only when intervention is needed.",
                "Ban or restore user accounts without changing wallet/payment logic from admin."
            },
            new String[]{
                "Pending Review shows item rows waiting for admin approval.",
                "Running Auctions shows sessions currently receiving bids.",
                "Payment Due follows FINISHED auctions without adding manual payment controls.",
                "Risk Accounts keeps SUSPENDED/BANNED users visible for audit."
            }
        ));

        map.put("items", page(
            "Item Review",
            "Approve listings, reject invalid items, and create auctions from approved items.",
            "Item Review",
            new String[]{"0", "0", "0", "0"},
            new String[]{"Items", "Pending", "Available", "In Auction"},
            new String[]{"Review queue", "Auction readiness", "Seller trace"},
            new String[]{
                "PENDING_REVIEW items can be approved or rejected.",
                "AVAILABLE items can be converted into OPEN auction rooms.",
                "IN_AUCTION and SOLD items stay linked to their auction detail."
            },
            new String[]{
                "Review image, seller, category, attributes, and starting price before approval.",
                "Create Auction uses the real item_id and seller_id returned by the server.",
                "Removed or sold items remain visible for audit instead of extra admin tools."
            }
        ));

        map.put("auctions", page(
            "Auctions",
            "Supervise auction sessions and use force close only for exceptional cases.",
            "Auction Monitoring",
            new String[]{"0", "0", "0", "0"},
            new String[]{"Auctions", "Running", "Open", "Finished/Paid"},
            new String[]{"Session table", "Linked item", "Safe intervention"},
            new String[]{
                "Rows show auction, seller, current price, bid count, status, and end time.",
                "OPEN/RUNNING auctions can be force closed through the existing backend command.",
                "FINISHED/PAID auctions are view-only so payment flow stays with user/backend logic."
            },
            new String[]{
                "Use View for a compact auction detail instead of a full admin editor.",
                "Force Close maps to ADMIN_FORCE_CLOSE with a simple audit reason.",
                "No manual bid, price, wallet, or payment editing is exposed here."
            }
        ));

        map.put("users", page(
            "Users",
            "Moderate user accounts with only the status actions backed by server logic.",
            "User Management",
            new String[]{"0", "0", "0", "0"},
            new String[]{"Total Users", "Active", "Suspended", "Banned"},
            new String[]{"Account table", "Status control", "Activity signal"},
            new String[]{
                "Rows are loaded from the accounts table through ADMIN_LIST_USERS.",
                "ACTIVE users can be banned; SUSPENDED/BANNED users can be restored.",
                "Item, running auction, and bid counts help moderation without extra screens."
            },
            new String[]{
                "Admin accounts are view-only from this screen.",
                "Ban maps to ADMIN_BAN_USER.",
                "Restore maps to ADMIN_UNBAN_USER.",
                "Wallet/payment edits are intentionally not part of admin-home."
            }
        ));

        return map;
    }

    private static BaseDashboardController.SectionContent page(
        String title,
        String subtitle,
        String surfaceTitle,
        String[] statValues,
        String[] statLabels,
        String[] featureTitles,
        String[] featureDescriptions,
        String[] activityLines) {
        return new BaseDashboardController.SectionContent(
            title,
            subtitle,
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

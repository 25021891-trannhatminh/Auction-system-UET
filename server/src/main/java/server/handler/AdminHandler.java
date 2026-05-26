package server.handler;

import server.common.ProtocolConstants;
import server.common.entity.Auction;
import server.common.entity.Item;
import server.common.enums.ItemStatus;
import server.network.ClientManager;
import server.repository.ItemDAO;
import server.service.AdminService;
import server.service.AuctionService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

/**
 * Xử lý lệnh ADMIN.
 */
public class AdminHandler {

    private final AdminService adminService;
    private final AuctionService auctionService;
    private final ItemDAO itemDAO = new ItemDAO(); // Di dời từ ClientHandler sang
    private static final DateTimeFormatter AUCTION_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public AdminHandler(AuctionService auctionService) {
        this.auctionService = auctionService;

        this.adminService = new AdminService(auctionService);
    }

    /**
     * Xử lý lệnh ADMIN_BAN_USER.
     * Format: ADMIN_BAN_USER targetUserId reason
     */
    public String handleBanUser(String[] request, int adminUserId) {
        if (adminUserId <= 0) {
            return ResponseBuilder.adminBanFail(ProtocolConstants.FAIL_NOT_LOGGED_IN);
        }

        if (request.length < 3) {
            return ResponseBuilder.adminBanFail(ProtocolConstants.FAIL_INVALID_FORMAT);
        }

        try {
            int targetUserId = Integer.parseInt(request[1]);
            String reason = String.join(" ", Arrays.copyOfRange(request, 2, request.length));

            boolean success = adminService.banUser(adminUserId, targetUserId, reason);

            if (success) {
                return ResponseBuilder.adminBanSuccess(targetUserId);
            }
            return ResponseBuilder.adminBanFail("USER_NOT_FOUND");

        } catch (NumberFormatException e) {
            return ResponseBuilder.adminBanFail(ProtocolConstants.FAIL_INVALID_FORMAT);
        }
    }

    /**
     * Xử lý lệnh ADMIN_UNBAN_USER.
     */
    public String handleUnbanUser(String[] request, int adminUserId) {
        if (request.length > 1) {
            try {
                int targetUserId = Integer.parseInt(request[1]);
                boolean unbanUserSuccess = adminService.unbanUser(adminUserId, targetUserId);
                return unbanUserSuccess ? "ADMIN_UNBAN_SUCCESS" : "ADMIN_UNBAN_FAIL";
            } catch (NumberFormatException e) {
                return "ADMIN_UNBAN_FAIL INVALID_FORMAT";
            }
        } else {
            return "ADMIN_UNBAN_FAIL INVALID_FORMAT";
        }
    }

    /**
     * Xử lý lệnh ADMIN_FORCE_CLOSE.
     */
    public String handleForceClose(String[] request, int adminUserId) {
        if (request.length > 2) {
            try {
                int auctionId = Integer.parseInt(request[1]);
                String reason = String.join(" ", Arrays.copyOfRange(request, 2, request.length));
                boolean forceCloseSuccess = adminService.forceCloseAuction(adminUserId, auctionId, reason);
                return forceCloseSuccess ? "ADMIN_CLOSE_SUCCESS" : "ADMIN_CLOSE_FAIL";
            } catch (NumberFormatException e) {
                return "ADMIN_CLOSE_FAIL INVALID_FORMAT";
            }
        }
        return "ADMIN_CLOSE_FAIL INVALID_FORMAT";
    }

    public String handleAdminCreateAuction(String payload,int userId) {
        List<String> fields = splitPayload(payload);
        if (fields.size() < 8) {
            return "ADMIN_CREATE_AUCTION_FAIL " + encodeFields("INVALID_PAYLOAD");
        }

        int itemId = parseIntOrDefault(safeField(fields, 0), 0);
        int sellerId = parseIntOrDefault(safeField(fields, 1), 0);
        LocalDateTime startTime = parseAuctionTime(safeField(fields, 2));
        LocalDateTime endTime = parseAuctionTime(safeField(fields, 3));
        BigDecimal minimumBidIncrement = parseBigDecimal(safeField(fields, 4));
        BigDecimal reservePrice = parseNullableBigDecimal(safeField(fields, 5));
        int snipeWindowSeconds = parseIntOrDefault(safeField(fields, 6), -1);
        int snipeExtensionSeconds = parseIntOrDefault(safeField(fields, 7), -1);

        String validationError = validateCreateAuctionRequest(
                itemId, sellerId, startTime, endTime, minimumBidIncrement, reservePrice, snipeWindowSeconds, snipeExtensionSeconds
        );
        if (!validationError.isBlank()) {
            return "ADMIN_CREATE_AUCTION_FAIL " + encodeFields(validationError);
        }

        Item item = itemDAO.getById(itemId);
        if (item == null) return "ADMIN_CREATE_AUCTION_FAIL " + encodeFields("ITEM_NOT_FOUND");
        if (item.getSellerId() != sellerId) return "ADMIN_CREATE_AUCTION_FAIL " + encodeFields("SELLER_MISMATCH");
        if (item.getStatus() != ItemStatus.AVAILABLE) return "ADMIN_CREATE_AUCTION_FAIL " + encodeFields("ITEM_NOT_AVAILABLE");

        Auction auction = auctionService.createAuction(item, sellerId, startTime, endTime, minimumBidIncrement, reservePrice, snipeWindowSeconds, snipeExtensionSeconds);
        if (auction == null) {
            return "ADMIN_CREATE_AUCTION_FAIL " + encodeFields("SAVE_ERROR");
        }

        // Thông báo đồng bộ trạng thái danh sách realtime
        ClientManager.broadcast("ADMIN_ITEMS_DIRTY");
        ClientManager.broadcast("USER_AUCTIONS_DIRTY");

        return "ADMIN_CREATE_AUCTION_SUCCESS " + encodeFields(auction.getId(), itemId);
    }

    private String validateCreateAuctionRequest(int itemId, int sellerId, LocalDateTime startTime, LocalDateTime endTime,
                                                BigDecimal minimumBidIncrement, BigDecimal reservePrice, int snipeWindowSeconds, int snipeExtensionSeconds) {
        if (itemId <= 0 || sellerId <= 0) return "item_id hoặc seller_id không hợp lệ";
        if (startTime == null || endTime == null) return "start_time/end_time phải đúng format yyyy-MM-dd HH:mm:ss";
        if (!endTime.isAfter(startTime)) return "end_time phải sau start_time";
        if (minimumBidIncrement == null || minimumBidIncrement.compareTo(BigDecimal.ZERO) < 0) return "min_bid_increment phải là số không âm";
        if (reservePrice != null && reservePrice.compareTo(BigDecimal.ZERO) < 0) return "reserve_price phải để trống hoặc là số không âm";
        if (snipeWindowSeconds < 0 || snipeExtensionSeconds < 0) return "snipe_window_seconds và snipe_extension_seconds phải không âm";
        return "";
    }

    private LocalDateTime parseAuctionTime(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDateTime.parse(value.trim(), AUCTION_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private BigDecimal parseNullableBigDecimal(String value) {
        return (value == null || value.isBlank()) ? null : parseBigDecimal(value);
    }

    private BigDecimal parseBigDecimal(String value) {
        try {
            return value == null || value.isBlank() ? null : new BigDecimal(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int parseIntOrDefault(String value, int fallbackValue) {
        try {
            return value == null || value.isBlank() ? fallbackValue : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallbackValue;
        }
    }

    private List<String> splitPayload(String payload) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < payload.length(); i++) {
            char character = payload.charAt(i);
            if (escaped) {
                switch (character) {
                    case 'p' -> current.append('|');
                    case 'n' -> current.append('\n');
                    case 'r' -> current.append('\r');
                    case '\\' -> current.append('\\');
                    default -> current.append(character);
                }
                escaped = false;
                continue;
            }
            if (character == '\\') {
                escaped = true;
                continue;
            }
            if (character == '|') {
                fields.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(character);
        }
        if (escaped) current.append('\\');
        fields.add(current.toString());
        return fields;
    }

    private String safeField(List<String> fields, int index) {
        return index >= 0 && index < fields.size() ? fields.get(index) : "";
    }

    private String encodeFields(Object... values) {
        List<String> encoded = new ArrayList<>();
        for (Object value : values) {
            if (value == null) {
                encoded.add("");
            } else {
                encoded.add(String.valueOf(value).replace("\\", "\\\\").replace("|", "\\p"));
            }
        }
        return String.join("|", encoded);
    }
}
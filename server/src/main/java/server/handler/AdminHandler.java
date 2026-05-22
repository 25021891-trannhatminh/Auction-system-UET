package server.handler;

import server.common.ProtocolConstants;
import server.service.AdminService;
import server.service.AuctionService;
import java.util.Arrays;

/**
 * Xử lý lệnh ADMIN.
 */
public class AdminHandler {
    
    private final AdminService adminService;
    
    public AdminHandler(AuctionService auctionService) {
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
            return ResponseBuilder.adminBanFail("INVALID_USER_ID");
        }
    }
}
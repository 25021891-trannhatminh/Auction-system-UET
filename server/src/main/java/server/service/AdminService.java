package server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.common.entity.*;
import server.common.entity.manager.AuctionManager;
import server.common.enums.AdminPermission;
import server.common.enums.UserStatus;
import server.common.model.AuctionDTO;
import server.repository.AccountDAO;
import server.repository.AuctionDAO;
import server.repository.ItemDAO;

import java.util.List;

public class AdminService {
  private static final Logger logger = LoggerFactory.getLogger(AdminService.class);

  private final AccountDAO accountDAO = new AccountDAO();
  private final ItemDAO itemDAO = new ItemDAO();
  private final AuctionDAO auctionDAO = new AuctionDAO();
  private final AuctionManager auctionManager = AuctionManager.getInstance();
  private final AuctionService auctionService; // inject để notify

  public AdminService(AuctionService auctionService) {
    this.auctionService = auctionService;
  }

  // ====================== QUẢN LÝ USER ======================
  public boolean banUser(int adminId, int userId, String reason) {
    Admin admin = accountDAO.getAdminById(adminId);
    if (admin == null || !admin.hasPermission(AdminPermission.BAN_USER)) {
      logger.warn("Admin {} không có quyền BAN_USER", adminId);
      return false;
    }

    boolean success = accountDAO.banUser(userId);
    if (success) {
      logger.info("Admin {} banned user {} | Reason: {}", adminId, userId, reason);
      auctionService.notifySystemNotification(userId, "Account Suspended",
          "Your account has been suspended. Reason: " + reason);
    } else logger.info("Admin {} cannot banned user {}", adminId, userId);
    return success;
  }

  public boolean unbanUser(int adminId, int userId) {
    Admin admin = accountDAO.getAdminById(adminId);
    if (admin == null || !admin.hasPermission(AdminPermission.UNBAN_USER)) return false;

    boolean success = accountDAO.updateStatus(userId, UserStatus.ACTIVE);
    if (success) {
      auctionService.notifySystemNotification(userId, "Account Reactivated",
          "Your account has been reactivated.");
    }
    return success;
  }

  // ====================== QUẢN LÝ AUCTION ======================
  public boolean forceCloseAuction(int adminId, int auctionId, String reason) {
    Admin admin = accountDAO.getAdminById(adminId);
    if (admin == null || !admin.hasPermission(AdminPermission.FORCE_CLOSE_AUCTION)) return false;

    try {
      // AuctionManager.forceCloseAuction() đã fire onAuctionClosedCallback bên trong
      // → AuctionService.onAuctionClosed() tự được gọi, không cần gọi thêm ở đây
      auctionService.forceCloseAuction(auctionId, reason);
      logger.info("Admin {} force closed auction {} | Reason: {}", adminId, auctionId, reason);
      return true;
    } catch (Exception e) {
      logger.error("Force close auction failed", e);
      return false;
    }
  }

  // ====================== QUẢN LÝ ITEM ======================
  public boolean approveItem(int adminId, int itemId) {
    Admin admin = accountDAO.getAdminById(adminId);
    if (admin == null || !admin.hasPermission(AdminPermission.APPROVE_ITEM)) return false;

    boolean approved = itemDAO.approveItem(itemId);
    if (approved) {
      // Lấy sellerId để notify
      Item item = itemDAO.getById(itemId);
      if (item != null) {
        auctionService.approveItem(item.getSellerId(), itemId, item.getName());
      }
    }
    return approved;
  }

  public boolean rejectItem(int adminId, int itemId, String reason) {
    Admin admin = accountDAO.getAdminById(adminId);
    if (admin == null || !admin.hasPermission(AdminPermission.REJECT_ITEM)) return false;

    boolean rejected = itemDAO.rejectItem(itemId);
    if (rejected) {
      server.common.entity.Item item = itemDAO.getById(itemId);
      if (item != null) {
        auctionService.rejectItem(item.getSellerId(), itemId, item.getName());
      }
    }
    return rejected;
  }

  // ====================== VIEW ALL ======================
  public List<User> getAllUsers() {
    return accountDAO.getAll();
  }

  public List<Item> getAllItems() {
    return itemDAO.getAllItem();
  }

  public List<AuctionDTO> getAllAuctions() {
    return auctionDAO.getAllAuction();
  }

  // ====================== BROADCAST ======================
  public void broadcastSystemNotification(String title, String message) {
    auctionService.broadcastSystemNotif(title, message);
  }
}

package server.common.entity;


import server.common.entity.manager.AuctionManager;
import server.common.enums.AccountRole;
import server.common.enums.AdminPermission;
import server.common.enums.UserStatus;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/*  Admin
    force-close auction, ban user, xem toàn bộ dữ liệu (transaction log).
    permissions lưu dưới dạng Set<String> để dễ kiểm tra.
    Note: UI cần check hasPermission() trước khi hiển thị
    các chức năng admin nhạy cảm. (Đăng nhập = tài khoản role = ADMIN trong DB)
 */
public class Admin extends Account {

    private final Set<AdminPermission> permissions = new HashSet<>(Arrays.asList(
        AdminPermission.BAN_USER,
        AdminPermission.UNBAN_USER,
        AdminPermission.FORCE_CLOSE_AUCTION,
        AdminPermission.APPROVE_ITEM,
        AdminPermission.REJECT_ITEM,
        AdminPermission.VIEW_ALL_USERS,
        AdminPermission.VIEW_ALL_ITEMS,
        AdminPermission.VIEW_ALL_AUCTIONS,
        AdminPermission.MANAGE_PAYMENTS,
        AdminPermission.SYSTEM_NOTIFICATION
        ));

    public Admin(String username, String email, String passwordHash,
                 String fullName, String phone) {
        super(username, email, passwordHash, fullName, phone, AccountRole.ADMIN);
    }

    /** Constructor load từ DB */
    public Admin(String id, LocalDateTime createdAt,
                 String username, String email, String passwordHash,
                 String fullName, String phone, UserStatus status,
                 LocalDateTime lastLogin) {
        super(id, createdAt, username, email, passwordHash, fullName, phone,
              AccountRole.ADMIN, status, lastLogin);
    }

    public boolean hasPermission(AdminPermission permission) {
        return permissions.contains(permission);
    }

    public void grantPermission(AdminPermission permission) { permissions.add(permission); }
    public void revokePermission(AdminPermission permission) { permissions.remove(permission); }

    public Set<AdminPermission> getPermissions() { return new HashSet<>(permissions); }  // Return bản sao


    @Override
    public void printInfo() {
        super.printInfo();
        System.out.println("  Permissions: " + permissions);
    }
}

package model.user;

import enums.AccountRole;
import enums.UserStatus;
import manager.AuctionManager;

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

    private final Set<String> permissions;

    public Admin(String username, String email, String passwordHash,
                 String fullName, String phone) {
        super(username, email, passwordHash, fullName, phone, AccountRole.ADMIN);
        this.permissions = new HashSet<>(Arrays.asList(
            "CLOSE_AUCTION", "BAN_USER", "VIEW_ALL", "MANAGE_ITEMS"
        ));
    }

    /** Constructor load từ DB */
    public Admin(String id, LocalDateTime createdAt,
                 String username, String email, String passwordHash,
                 String fullName, String phone, UserStatus status,
                 LocalDateTime lastLogin, Set<String> permissions) {
        super(id, createdAt, username, email, passwordHash, fullName, phone,
              AccountRole.ADMIN, status, lastLogin);
        this.permissions = new HashSet<>(permissions);
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    public void grantPermission(String permission) { permissions.add(permission); }
    public void revokePermission(String permission) { permissions.remove(permission); }

    public Set<String> getPermissions() { return new HashSet<>(permissions); }  // Return bản sao

    public void closeAuction(String auctionId, String reason){
        AuctionManager.getInstance().forceCloseAuction(auctionId,reason);
    }

    @Override
    public void printInfo() {
        super.printInfo();
        System.out.println("  Permissions: " + permissions);
    }
}

package server.common.entity;

import server.common.enums.AccountRole;
import server.common.enums.AdminPermission;
import server.common.enums.UserStatus;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Tài khoản Quản trị viên của hệ thống đấu giá.
 *
 * <p>Admin được trao quyền mặc định đầy đủ khi khởi tạo. Tập quyền
 * có thể được thu hẹp hoặc mở rộng qua {@link #grantPermission} / {@link #revokePermission}.</p>
 *
 * <p>UI: gọi {@link #hasPermission(AdminPermission)} trước khi hiển thị
 * các chức năng nhạy cảm (force-close auction, ban user, v.v.).</p>
 *
 * <p>DB: đăng nhập bằng tài khoản có {@code role = ADMIN} trong bảng {@code accounts}.</p>
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

    /** Constructor load từ DB — toàn bộ trường đã được persist. */
    public Admin(int id, LocalDateTime createdAt,
                 String username, String email, String passwordHash,
                 String fullName, String phone, UserStatus status,
                 LocalDateTime lastLogin) {
        super(id, createdAt, username, email, passwordHash, fullName, phone,
            AccountRole.ADMIN, status, lastLogin);
    }

    // ── Permission management ─────────────────────────────────────────────────

    /**
     * Kiểm tra admin có quyền thực hiện một hành động cụ thể không.
     *
     * @param permission quyền cần kiểm tra
     * @return {@code true} nếu admin có quyền đó
     */
    public boolean hasPermission(AdminPermission permission) {
        return permissions.contains(permission);
    }

    public void grantPermission(AdminPermission permission) { permissions.add(permission); }
    public void revokePermission(AdminPermission permission) { permissions.remove(permission); }

    /** Trả về bản sao của tập quyền — tránh caller sửa trực tiếp. */
    public Set<AdminPermission> getPermissions() { return new HashSet<>(permissions); }

    @Override
    public void printInfo() {
        super.printInfo();
        System.out.println("  Permissions: " + permissions);
    }
}

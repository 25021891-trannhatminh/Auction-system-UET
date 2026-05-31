package server.common.entity;

import java.time.LocalDateTime;

/**
 * Lớp trừu tượng gốc cho mọi Entity trong hệ thống.
 *
 * <p>Mọi entity đều có {@code id} (ánh xạ sang INT auto_increment của MySQL)
 * và {@code createdAt} (thời điểm tạo bản ghi).
 * Hai entity được coi là bằng nhau khi có cùng {@code id}.</p>
 *
 * <p>Lý do dùng {@code int} thay vì UUID:
 * tầng DAO map giá trị này sang {@code INT auto_increment} của MySQL.
 * Khi tạo mới entity chưa persist, {@code id = 0} là sentinel "chưa có ID thật".</p>
 */
public abstract class Entity {

    private final int id;
    private final LocalDateTime createdAt;

    protected Entity() {
        this.id = 0;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Constructor dùng khi load từ DB — {@code id} và {@code createdAt} đã có sẵn.
     */
    protected Entity(int id, LocalDateTime createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * In thông tin entity ra console — mỗi subclass tự định nghĩa format hiển thị.
     */
    public abstract void printInfo();

    /**
     * Hai Entity bằng nhau khi và chỉ khi hai {@code id} bằng nhau.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Entity)) return false;
        return id == (((Entity) o).id);
    }

    /**
     * HashCode của Entity = HashCode của {@code id}.
     */
    @Override
    public int hashCode() {
        return String.valueOf(id).hashCode();
    }
}
package model;

import java.time.LocalDateTime;
import java.util.UUID;
    // Dùng UUID: tránh lộ số lượng, khó dò tìm lộ thông tin, không bị trùng ID

/*
    Mọi entity đều có id và createdAt

    id tạo auto bằng UUID —> khi kết nối DB, tầng DAO sẽ map UUID này sang INT auto_increment của MySQL.
 */
public abstract class Entity {

    private final String id;
    private final LocalDateTime createdAt;

    protected Entity() {
        this.id        = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
    }

    /* Constructor dùng cho DB (id và createdAt đã có sẵn) */
    protected Entity(String id, LocalDateTime createdAt) {
        this.id        = id;
        this.createdAt = createdAt;
    }

    public String        getId()        { return id; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public abstract void printInfo();

    // 2 Entity = nhau (=) 2 ID = nhau
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Entity)) return false;
        return id.equals(((Entity) o).id);
    }

    // HashCode Entity = HashCode ID của nó
    @Override
    public int hashCode() { return id.hashCode(); }
}

package server.common.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;

public class ItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private int itemId;
    private String name;
    private String category;
    private BigDecimal startingPrice;
    private Timestamp createdAt;

    public ItemDTO() {}

    public ItemDTO(int itemId, String name, String category,
                   BigDecimal startingPrice, Timestamp createdAt) {
        this.itemId = itemId;
        this.name = name;
        this.category = category;
        this.startingPrice = startingPrice;
        this.createdAt = createdAt;
    }

    public int getItemId() { return itemId; }
    public void setItemId(int itemId) { this.itemId = itemId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public BigDecimal getStartingPrice() { return startingPrice; }
    public void setStartingPrice(BigDecimal startingPrice) { this.startingPrice = startingPrice; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
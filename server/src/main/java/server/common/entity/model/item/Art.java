package server.common.entity.model.item;

import server.common.entity.Item;
import server.common.enums.ItemCategory;
import server.common.enums.ItemStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/*
    Thuộc tính riêng: artist, yearCreated, medium (chất liệu).
 */
public class Art extends Item {

    private String artist;
    private int    yearCreated;
    private String medium;

    public Art(int sellerId, String name, String description,
               BigDecimal startingPrice, ItemStatus status, String artist, int yearCreated, String medium) {
        super(sellerId, name, description, startingPrice, status, ItemCategory.ART);
        this.artist      = artist;
        this.yearCreated = yearCreated;
        this.medium      = medium;
    }

    /** Load từ DB */
    public Art(int id, LocalDateTime createdAt,
               int sellerId, String name, String description,
               BigDecimal startingPrice, ItemStatus status,
               String artist, int yearCreated, String medium) {
        super(id, createdAt, sellerId, name, description, startingPrice, status, ItemCategory.ART);
        this.artist      = artist;
        this.yearCreated = yearCreated;
        this.medium      = medium;
    }
    public Art (Item item){
        super(item);
    }

    @Override public String  getCategory() { return "ART"; }
    @Override public boolean validate()    { return artist != null && !artist.isBlank() && yearCreated > 0; }

    public String getArtist()          { return artist; }
    public int    getYearCreated()     { return yearCreated; }
    public String getMedium()          { return medium; }
    public void   setArtist(String a)  { this.artist = a; }
    public void   setMedium(String m)  { this.medium = m; }

    @Override
    public void printInfo() {
        super.printInfo();
        System.out.printf("  Artist: %s | Year: %d | Medium: %s%n", artist, yearCreated, medium);
    }
}

package model.item;

import java.time.LocalDateTime;

/*
    Thuộc tính riêng: artist, yearCreated, medium (chất liệu).
 */
public class Art extends Item {

    private String artist;
    private int    yearCreated;
    private String medium;

    public Art(String sellerId, String name, String description,
               double startingPrice, String artist, int yearCreated, String medium) {
        super(sellerId, name, description, startingPrice);
        this.artist      = artist;
        this.yearCreated = yearCreated;
        this.medium      = medium;
    }

    /** Load từ DB */
    public Art(String id, LocalDateTime createdAt,
               String sellerId, String name, String description,
               double startingPrice, String status,
               String artist, int yearCreated, String medium) {
        super(id, createdAt, sellerId, name, description, startingPrice, status);
        this.artist      = artist;
        this.yearCreated = yearCreated;
        this.medium      = medium;
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

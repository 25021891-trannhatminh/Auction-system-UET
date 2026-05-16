package server.common.entity.model.item;

import server.common.entity.Item;
import server.common.enums.ItemCategory;
import server.common.enums.ItemStatus;

import java.time.LocalDateTime;

/*
 Thuộc tính riêng: brand, model, yearManufactured, mileageKm.
 */
public class Vehicle extends Item {

    private String brand;
    private String model;
    private int    yearManufactured;
    private int    mileageKm;

    public Vehicle(String sellerId, String name, String description,
                   double startingPrice,ItemStatus status, String brand,  String model,
                   int yearManufactured, int mileageKm) {
        super(sellerId, name, description, startingPrice,status, ItemCategory.VEHICLE);
        this.brand            = brand;
        this.model            = model;
        this.yearManufactured = yearManufactured;
        this.mileageKm        = mileageKm;
    }

    /** Load từ DB */
    public Vehicle(String id, LocalDateTime createdAt,
                   String sellerId, String name, String description,
                   double startingPrice, ItemStatus status,
                   String brand, String model, int yearManufactured, int mileageKm) {
        super(id, createdAt, sellerId, name, description, startingPrice, status, ItemCategory.VEHICLE);
        this.brand            = brand;
        this.model            = model;
        this.yearManufactured = yearManufactured;
        this.mileageKm        = mileageKm;
    }
    public Vehicle(Item item){
        super(item);
    }

    @Override public String  getCategory() { return "VEHICLE"; }
    @Override public boolean validate()    {
        return brand != null && !brand.isBlank()
            && yearManufactured > 1900
            && mileageKm >= 0;
    }

    public String getBrand()            { return brand; }
    public String getModel()            { return model; }
    public int    getYearManufactured() { return yearManufactured; }
    public int    getMileageKm()        { return mileageKm; }
    public void   setBrand(String b)    { this.brand = b; }
    public void   setModel(String m)    { this.model = m; }
    public void   setMileageKm(int km)  { this.mileageKm = km; }

    @Override
    public void printInfo() {
        super.printInfo();
        System.out.printf("  %s %s (%d) | %d km%n", brand, model, yearManufactured, mileageKm);
    }
}

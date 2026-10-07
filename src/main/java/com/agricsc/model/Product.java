package com.agricsc.model;

import java.sql.Timestamp;

/**
 * Maps one row of the `products` table.
 * A product belongs to exactly one farmer (N:1) and has at most one
 * inventory row (1:1, products.product_id -> inventory.product_id).
 */
public class Product {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";

    private int productId;
    private int farmerId;
    private String productName;
    private String category;
    private String unit;
    private double price;
    private String description;
    private String status;
    private Timestamp createdAt;

    public Product() {
    }

    public Product(int farmerId, String productName, String category,
                   String unit, double price, String description) {
        this.farmerId = farmerId;
        this.productName = productName;
        this.category = category;
        this.unit = unit;
        this.price = price;
        this.description = description;
        this.status = STATUS_ACTIVE;
    }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public int getFarmerId() { return farmerId; }
    public void setFarmerId(int farmerId) { this.farmerId = farmerId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "Product{id=" + productId + ", name='" + productName + '\''
                + ", price=" + price + ' ' + unit + ", status='" + status + '\'' + '}';
    }
}

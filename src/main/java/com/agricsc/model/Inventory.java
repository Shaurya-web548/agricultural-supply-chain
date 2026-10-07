package com.agricsc.model;

import java.sql.Timestamp;

/**
 * Maps one row of the `inventory` table: how many units of a product are
 * currently available. quantity_available has a DB CHECK (>= 0), and all
 * quantity changes in Java go through InventoryDAO with synchronization so
 * two threads can never oversell the same product.
 */
public class Inventory {

    private int inventoryId;
    private int productId;
    private int quantityAvailable;
    private Timestamp lastUpdated;

    public Inventory() {
    }

    public Inventory(int productId, int quantityAvailable) {
        this.productId = productId;
        this.quantityAvailable = quantityAvailable;
    }

    public int getInventoryId() { return inventoryId; }
    public void setInventoryId(int inventoryId) { this.inventoryId = inventoryId; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public int getQuantityAvailable() { return quantityAvailable; }
    public void setQuantityAvailable(int quantityAvailable) { this.quantityAvailable = quantityAvailable; }

    public Timestamp getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Timestamp lastUpdated) { this.lastUpdated = lastUpdated; }

    @Override
    public String toString() {
        return "Inventory{productId=" + productId + ", qty=" + quantityAvailable + '}';
    }
}

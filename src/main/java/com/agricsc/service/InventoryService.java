package com.agricsc.service;

import com.agricsc.dao.InventoryDAO;
import com.agricsc.exceptions.DatabaseException;
import com.agricsc.exceptions.InsufficientStockException;
import com.agricsc.exceptions.ProductNotFoundException;
import com.agricsc.model.Inventory;

import java.util.List;

/**
 * Rules around stock quantities.
 *
 * The actual SQL lives in InventoryDAO which synchronizes all mutations -
 * this service adds validation and converts "row missing" into the custom
 * ProductNotFoundException / InsufficientStockException.
 */
public class InventoryService {

    private final InventoryDAO inventoryDAO = new InventoryDAO();

    /** @return current stock for a product (-1 when no inventory row exists) */
    public int getQuantity(int productId) throws DatabaseException {
        return inventoryDAO.getQuantity(productId);
    }

    /**
     * Checks stock and throws the custom exception when unavailable.
     *
     * @throws InsufficientStockException when available &lt; requested
     * @throws ProductNotFoundException    when the product has no inventory row
     */
    public void requireStock(int productId, int requested)
            throws InsufficientStockException, ProductNotFoundException, DatabaseException {
        int available = inventoryDAO.getQuantity(productId);
        if (available < 0) {
            throw new ProductNotFoundException(productId);
        }
        if (available < requested) {
            throw new InsufficientStockException(productId, requested, available);
        }
    }

    /** Atomically removes stock; false when not enough is available. */
    public boolean takeFromStock(int productId, int quantity) throws DatabaseException {
        return inventoryDAO.reduceStock(productId, quantity);
    }

    /** Gives stock back (used to undo a partial order). */
    public void returnToStock(int productId, int quantity) throws DatabaseException {
        if (quantity > 0) {
            inventoryDAO.addStock(productId, quantity);
        }
    }

    /** Farmer/distributor receives fresh goods. */
    public void restock(int productId, int quantity)
            throws IllegalArgumentException, ProductNotFoundException, DatabaseException {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Restock quantity must be positive");
        }
        if (inventoryDAO.getQuantity(productId) < 0) {
            throw new ProductNotFoundException(productId);
        }
        inventoryDAO.addStock(productId, quantity);
    }

    /** Farmer directly sets the available quantity. */
    public void setQuantity(int productId, int quantity)
            throws IllegalArgumentException, ProductNotFoundException, DatabaseException {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
        if (inventoryDAO.getQuantity(productId) < 0) {
            throw new ProductNotFoundException(productId);
        }
        inventoryDAO.setQuantity(productId, quantity);
    }

    /** All stock rows for the inventory screens. */
    public List<Inventory> listInventory() throws DatabaseException {
        return inventoryDAO.findAll();
    }
}

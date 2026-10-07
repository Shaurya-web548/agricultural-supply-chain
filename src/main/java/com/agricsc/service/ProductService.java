package com.agricsc.service;

import com.agricsc.dao.InventoryDAO;
import com.agricsc.dao.ProductDAO;
import com.agricsc.exceptions.DatabaseException;
import com.agricsc.exceptions.ProductNotFoundException;
import com.agricsc.model.Product;

import java.util.HashMap;
import java.util.List;

/**
 * Business rules around products (farmer catalog + search).
 * UI screens call these methods - never the DAOs directly.
 */
public class ProductService {

    private final ProductDAO productDAO = new ProductDAO();
    private final InventoryDAO inventoryDAO = new InventoryDAO();

    /**
     * Farmer adds a product together with its initial stock.
     * Validates input BEFORE touching the database.
     */
    public Product addProduct(int farmerId, String name, String category, String unit,
                              double price, String description, int initialQuantity)
            throws IllegalArgumentException, DatabaseException {
        if (farmerId <= 0) {
            throw new IllegalArgumentException("Invalid farmer");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Product name is required");
        }
        if (price <= 0) {
            throw new IllegalArgumentException("Price must be greater than zero");
        }
        if (initialQuantity < 0) {
            throw new IllegalArgumentException("Initial quantity cannot be negative");
        }

        Product p = new Product(farmerId, name.trim(),
                (category == null || category.trim().isEmpty()) ? "Grains" : category.trim(),
                (unit == null || unit.trim().isEmpty()) ? "kg" : unit.trim(),
                price, description);
        int productId = productDAO.insertProduct(p);
        p.setProductId(productId);

        // A product without an inventory row cannot be sold - create both now.
        inventoryDAO.insertInventory(productId, initialQuantity);
        return p;
    }

    /** Farmer updates product info (price, category, status...). */
    public void updateProduct(Product p) throws IllegalArgumentException, DatabaseException {
        if (p.getProductId() <= 0) {
            throw new IllegalArgumentException("Product id is required");
        }
        if (p.getProductName() == null || p.getProductName().trim().isEmpty()) {
            throw new IllegalArgumentException("Product name is required");
        }
        if (p.getPrice() <= 0) {
            throw new IllegalArgumentException("Price must be greater than zero");
        }
        if (productDAO.updateProduct(p) == 0) {
            throw new IllegalArgumentException(
                    "Product " + p.getProductId() + " does not exist");
        }
    }

    /**
     * Removes a product.
     *
     * A product that was never ordered is deleted (its inventory row
     * cascades away). A product that appears in past orders is only marked
     * INACTIVE - hard-deleting it would cascade into order_items and erase
     * order history. INACTIVE products can no longer be ordered.
     *
     * @return true when the row was deleted, false when it was deactivated
     */
    public boolean deleteProduct(int productId)
            throws ProductNotFoundException, DatabaseException {
        getProduct(productId);   // ProductNotFoundException when missing
        if (productDAO.hasOrderHistory(productId)) {
            productDAO.updateStatus(productId, Product.STATUS_INACTIVE);
            return false;
        }
        productDAO.deleteProduct(productId);
        return true;
    }

    /**
     * Ownership rule for farmer actions: a farmer may only change their own
     * products. Enforced here, not in the UI, so every caller is covered.
     */
    public Product requireOwnedBy(int productId, int farmerId)
            throws ProductNotFoundException, DatabaseException {
        Product p = getProduct(productId);
        if (p.getFarmerId() != farmerId) {
            throw new IllegalArgumentException(
                    "Product " + productId + " belongs to another farmer");
        }
        return p;
    }

    /** @throws ProductNotFoundException when the id does not exist */
    public Product getProduct(int productId)
            throws ProductNotFoundException, DatabaseException {
        Product p = productDAO.findById(productId);
        if (p == null) {
            throw new ProductNotFoundException(productId);
        }
        return p;
    }

    /** Search with optional filters - results arrive as an ArrayList. */
    public List<Product> search(String keyword, String category, Double maxPrice)
            throws DatabaseException {
        return productDAO.search(keyword, category, maxPrice);
    }

    public List<Product> listAll() throws DatabaseException {
        return productDAO.findAll();
    }

    public List<Product> listByFarmer(int farmerId) throws DatabaseException {
        return productDAO.findByFarmer(farmerId);
    }

    /**
     * COLLECTIONS: HashMap&lt;Integer,Product&gt; keyed by id - used by
     * OrderService to price order items without hitting the DB per item.
     */
    public HashMap<Integer, Product> getProductMap() throws DatabaseException {
        return productDAO.loadProductMap();
    }
}

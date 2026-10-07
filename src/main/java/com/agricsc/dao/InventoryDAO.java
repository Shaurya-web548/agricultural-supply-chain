package com.agricsc.dao;

import com.agricsc.database.DBConnection;
import com.agricsc.exceptions.DatabaseException;
import com.agricsc.model.Inventory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * All SQL for the inventory table.
 *
 * MULTITHREADING: stock changes are the classic race-condition spot - two
 * threads could both read "10 left", both sell 10, and oversell. Two layers
 * of protection are used:
 *
 *  1. `synchronized` blocks on a class-level lock, so inside this JVM only
 *     one thread can check+change stock at a time.
 *  2. A conditional UPDATE
 *         UPDATE inventory SET quantity_available = quantity_available - ?
 *         WHERE product_id = ? AND quantity_available >= ?
 *     which only succeeds when enough stock exists - even a second JVM
 *     cannot oversell. The DB CHECK (quantity_available >= 0) is the
 *     final safety net.
 */
public class InventoryDAO {

    /** Shared monitor object: one lock for ALL inventory mutations. */
    private static final Object INVENTORY_LOCK = new Object();

    /** Creates the stock row for a newly added product. */
    public void insertInventory(int productId, int quantity) throws DatabaseException {
        String sql = "INSERT INTO inventory (product_id, quantity_available) VALUES (?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setInt(2, quantity);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to create inventory for product " + productId, e);
        }
    }

    /** @return current stock, or -1 when the product has no inventory row */
    public int getQuantity(int productId) throws DatabaseException {
        String sql = "SELECT quantity_available FROM inventory WHERE product_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to read stock for product " + productId, e);
        }
    }

    /**
     * Atomically checks and decrements stock.
     *
     * @return true when the requested quantity was available and removed;
     *         false when stock was too low (nothing is changed)
     */
    public boolean reduceStock(int productId, int quantity) throws DatabaseException {
        String sql = "UPDATE inventory SET quantity_available = quantity_available - ? "
                + "WHERE product_id = ? AND quantity_available >= ?";
        synchronized (INVENTORY_LOCK) {          // prevents concurrent oversell in-JVM
            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, quantity);
                ps.setInt(2, productId);
                ps.setInt(3, quantity);
                return ps.executeUpdate() == 1; // 0 rows = not enough stock
            } catch (SQLException e) {
                throw new DatabaseException(
                        "Failed to reduce stock for product " + productId, e);
            }
        }
    }

    /** Farmer/distributor adds fresh stock. */
    public void addStock(int productId, int quantity) throws DatabaseException {
        String sql = "UPDATE inventory SET quantity_available = quantity_available + ? "
                + "WHERE product_id = ?";
        synchronized (INVENTORY_LOCK) {
            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, quantity);
                ps.setInt(2, productId);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new DatabaseException(
                        "Failed to add stock for product " + productId, e);
            }
        }
    }

    /** Farmer directly sets a new absolute quantity ("update available quantity"). */
    public void setQuantity(int productId, int quantity) throws DatabaseException {
        if (quantity < 0) {
            throw new DatabaseException("Quantity cannot be negative: " + quantity);
        }
        String sql = "UPDATE inventory SET quantity_available = ? WHERE product_id = ?";
        synchronized (INVENTORY_LOCK) {
            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, quantity);
                ps.setInt(2, productId);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new DatabaseException(
                        "Failed to set stock for product " + productId, e);
            }
        }
    }

    /** All stock rows - admin inventory screen. */
    public List<Inventory> findAll() throws DatabaseException {
        String sql = "SELECT inventory_id, product_id, quantity_available, last_updated "
                + "FROM inventory ORDER BY product_id";
        List<Inventory> result = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Inventory inv = new Inventory();
                inv.setInventoryId(rs.getInt("inventory_id"));
                inv.setProductId(rs.getInt("product_id"));
                inv.setQuantityAvailable(rs.getInt("quantity_available"));
                inv.setLastUpdated(rs.getTimestamp("last_updated"));
                result.add(inv);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to list inventory", e);
        }
        return result;
    }
}

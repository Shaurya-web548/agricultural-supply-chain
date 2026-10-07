package com.agricsc.dao;

import com.agricsc.database.DBConnection;
import com.agricsc.exceptions.DatabaseException;
import com.agricsc.model.Product;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * All SQL for the products table (farmer's catalog).
 * Demonstrates: PreparedStatement with parameters, generated keys,
 * dynamic search query building, and a HashMap product cache.
 */
public class ProductDAO {

    public int insertProduct(Product p) throws DatabaseException {
        String sql = "INSERT INTO products (farmer_id, product_name, category, unit, price, description, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, p.getFarmerId());
            ps.setString(2, p.getProductName());
            ps.setString(3, p.getCategory());
            ps.setString(4, p.getUnit());
            ps.setDouble(5, p.getPrice());
            ps.setString(6, p.getDescription());
            ps.setString(7, p.getStatus());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            throw new DatabaseException("Product insert returned no generated key");
        } catch (SQLException e) {
            throw new DatabaseException("Failed to insert product: " + e.getMessage(), e);
        }
    }

    /** @return the product or null when the id does not exist */
    public Product findById(int productId) throws DatabaseException {
        String sql = "SELECT product_id, farmer_id, product_name, category, unit, price, "
                + "description, status, created_at FROM products WHERE product_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapProduct(rs) : null;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to load product " + productId, e);
        }
    }

    /** Every product - used by admin and distributor browse screens. */
    public List<Product> findAll() throws DatabaseException {
        String sql = "SELECT product_id, farmer_id, product_name, category, unit, price, "
                + "description, status, created_at FROM products ORDER BY product_id";
        List<Product> result = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapProduct(rs));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to list products", e);
        }
        return result;
    }

    /** The farmer's own catalog (farmer panel). */
    public List<Product> findByFarmer(int farmerId) throws DatabaseException {
        String sql = "SELECT product_id, farmer_id, product_name, category, unit, price, "
                + "description, status, created_at FROM products "
                + "WHERE farmer_id = ? ORDER BY product_id";
        List<Product> result = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, farmerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapProduct(rs));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to list products for farmer " + farmerId, e);
        }
        return result;
    }

    /**
     * Keyword / category / max-price search.
     *
     * The WHERE clause is built dynamically BUT every user value still goes
     * through setXxx() placeholders - this is what prevents SQL injection
     * even when the query text changes.
     *
     * @param keyword   matched against name and description (null = ignore)
     * @param category  exact category match (null = ignore)
     * @param maxPrice  upper price bound (null = ignore)
     */
    public List<Product> search(String keyword, String category, Double maxPrice)
            throws DatabaseException {
        StringBuilder sql = new StringBuilder(
                "SELECT product_id, farmer_id, product_name, category, unit, price, "
                        + "description, status, created_at FROM products WHERE status = 'ACTIVE'");
        List<Object> params = new ArrayList<>();

        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND (product_name LIKE ? OR description LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
        }
        if (category != null && !category.trim().isEmpty()) {
            sql.append(" AND category = ?");
            params.add(category.trim());
        }
        if (maxPrice != null) {
            sql.append(" AND price <= ?");
            params.add(maxPrice);
        }
        sql.append(" ORDER BY product_name");

        List<Product> result = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof String) {
                    ps.setString(i + 1, (String) p);
                } else {
                    ps.setDouble(i + 1, (Double) p);
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapProduct(rs));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Product search failed: " + e.getMessage(), e);
        }
        return result;
    }

    /** Farmer edits price / quantity-relevant fields / status. */
    public int updateProduct(Product p) throws DatabaseException {
        String sql = "UPDATE products SET product_name = ?, category = ?, unit = ?, "
                + "price = ?, description = ?, status = ? WHERE product_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, p.getProductName());
            ps.setString(2, p.getCategory());
            ps.setString(3, p.getUnit());
            ps.setDouble(4, p.getPrice());
            ps.setString(5, p.getDescription());
            ps.setString(6, p.getStatus());
            ps.setInt(7, p.getProductId());
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to update product " + p.getProductId(), e);
        }
    }

    /** Hard delete; inventory row disappears via ON DELETE CASCADE. */
    /** @return true when any order line references this product */
    public boolean hasOrderHistory(int productId) throws DatabaseException {
        String sql = "SELECT 1 FROM order_items WHERE product_id = ? LIMIT 1";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to check orders of product " + productId, e);
        }
    }

    /** Soft delete / re-activate: ACTIVE or INACTIVE. */
    public int updateStatus(int productId, String status) throws DatabaseException {
        String sql = "UPDATE products SET status = ? WHERE product_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, productId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to change status of product " + productId, e);
        }
    }

    public int deleteProduct(int productId) throws DatabaseException {
        String sql = "DELETE FROM products WHERE product_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, productId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to delete product " + productId, e);
        }
    }

    /**
     * COLLECTIONS FRAMEWORK: loads all products into a HashMap keyed by
     * product_id so any product can be found in O(1) during order
     * processing instead of re-querying the database for every item.
     */
    public HashMap<Integer, Product> loadProductMap() throws DatabaseException {
        HashMap<Integer, Product> map = new HashMap<>();
        for (Product p : findAll()) {
            map.put(p.getProductId(), p);
        }
        return map;
    }

    private Product mapProduct(ResultSet rs) throws SQLException {
        Product p = new Product();
        p.setProductId(rs.getInt("product_id"));
        p.setFarmerId(rs.getInt("farmer_id"));
        p.setProductName(rs.getString("product_name"));
        p.setCategory(rs.getString("category"));
        p.setUnit(rs.getString("unit"));
        p.setPrice(rs.getDouble("price"));
        p.setDescription(rs.getString("description"));
        p.setStatus(rs.getString("status"));
        p.setCreatedAt(rs.getTimestamp("created_at"));
        return p;
    }
}

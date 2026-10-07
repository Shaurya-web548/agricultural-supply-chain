package com.agricsc.dao;

import com.agricsc.database.DBConnection;
import com.agricsc.exceptions.DatabaseException;
import com.agricsc.model.Order;
import com.agricsc.model.OrderItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * All SQL for orders and order_items.
 *
 * Order items are stored in an ArrayList<OrderItem> inside the Order model,
 * and the DAO loads/saves that list - Collections + JDBC working together.
 * The simulated payments table (1:1 with orders) is also written here,
 * because a payment only ever changes together with its order.
 * Note that insertOrder and insertOrderItem use SEPARATE connections; a
 * production system would wrap them in one transaction (see README
 * "Future enhancements").
 */
public class OrderDAO {

    /** Inserts the order header and returns its generated order_id. */
    public int insertOrder(Order order) throws DatabaseException {
        String sql = "INSERT INTO orders (customer_id, distributor_id, total_amount, status) "
                + "VALUES (?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, order.getCustomerId());
            if (order.getDistributorId() == null) {
                ps.setNull(2, java.sql.Types.INTEGER);
            } else {
                ps.setInt(2, order.getDistributorId());
            }
            ps.setDouble(3, order.getTotalAmount());
            ps.setString(4, order.getStatus());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            throw new DatabaseException("Order insert returned no generated key");
        } catch (SQLException e) {
            throw new DatabaseException("Failed to insert order: " + e.getMessage(), e);
        }
    }

    /** Inserts one line item of an order. */
    public int insertOrderItem(OrderItem item) throws DatabaseException {
        String sql = "INSERT INTO order_items (order_id, product_id, quantity, unit_price, subtotal) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, item.getOrderId());
            ps.setInt(2, item.getProductId());
            ps.setInt(3, item.getQuantity());
            ps.setDouble(4, item.getUnitPrice());
            ps.setDouble(5, item.getSubtotal());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            throw new DatabaseException("Order item insert returned no key");
        } catch (SQLException e) {
            throw new DatabaseException("Failed to insert order item: " + e.getMessage(), e);
        }
    }

    /** Loads a full order (header + its ArrayList of items), or null. */
    public Order findById(int orderId) throws DatabaseException {
        String sql = "SELECT order_id, customer_id, distributor_id, order_date, "
                + "total_amount, status FROM orders WHERE order_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Order order = mapOrder(rs);
                order.setItems(loadItems(orderId));
                return order;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to load order " + orderId, e);
        }
    }

    /** Every order with its items - admin "view orders" screen. */
    public List<Order> findAll() throws DatabaseException {
        String sql = "SELECT order_id, customer_id, distributor_id, order_date, "
                + "total_amount, status FROM orders ORDER BY order_id DESC";
        return loadOrders(sql, null, null);
    }

    /** Orders placed by one customer - customer order history. */
    public List<Order> findByCustomer(int customerId) throws DatabaseException {
        String sql = "SELECT order_id, customer_id, distributor_id, order_date, "
                + "total_amount, status FROM orders WHERE customer_id = ? ORDER BY order_id DESC";
        return loadOrders(sql, "customer_id", customerId);
    }

    /** Order status transitions (farmer/distributor update order status). */
    public int updateStatus(int orderId, String status) throws DatabaseException {
        String sql = "UPDATE orders SET status = ? WHERE order_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, orderId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to update status of order " + orderId, e);
        }
    }

    /**
     * Atomically moves an order PENDING -> PROCESSING.
     *
     * Check-and-set happens inside ONE conditional UPDATE, so if two threads
     * try to process the same order only one gets rowsAffected == 1.
     *
     * @return true when this caller now owns the order's processing
     */
    public boolean claimForProcessing(int orderId) throws DatabaseException {
        String sql = "UPDATE orders SET status = ? WHERE order_id = ? AND status = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, Order.STATUS_PROCESSING);
            ps.setInt(2, orderId);
            ps.setString(3, Order.STATUS_PENDING);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to claim order " + orderId, e);
        }
    }

    /** Orders that contain at least one product of this farmer. */
    public List<Order> findByFarmer(int farmerId) throws DatabaseException {
        String sql = "SELECT DISTINCT o.order_id, o.customer_id, o.distributor_id, o.order_date, "
                + "o.total_amount, o.status FROM orders o "
                + "JOIN order_items oi ON oi.order_id = o.order_id "
                + "JOIN products p ON p.product_id = oi.product_id "
                + "WHERE p.farmer_id = ? ORDER BY o.order_id DESC";
        return loadOrders(sql, "farmer_id", farmerId);
    }

    // ------------------------------------------------------------- payments

    /** Creates the (simulated) payment row for a new order - status PENDING. */
    public void insertPayment(int orderId, double amount) throws DatabaseException {
        String sql = "INSERT INTO payments (order_id, amount) VALUES (?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setDouble(2, amount);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to create payment for order " + orderId, e);
        }
    }

    /** PENDING -> PAID when the order confirms, FAILED when it is cancelled. */
    public void updatePaymentStatus(int orderId, String paymentStatus) throws DatabaseException {
        String sql = "UPDATE payments SET payment_status = ? WHERE order_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, paymentStatus);
            ps.setInt(2, orderId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to update payment of order " + orderId, e);
        }
    }

    /** @return payment_status of the order, or null when it has no payment row */
    public String findPaymentStatus(int orderId) throws DatabaseException {
        String sql = "SELECT payment_status FROM payments WHERE order_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to read payment of order " + orderId, e);
        }
    }

    /** Links an order to the distributor who will fulfil it. */
    public void assignDistributor(int orderId, int distributorId) throws DatabaseException {
        String sql = "UPDATE orders SET distributor_id = ? WHERE order_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, distributorId);
            ps.setInt(2, orderId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException(
                    "Failed to assign distributor to order " + orderId, e);
        }
    }

    /** Recalculates the stored total after items are inserted. */
    public void updateTotal(int orderId, double total) throws DatabaseException {
        String sql = "UPDATE orders SET total_amount = ? WHERE order_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDouble(1, total);
            ps.setInt(2, orderId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to update total of order " + orderId, e);
        }
    }

    // -------------------------------------------------------------- helpers

    private List<Order> loadOrders(String sql, String paramColumn, Integer paramValue)
            throws DatabaseException {
        List<Order> result = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (paramColumn != null) {
                ps.setInt(1, paramValue);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Order order = mapOrder(rs);
                    order.setItems(loadItems(order.getOrderId()));
                    result.add(order);
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to load orders", e);
        }
        return result;
    }

    /** Builds the ArrayList<OrderItem> belonging to one order. */
    private List<OrderItem> loadItems(int orderId) throws DatabaseException {
        String sql = "SELECT item_id, order_id, product_id, quantity, unit_price, subtotal "
                + "FROM order_items WHERE order_id = ? ORDER BY item_id";
        List<OrderItem> items = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OrderItem item = new OrderItem();
                    item.setItemId(rs.getInt("item_id"));
                    item.setOrderId(rs.getInt("order_id"));
                    item.setProductId(rs.getInt("product_id"));
                    item.setQuantity(rs.getInt("quantity"));
                    item.setUnitPrice(rs.getDouble("unit_price"));
                    item.setSubtotal(rs.getDouble("subtotal"));
                    items.add(item);
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to load items of order " + orderId, e);
        }
        return items;
    }

    private Order mapOrder(ResultSet rs) throws SQLException {
        Order o = new Order();
        o.setOrderId(rs.getInt("order_id"));
        o.setCustomerId(rs.getInt("customer_id"));
        int dist = rs.getInt("distributor_id");
        o.setDistributorId(rs.wasNull() ? null : dist);
        o.setOrderDate(rs.getTimestamp("order_date"));
        o.setTotalAmount(rs.getDouble("total_amount"));
        o.setStatus(rs.getString("status"));
        return o;
    }
}

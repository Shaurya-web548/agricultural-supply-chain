package com.agricsc.dao;

import com.agricsc.database.DBConnection;
import com.agricsc.exceptions.DatabaseException;
import com.agricsc.model.Shipment;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * All SQL for the shipments table (tracking + status updates).
 */
public class ShipmentDAO {

    /** Creates a shipment for an order; returns generated shipment_id. */
    public int insertShipment(Shipment s) throws DatabaseException {
        String sql = "INSERT INTO shipments (order_id, tracking_number, carrier, "
                + "shipped_date, expected_delivery, current_location, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, s.getOrderId());
            ps.setString(2, s.getTrackingNumber());
            ps.setString(3, s.getCarrier());
            ps.setTimestamp(4, s.getShippedDate());
            ps.setTimestamp(5, s.getExpectedDelivery());
            ps.setString(6, s.getCurrentLocation());
            ps.setString(7, s.getStatus());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            throw new DatabaseException("Shipment insert returned no key");
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
                throw new DatabaseException(
                        "Shipment already exists for this order (unique constraint)", e);
            }
            throw new DatabaseException("Failed to create shipment: " + e.getMessage(), e);
        }
    }

    /** 1:1 with orders - at most one shipment per order. */
    public Shipment findByOrderId(int orderId) throws DatabaseException {
        String sql = selectCols() + " WHERE order_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapShipment(rs) : null;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to find shipment for order " + orderId, e);
        }
    }

    /** Lookup by primary key, or null. */
    public Shipment findById(int shipmentId) throws DatabaseException {
        String sql = selectCols() + " WHERE shipment_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, shipmentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapShipment(rs) : null;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to find shipment " + shipmentId, e);
        }
    }

    /** Customer "track shipment" screen - lookup by unique tracking number. */
    public Shipment findByTrackingNumber(String trackingNumber) throws DatabaseException {
        String sql = selectCols() + " WHERE tracking_number = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, trackingNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapShipment(rs) : null;
            }
        } catch (SQLException e) {
            throw new DatabaseException(
                    "Failed to track shipment '" + trackingNumber + "'", e);
        }
    }

    /**
     * Progresses a shipment: new status + new location (+ ship date when it
     * first leaves). Called by ShipmentTrackingThread / distributor panel.
     */
    public int updateShipmentStatus(int shipmentId, String status, String location)
            throws DatabaseException {
        String sql = "UPDATE shipments SET status = ?, current_location = ?, "
                + "shipped_date = COALESCE(shipped_date, ?) WHERE shipment_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, location);
            ps.setTimestamp(3, "PENDING".equals(status) ? null
                    : new java.sql.Timestamp(System.currentTimeMillis()));
            ps.setInt(4, shipmentId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to update shipment " + shipmentId, e);
        }
    }

    /** All shipments - admin "manage shipments" screen. */
    public List<Shipment> findAll() throws DatabaseException {
        String sql = selectCols() + " ORDER BY shipment_id DESC";
        List<Shipment> result = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapShipment(rs));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to list shipments", e);
        }
        return result;
    }

    // -------------------------------------------------------------- helpers

    private String selectCols() {
        return "SELECT shipment_id, order_id, tracking_number, carrier, shipped_date, "
                + "expected_delivery, current_location, status, created_at FROM shipments";
    }

    private Shipment mapShipment(ResultSet rs) throws SQLException {
        Shipment s = new Shipment();
        s.setShipmentId(rs.getInt("shipment_id"));
        s.setOrderId(rs.getInt("order_id"));
        s.setTrackingNumber(rs.getString("tracking_number"));
        s.setCarrier(rs.getString("carrier"));
        s.setShippedDate(rs.getTimestamp("shipped_date"));
        s.setExpectedDelivery(rs.getTimestamp("expected_delivery"));
        s.setCurrentLocation(rs.getString("current_location"));
        s.setStatus(rs.getString("status"));
        s.setCreatedAt(rs.getTimestamp("created_at"));
        return s;
    }
}

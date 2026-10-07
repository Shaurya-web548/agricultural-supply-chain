package com.agricsc.service;

import com.agricsc.dao.OrderDAO;
import com.agricsc.dao.ShipmentDAO;
import com.agricsc.exceptions.DatabaseException;
import com.agricsc.model.Order;
import com.agricsc.model.Shipment;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Shipment creation, tracking and status updates.
 *
 * Shipment tracking numbers are unique (DB UNIQUE constraint) and generated
 * from the order id + a random suffix so two threads creating shipments at
 * the same moment cannot collide.
 */
public class ShipmentService {

    /** Every status the shipments.status ENUM accepts - O(1) validation. */
    public static final Set<String> VALID_STATUSES = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    Shipment.STATUS_PENDING, Shipment.STATUS_IN_TRANSIT,
                    Shipment.STATUS_OUT_FOR_DELIVERY, Shipment.STATUS_DELIVERED,
                    Shipment.STATUS_CANCELLED)));

    /** Shipment status -> order status it implies. */
    private static final Map<String, String> SHIPMENT_TO_ORDER_STATUS = new HashMap<>();
    static {
        SHIPMENT_TO_ORDER_STATUS.put(Shipment.STATUS_IN_TRANSIT, Order.STATUS_SHIPPED);
        SHIPMENT_TO_ORDER_STATUS.put(Shipment.STATUS_OUT_FOR_DELIVERY, Order.STATUS_SHIPPED);
        SHIPMENT_TO_ORDER_STATUS.put(Shipment.STATUS_DELIVERED, Order.STATUS_DELIVERED);
    }

    private final ShipmentDAO shipmentDAO = new ShipmentDAO();
    private final OrderDAO orderDAO = new OrderDAO();

    /** Creates the initial PENDING shipment right after an order confirms. */
    public Shipment createShipmentForOrder(int orderId) throws DatabaseException {
        Shipment existing = shipmentDAO.findByOrderId(orderId);
        if (existing != null) {
            return existing;    // idempotent: never create 1:1 rows twice
        }

        String tracking = "TRK-" + orderId + "-"
                + (1000 + ThreadLocalRandom.current().nextInt(9000));
        Shipment shipment = new Shipment(orderId, tracking, "Self Transport",
                Shipment.STATUS_PENDING);
        shipment.setCurrentLocation("FARMER_WAREHOUSE");
        shipment.setExpectedDelivery(new Timestamp(
                System.currentTimeMillis() + 3L * 24 * 60 * 60 * 1000)); // +3 days
        shipment.setShipmentId(shipmentDAO.insertShipment(shipment));
        return shipment;
    }

    /** @return the shipment for an order, or null when none exists yet */
    public Shipment getShipmentForOrder(int orderId) throws DatabaseException {
        return shipmentDAO.findByOrderId(orderId);
    }

    /** Customer "track shipment" - lookup by tracking number, or null. */
    public Shipment track(String trackingNumber) throws DatabaseException {
        if (trackingNumber == null || trackingNumber.trim().isEmpty()) {
            return null;
        }
        return shipmentDAO.findByTrackingNumber(trackingNumber.trim());
    }

    /**
     * Distributor/admin (or ShipmentTrackingThread) advances shipment status
     * and live location, and keeps the order status in step:
     *   IN_TRANSIT / OUT_FOR_DELIVERY -> order SHIPPED
     *   DELIVERED                     -> order DELIVERED
     */
    public void updateStatus(int shipmentId, String status, String location)
            throws IllegalArgumentException, DatabaseException {
        if (status == null || !VALID_STATUSES.contains(status.trim())) {
            throw new IllegalArgumentException("Status must be one of " + VALID_STATUSES);
        }
        String newStatus = status.trim();
        Shipment shipment = shipmentDAO.findById(shipmentId);
        if (shipment == null) {
            throw new IllegalArgumentException("Shipment " + shipmentId + " not found");
        }
        shipmentDAO.updateShipmentStatus(shipmentId, newStatus, location);
        syncOrderStatus(shipment.getOrderId(), newStatus);
    }

    /** Mirrors shipment progress onto the order (never revives a cancelled order). */
    private void syncOrderStatus(int orderId, String shipmentStatus) throws DatabaseException {
        String orderStatus = SHIPMENT_TO_ORDER_STATUS.get(shipmentStatus);
        if (orderStatus == null) {
            return;     // PENDING / CANCELLED shipments do not move the order
        }
        Order order = orderDAO.findById(orderId);
        if (order != null && !Order.STATUS_CANCELLED.equals(order.getStatus())) {
            orderDAO.updateStatus(orderId, orderStatus);
        }
    }

    public List<Shipment> listAll() throws DatabaseException {
        return shipmentDAO.findAll();
    }
}

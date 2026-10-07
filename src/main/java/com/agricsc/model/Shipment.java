package com.agricsc.model;

import java.sql.Timestamp;

/**
 * Maps one row of the `shipments` table.
 * One order has at most one shipment (1:1, orders.order_id UNIQUE FK);
 * the tracking_number is UNIQUE so a shipment can be tracked globally.
 */
public class Shipment {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_IN_TRANSIT = "IN_TRANSIT";
    public static final String STATUS_OUT_FOR_DELIVERY = "OUT_FOR_DELIVERY";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private int shipmentId;
    private int orderId;
    private String trackingNumber;
    private String carrier;
    private Timestamp shippedDate;
    private Timestamp expectedDelivery;
    private String currentLocation;
    private String status;
    private Timestamp createdAt;

    public Shipment() {
    }

    public Shipment(int orderId, String trackingNumber, String carrier, String status) {
        this.orderId = orderId;
        this.trackingNumber = trackingNumber;
        this.carrier = carrier;
        this.status = status;
    }

    public int getShipmentId() { return shipmentId; }
    public void setShipmentId(int shipmentId) { this.shipmentId = shipmentId; }

    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }

    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }

    public String getCarrier() { return carrier; }
    public void setCarrier(String carrier) { this.carrier = carrier; }

    public Timestamp getShippedDate() { return shippedDate; }
    public void setShippedDate(Timestamp shippedDate) { this.shippedDate = shippedDate; }

    public Timestamp getExpectedDelivery() { return expectedDelivery; }
    public void setExpectedDelivery(Timestamp expectedDelivery) { this.expectedDelivery = expectedDelivery; }

    public String getCurrentLocation() { return currentLocation; }
    public void setCurrentLocation(String currentLocation) { this.currentLocation = currentLocation; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "Shipment{id=" + shipmentId + ", order=" + orderId
                + ", tracking='" + trackingNumber + '\'' + ", status='" + status + '\'' + '}';
    }
}

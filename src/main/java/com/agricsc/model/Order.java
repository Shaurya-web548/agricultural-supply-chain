package com.agricsc.model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps one row of the `orders` table plus its line items.
 *
 * COLLECTIONS FRAMEWORK: an order owns an ArrayList<OrderItem> - one order
 * has many items (1:N). OrderDAO fills this list when loading the order.
 */
public class Order {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_SHIPPED = "SHIPPED";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private int orderId;
    private int customerId;
    private Integer distributorId;      // nullable: order may not be assigned yet
    private Timestamp orderDate;
    private double totalAmount;
    private String status;
    private List<OrderItem> items = new ArrayList<>();

    public Order() {
    }

    public Order(int customerId, double totalAmount, String status) {
        this.customerId = customerId;
        this.totalAmount = totalAmount;
        this.status = status;
    }

    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }

    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }

    public Integer getDistributorId() { return distributorId; }
    public void setDistributorId(Integer distributorId) { this.distributorId = distributorId; }

    public Timestamp getOrderDate() { return orderDate; }
    public void setOrderDate(Timestamp orderDate) { this.orderDate = orderDate; }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    /** Convenience used by OrderService when building a new order. */
    public void addItem(OrderItem item) { items.add(item); }

    @Override
    public String toString() {
        return "Order{id=" + orderId + ", customer=" + customerId
                + ", total=" + totalAmount + ", status='" + status + '\''
                + ", items=" + items.size() + '}';
    }
}

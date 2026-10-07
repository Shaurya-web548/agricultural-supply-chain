package com.agricsc.model;

/**
 * Maps one row of the `order_items` table: one product line inside an order.
 * The price is captured at purchase time (unit_price) so later product price
 * changes do not alter historical orders.
 */
public class OrderItem {

    private int itemId;
    private int orderId;
    private int productId;
    private int quantity;
    private double unitPrice;
    private double subtotal;

    public OrderItem() {
    }

    public OrderItem(int productId, int quantity, double unitPrice) {
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = quantity * unitPrice;
    }

    public int getItemId() { return itemId; }
    public void setItemId(int itemId) { this.itemId = itemId; }

    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }

    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }

    @Override
    public String toString() {
        return "OrderItem{product=" + productId + ", qty=" + quantity
                + ", subtotal=" + subtotal + '}';
    }
}

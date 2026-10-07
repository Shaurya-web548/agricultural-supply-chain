package com.agricsc.exceptions;

/**
 * Thrown when an order asks for more units than the inventory holds.
 * Carries the requested and available quantities so the UI can display
 * a helpful message ("requested 50, only 30 available").
 */
public class InsufficientStockException extends Exception {

    private static final long serialVersionUID = 1L;

    private final int productId;
    private final int requested;
    private final int available;

    public InsufficientStockException(int productId, int requested, int available) {
        super("Insufficient stock for product " + productId
                + ": requested " + requested + ", available " + available);
        this.productId = productId;
        this.requested = requested;
        this.available = available;
    }

    public int getProductId() { return productId; }
    public int getRequested() { return requested; }
    public int getAvailable() { return available; }
}

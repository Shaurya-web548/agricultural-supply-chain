package com.agricsc.exceptions;

/**
 * Thrown when a requested product does not exist or has been deactivated.
 */
public class ProductNotFoundException extends Exception {

    private static final long serialVersionUID = 1L;

    private final int productId;

    public ProductNotFoundException(String message) {
        super(message);
        this.productId = -1;
    }

    public ProductNotFoundException(int productId) {
        super("Product not found: id=" + productId);
        this.productId = productId;
    }

    public int getProductId() {
        return productId;
    }
}

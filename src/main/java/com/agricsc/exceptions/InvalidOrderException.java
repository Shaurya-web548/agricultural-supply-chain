package com.agricsc.exceptions;

/**
 * Thrown when an order payload is invalid: empty item list, quantity <= 0,
 * unknown customer, negative total, cancelled order, etc.
 */
public class InvalidOrderException extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidOrderException(String message) {
        super(message);
    }

    public InvalidOrderException(String message, Throwable cause) {
        super(message, cause);
    }
}

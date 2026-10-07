package com.agricsc.exceptions;

/**
 * Thrown when login credentials do not match any user record.
 * Custom checked exception so the UI can show a clean message
 * instead of a stack trace.
 */
public class InvalidLoginException extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidLoginException(String message) {
        super(message);
    }
}

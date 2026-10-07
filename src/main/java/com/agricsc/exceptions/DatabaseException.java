package com.agricsc.exceptions;

/**
 * Wraps every low-level JDBC failure (SQLException / misconfiguration) in one
 * checked exception so upper layers never deal with raw SQLException objects.
 *
 * Checked (extends Exception) so the compiler forces callers to handle it.
 */
public class DatabaseException extends Exception {

    private static final long serialVersionUID = 1L;

    public DatabaseException(String message) {
        super(message);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}

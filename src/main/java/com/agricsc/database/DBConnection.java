package com.agricsc.database;

import com.agricsc.exceptions.DatabaseException;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Single, central place that knows HOW to connect to MySQL.
 *
 * Architecture role (top -> bottom):
 *   Applet UI -> Service -> DAO -> [DBConnection] -> JDBC driver -> MySQL
 *
 * Configuration is loaded ONCE from db.properties on the classpath
 * (src/main/resources/db.properties, which is git-ignored). Values can be
 * overridden with JVM flags: -Ddb.url=... -Ddb.username=... -Ddb.password=...
 * Credentials are therefore never hardcoded or duplicated in other classes.
 *
 * Each caller receives a fresh Connection and is responsible for closing it -
 * DAO methods do this with try-with-resources.
 */
public final class DBConnection {

    private static final String CONFIG_RESOURCE = "/db.properties";
    private static final String DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";
    private static final Properties CONFIG = new Properties();

    static {
        // Runs exactly once, when this class is first used.
        try (InputStream in = DBConnection.class.getResourceAsStream(CONFIG_RESOURCE)) {
            if (in == null) {
                throw new DatabaseException(
                        "db.properties not found on classpath (expected " + CONFIG_RESOURCE + ")");
            }
            CONFIG.load(in);
        } catch (IOException | DatabaseException e) {
            // Fail fast with a clear message; stack trace keeps the root cause.
            throw new ExceptionInInitializerError(e);
        }
        // JVM-flag overrides win over the file (useful for tests / demos).
        overrideFromSystemProperty("db.url");
        overrideFromSystemProperty("db.username");
        overrideFromSystemProperty("db.password");
        loadDriver();
    }

    /**
     * Registers the MySQL JDBC driver explicitly.
     *
     * JDBC 4 auto-loading only scans the system classpath. Inside
     * appletviewer the driver jar is loaded by the applet's class loader,
     * so without this Class.forName() DriverManager reports
     * "No suitable driver found".
     */
    private static void loadDriver() {
        try {
            Class.forName(DRIVER_CLASS);
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(
                    "MySQL driver " + DRIVER_CLASS + " not on classpath (mysql-connector-j jar)");
        }
    }

    private DBConnection() {
        // utility class - no instances
    }

    private static void overrideFromSystemProperty(String key) {
        String value = System.getProperty(key);
        if (value != null && !value.trim().isEmpty()) {
            CONFIG.setProperty(key, value);
        }
    }

    /**
     * Opens a new database connection.
     *
     * @throws DatabaseException if configuration is missing or the driver
     *                           cannot reach the server (wraps SQLException)
     */
    public static Connection getConnection() throws DatabaseException {
        String url = CONFIG.getProperty("db.url");
        String user = CONFIG.getProperty("db.username");
        String password = CONFIG.getProperty("db.password");
        if (url == null || user == null || password == null) {
            throw new DatabaseException(
                    "Incomplete database configuration: db.url / db.username / db.password required");
        }
        try {
            // Driver was registered in loadDriver(); never print credentials.
            return DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            throw new DatabaseException(
                    "Could not connect to the database. Is MySQL running "
                            + "and the database 'agricultural_supply_chain' created?", e);
        }
    }

    /**
     * Opens a connection with explicit parameters.
     * Used by tests to demonstrate SQLException -> DatabaseException wrapping
     * with an intentionally unreachable server; normal code calls the
     * no-argument getConnection() above.
     */
    public static Connection openConnection(String url, String user, String password)
            throws DatabaseException {
        try {
            return DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            // URL contains no credentials (they are passed separately).
            throw new DatabaseException("Could not connect to " + url, e);
        }
    }

    /**
     * Small health check used by tests/startup diagnostics.
     *
     * @throws DatabaseException when the connection cannot be established
     */
    public static void verifyConnection() throws DatabaseException {
        try (Connection con = getConnection()) {
            if (!con.isValid(5)) {
                throw new DatabaseException("Connection reported as invalid");
            }
        } catch (SQLException e) {
            throw new DatabaseException("Connection verification failed", e);
        }
    }
}

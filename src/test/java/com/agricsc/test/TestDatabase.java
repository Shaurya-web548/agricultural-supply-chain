package com.agricsc.test;

import com.agricsc.database.DBConnection;

import java.sql.Connection;
import java.sql.Statement;

/**
 * Shared test helper: empties every table of agricultural_supply_chain_test
 * so each test starts from a clean slate.
 */
final class TestDatabase {

    /** Child tables first; FK checks are off anyway, the order is for readers. */
    private static final String[] TABLES = {"payments", "shipments", "order_items", "orders",
            "inventory", "products", "customers", "distributors", "farmers", "users"};

    private TestDatabase() {
    }

    static void truncateAll() throws Exception {
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement()) {
            st.execute("SET FOREIGN_KEY_CHECKS = 0");
            for (String t : TABLES) {
                st.execute("TRUNCATE TABLE " + t);   // fixed table names - not user input
            }
            st.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }
}

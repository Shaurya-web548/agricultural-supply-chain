package com.agricsc.model;

import java.sql.Timestamp;

/**
 * Maps one row of the `users` table.
 *
 * Every role (admin/farmer/distributor/customer) has exactly one row here;
 * role-specific data lives in the farmers/distributors/customers tables.
 * This demonstrates OOP: a plain, focused class holding data only - all
 * logic lives in the service layer.
 */
public class User {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_FARMER = "FARMER";
    public static final String ROLE_DISTRIBUTOR = "DISTRIBUTOR";
    public static final String ROLE_CUSTOMER = "CUSTOMER";

    private int userId;
    private String username;
    private String password;   // SHA-256 hash, never logged or returned to the UI
    private String fullName;
    private String email;
    private String phone;
    private String role;
    private Timestamp createdAt;

    public User() {
        // required no-arg constructor for JDBC row mapping
    }

    public User(String username, String password, String fullName,
                String email, String phone, String role) {
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.role = role;
    }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "User{id=" + userId + ", username='" + username + '\''
                + ", fullName='" + fullName + '\'' + ", role='" + role + '\'' + '}';
    }
}

package com.agricsc.model;

import java.sql.Timestamp;

/**
 * Maps one row of the `customers` table.
 * Relationship: exactly one customers row per users row (1:1);
 *               one customer can place many orders (1:N).
 */
public class Customer {

    private int customerId;
    private int userId;
    private String address;
    private String city;
    private String pincode;
    private Timestamp createdAt;

    public Customer() {
    }

    public Customer(int userId, String address, String city, String pincode) {
        this.userId = userId;
        this.address = address;
        this.city = city;
        this.pincode = pincode;
    }

    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "Customer{id=" + customerId + ", city='" + city + '\'' + '}';
    }
}

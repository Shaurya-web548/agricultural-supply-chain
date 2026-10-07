package com.agricsc.model;

import java.sql.Timestamp;

/**
 * Maps one row of the `farmers` table.
 * Relationship: exactly one farmers row per users row (1:1, user_id UNIQUE FK).
 */
public class Farmer {

    private int farmerId;
    private int userId;
    private String farmName;
    private String farmLocation;
    private String registrationNumber;
    private Timestamp createdAt;

    public Farmer() {
    }

    public Farmer(int userId, String farmName, String farmLocation,
                  String registrationNumber) {
        this.userId = userId;
        this.farmName = farmName;
        this.farmLocation = farmLocation;
        this.registrationNumber = registrationNumber;
    }

    public int getFarmerId() { return farmerId; }
    public void setFarmerId(int farmerId) { this.farmerId = farmerId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getFarmName() { return farmName; }
    public void setFarmName(String farmName) { this.farmName = farmName; }

    public String getFarmLocation() { return farmLocation; }
    public void setFarmLocation(String farmLocation) { this.farmLocation = farmLocation; }

    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "Farmer{id=" + farmerId + ", farmName='" + farmName + '\''
                + ", location='" + farmLocation + '\'' + '}';
    }
}

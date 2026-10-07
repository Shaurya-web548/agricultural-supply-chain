package com.agricsc.model;

import java.sql.Timestamp;

/**
 * Maps one row of the `distributors` table.
 * Relationship: exactly one distributors row per users row (1:1, user_id UNIQUE FK).
 */
public class Distributor {

    private int distributorId;
    private int userId;
    private String companyName;
    private String warehouseLocation;
    private String licenseNumber;
    private Timestamp createdAt;

    public Distributor() {
    }

    public Distributor(int userId, String companyName, String warehouseLocation,
                       String licenseNumber) {
        this.userId = userId;
        this.companyName = companyName;
        this.warehouseLocation = warehouseLocation;
        this.licenseNumber = licenseNumber;
    }

    public int getDistributorId() { return distributorId; }
    public void setDistributorId(int distributorId) { this.distributorId = distributorId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getWarehouseLocation() { return warehouseLocation; }
    public void setWarehouseLocation(String warehouseLocation) { this.warehouseLocation = warehouseLocation; }

    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "Distributor{id=" + distributorId + ", company='" + companyName + '\''
                + ", warehouse='" + warehouseLocation + '\'' + '}';
    }
}

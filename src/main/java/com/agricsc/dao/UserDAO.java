package com.agricsc.dao;

import com.agricsc.database.DBConnection;
import com.agricsc.exceptions.DatabaseException;
import com.agricsc.model.Customer;
import com.agricsc.model.Distributor;
import com.agricsc.model.Farmer;
import com.agricsc.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * All SQL touching the users / farmers / distributors / customers tables.
 * The UI never sees these queries - it only calls the service layer.
 *
 * Every method: uses PreparedStatement (SQL-injection safe), opens/closes
 * its own resources with try-with-resources, and converts SQLException into
 * the custom checked DatabaseException.
 */
public class UserDAO {

    // ---------------------------------------------------------------- users

    /** Inserts a user row and returns the generated user_id. */
    public int insertUser(User user) throws DatabaseException {
        String sql = "INSERT INTO users (username, password, full_name, email, phone, role) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getFullName());
            ps.setString(4, user.getEmail());
            ps.setString(5, user.getPhone());
            ps.setString(6, user.getRole());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            throw new DatabaseException("Insert succeeded but no generated key returned");
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
                throw new DatabaseException(
                        "Username or email already exists: " + e.getMessage(), e);
            }
            throw new DatabaseException("Failed to insert user: " + e.getMessage(), e);
        }
    }

    /** Returns the user with this username, or null if none exists. */
    public User findByUsername(String username) throws DatabaseException {
        String sql = "SELECT user_id, username, password, full_name, email, phone, role, created_at "
                + "FROM users WHERE username = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapUser(rs) : null;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to look up user '" + username + "'", e);
        }
    }

    /** Users filtered by role - used by the admin panel lists. */
    public List<User> findByRole(String role) throws DatabaseException {
        String sql = "SELECT user_id, username, password, full_name, email, phone, role, created_at "
                + "FROM users WHERE role = ? ORDER BY user_id";
        List<User> result = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, role);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapUser(rs));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to list users with role " + role, e);
        }
        return result;
    }

    // ------------------------------------------------------------- farmers

    public int insertFarmer(Farmer farmer) throws DatabaseException {
        String sql = "INSERT INTO farmers (user_id, farm_name, farm_location, registration_number) "
                + "VALUES (?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, farmer.getUserId());
            ps.setString(2, farmer.getFarmName());
            ps.setString(3, farmer.getFarmLocation());
            ps.setString(4, farmer.getRegistrationNumber());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            throw new DatabaseException("Farmer insert returned no key");
        } catch (SQLException e) {
            throw new DatabaseException("Failed to insert farmer: " + e.getMessage(), e);
        }
    }

    public Farmer findFarmerByUserId(int userId) throws DatabaseException {
        String sql = "SELECT farmer_id, user_id, farm_name, farm_location, "
                + "registration_number, created_at FROM farmers WHERE user_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Farmer f = new Farmer();
                f.setFarmerId(rs.getInt("farmer_id"));
                f.setUserId(rs.getInt("user_id"));
                f.setFarmName(rs.getString("farm_name"));
                f.setFarmLocation(rs.getString("farm_location"));
                f.setRegistrationNumber(rs.getString("registration_number"));
                f.setCreatedAt(rs.getTimestamp("created_at"));
                return f;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to look up farmer for user " + userId, e);
        }
    }

    // --------------------------------------------------------- distributors

    public int insertDistributor(Distributor d) throws DatabaseException {
        String sql = "INSERT INTO distributors (user_id, company_name, warehouse_location, license_number) "
                + "VALUES (?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, d.getUserId());
            ps.setString(2, d.getCompanyName());
            ps.setString(3, d.getWarehouseLocation());
            ps.setString(4, d.getLicenseNumber());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            throw new DatabaseException("Distributor insert returned no key");
        } catch (SQLException e) {
            throw new DatabaseException("Failed to insert distributor: " + e.getMessage(), e);
        }
    }

    public Distributor findDistributorByUserId(int userId) throws DatabaseException {
        String sql = "SELECT distributor_id, user_id, company_name, warehouse_location, "
                + "license_number, created_at FROM distributors WHERE user_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Distributor d = new Distributor();
                d.setDistributorId(rs.getInt("distributor_id"));
                d.setUserId(rs.getInt("user_id"));
                d.setCompanyName(rs.getString("company_name"));
                d.setWarehouseLocation(rs.getString("warehouse_location"));
                d.setLicenseNumber(rs.getString("license_number"));
                d.setCreatedAt(rs.getTimestamp("created_at"));
                return d;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to look up distributor for user " + userId, e);
        }
    }

    // ----------------------------------------------------------- customers

    public int insertCustomer(Customer c) throws DatabaseException {
        String sql = "INSERT INTO customers (user_id, address, city, pincode) VALUES (?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, c.getUserId());
            ps.setString(2, c.getAddress());
            ps.setString(3, c.getCity());
            ps.setString(4, c.getPincode());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            throw new DatabaseException("Customer insert returned no key");
        } catch (SQLException e) {
            throw new DatabaseException("Failed to insert customer: " + e.getMessage(), e);
        }
    }

    public Customer findCustomerByUserId(int userId) throws DatabaseException {
        String sql = "SELECT customer_id, user_id, address, city, pincode, created_at "
                + "FROM customers WHERE user_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Customer c = new Customer();
                c.setCustomerId(rs.getInt("customer_id"));
                c.setUserId(rs.getInt("user_id"));
                c.setAddress(rs.getString("address"));
                c.setCity(rs.getString("city"));
                c.setPincode(rs.getString("pincode"));
                c.setCreatedAt(rs.getTimestamp("created_at"));
                return c;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to look up customer for user " + userId, e);
        }
    }

    // -------------------------------------------------------------- helper

    private User mapUser(ResultSet rs) throws SQLException {
        User u = new User();
        u.setUserId(rs.getInt("user_id"));
        u.setUsername(rs.getString("username"));
        u.setPassword(rs.getString("password"));
        u.setFullName(rs.getString("full_name"));
        u.setEmail(rs.getString("email"));
        u.setPhone(rs.getString("phone"));
        u.setRole(rs.getString("role"));
        u.setCreatedAt(rs.getTimestamp("created_at"));
        return u;
    }
}

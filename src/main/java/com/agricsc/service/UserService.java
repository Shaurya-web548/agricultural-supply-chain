package com.agricsc.service;

import com.agricsc.dao.UserDAO;
import com.agricsc.exceptions.DatabaseException;
import com.agricsc.exceptions.InvalidLoginException;
import com.agricsc.model.Customer;
import com.agricsc.model.Distributor;
import com.agricsc.model.Farmer;
import com.agricsc.model.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Business logic for authentication and registration.
 *
 * The Applet UI calls ONLY service classes - services validate input,
 * apply business rules, and delegate actual SQL to the DAO layer.
 *
 * EXCEPTION HANDLING demo:
 *  - login() THROWS the custom checked InvalidLoginException
 *  - every DAO call is declared `throws DatabaseException`
 *  - validation uses IllegalArgumentException for bad input
 */
public class UserService {

    private final UserDAO userDAO = new UserDAO();

    /**
     * SHA-256 hash. Identical result to MySQL's SHA2('text',256), which is
     * how the seed data passwords were produced, so logins work for both.
     */
    public static String hashPassword(String plain) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(plain.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 exists on every Java platform - should never happen.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Validates credentials.
     *
     * @throws InvalidLoginException when username/password is empty or wrong
     * @throws DatabaseException      when the lookup itself fails
     */
    public User login(String username, String password)
            throws InvalidLoginException, DatabaseException {
        if (username == null || username.trim().isEmpty()) {
            throw new InvalidLoginException("Username must not be empty");
        }
        if (password == null || password.isEmpty()) {
            throw new InvalidLoginException("Password must not be empty");
        }

        User user = userDAO.findByUsername(username.trim());
        // Never reveal WHICH part was wrong - same message for both cases.
        if (user == null || !user.getPassword().equals(hashPassword(password))) {
            throw new InvalidLoginException("Invalid username or password");
        }
        return user;
    }

    // --------------------------------------------------------- registration

    /** Registers a new customer (user row + customers row). */
    public User registerCustomer(String username, String password, String fullName,
                                 String email, String phone, String address,
                                 String city, String pincode)
            throws IllegalArgumentException, DatabaseException {
        validateCommon(username, password, fullName, email);
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException("Address is required");
        }
        User user = new User(username.trim(), hashPassword(password), fullName.trim(),
                email.trim(), phone, User.ROLE_CUSTOMER);
        int userId = userDAO.insertUser(user);
        user.setUserId(userId);
        userDAO.insertCustomer(new Customer(userId, address.trim(), city, pincode));
        return user;
    }

    /** Registers a new farmer (user row + farmers row). */
    public User registerFarmer(String username, String password, String fullName,
                               String email, String phone, String farmName,
                               String farmLocation, String regNumber)
            throws IllegalArgumentException, DatabaseException {
        validateCommon(username, password, fullName, email);
        if (farmName == null || farmName.trim().isEmpty()) {
            throw new IllegalArgumentException("Farm name is required");
        }
        User user = new User(username.trim(), hashPassword(password), fullName.trim(),
                email.trim(), phone, User.ROLE_FARMER);
        int userId = userDAO.insertUser(user);
        user.setUserId(userId);
        userDAO.insertFarmer(new Farmer(userId, farmName.trim(), farmLocation, regNumber));
        return user;
    }

    /** Registers a new distributor (user row + distributors row). */
    public User registerDistributor(String username, String password, String fullName,
                                    String email, String phone, String companyName,
                                    String warehouse, String license)
            throws IllegalArgumentException, DatabaseException {
        validateCommon(username, password, fullName, email);
        if (companyName == null || companyName.trim().isEmpty()) {
            throw new IllegalArgumentException("Company name is required");
        }
        User user = new User(username.trim(), hashPassword(password), fullName.trim(),
                email.trim(), phone, User.ROLE_DISTRIBUTOR);
        int userId = userDAO.insertUser(user);
        user.setUserId(userId);
        userDAO.insertDistributor(
                new Distributor(userId, companyName.trim(), warehouse, license));

        // The orders table requires customer_id, so every distributor also
        // gets a buyer-account row; distributor purchases use it.
        userDAO.insertCustomer(new Customer(userId,
                warehouse == null ? "Warehouse" : warehouse.trim(),
                companyName.trim(), "000000"));
        return user;
    }

    // ---------------------------------------------------------- profile I/O

    public Farmer getFarmerProfile(int userId) throws DatabaseException {
        return userDAO.findFarmerByUserId(userId);
    }

    public Distributor getDistributorProfile(int userId) throws DatabaseException {
        return userDAO.findDistributorByUserId(userId);
    }

    public Customer getCustomerProfile(int userId) throws DatabaseException {
        return userDAO.findCustomerByUserId(userId);
    }

    /** Admin panel: list users of one role. */
    public List<User> listUsersByRole(String role) throws DatabaseException {
        return userDAO.findByRole(role);
    }

    // ----------------------------------------------------------- validation

    private void validateCommon(String username, String password,
                                String fullName, String email) {
        if (username == null || username.trim().length() < 3) {
            throw new IllegalArgumentException("Username must be at least 3 characters");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new IllegalArgumentException("Full name is required");
        }
        if (email == null || !email.contains("@") || !email.contains(".")) {
            throw new IllegalArgumentException("Valid email is required");
        }
    }
}

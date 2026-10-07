package com.agricsc.test;

import com.agricsc.database.DBConnection;
import com.agricsc.exceptions.DatabaseException;
import com.agricsc.exceptions.InsufficientStockException;
import com.agricsc.exceptions.InvalidLoginException;
import com.agricsc.exceptions.InvalidOrderException;
import com.agricsc.exceptions.ProductNotFoundException;
import com.agricsc.model.Customer;
import com.agricsc.model.Order;
import com.agricsc.model.Product;
import com.agricsc.model.Shipment;
import com.agricsc.model.User;
import com.agricsc.service.InventoryService;
import com.agricsc.service.OrderService;
import com.agricsc.service.ProductService;
import com.agricsc.service.ShipmentService;
import com.agricsc.service.UserService;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The 15 required test cases for the project.
 *
 * Runs against agricultural_supply_chain_test (see
 * src/test/resources/db.properties) so the demo data in the main database
 * is never touched. Every test starts from a clean slate: @Before truncates
 * all tables and re-creates one farmer, one distributor, one customer and
 * two products through the real service layer.
 */
public class SupplyChainTest {

    private final UserService userService = new UserService();
    private final ProductService productService = new ProductService();
    private final InventoryService inventoryService = new InventoryService();
    private final OrderService orderService = new OrderService();
    private final ShipmentService shipmentService = new ShipmentService();

    private Customer customer;
    private Customer distributorBuyerAccount;
    private int farmerId;
    private int distributorId;
    private int productId;

    // ------------------------------------------------------------- fixtures

    @Before
    public void resetAndSeed() throws Exception {
        truncateAll();
        User farmer = userService.registerFarmer("tfarmer", "farm123", "Test Farmer",
                "tfarmer@test.in", "9111111111", "Test Farm", "Nashik", "FREG1");
        User dist = userService.registerDistributor("tdist", "dist123", "Test Distributor",
                "tdist@test.in", "9222222222", "Test Traders", "Pune", "LIC1");
        User cust = userService.registerCustomer("tcust", "cust123", "Test Customer",
                "tcust@test.in", "9333333333", "12 Test Road", "Mumbai", "400001");

        farmerId = userService.getFarmerProfile(farmer.getUserId()).getFarmerId();
        distributorId = userService.getDistributorProfile(dist.getUserId()).getDistributorId();
        customer = userService.getCustomerProfile(cust.getUserId());
        distributorBuyerAccount = userService.getCustomerProfile(dist.getUserId());

        Product p = productService.addProduct(farmerId, "Wheat", "Grains", "kg",
                25.0, "Test wheat", 100);
        productId = p.getProductId();
    }

    /** Wipes every table with FK checks off (agri_app owns this schema). */
    private void truncateAll() throws Exception {
        TestDatabase.truncateAll();
    }

    /**
     * The order pipeline runs in OrderProcessingThread - tests wait until
     * the status settles on CONFIRMED or CANCELLED (max 5 seconds).
     */
    private String waitUntilSettled(int orderId) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            String status = orderService.getOrder(orderId).getStatus();
            if (Order.STATUS_CONFIRMED.equals(status)
                    || Order.STATUS_CANCELLED.equals(status)
                    || Order.STATUS_DELIVERED.equals(status)) {
                return status;
            }
            Thread.sleep(100);
        }
        return orderService.getOrder(orderId).getStatus();
    }

    private HashMap<Integer, Integer> qty(int productId, int quantity) {
        HashMap<Integer, Integer> items = new HashMap<>();
        items.put(productId, quantity);
        return items;
    }

    // ------------------------------------------- 1-3: auth test cases

    /** Test 1: user registration persists both user and role rows. */
    @Test
    public void test01_userRegistration() throws Exception {
        User u = userService.registerCustomer("newcust", "secret1", "New Customer",
                "new@mail.in", "9444444444", "Addr", "City", "123456");
        assertTrue("user id must be generated", u.getUserId() > 0);

        Customer saved = userService.getCustomerProfile(u.getUserId());
        assertNotNull("customers row must exist", saved);
        assertEquals("City", saved.getCity());
    }

    /** Test 2: correct credentials return the user. */
    @Test
    public void test02_loginSuccess() throws Exception {
        User u = userService.login("tcust", "cust123");
        assertEquals("tcust", u.getUsername());
        assertEquals(User.ROLE_CUSTOMER, u.getRole());
    }

    /** Test 3: wrong password raises the custom InvalidLoginException. */
    @Test
    public void test03_invalidLogin() {
        try {
            userService.login("tcust", "wrong-password");
            fail("expected InvalidLoginException");
        } catch (InvalidLoginException expected) {
            assertTrue(expected.getMessage().contains("Invalid username or password"));
        } catch (Exception other) {
            fail("wrong exception type: " + other);
        }
    }

    // -------------------------------------- 4-7: product test cases

    /** Test 4: adding a product also creates its inventory row. */
    @Test
    public void test04_addProduct() throws Exception {
        Product p = productService.addProduct(farmerId, "Rice", "Grains", "kg",
                42.0, "Basmati", 50);
        assertTrue(p.getProductId() > 0);
        assertEquals(50, inventoryService.getQuantity(p.getProductId()));
    }

    /** Test 5: updated price is read back from the database. */
    @Test
    public void test05_updateProduct() throws Exception {
        Product p = productService.getProduct(productId);
        p.setPrice(30.5);
        p.setCategory("Cereals");
        productService.updateProduct(p);

        Product reloaded = productService.getProduct(productId);
        assertEquals(30.5, reloaded.getPrice(), 0.001);
        assertEquals("Cereals", reloaded.getCategory());
    }

    /** Test 6: deleted product can no longer be found. */
    @Test
    public void test06_deleteProduct() throws Exception {
        productService.deleteProduct(productId);
        try {
            productService.getProduct(productId);
            fail("expected ProductNotFoundException");
        } catch (ProductNotFoundException expected) {
            assertEquals(productId, expected.getProductId());
        }
        // inventory row must be gone too (ON DELETE CASCADE)
        assertEquals(-1, inventoryService.getQuantity(productId));
    }

    /** Test 7: keyword search returns matches and blocks SQL injection. */
    @Test
    public void test07_productSearch() throws Exception {
        productService.addProduct(farmerId, "Barley", "Grains", "kg", 20.0, "", 10);

        List<Product> hits = productService.search("wheat", null, null);
        assertEquals(1, hits.size());
        assertEquals("Wheat", hits.get(0).getProductName());

        // Classic injection payload must match NOTHING instead of everything.
        List<Product> injected = productService.search("' OR '1'='1", null, null);
        assertTrue("SQLi payload should return no rows", injected.isEmpty());

        // max price filter
        List<Product> cheap = productService.search(null, null, 22.0);
        assertEquals(1, cheap.size());
        assertEquals("Barley", cheap.get(0).getProductName());
    }

    // ------------------------------------ 8-9: order + stock test cases

    /** Test 8: place order -> background thread confirms + cuts stock. */
    @Test
    public void test08_placeOrderProcessesInBackground() throws Exception {
        Order order = orderService.placeOrder(customer, qty(productId, 5));
        assertTrue(order.getOrderId() > 0);
        assertEquals(Order.STATUS_PENDING, order.getStatus());

        String settled = waitUntilSettled(order.getOrderId());
        assertEquals(Order.STATUS_CONFIRMED, settled);
        assertEquals("stock reduced by 5", 95, inventoryService.getQuantity(productId));
        assertNotNull("shipment must be created for the confirmed order",
                shipmentService.getShipmentForOrder(order.getOrderId()));
    }

    /** Test 9: ordering more than available cancels the order, stock intact. */
    @Test
    public void test09_insufficientInventory() throws Exception {
        Order order = orderService.placeOrder(customer, qty(productId, 1000));
        String settled = waitUntilSettled(order.getOrderId());

        assertEquals(Order.STATUS_CANCELLED, settled);
        assertEquals("stock must stay untouched", 100,
                inventoryService.getQuantity(productId));
    }

    // ------------------------------- 10-11: inventory + order flow cases

    /** Test 10: restock adds units, setQuantity replaces them, negative rejected. */
    @Test
    public void test10_inventoryUpdate() throws Exception {
        inventoryService.restock(productId, 50);
        assertEquals(150, inventoryService.getQuantity(productId));

        inventoryService.setQuantity(productId, 20);
        assertEquals(20, inventoryService.getQuantity(productId));

        try {
            inventoryService.setQuantity(productId, -1);
            fail("negative quantity must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("negative"));
        }
    }

    /** Test 11: full status workflow PENDING..CONFIRMED -> SHIPPED -> DELIVERED. */
    @Test
    public void test11_orderProcessingWorkflow() throws Exception {
        Order order = orderService.placeOrder(customer, qty(productId, 5));
        assertEquals(Order.STATUS_CONFIRMED, waitUntilSettled(order.getOrderId()));

        orderService.advanceOrderStatus(order.getOrderId());
        assertEquals(Order.STATUS_SHIPPED, orderService.getOrder(order.getOrderId()).getStatus());

        orderService.advanceOrderStatus(order.getOrderId());
        assertEquals(Order.STATUS_DELIVERED, orderService.getOrder(order.getOrderId()).getStatus());

        // A finished order cannot move again - custom exception expected.
        try {
            orderService.advanceOrderStatus(order.getOrderId());
            fail("expected InvalidOrderException");
        } catch (InvalidOrderException expected) {
            assertTrue(expected.getMessage().contains("DELIVERED"));
        }
    }

    // ------------------------------------ 12-13: shipment test cases

    /** Test 12: confirmed order gets exactly one shipment with a tracking number. */
    @Test
    public void test12_shipmentCreation() throws Exception {
        Order order = orderService.placeOrder(customer, qty(productId, 3));
        assertEquals(Order.STATUS_CONFIRMED, waitUntilSettled(order.getOrderId()));

        Shipment shipment = shipmentService.getShipmentForOrder(order.getOrderId());
        assertNotNull(shipment);
        assertTrue(shipment.getTrackingNumber().startsWith("TRK-"));
        assertEquals(Shipment.STATUS_PENDING, shipment.getStatus());
    }

    /** Test 13: tracking number lookup returns the same shipment. */
    @Test
    public void test13_shipmentTracking() throws Exception {
        Order order = orderService.placeOrder(customer, qty(productId, 2));
        assertEquals(Order.STATUS_CONFIRMED, waitUntilSettled(order.getOrderId()));

        Shipment created = shipmentService.getShipmentForOrder(order.getOrderId());
        Shipment tracked = shipmentService.track(created.getTrackingNumber());
        assertNotNull(tracked);
        assertEquals(created.getShipmentId(), tracked.getShipmentId());
        assertEquals(order.getOrderId(), tracked.getOrderId());

        assertNull("unknown tracking number must return null",
                shipmentService.track("TRK-DOES-NOT-EXIST"));
    }

    // -------------------------------- 14-15: error handling test cases

    /** Test 14: invalid input is rejected before any SQL runs. */
    @Test
    public void test14_invalidInput() throws Exception {
        try {
            userService.registerCustomer("ab", "12345", "X", "bad-email", null,
                    "Addr", "City", "111111");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }

        try {
            orderService.placeOrder(customer, new HashMap<Integer, Integer>());
            fail("expected InvalidOrderException for empty order");
        } catch (InvalidOrderException expected) {
            assertTrue(expected.getMessage().contains("at least one item"));
        }

        try {
            productService.addProduct(farmerId, "", "Grains", "kg", 10, "", 1);
            fail("expected IllegalArgumentException for blank name");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    /** Test 15: unreachable server -> SQLException wrapped in DatabaseException. */
    @Test
    public void test15_databaseFailure() {
        try (Connection con = DBConnection.openConnection(
                "jdbc:mysql://127.0.0.1:1/unreachable?useSSL=false&connectTimeout=1000",
                "nobody", "nothing")) {
            fail("connection to a dead port must not succeed");
        } catch (DatabaseException expected) {
            // This is exactly what every DAO method throws to its caller.
            assertNotNull(expected.getMessage());
            assertNotNull("original SQLException must be kept as cause", expected.getCause());
        } catch (Exception other) {
            fail("wrong exception type: " + other);
        }
    }
}

package com.agricsc.test;

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
import com.agricsc.threads.InventoryUpdateThread;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Extra tests beyond the 15 required ones: concurrency (no overselling),
 * payment lifecycle, shipment -> order status sync, safe product deletion
 * and farmer ownership rules.
 */
public class OrderIntegrityTest {

    private static final int START_STOCK = 100;
    private static final int MAX_WAIT_MILLIS = 10000;

    private final UserService userService = new UserService();
    private final ProductService productService = new ProductService();
    private final InventoryService inventoryService = new InventoryService();
    private final OrderService orderService = new OrderService();
    private final ShipmentService shipmentService = new ShipmentService();

    private Customer customer;
    private int farmerId;
    private int productId;

    @Before
    public void resetAndSeed() throws Exception {
        TestDatabase.truncateAll();
        User farmer = userService.registerFarmer("ifarmer", "farm123", "Integrity Farmer",
                "ifarmer@test.in", "9111111111", "Integrity Farm", "Nashik", "IREG1");
        User cust = userService.registerCustomer("icust", "cust123", "Integrity Customer",
                "icust@test.in", "9333333333", "1 Test Lane", "Pune", "411001");
        farmerId = userService.getFarmerProfile(farmer.getUserId()).getFarmerId();
        customer = userService.getCustomerProfile(cust.getUserId());
        Product p = productService.addProduct(farmerId, "Rice", "Grains", "kg",
                40.0, "Integrity rice", START_STOCK);
        productId = p.getProductId();
    }

    private HashMap<Integer, Integer> qty(int quantity) {
        HashMap<Integer, Integer> items = new HashMap<>();
        items.put(productId, quantity);
        return items;
    }

    private String waitUntilSettled(int orderId) throws Exception {
        long deadline = System.currentTimeMillis() + MAX_WAIT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            String status = orderService.getOrder(orderId).getStatus();
            if (Order.STATUS_CONFIRMED.equals(status) || Order.STATUS_CANCELLED.equals(status)) {
                return status;
            }
            Thread.sleep(50);
        }
        return orderService.getOrder(orderId).getStatus();
    }

    private int placeAndConfirm(int quantity) throws Exception {
        Order order = orderService.placeOrder(customer, qty(quantity));
        assertEquals(Order.STATUS_CONFIRMED, waitUntilSettled(order.getOrderId()));
        return order.getOrderId();
    }

    /**
     * 10 customers order 15 units each at the same moment with only 100 in
     * stock. Exactly 6 orders can be fulfilled; the rest must be cancelled
     * and stock must end at exactly 100 - 6*15 = 10 (never negative).
     */
    @Test
    public void concurrentOrdersNeverOversell() throws Exception {
        final int buyers = 10;
        final int perOrder = 15;
        final CountDownLatch startGun = new CountDownLatch(1);
        final List<Integer> orderIds = Collections.synchronizedList(new ArrayList<Integer>());
        final List<Throwable> errors = Collections.synchronizedList(new ArrayList<Throwable>());

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            Thread t = new Thread(() -> {
                try {
                    startGun.await();   // all threads place their order together
                    orderIds.add(orderService.placeOrder(customer, qty(perOrder)).getOrderId());
                } catch (Exception e) {
                    errors.add(e);
                }
            });
            threads.add(t);
            t.start();
        }
        startGun.countDown();
        for (Thread t : threads) {
            t.join();
        }
        assertTrue("placing orders must not fail: " + errors, errors.isEmpty());

        int confirmed = 0;
        int cancelled = 0;
        for (int id : orderIds) {
            String status = waitUntilSettled(id);
            if (Order.STATUS_CONFIRMED.equals(status)) {
                confirmed++;
            } else if (Order.STATUS_CANCELLED.equals(status)) {
                cancelled++;
            }
        }
        assertEquals(6, confirmed);
        assertEquals(4, cancelled);
        assertEquals(START_STOCK - 6 * perOrder, inventoryService.getQuantity(productId));
    }

    /**
     * 5 InventoryUpdateThreads restock the same product at once. With the
     * synchronized InventoryDAO no update is lost: 100 + 5*20 = 200, and
     * every thread's callback fires.
     */
    @Test
    public void concurrentRestocksAreNotLost() throws Exception {
        final int threads = 5;
        final int perRestock = 20;
        final List<String> results = Collections.synchronizedList(new ArrayList<String>());
        List<InventoryUpdateThread> workers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            InventoryUpdateThread t = new InventoryUpdateThread(productId, perRestock,
                    InventoryUpdateThread.MODE_RESTOCK, inventoryService, results::add);
            workers.add(t);
            t.start();
        }
        for (InventoryUpdateThread t : workers) {
            t.join();
        }
        assertEquals(threads, results.size());
        for (String r : results) {
            assertTrue(r, r.startsWith("RESTOCKED"));
        }
        assertEquals(START_STOCK + threads * perRestock, inventoryService.getQuantity(productId));
    }

    /** Processing the same order a second time must not cut stock again. */
    @Test
    public void processingAnOrderTwiceIsANoOp() throws Exception {
        int orderId = placeAndConfirm(10);
        assertFalse(orderService.processOrder(orderId));
        assertEquals(START_STOCK - 10, inventoryService.getQuantity(productId));
    }

    /** Payment row: PENDING at placement, PAID on confirm, FAILED on cancel. */
    @Test
    public void paymentFollowsOrderOutcome() throws Exception {
        int confirmedId = placeAndConfirm(5);
        assertEquals(OrderService.PAYMENT_PAID, orderService.getPaymentStatus(confirmedId));

        Order tooBig = orderService.placeOrder(customer, qty(START_STOCK * 10));
        assertEquals(Order.STATUS_CANCELLED, waitUntilSettled(tooBig.getOrderId()));
        assertEquals(OrderService.PAYMENT_FAILED, orderService.getPaymentStatus(tooBig.getOrderId()));
    }

    /** Shipment progress moves the order to SHIPPED, then DELIVERED. */
    @Test
    public void shipmentProgressUpdatesOrderStatus() throws Exception {
        int orderId = placeAndConfirm(5);
        Shipment s = shipmentService.getShipmentForOrder(orderId);

        shipmentService.updateStatus(s.getShipmentId(), Shipment.STATUS_IN_TRANSIT, "Pune Hub");
        assertEquals(Order.STATUS_SHIPPED, orderService.getOrder(orderId).getStatus());

        shipmentService.updateStatus(s.getShipmentId(), Shipment.STATUS_DELIVERED, "Customer");
        assertEquals(Order.STATUS_DELIVERED, orderService.getOrder(orderId).getStatus());

        try {
            shipmentService.updateStatus(s.getShipmentId(), "LOST_AT_SEA", "?");
            fail("unknown shipment status must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("must be one of"));
        }
    }

    /** Deleting an ordered product deactivates it and keeps order history. */
    @Test
    public void deletingOrderedProductKeepsHistory() throws Exception {
        int orderId = placeAndConfirm(5);

        assertFalse("ordered product must be deactivated, not deleted",
                productService.deleteProduct(productId));
        assertEquals(Product.STATUS_INACTIVE, productService.getProduct(productId).getStatus());
        assertEquals(1, orderService.getOrder(orderId).getItems().size());

        try {
            orderService.placeOrder(customer, qty(1));
            fail("inactive product must not be orderable");
        } catch (ProductNotFoundException expected) {
            assertEquals(productId, expected.getProductId());
        }
    }

    /** A farmer cannot touch another farmer's products or orders. */
    @Test
    public void farmerCannotChangeAnotherFarmersData() throws Exception {
        int orderId = placeAndConfirm(5);
        User other = userService.registerFarmer("ofarmer", "farm123", "Other Farmer",
                "ofarmer@test.in", "9444444444", "Other Farm", "Karnal", "OREG1");
        int otherFarmerId = userService.getFarmerProfile(other.getUserId()).getFarmerId();

        assertTrue(orderService.getFarmerOrders(otherFarmerId).isEmpty());
        assertEquals(1, orderService.getFarmerOrders(farmerId).size());

        try {
            orderService.advanceFarmerOrderStatus(orderId, otherFarmerId);
            fail("expected InvalidOrderException");
        } catch (InvalidOrderException expected) {
            assertTrue(expected.getMessage().contains("does not contain"));
        }
        try {
            productService.requireOwnedBy(productId, otherFarmerId);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("another farmer"));
        }

        orderService.advanceFarmerOrderStatus(orderId, farmerId);   // owner may advance
        assertEquals(Order.STATUS_SHIPPED, orderService.getOrder(orderId).getStatus());
    }
}

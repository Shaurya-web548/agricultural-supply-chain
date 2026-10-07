package com.agricsc.service;

import com.agricsc.dao.OrderDAO;
import com.agricsc.exceptions.DatabaseException;
import com.agricsc.exceptions.InsufficientStockException;
import com.agricsc.exceptions.InvalidOrderException;
import com.agricsc.exceptions.ProductNotFoundException;
import com.agricsc.model.Customer;
import com.agricsc.model.Order;
import com.agricsc.model.OrderItem;
import com.agricsc.model.Product;
import com.agricsc.threads.OrderProcessingThread;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creates, validates and progresses orders - the heart of the supply chain.
 *
 * ORDER WORKFLOW (multithreaded):
 *   placeOrder()
 *     -> insert PENDING order + items (priced from a HashMap product cache)
 *     -> start OrderProcessingThread          <-- MULTITHREADING
 *          thread: PROCESSING -> check stock -> reduce stock
 *                -> CONFIRMED  -> create PENDING shipment
 *                (or CANCELLED when stock is insufficient)
 *
 * Tests can call processOrder() directly to run the same steps
 * synchronously - the thread's run() simply delegates to it.
 */
public class OrderService {

    private final OrderDAO orderDAO = new OrderDAO();
    private final InventoryService inventoryService = new InventoryService();
    private final ProductService productService = new ProductService();
    private final ShipmentService shipmentService = new ShipmentService();

    /** payments.payment_status values used by the order pipeline. */
    public static final String PAYMENT_PAID = "PAID";
    public static final String PAYMENT_FAILED = "FAILED";

    /** Allowed forward transitions of the order status machine. */
    private static final HashMap<String, String> NEXT_STATUS = new HashMap<>();
    static {
        NEXT_STATUS.put(Order.STATUS_PENDING, Order.STATUS_PROCESSING);
        NEXT_STATUS.put(Order.STATUS_PROCESSING, Order.STATUS_CONFIRMED);
        NEXT_STATUS.put(Order.STATUS_CONFIRMED, Order.STATUS_SHIPPED);
        NEXT_STATUS.put(Order.STATUS_SHIPPED, Order.STATUS_DELIVERED);
    }

    /**
     * Places a customer order and hands processing to a background thread.
     *
     * @param items productId -> quantity requested (Map interface, HashMap impl)
     * @return the persisted PENDING order (thread finishes it asynchronously)
     */
    public Order placeOrder(Customer customer, Map<Integer, Integer> items)
            throws InvalidOrderException, ProductNotFoundException, DatabaseException {
        if (customer == null) {
            throw new InvalidOrderException("Unknown customer");
        }
        if (items == null || items.isEmpty()) {
            throw new InvalidOrderException("Order must contain at least one item");
        }

        // HashMap cache: price every line with ONE bulk product query.
        HashMap<Integer, Product> catalog = productService.getProductMap();

        Order order = new Order(customer.getCustomerId(), 0.0, Order.STATUS_PENDING);
        double total = 0.0;
        for (Map.Entry<Integer, Integer> entry : items.entrySet()) {
            int productId = entry.getKey();
            int qty = entry.getValue() == null ? 0 : entry.getValue();
            if (qty <= 0) {
                throw new InvalidOrderException(
                        "Quantity must be positive for product " + productId);
            }
            Product product = catalog.get(productId);   // O(1) lookup
            if (product == null || !Product.STATUS_ACTIVE.equals(product.getStatus())) {
                throw new ProductNotFoundException(productId) ;
            }
            OrderItem item = new OrderItem(productId, qty, product.getPrice());
            order.addItem(item);
            total += item.getSubtotal();
        }
        order.setTotalAmount(total);

        // Persist header first (to get the id), then each line item.
        int orderId = orderDAO.insertOrder(order);
        order.setOrderId(orderId);
        for (OrderItem item : order.getItems()) {
            item.setOrderId(orderId);
            orderDAO.insertOrderItem(item);
        }
        orderDAO.updateTotal(orderId, total);
        orderDAO.insertPayment(orderId, total);   // simulated payment, PENDING

        // MULTITHREADING: stock check + shipment creation run in background.
        new OrderProcessingThread(orderId, this).start();
        return order;
    }

    /**
     * The pipeline executed by OrderProcessingThread (also callable
     * directly from tests). Steps are individually commented because this
     * exact flow is a viva question ("Explain the complete order workflow").
     *
     * @return true when the order was confirmed, false when nothing to do
     * @throws InsufficientStockException when any line cannot be fulfilled
     *                                    (order is cancelled by this method)
     * @throws DatabaseException          when a DB step fails (order is
     *                                    cancelled and stock given back)
     */
    public boolean processOrder(int orderId)
            throws InsufficientStockException, DatabaseException {
        // Step 1: claim the order PENDING -> PROCESSING in one conditional
        //         UPDATE. If another thread already claimed it, stop here,
        //         so the same order can never cut stock twice.
        if (!orderDAO.claimForProcessing(orderId)) {
            return false;   // already processed or does not exist
        }
        Order order = orderDAO.findById(orderId);

        // Step 2: reduce stock for every line, tracking what we took so we
        //         can undo it if a later step fails.
        List<OrderItem> alreadyTaken = new ArrayList<>();
        try {
            for (OrderItem item : order.getItems()) {
                boolean ok = inventoryService.takeFromStock(
                        item.getProductId(), item.getQuantity());
                if (!ok) {
                    int available = inventoryService.getQuantity(item.getProductId());
                    throw new InsufficientStockException(
                            item.getProductId(), item.getQuantity(), Math.max(available, 0));
                }
                alreadyTaken.add(item);
            }

            // Step 3: confirm, mark paid, create the shipment (1:1, PENDING).
            orderDAO.updateStatus(orderId, Order.STATUS_CONFIRMED);
            orderDAO.updatePaymentStatus(orderId, PAYMENT_PAID);
            shipmentService.createShipmentForOrder(orderId);
            return true;
        } catch (InsufficientStockException | DatabaseException failure) {
            // COMPENSATION for ANY failure: give back the stock we removed and
            // cancel the order, so nothing is left half-done in PROCESSING.
            try {
                compensate(orderId, alreadyTaken);
            } catch (DatabaseException undoFailure) {
                // Keep the original cause; attach the undo error to it.
                failure.addSuppressed(undoFailure);
            }
            throw failure;   // thread records the reason
        }
    }

    /** Undo for processOrder(): return stock, cancel order, fail payment. */
    private void compensate(int orderId, List<OrderItem> alreadyTaken)
            throws DatabaseException {
        for (OrderItem item : alreadyTaken) {
            inventoryService.returnToStock(item.getProductId(), item.getQuantity());
        }
        orderDAO.updateStatus(orderId, Order.STATUS_CANCELLED);
        orderDAO.updatePaymentStatus(orderId, PAYMENT_FAILED);
    }

    /** @return PENDING / PAID / FAILED, or null when the order has no payment */
    public String getPaymentStatus(int orderId) throws DatabaseException {
        return orderDAO.findPaymentStatus(orderId);
    }

    /**
     * Farmer/distributor/admin moves an order one step forward through
     * PENDING -> PROCESSING -> CONFIRMED -> SHIPPED -> DELIVERED.
     */
    public void advanceOrderStatus(int orderId)
            throws InvalidOrderException, DatabaseException {
        Order order = getOrder(orderId);
        String current = order.getStatus();
        if (Order.STATUS_CANCELLED.equals(current) || Order.STATUS_DELIVERED.equals(current)) {
            throw new InvalidOrderException(
                    "Order is already " + current + " and cannot move further");
        }
        String next = NEXT_STATUS.get(current);
        if (next == null) {
            throw new InvalidOrderException(
                    "No forward transition from status " + current);
        }
        orderDAO.updateStatus(orderId, next);
    }

    /** Cancels an order (stock refund is handled by the caller if needed). */
    public void cancelOrder(int orderId) throws DatabaseException {
        orderDAO.updateStatus(orderId, Order.STATUS_CANCELLED);
    }

    public Order getOrder(int orderId) throws InvalidOrderException, DatabaseException {
        Order order = orderDAO.findById(orderId);
        if (order == null) {
            throw new InvalidOrderException("Order " + orderId + " does not exist");
        }
        return order;
    }

    public List<Order> getCustomerOrders(int customerId) throws DatabaseException {
        return orderDAO.findByCustomer(customerId);
    }

    public List<Order> getAllOrders() throws DatabaseException {
        return orderDAO.findAll();
    }

    /** Farmer "view orders": only orders that contain this farmer's products. */
    public List<Order> getFarmerOrders(int farmerId) throws DatabaseException {
        return orderDAO.findByFarmer(farmerId);
    }

    /**
     * Farmer "update order status": same state machine as
     * advanceOrderStatus(), but only for orders containing this farmer's
     * products. A HashSet gives an O(1) "is this order mine?" check.
     */
    public void advanceFarmerOrderStatus(int orderId, int farmerId)
            throws InvalidOrderException, DatabaseException {
        Set<Integer> myOrderIds = new HashSet<>();
        for (Order o : orderDAO.findByFarmer(farmerId)) {
            myOrderIds.add(o.getOrderId());
        }
        if (!myOrderIds.contains(orderId)) {
            throw new InvalidOrderException(
                    "Order " + orderId + " does not contain any of your products");
        }
        advanceOrderStatus(orderId);
    }

    /**
     * Distributor wholesale purchase: same pipeline as placeOrder(), but the
     * order is additionally tagged with distributor_id. The orders table
     * requires a customer_id, so distributor registration also creates a
     * small "buyer account" customers row (see UserService).
     */
    public Order placeDistributorPurchase(Customer buyerAccount, int distributorId,
                                          Map<Integer, Integer> items)
            throws InvalidOrderException, ProductNotFoundException, DatabaseException {
        if (distributorId <= 0) {
            throw new InvalidOrderException("Invalid distributor");
        }
        Order order = placeOrder(buyerAccount, items);
        orderDAO.assignDistributor(order.getOrderId(), distributorId);
        order.setDistributorId(distributorId);
        return order;
    }

    /** Links an unassigned order to the distributor that will fulfil it. */
    public void assignDistributor(int orderId, int distributorId)
            throws InvalidOrderException, DatabaseException {
        if (distributorId <= 0) {
            throw new InvalidOrderException("Invalid distributor");
        }
        getOrder(orderId);   // fails fast when the order does not exist
        orderDAO.assignDistributor(orderId, distributorId);
    }
}

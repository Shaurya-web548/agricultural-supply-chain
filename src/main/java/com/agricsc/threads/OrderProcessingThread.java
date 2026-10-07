package com.agricsc.threads;

import com.agricsc.exceptions.DatabaseException;
import com.agricsc.exceptions.InsufficientStockException;
import com.agricsc.service.OrderService;

/**
 * Runs the order pipeline in the BACKGROUND so the Swing UI thread (EDT)
 * never blocks on stock updates and shipment creation.
 *
 * Flow implemented by OrderService.processOrder():
 *   PENDING -> PROCESSING -> check inventory -> reduce inventory
 *           -> CONFIRMED -> create shipment   (or CANCELLED on shortage)
 *
 * Results are stored in volatile fields so the EDT always sees the latest
 * value written by this thread (visibility without extra locking).
 */
public class OrderProcessingThread extends Thread {

    private final int orderId;
    private final OrderService orderService;

    /** volatile: written here, read by the UI thread. */
    private volatile String result = "RUNNING";
    private volatile boolean success = false;
    private volatile boolean finished = false;

    public OrderProcessingThread(int orderId, OrderService orderService) {
        // Explicit name makes the thread visible in thread dumps / viva demos.
        super("order-processor-" + orderId);
        this.orderId = orderId;
        this.orderService = orderService;
    }

    @Override
    public void run() {
        try {
            boolean confirmed = orderService.processOrder(orderId);
            success = confirmed;
            result = confirmed ? "CONFIRMED" : "SKIPPED (already processed)";
        } catch (InsufficientStockException e) {
            result = "CANCELLED: " + e.getMessage();   // order already cancelled
        } catch (DatabaseException e) {
            result = "ERROR: " + e.getMessage();
        } catch (RuntimeException e) {
            // Never let a background thread die silently.
            result = "ERROR: unexpected " + e.getClass().getSimpleName();
        } finally {
            // Runs on success AND on every failure path, so callers can
            // always rely on isFinished() eventually becoming true.
            finished = true;
        }
    }

    /** true once run() has ended, whatever the outcome. */
    public boolean isFinished() { return finished; }

    public int getOrderId() { return orderId; }
    public String getResult() { return result; }
    public boolean isSuccess() { return success; }
}

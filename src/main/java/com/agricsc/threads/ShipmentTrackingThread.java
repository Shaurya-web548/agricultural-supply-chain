package com.agricsc.threads;

import com.agricsc.exceptions.DatabaseException;
import com.agricsc.model.Shipment;
import com.agricsc.service.ShipmentService;

import java.util.HashSet;
import java.util.List;

/**
 * Background tracker that periodically refreshes shipments, simulating a
 * carrier feed: an IN_TRANSIT shipment moves location each tick and is
 * finally marked DELIVERED.
 *
 * Demonstrates: thread lifecycle (start / loop / interruptible sleep),
 * a shared HashSet&lt;Integer&gt; of already-processed shipments guarded by
 * synchronized, and cooperative cancellation via interrupt().
 */
public class ShipmentTrackingThread extends Thread {

    private static final long TICK_MILLIS = 2000L;   // one tick every 2 s

    private final ShipmentService shipmentService;
    private volatile boolean running = true;

    /** synchronized on this set: only one tick mutates it at a time. */
    private final HashSet<Integer> delivered = new HashSet<>();

    private volatile String lastUpdate = "idle";

    public ShipmentTrackingThread(ShipmentService shipmentService) {
        super("shipment-tracker");
        this.shipmentService = shipmentService;
    }

    /** Cooperative shutdown - interrupts the sleep and ends the loop. */
    public void shutdown() {
        running = false;
        interrupt();
    }

    @Override
    public void run() {
        try {
            while (running) {
                try {
                    tick();
                    sleep(TICK_MILLIS);         // InterruptedException handled below
                } catch (InterruptedException e) {
                    // shutdown() was called - exit cleanly.
                    Thread.currentThread().interrupt();
                    break;
                } catch (DatabaseException e) {
                    lastUpdate = "db error: " + e.getMessage();
                }
            }
        } finally {
            // Reached on shutdown AND if an unexpected RuntimeException
            // kills the loop, so the UI never shows a dead tracker as live.
            running = false;
            lastUpdate = "stopped";
        }
    }

    /** One refresh pass over all shipments. */
    private void tick() throws DatabaseException {
        List<Shipment> shipments = shipmentService.listAll();
        for (Shipment s : shipments) {
            if (Shipment.STATUS_DELIVERED.equals(s.getStatus())
                    || Shipment.STATUS_CANCELLED.equals(s.getStatus())) {
                continue;
            }
            synchronized (delivered) {      // protect the shared HashSet
                if (delivered.contains(s.getShipmentId())) {
                    continue;
                }
            }

            if (Shipment.STATUS_PENDING.equals(s.getStatus())) {
                shipmentService.updateStatus(s.getShipmentId(),
                        Shipment.STATUS_IN_TRANSIT, "DISTRICT HUB");
                lastUpdate = s.getTrackingNumber() + " -> IN_TRANSIT";
            } else if (Shipment.STATUS_IN_TRANSIT.equals(s.getStatus())) {
                shipmentService.updateStatus(s.getShipmentId(),
                        Shipment.STATUS_OUT_FOR_DELIVERY, "LOCAL HUB");
                lastUpdate = s.getTrackingNumber() + " -> OUT_FOR_DELIVERY";
            } else {    // OUT_FOR_DELIVERY -> DELIVERED
                shipmentService.updateStatus(s.getShipmentId(),
                        Shipment.STATUS_DELIVERED, "CUSTOMER ADDRESS");
                synchronized (delivered) {
                    delivered.add(s.getShipmentId());
                }
                lastUpdate = s.getTrackingNumber() + " -> DELIVERED";
            }
        }
    }

    public String getLastUpdate() { return lastUpdate; }
}

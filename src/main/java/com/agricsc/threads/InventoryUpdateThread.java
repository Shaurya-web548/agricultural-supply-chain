package com.agricsc.threads;

import com.agricsc.exceptions.DatabaseException;
import com.agricsc.exceptions.ProductNotFoundException;
import com.agricsc.service.InventoryService;

import java.util.function.Consumer;

/**
 * Applies a stock adjustment asynchronously - for example when a farmer
 * submits "add 100 kg of wheat" from the UI, the panel stays responsive
 * while this thread performs the database update.
 *
 * All inventory mutation goes through InventoryService/InventoryDAO, whose
 * synchronized blocks + conditional UPDATE guarantee no race conditions
 * even when several of these threads run at once.
 */
public class InventoryUpdateThread extends Thread {

    public static final int MODE_RESTOCK = 1;   // add quantity
    public static final int MODE_SET = 2;       // replace quantity

    private final int productId;
    private final int quantity;
    private final int mode;
    private final InventoryService inventoryService;

    /** Optional callback that receives the result text (null = none). */
    private final Consumer<String> onFinished;

    private volatile String result = "RUNNING";

    public InventoryUpdateThread(int productId, int quantity, int mode,
                                 InventoryService inventoryService) {
        this(productId, quantity, mode, inventoryService, null);
    }

    /**
     * @param onFinished called on THIS background thread when the update is
     *                   done; a Swing caller must hop back to the EDT with
     *                   SwingUtilities.invokeLater inside it
     */
    public InventoryUpdateThread(int productId, int quantity, int mode,
                                 InventoryService inventoryService, Consumer<String> onFinished) {
        super("inventory-updater-" + productId);
        this.productId = productId;
        this.quantity = quantity;
        this.mode = mode;
        this.inventoryService = inventoryService;
        this.onFinished = onFinished;
    }

    @Override
    public void run() {
        try {
            if (mode == MODE_SET) {
                inventoryService.setQuantity(productId, quantity);
                result = "SET product " + productId + " to " + quantity;
            } else {
                inventoryService.restock(productId, quantity);
                result = "RESTOCKED product " + productId + " by " + quantity;
            }
        } catch (IllegalArgumentException | ProductNotFoundException
                 | DatabaseException e) {
            // Message is safe to show - DatabaseException never contains SQL creds.
            result = "FAILED: " + e.getMessage();
        } catch (RuntimeException e) {
            result = "FAILED: unexpected " + e.getClass().getSimpleName();
        } finally {
            // Notify the caller whether the update succeeded or failed.
            if (onFinished != null) {
                onFinished.accept(result);
            }
        }
    }

    public String getResult() { return result; }
}

package com.agricsc.applet;

import com.agricsc.exceptions.DatabaseException;
import com.agricsc.exceptions.InsufficientStockException;
import com.agricsc.exceptions.InvalidOrderException;
import com.agricsc.exceptions.ProductNotFoundException;
import com.agricsc.model.Customer;
import com.agricsc.model.Distributor;
import com.agricsc.model.Inventory;
import com.agricsc.model.Order;
import com.agricsc.model.Product;
import com.agricsc.model.Shipment;
import com.agricsc.service.InventoryService;
import com.agricsc.service.OrderService;
import com.agricsc.service.ProductService;
import com.agricsc.service.ShipmentService;
import com.agricsc.service.UserService;
import com.agricsc.threads.ShipmentTrackingThread;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.List;

/**
 * Distributor screens: browse/search products, wholesale purchase,
 * inventory view, shipment creation/status updates and the live
 * ShipmentTrackingThread (start/stop buttons).
 */
public class DistributorPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final MainApplet applet;
    private final ProductService productService = new ProductService();
    private final InventoryService inventoryService = new InventoryService();
    private final OrderService orderService = new OrderService();
    private final ShipmentService shipmentService = new ShipmentService();
    private final UserService userService = new UserService();

    private final JLabel userLabel = new JLabel(" ");
    private final JLabel trackerLabel = new JLabel("tracker: stopped");

    private final DefaultTableModel productModel = new DefaultTableModel(
            new Object[]{"ID", "Name", "Category", "Price", "Stock"}, 0);
    private final DefaultTableModel invModel = new DefaultTableModel(
            new Object[]{"Product ID", "Product", "Qty", "Last updated"}, 0);
    private final DefaultTableModel shipModel = new DefaultTableModel(
            new Object[]{"ID", "Order", "Tracking", "Status", "Location"}, 0);
    private final DefaultTableModel orderModel = new DefaultTableModel(
            new Object[]{"Order ID", "Customer", "Total", "Status", "Distributor"}, 0);

    private final JTextField searchField = new JTextField(10);
    private final JTextField buyProductField = new JTextField(4);
    private final JTextField buyQtyField = new JTextField(4);
    private final JTextField createShipOrderField = new JTextField(4);
    private final JTextField shipIdField = new JTextField(4);
    private final JComboBox<String> shipStatusBox = new JComboBox<>(
            new String[]{"IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED"});
    private final JTextField shipLocationField = new JTextField(10);
    private final JTextField assignOrderField = new JTextField(4);

    private Distributor distributor;
    private Customer buyerAccount;      // shadow customer row used for purchases
    private ShipmentTrackingThread tracker;   // live background tracker

    public DistributorPanel(MainApplet applet) {
        super(new BorderLayout(6, 6));
        this.applet = applet;
        setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JButton logout = new JButton("Logout");
        logout.addActionListener(e -> {
            stopTracker();
            applet.onLogout();
        });
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT));
        header.add(new JLabel("Distributor Panel -"));
        header.add(userLabel);
        header.add(new JLabel("|"));
        header.add(trackerLabel);
        header.add(logout);
        add(header, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Products", buildProductsTab());
        tabs.addTab("Inventory", buildInventoryTab());
        tabs.addTab("Shipments", buildShipmentsTab());
        tabs.addTab("Order history", buildOrdersTab());
        add(tabs, BorderLayout.CENTER);
    }

    private JPanel buildProductsTab() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.add(new JScrollPane(new JTable(productModel)), BorderLayout.CENTER);

        JPanel controls = new JPanel(new GridLayout(0, 1, 2, 2));

        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchRow.setBorder(BorderFactory.createTitledBorder("Search products"));
        searchRow.add(new JLabel("Keyword:"));
        searchRow.add(searchField);
        JButton searchBtn = new JButton("Search");
        searchBtn.addActionListener(this::doSearch);
        JButton listBtn = new JButton("List all");
        listBtn.addActionListener(e -> refreshProducts());
        searchRow.add(searchBtn);
        searchRow.add(listBtn);

        JPanel buyRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buyRow.setBorder(BorderFactory.createTitledBorder(
                "Purchase (wholesale) - reduces farmer stock via OrderProcessingThread"));
        buyRow.add(new JLabel("Product id:"));
        buyRow.add(buyProductField);
        buyRow.add(new JLabel("qty:"));
        buyRow.add(buyQtyField);
        JButton buyBtn = new JButton("Purchase");
        buyBtn.addActionListener(this::doPurchase);
        buyRow.add(buyBtn);

        controls.add(searchRow);
        controls.add(buyRow);
        p.add(controls, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildInventoryTab() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.add(new JScrollPane(new JTable(invModel)), BorderLayout.CENTER);
        JButton refresh = new JButton("Refresh inventory");
        refresh.addActionListener(e -> refreshInventory());
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row.add(refresh);
        p.add(row, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildShipmentsTab() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.add(new JScrollPane(new JTable(shipModel)), BorderLayout.CENTER);

        JPanel controls = new JPanel(new GridLayout(0, 1, 2, 2));

        JPanel createRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        createRow.setBorder(BorderFactory.createTitledBorder("Create shipment for order"));
        createRow.add(new JLabel("Order id:"));
        createRow.add(createShipOrderField);
        JButton createBtn = new JButton("Create shipment");
        createBtn.addActionListener(this::doCreateShipment);
        createRow.add(createBtn);

        JPanel updateRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        updateRow.setBorder(BorderFactory.createTitledBorder("Update shipment status"));
        updateRow.add(new JLabel("Shipment id:"));
        updateRow.add(shipIdField);
        updateRow.add(new JLabel("Status:"));
        updateRow.add(shipStatusBox);
        updateRow.add(new JLabel("Location:"));
        updateRow.add(shipLocationField);
        JButton updBtn = new JButton("Update");
        updBtn.addActionListener(this::doUpdateShipment);
        updateRow.add(updBtn);

        JPanel trackerRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        trackerRow.setBorder(BorderFactory.createTitledBorder(
                "Automatic tracking (ShipmentTrackingThread)"));
        JButton startBtn = new JButton("Start tracker");
        startBtn.addActionListener(e -> startTracker());
        JButton stopBtn = new JButton("Stop tracker");
        stopBtn.addActionListener(e -> stopTracker());
        trackerRow.add(startBtn);
        trackerRow.add(stopBtn);
        trackerRow.add(new JLabel("advances shipments every 2 s"));

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refreshShipments());

        controls.add(createRow);
        controls.add(updateRow);
        controls.add(trackerRow);
        controls.add(refresh);
        p.add(controls, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildOrdersTab() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.add(new JScrollPane(new JTable(orderModel)), BorderLayout.CENTER);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row.setBorder(BorderFactory.createTitledBorder("Assign order to myself"));
        row.add(new JLabel("Order id:"));
        row.add(assignOrderField);
        JButton assignBtn = new JButton("Assign");
        assignBtn.addActionListener(this::doAssign);
        row.add(assignBtn);
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refreshOrders());
        row.add(refresh);
        p.add(row, BorderLayout.SOUTH);
        return p;
    }

    // ------------------------------------------------------------- refresh

    void refresh() {
        try {
            int userId = applet.getCurrentUser().getUserId();
            distributor = userService.getDistributorProfile(userId);
            buyerAccount = userService.getCustomerProfile(userId);
            userLabel.setText(applet.getCurrentUser().getFullName()
                    + " (" + applet.getCurrentUser().getUsername() + ")");
            refreshProducts();
            refreshInventory();
            refreshShipments();
            refreshOrders();
        } catch (DatabaseException e) {
            showError(e);
        }
    }

    private void refreshProducts() {
        try {
            fillProducts(productService.listAll());
        } catch (DatabaseException e) {
            showError(e);
        }
    }

    private void doSearch(ActionEvent ignored) {
        try {
            fillProducts(productService.search(searchField.getText(), null, null));
        } catch (DatabaseException e) {
            showError(e);
        }
    }

    private void fillProducts(List<Product> products) {
        productModel.setRowCount(0);
        for (Product p : products) {
            int stock;
            try {
                stock = inventoryService.getQuantity(p.getProductId());
            } catch (DatabaseException e) {
                stock = -1;
            }
            productModel.addRow(new Object[]{p.getProductId(), p.getProductName(),
                    p.getCategory(), p.getPrice(), stock});
        }
    }

    private void refreshInventory() {
        try {
            invModel.setRowCount(0);
            // HashMap from the product service = O(1) name lookup per row.
            HashMap<Integer, Product> names = productService.getProductMap();
            List<Inventory> rows = inventoryService.listInventory();
            for (Inventory inv : rows) {
                Product p = names.get(inv.getProductId());
                invModel.addRow(new Object[]{inv.getProductId(),
                        p == null ? "?" : p.getProductName(),
                        inv.getQuantityAvailable(), inv.getLastUpdated()});
            }
        } catch (DatabaseException e) {
            showError(e);
        }
    }

    private void refreshShipments() {
        try {
            shipModel.setRowCount(0);
            List<Shipment> shipments = shipmentService.listAll();
            for (Shipment s : shipments) {
                shipModel.addRow(new Object[]{s.getShipmentId(), s.getOrderId(),
                        s.getTrackingNumber(), s.getStatus(), s.getCurrentLocation()});
            }
            if (tracker != null) {
                trackerLabel.setText("tracker: " + tracker.getLastUpdate());
            }
        } catch (DatabaseException e) {
            showError(e);
        }
    }

    private void refreshOrders() {
        try {
            orderModel.setRowCount(0);
            List<Order> orders = orderService.getAllOrders();
            for (Order o : orders) {
                orderModel.addRow(new Object[]{o.getOrderId(), o.getCustomerId(),
                        o.getTotalAmount(), o.getStatus(),
                        o.getDistributorId() == null ? "-" : o.getDistributorId()});
            }
        } catch (DatabaseException e) {
            showError(e);
        }
    }

    // ------------------------------------------------------------- actions

    /** Wholesale purchase: same order pipeline as a customer order. */
    private void doPurchase(ActionEvent ignored) {
        try {
            int productId = Integer.parseInt(buyProductField.getText().trim());
            int qty = Integer.parseInt(buyQtyField.getText().trim());

            HashMap<Integer, Integer> items = new HashMap<>();
            items.put(productId, qty);

            Order order = orderService.placeDistributorPurchase(
                    buyerAccount, distributor.getDistributorId(), items);
            JOptionPane.showMessageDialog(this,
                    "Purchase order #" + order.getOrderId() + " placed",
                    "Done", JOptionPane.INFORMATION_MESSAGE);
            refreshProducts();
            refreshInventory();
            refreshOrders();
        } catch (NumberFormatException e) {
            warn("Product id and qty must be numbers");
        } catch (InvalidOrderException | ProductNotFoundException e) {
            warn(e.getMessage());
        } catch (DatabaseException e) {
            showError(e);
        }
    }

    private void doCreateShipment(ActionEvent ignored) {
        try {
            int orderId = Integer.parseInt(createShipOrderField.getText().trim());
            // Fails with InvalidOrderException when the order does not exist.
            orderService.getOrder(orderId);
            Shipment s = shipmentService.createShipmentForOrder(orderId);
            JOptionPane.showMessageDialog(this,
                    "Shipment created: " + s.getTrackingNumber(), "Done",
                    JOptionPane.INFORMATION_MESSAGE);
            refreshShipments();
        } catch (NumberFormatException e) {
            warn("Order id must be a number");
        } catch (InvalidOrderException e) {
            warn(e.getMessage());
        } catch (DatabaseException e) {
            showError(e);
        }
    }

    private void doUpdateShipment(ActionEvent ignored) {
        try {
            int id = Integer.parseInt(shipIdField.getText().trim());
            String status = (String) shipStatusBox.getSelectedItem();
            String location = shipLocationField.getText().trim();
            if (location.isEmpty()) {
                location = "HUB";
            }
            shipmentService.updateStatus(id, status, location);
            JOptionPane.showMessageDialog(this, "Shipment updated", "Done",
                    JOptionPane.INFORMATION_MESSAGE);
            refreshShipments();
        } catch (NumberFormatException e) {
            warn("Shipment id must be a number");
        } catch (IllegalArgumentException e) {
            warn(e.getMessage());
        } catch (DatabaseException e) {
            showError(e);
        }
    }

    private void doAssign(ActionEvent ignored) {
        try {
            int orderId = Integer.parseInt(assignOrderField.getText().trim());
            orderService.assignDistributor(orderId, distributor.getDistributorId());
            JOptionPane.showMessageDialog(this, "Order assigned to you", "Done",
                    JOptionPane.INFORMATION_MESSAGE);
            refreshOrders();
        } catch (NumberFormatException e) {
            warn("Order id must be a number");
        } catch (InvalidOrderException e) {
            warn(e.getMessage());
        } catch (DatabaseException e) {
            showError(e);
        }
    }

    // --------------------------------------------------------- tracker life

    private void startTracker() {
        if (tracker != null && tracker.isAlive()) {
            return;   // already running
        }
        tracker = new ShipmentTrackingThread(shipmentService);
        tracker.start();
        trackerLabel.setText("tracker: running");
    }

    private void stopTracker() {
        if (tracker != null) {
            tracker.shutdown();       // cooperative stop via interrupt()
            tracker = null;
            trackerLabel.setText("tracker: stopped");
        }
    }

    private void warn(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Invalid input",
                JOptionPane.WARNING_MESSAGE);
    }

    private void showError(DatabaseException e) {
        JOptionPane.showMessageDialog(this, "Database problem: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }
}

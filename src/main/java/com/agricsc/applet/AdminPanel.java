package com.agricsc.applet;

import com.agricsc.exceptions.DatabaseException;
import com.agricsc.model.Inventory;
import com.agricsc.model.Order;
import com.agricsc.model.Product;
import com.agricsc.model.Shipment;
import com.agricsc.model.User;
import com.agricsc.service.InventoryService;
import com.agricsc.service.OrderService;
import com.agricsc.service.ProductService;
import com.agricsc.service.ShipmentService;
import com.agricsc.service.UserService;

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
import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.List;

/**
 * Admin screens: inspect farmers / distributors / customers, products,
 * inventory, orders, shipments and simple statistics.
 * The admin account is seeded (sql/seed_data.sql), not self-registered.
 */
public class AdminPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final MainApplet applet;
    private final UserService userService = new UserService();
    private final ProductService productService = new ProductService();
    private final InventoryService inventoryService = new InventoryService();
    private final OrderService orderService = new OrderService();
    private final ShipmentService shipmentService = new ShipmentService();

    private final JLabel userLabel = new JLabel(" ");

    private final DefaultTableModel userModel = new DefaultTableModel(
            new Object[]{"ID", "Username", "Name", "Email", "Role"}, 0);
    private final DefaultTableModel productModel = new DefaultTableModel(
            new Object[]{"ID", "Farmer", "Name", "Category", "Price", "Status"}, 0);
    private final DefaultTableModel invModel = new DefaultTableModel(
            new Object[]{"Product ID", "Product", "Qty", "Last updated"}, 0);
    private final DefaultTableModel orderModel = new DefaultTableModel(
            new Object[]{"Order ID", "Customer", "Distributor", "Total", "Status"}, 0);
    private final DefaultTableModel shipModel = new DefaultTableModel(
            new Object[]{"ID", "Order", "Tracking", "Status", "Location"}, 0);

    private final JTextField statsField = new JTextField(40);

    public AdminPanel(MainApplet applet) {
        super(new BorderLayout(6, 6));
        this.applet = applet;
        setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JButton logout = new JButton("Logout");
        logout.addActionListener(e -> applet.onLogout());
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT));
        header.add(new JLabel("Admin Panel -"));
        header.add(userLabel);
        header.add(logout);
        add(header, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Users", buildUsersTab());
        tabs.addTab("Products", buildProductsTab());
        tabs.addTab("Inventory", buildInventoryTab());
        tabs.addTab("Orders", buildOrdersTab());
        tabs.addTab("Shipments", buildShipmentsTab());
        tabs.addTab("Statistics", buildStatsTab());
        add(tabs, BorderLayout.CENTER);
    }

    private JPanel buildUsersTab() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.add(new JScrollPane(new JTable(userModel)), BorderLayout.CENTER);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row.add(new JLabel("Show role:"));
        JComboBox<String> roleBox = new JComboBox<>(
                new String[]{"FARMER", "DISTRIBUTOR", "CUSTOMER"});
        row.add(roleBox);
        JButton showBtn = new JButton("List users");
        showBtn.addActionListener(e -> refreshUsers((String) roleBox.getSelectedItem()));
        row.add(showBtn);
        p.add(row, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildProductsTab() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.add(new JScrollPane(new JTable(productModel)), BorderLayout.CENTER);
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refreshProducts());
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row.add(refresh);
        p.add(row, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildInventoryTab() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.add(new JScrollPane(new JTable(invModel)), BorderLayout.CENTER);
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refreshInventory());
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row.add(refresh);
        p.add(row, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildOrdersTab() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.add(new JScrollPane(new JTable(orderModel)), BorderLayout.CENTER);
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refreshOrders());
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row.add(refresh);
        p.add(row, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildShipmentsTab() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.add(new JScrollPane(new JTable(shipModel)), BorderLayout.CENTER);
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refreshShipments());
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row.add(refresh);
        p.add(row, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildStatsTab() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        statsField.setEditable(false);
        p.add(statsField, BorderLayout.NORTH);
        JButton calc = new JButton("Calculate statistics");
        calc.addActionListener(this::doStats);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row.add(calc);
        p.add(row, BorderLayout.SOUTH);
        return p;
    }

    // ------------------------------------------------------------- refresh

    void refresh() {
        userLabel.setText(applet.getCurrentUser().getFullName()
                + " (" + applet.getCurrentUser().getUsername() + ")");
        refreshUsers("FARMER");
        refreshProducts();
        refreshInventory();
        refreshOrders();
        refreshShipments();
    }

    private void refreshUsers(String role) {
        try {
            userModel.setRowCount(0);
            List<User> users = userService.listUsersByRole(role);
            for (User u : users) {
                userModel.addRow(new Object[]{u.getUserId(), u.getUsername(),
                        u.getFullName(), u.getEmail(), u.getRole()});
            }
        } catch (DatabaseException e) {
            showError(e);
        }
    }

    private void refreshProducts() {
        try {
            productModel.setRowCount(0);
            List<Product> products = productService.listAll();
            for (Product p : products) {
                productModel.addRow(new Object[]{p.getProductId(), p.getFarmerId(),
                        p.getProductName(), p.getCategory(), p.getPrice(),
                        p.getStatus()});
            }
        } catch (DatabaseException e) {
            showError(e);
        }
    }

    private void refreshInventory() {
        try {
            invModel.setRowCount(0);
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

    private void refreshOrders() {
        try {
            orderModel.setRowCount(0);
            List<Order> orders = orderService.getAllOrders();
            for (Order o : orders) {
                orderModel.addRow(new Object[]{o.getOrderId(), o.getCustomerId(),
                        o.getDistributorId() == null ? "-" : o.getDistributorId(),
                        o.getTotalAmount(), o.getStatus()});
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
        } catch (DatabaseException e) {
            showError(e);
        }
    }

    /**
     * Simple report built from in-memory lists: counts, total stock and
     * revenue (sum of non-cancelled order totals).
     */
    private void doStats(ActionEvent ignored) {
        try {
            int farmers = userService.listUsersByRole(User.ROLE_FARMER).size();
            int distributors = userService.listUsersByRole(User.ROLE_DISTRIBUTOR).size();
            int customers = userService.listUsersByRole(User.ROLE_CUSTOMER).size();
            int products = productService.listAll().size();

            int totalStock = 0;
            for (Inventory inv : inventoryService.listInventory()) {
                totalStock += inv.getQuantityAvailable();
            }

            int orders = 0, cancelled = 0;
            double revenue = 0.0;
            for (Order o : orderService.getAllOrders()) {
                orders++;
                if (Order.STATUS_CANCELLED.equals(o.getStatus())) {
                    cancelled++;
                } else {
                    revenue += o.getTotalAmount();
                }
            }
            int shipments = shipmentService.listAll().size();

            statsField.setText("Farmers=" + farmers
                    + "  Distributors=" + distributors
                    + "  Customers=" + customers
                    + "  Products=" + products
                    + "  TotalStock=" + totalStock
                    + "  Orders=" + orders
                    + " (cancelled " + cancelled + ")"
                    + "  Shipments=" + shipments
                    + "  Revenue=" + String.format("%.2f", revenue));
        } catch (DatabaseException e) {
            showError(e);
        }
    }

    private void showError(DatabaseException e) {
        JOptionPane.showMessageDialog(this, "Database problem: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }
}

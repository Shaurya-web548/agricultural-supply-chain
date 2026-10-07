package com.agricsc.applet;

import com.agricsc.exceptions.DatabaseException;
import com.agricsc.exceptions.InvalidOrderException;
import com.agricsc.exceptions.ProductNotFoundException;
import com.agricsc.model.Customer;
import com.agricsc.model.Order;
import com.agricsc.model.Product;
import com.agricsc.model.Shipment;
import com.agricsc.service.InventoryService;
import com.agricsc.service.OrderService;
import com.agricsc.service.ProductService;
import com.agricsc.service.ShipmentService;
import com.agricsc.service.UserService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
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
 * Customer screens: browse/search products, place orders, order history
 * and shipment tracking. All actions go through the service layer.
 */
public class CustomerPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final MainApplet applet;
    private final ProductService productService = new ProductService();
    private final InventoryService inventoryService = new InventoryService();
    private final OrderService orderService = new OrderService();
    private final ShipmentService shipmentService = new ShipmentService();
    private final UserService userService = new UserService();

    private final JLabel userLabel = new JLabel(" ");
    private final DefaultTableModel productModel = new DefaultTableModel(
            new Object[]{"ID", "Name", "Category", "Unit", "Price", "Stock"}, 0);
    private final DefaultTableModel orderModel = new DefaultTableModel(
            new Object[]{"Order ID", "Date", "Items", "Total", "Status", "Tracking No."}, 0);

    private final JTextField searchField = new JTextField(10);
    private final JTextField categoryField = new JTextField(8);
    private final JTextField orderProductField = new JTextField(4);
    private final JTextField orderQtyField = new JTextField(4);
    private final JTextField trackField = new JTextField(12);

    private Customer customer;   // profile of the logged-in user

    public CustomerPanel(MainApplet applet) {
        super(new BorderLayout(6, 6));
        this.applet = applet;
        setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        // ---- header: who is logged in + logout ----
        JButton logout = new JButton("Logout");
        logout.addActionListener(e -> applet.onLogout());
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT));
        header.add(new JLabel("Customer Panel -"));
        header.add(userLabel);
        header.add(logout);
        add(header, BorderLayout.NORTH);

        // ---- center: products table + search/place/track controls ----
        JTable productTable = new JTable(productModel);
        JPanel center = new JPanel(new BorderLayout(4, 4));
        center.add(new JScrollPane(productTable), BorderLayout.CENTER);

        JPanel controls = new JPanel(new GridLayout(0, 1, 2, 2));
        controls.setBorder(BorderFactory.createTitledBorder("Actions"));

        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchRow.add(new JLabel("Search:"));
        searchRow.add(searchField);
        searchRow.add(new JLabel("Category:"));
        searchRow.add(categoryField);
        JButton searchBtn = new JButton("Search");
        searchBtn.addActionListener(this::doSearch);
        JButton listAllBtn = new JButton("List all");
        listAllBtn.addActionListener(e -> refreshProducts());
        searchRow.add(searchBtn);
        searchRow.add(listAllBtn);

        JPanel orderRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        orderRow.add(new JLabel("Place order - product id:"));
        orderRow.add(orderProductField);
        orderRow.add(new JLabel("qty:"));
        orderRow.add(orderQtyField);
        JButton placeBtn = new JButton("Place order");
        placeBtn.addActionListener(this::doPlaceOrder);
        orderRow.add(placeBtn);

        JPanel trackRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        trackRow.add(new JLabel("Track shipment no:"));
        trackRow.add(trackField);
        JButton trackBtn = new JButton("Track");
        trackBtn.addActionListener(this::doTrack);
        trackRow.add(trackBtn);

        controls.add(searchRow);
        controls.add(orderRow);
        controls.add(trackRow);
        center.add(controls, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        // ---- bottom: my orders ----
        JPanel ordersPanel = new JPanel(new BorderLayout());
        ordersPanel.setBorder(BorderFactory.createTitledBorder("My orders"));
        ordersPanel.add(new JScrollPane(new JTable(orderModel)), BorderLayout.CENTER);
        JButton refreshOrders = new JButton("Refresh orders");
        refreshOrders.addActionListener(e -> refreshOrders());
        JPanel orderBtnRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        orderBtnRow.add(refreshOrders);
        ordersPanel.add(orderBtnRow, BorderLayout.SOUTH);
        add(ordersPanel, BorderLayout.SOUTH);
    }

    /** Called by MainApplet whenever this panel becomes visible. */
    void refresh() {
        try {
            customer = userService.getCustomerProfile(applet.getCurrentUser().getUserId());
            userLabel.setText(applet.getCurrentUser().getFullName()
                    + " (" + applet.getCurrentUser().getUsername() + ")");
            refreshProducts();
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
            // Production code would not allow null here, but the service
            // treats null/empty filters as "ignore this filter".
            fillProducts(productService.search(
                    searchField.getText(), categoryField.getText(), null));
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
                    p.getCategory(), p.getUnit(), p.getPrice(), stock});
        }
    }

    /** Places an order - the background OrderProcessingThread finishes it. */
    private void doPlaceOrder(ActionEvent ignored) {
        try {
            int productId = Integer.parseInt(orderProductField.getText().trim());
            int qty = Integer.parseInt(orderQtyField.getText().trim());

            HashMap<Integer, Integer> items = new HashMap<>();
            items.put(productId, qty);

            Order order = orderService.placeOrder(customer, items);
            JOptionPane.showMessageDialog(this,
                    "Order #" + order.getOrderId() + " placed (PENDING).\n"
                            + "It is being processed in the background.",
                    "Order placed", JOptionPane.INFORMATION_MESSAGE);
            refreshOrders();
            refreshProducts();
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Product id and qty must be numbers",
                    "Invalid input", JOptionPane.WARNING_MESSAGE);
        } catch (InvalidOrderException | ProductNotFoundException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(),
                    "Cannot place order", JOptionPane.WARNING_MESSAGE);
        } catch (DatabaseException e) {
            showError(e);
        }
    }

    private void doTrack(ActionEvent ignored) {
        try {
            Shipment s = shipmentService.track(trackField.getText());
            if (s == null) {
                JOptionPane.showMessageDialog(this, "No shipment with that number",
                        "Not found", JOptionPane.WARNING_MESSAGE);
                return;
            }
            JOptionPane.showMessageDialog(this,
                    "Shipment #" + s.getShipmentId() + "\nStatus: " + s.getStatus()
                            + "\nLocation: " + s.getCurrentLocation()
                            + "\nCarrier: " + s.getCarrier()
                            + "\nExpected: " + s.getExpectedDelivery(),
                    "Tracking " + s.getTrackingNumber(),
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (DatabaseException e) {
            showError(e);
        }
    }

    private void refreshOrders() {
        try {
            orderModel.setRowCount(0);
            if (customer == null) {
                return;
            }
            List<Order> orders = orderService.getCustomerOrders(customer.getCustomerId());
            for (Order o : orders) {
                // Shipment exists once the order is confirmed; copy its number into "Track".
                Shipment s = shipmentService.getShipmentForOrder(o.getOrderId());
                orderModel.addRow(new Object[]{o.getOrderId(), o.getOrderDate(),
                        o.getItems().size(), o.getTotalAmount(), o.getStatus(),
                        s == null ? "-" : s.getTrackingNumber()});
            }
        } catch (DatabaseException e) {
            showError(e);
        }
    }

    private void showError(DatabaseException e) {
        JOptionPane.showMessageDialog(this, "Database problem: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }
}

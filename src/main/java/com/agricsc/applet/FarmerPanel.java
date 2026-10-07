package com.agricsc.applet;

import com.agricsc.exceptions.DatabaseException;
import com.agricsc.exceptions.InvalidOrderException;
import com.agricsc.exceptions.ProductNotFoundException;
import com.agricsc.model.Farmer;
import com.agricsc.model.Order;
import com.agricsc.model.Product;
import com.agricsc.service.InventoryService;
import com.agricsc.service.OrderService;
import com.agricsc.service.ProductService;
import com.agricsc.service.UserService;
import com.agricsc.threads.InventoryUpdateThread;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * Farmer screens: manage own products (add/update/delete), update the
 * available quantity, view all orders and advance their status.
 */
public class FarmerPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final MainApplet applet;
    private final ProductService productService = new ProductService();
    private final InventoryService inventoryService = new InventoryService();
    private final OrderService orderService = new OrderService();
    private final UserService userService = new UserService();

    private final JLabel userLabel = new JLabel(" ");
    private final DefaultTableModel productModel = new DefaultTableModel(
            new Object[]{"ID", "Name", "Category", "Unit", "Price", "Status"}, 0);
    private final DefaultTableModel orderModel = new DefaultTableModel(
            new Object[]{"Order ID", "Date", "Customer", "Total", "Status"}, 0);

    private final JTextField nameField = new JTextField(10);
    private final JTextField catField = new JTextField(8);
    private final JTextField unitField = new JTextField(4);
    private final JTextField priceField = new JTextField(6);
    private final JTextField descField = new JTextField(12);
    private final JTextField qtyField = new JTextField(5);

    private final JTextField updIdField = new JTextField(4);
    private final JTextField updPriceField = new JTextField(6);
    private final JTextField updQtyIdField = new JTextField(4);
    private final JTextField updQtyField = new JTextField(5);
    private final JTextField delIdField = new JTextField(4);
    private final JTextField advanceIdField = new JTextField(4);

    private Farmer farmer;

    public FarmerPanel(MainApplet applet) {
        super(new BorderLayout(6, 6));
        this.applet = applet;
        setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JButton logout = new JButton("Logout");
        logout.addActionListener(e -> applet.onLogout());
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT));
        header.add(new JLabel("Farmer Panel -"));
        header.add(userLabel);
        header.add(logout);
        add(header, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("My products", buildProductsTab());
        tabs.addTab("Orders", buildOrdersTab());
        add(tabs, BorderLayout.CENTER);
    }

    private JPanel buildProductsTab() {
        JPanel p = new JPanel(new BorderLayout(4, 4));

        JTable table = new JTable(productModel);
        p.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel controls = new JPanel();
        controls.setLayout(new GridLayout(0, 1, 2, 2));

        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addRow.setBorder(BorderFactory.createTitledBorder("Add product"));
        addRow.add(new JLabel("Name:")); addRow.add(nameField);
        addRow.add(new JLabel("Cat:")); addRow.add(catField);
        addRow.add(new JLabel("Unit:")); addRow.add(unitField);
        addRow.add(new JLabel("Price:")); addRow.add(priceField);
        addRow.add(new JLabel("Qty:")); addRow.add(qtyField);
        addRow.add(new JLabel("Desc:")); addRow.add(descField);
        JButton addBtn = new JButton("Add");
        addBtn.addActionListener(this::doAddProduct);
        addRow.add(addBtn);

        JPanel updRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        updRow.setBorder(BorderFactory.createTitledBorder("Update price"));
        updRow.add(new JLabel("Product id:")); updRow.add(updIdField);
        updRow.add(new JLabel("New price:")); updRow.add(updPriceField);
        JButton updBtn = new JButton("Update");
        updBtn.addActionListener(this::doUpdatePrice);
        updRow.add(updBtn);

        JPanel qtyRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        qtyRow.setBorder(BorderFactory.createTitledBorder("Update available quantity"));
        qtyRow.add(new JLabel("Product id:")); qtyRow.add(updQtyIdField);
        qtyRow.add(new JLabel("New qty:")); qtyRow.add(updQtyField);
        JButton qtyBtn = new JButton("Set quantity");
        qtyBtn.addActionListener(this::doSetQuantity);
        qtyRow.add(qtyBtn);

        JPanel delRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        delRow.setBorder(BorderFactory.createTitledBorder("Delete product"));
        delRow.add(new JLabel("Product id:")); delRow.add(delIdField);
        JButton delBtn = new JButton("Delete");
        delBtn.addActionListener(this::doDeleteProduct);
        delRow.add(delBtn);
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refreshProducts());
        delRow.add(refreshBtn);

        controls.add(addRow);
        controls.add(updRow);
        controls.add(qtyRow);
        controls.add(delRow);
        p.add(controls, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildOrdersTab() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.add(new JScrollPane(new JTable(orderModel)), BorderLayout.CENTER);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row.add(new JLabel("Advance order id:"));
        row.add(advanceIdField);
        JButton advanceBtn = new JButton("Move to next status");
        advanceBtn.addActionListener(this::doAdvanceOrder);
        row.add(advanceBtn);
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refreshOrders());
        row.add(refreshBtn);
        p.add(row, BorderLayout.SOUTH);
        return p;
    }

    // ------------------------------------------------------------- refresh

    void refresh() {
        try {
            farmer = userService.getFarmerProfile(applet.getCurrentUser().getUserId());
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
            productModel.setRowCount(0);
            if (farmer == null) {
                return;
            }
            List<Product> products = productService.listByFarmer(farmer.getFarmerId());
            for (Product p : products) {
                productModel.addRow(new Object[]{p.getProductId(), p.getProductName(),
                        p.getCategory(), p.getUnit(), p.getPrice(), p.getStatus()});
            }
        } catch (DatabaseException e) {
            showError(e);
        }
    }

    private void refreshOrders() {
        try {
            orderModel.setRowCount(0);
            if (farmer == null) {
                return;
            }
            List<Order> orders = orderService.getFarmerOrders(farmer.getFarmerId());
            for (Order o : orders) {
                orderModel.addRow(new Object[]{o.getOrderId(), o.getOrderDate(),
                        o.getCustomerId(), o.getTotalAmount(), o.getStatus()});
            }
        } catch (DatabaseException e) {
            showError(e);
        }
    }

    // ------------------------------------------------------------- actions

    private void doAddProduct(ActionEvent ignored) {
        try {
            double price = Double.parseDouble(priceField.getText().trim());
            int qty = Integer.parseInt(qtyField.getText().trim());
            Product p = productService.addProduct(farmer.getFarmerId(),
                    nameField.getText(), catField.getText(), unitField.getText(),
                    price, descField.getText(), qty);
            JOptionPane.showMessageDialog(this,
                    "Added product #" + p.getProductId(), "Done",
                    JOptionPane.INFORMATION_MESSAGE);
            refreshProducts();
        } catch (NumberFormatException e) {
            warn("Price and quantity must be numbers");
        } catch (IllegalArgumentException e) {
            warn(e.getMessage());
        } catch (DatabaseException e) {
            showError(e);
        }
    }

    private void doUpdatePrice(ActionEvent ignored) {
        try {
            int id = Integer.parseInt(updIdField.getText().trim());
            double price = Double.parseDouble(updPriceField.getText().trim());
            Product p = productService.requireOwnedBy(id, farmer.getFarmerId());
            p.setPrice(price);
            productService.updateProduct(p);
            JOptionPane.showMessageDialog(this, "Price updated", "Done",
                    JOptionPane.INFORMATION_MESSAGE);
            refreshProducts();
        } catch (NumberFormatException e) {
            warn("Id and price must be numbers");
        } catch (IllegalArgumentException | ProductNotFoundException e) {
            warn(e.getMessage());
        } catch (DatabaseException e) {
            showError(e);
        }
    }

    private void doSetQuantity(ActionEvent ignored) {
        try {
            int id = Integer.parseInt(updQtyIdField.getText().trim());
            int qty = Integer.parseInt(updQtyField.getText().trim());
            if (qty < 0) {
                throw new IllegalArgumentException("Quantity cannot be negative");
            }
            productService.requireOwnedBy(id, farmer.getFarmerId());

            // MULTITHREADING: the stock write runs on InventoryUpdateThread so
            // the window stays responsive. Its callback runs on that thread,
            // so we hop back to the Swing EDT before touching any component.
            new InventoryUpdateThread(id, qty, InventoryUpdateThread.MODE_SET, inventoryService,
                    result -> SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(this, result,
                                    "Inventory update", JOptionPane.INFORMATION_MESSAGE)))
                    .start();
        } catch (NumberFormatException e) {
            warn("Id and quantity must be numbers");
        } catch (IllegalArgumentException | ProductNotFoundException e) {
            warn(e.getMessage());
        } catch (DatabaseException e) {
            showError(e);
        }
    }

    private void doDeleteProduct(ActionEvent ignored) {
        try {
            int id = Integer.parseInt(delIdField.getText().trim());
            productService.requireOwnedBy(id, farmer.getFarmerId());
            boolean deleted = productService.deleteProduct(id);
            JOptionPane.showMessageDialog(this, deleted
                            ? "Product deleted"
                            : "Product has order history, so it was marked INACTIVE instead",
                    "Done", JOptionPane.INFORMATION_MESSAGE);
            refreshProducts();
        } catch (NumberFormatException e) {
            warn("Id must be a number");
        } catch (IllegalArgumentException | ProductNotFoundException e) {
            warn(e.getMessage());
        } catch (DatabaseException e) {
            showError(e);
        }
    }

    private void doAdvanceOrder(ActionEvent ignored) {
        try {
            int id = Integer.parseInt(advanceIdField.getText().trim());
            orderService.advanceFarmerOrderStatus(id, farmer.getFarmerId());
            JOptionPane.showMessageDialog(this, "Order moved to next status", "Done",
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

    private void warn(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Invalid input",
                JOptionPane.WARNING_MESSAGE);
    }

    private void showError(DatabaseException e) {
        JOptionPane.showMessageDialog(this, "Database problem: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }
}

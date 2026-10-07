package com.agricsc.applet;

import com.agricsc.exceptions.DatabaseException;
import com.agricsc.exceptions.InvalidLoginException;
import com.agricsc.model.User;
import com.agricsc.service.UserService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;

/**
 * Login screen + self-registration for farmer / distributor / customer.
 * (The admin account is seeded - see sql/seed_data.sql.)
 *
 * All failures surface as dialogs: InvalidLoginException for bad
 * credentials, IllegalArgumentException for invalid form input and
 * DatabaseException for infrastructure problems - never raw stack traces.
 */
public class LoginPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final MainApplet applet;
    private final UserService userService = new UserService();

    private final JTextField usernameField = new JTextField(16);
    private final JPasswordField passwordField = new JPasswordField(16);

    // registration fields
    private JComboBox<String> roleBox;
    private JTextField regUser, regName, regEmail, regPhone, regExtra1, regExtra2, regExtra3;
    private JPasswordField regPass;
    private JPanel loginCard, registerCard;

    public LoginPanel(MainApplet applet) {
        super(new BorderLayout());
        this.applet = applet;

        JLabel title = new JLabel("Agricultural Supply Chain Management System",
                JLabel.CENTER);
        title.setBorder(BorderFactory.createEmptyBorder(12, 10, 6, 10));
        add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new CardLayout());
        loginCard = buildLoginCard();
        registerCard = buildRegisterCard();
        center.add(loginCard, "login");
        center.add(registerCard, "register");
        add(center, BorderLayout.CENTER);

        JLabel hint = new JLabel(
                "Demo: admin/admin123, farmer1/farm123, dist1/dist123, cust1/cust123",
                JLabel.CENTER);
        hint.setBorder(BorderFactory.createEmptyBorder(6, 10, 12, 10));
        add(hint, BorderLayout.SOUTH);
    }

    private JPanel buildLoginCard() {
        JPanel p = new JPanel(new GridLayout(0, 2, 8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(30, 180, 30, 180));
        p.add(new JLabel("Username:"));
        p.add(usernameField);
        p.add(new JLabel("Password:"));
        p.add(passwordField);

        JButton loginBtn = new JButton("Login");
        loginBtn.addActionListener(this::doLogin);
        JButton toRegBtn = new JButton("Register new account");
        toRegBtn.addActionListener(e -> showCard("register"));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 4));
        buttons.add(loginBtn);
        buttons.add(toRegBtn);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(p, BorderLayout.CENTER);
        wrap.add(buttons, BorderLayout.SOUTH);
        return wrap;
    }

    private JPanel buildRegisterCard() {
        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.setBorder(BorderFactory.createEmptyBorder(16, 120, 16, 120));

        roleBox = new JComboBox<>(new String[]{"CUSTOMER", "FARMER", "DISTRIBUTOR"});
        regUser = new JTextField();
        regPass = new JPasswordField();
        regName = new JTextField();
        regEmail = new JTextField();
        regPhone = new JTextField();
        regExtra1 = new JTextField();   // farmer: farm name / dist: company / cust: address
        regExtra2 = new JTextField();   // location / warehouse / city
        regExtra3 = new JTextField();   // reg no / license / pincode

        form.add(new JLabel("Role:"));
        form.add(roleBox);
        form.add(new JLabel("Username:"));
        form.add(regUser);
        form.add(new JLabel("Password:"));
        form.add(regPass);
        form.add(new JLabel("Full name:"));
        form.add(regName);
        form.add(new JLabel("Email:"));
        form.add(regEmail);
        form.add(new JLabel("Phone:"));
        form.add(regPhone);
        form.add(new JLabel("Detail 1:"));
        form.add(regExtra1);
        form.add(new JLabel("Detail 2:"));
        form.add(regExtra2);
        form.add(new JLabel("Detail 3:"));
        form.add(regExtra3);

        JButton regBtn = new JButton("Create account");
        regBtn.addActionListener(this::doRegister);
        JButton backBtn = new JButton("Back to login");
        backBtn.addActionListener(e -> showCard("login"));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 4));
        buttons.add(regBtn);
        buttons.add(backBtn);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(form, BorderLayout.CENTER);
        wrap.add(buttons, BorderLayout.SOUTH);
        return wrap;
    }

    /** Switches between the login / registration card of this panel. */
    private void showCard(String name) {
        java.awt.Container parent = getParent();
        // Walk up until we find the CardLayout container that holds us.
        while (parent != null && !(parent.getLayout() instanceof CardLayout)) {
            parent = parent.getParent();
        }
        if (parent != null) {
            ((CardLayout) parent.getLayout()).show(parent, name);
        }
    }

    private void doLogin(ActionEvent ignored) {
        try {
            User user = userService.login(
                    usernameField.getText(), new String(passwordField.getPassword()));
            applet.onLogin(user);                 // switches to the role panel
        } catch (InvalidLoginException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(),
                    "Login failed", JOptionPane.WARNING_MESSAGE);
        } catch (DatabaseException e) {
            JOptionPane.showMessageDialog(this,
                    "Database problem: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doRegister(ActionEvent ignored) {
        try {
            String role = (String) roleBox.getSelectedItem();
            String pass = new String(regPass.getPassword());
            User user;
            switch (role) {
                case "FARMER":
                    user = userService.registerFarmer(regUser.getText(), pass,
                            regName.getText(), regEmail.getText(), regPhone.getText(),
                            regExtra1.getText(), regExtra2.getText(), regExtra3.getText());
                    break;
                case "DISTRIBUTOR":
                    user = userService.registerDistributor(regUser.getText(), pass,
                            regName.getText(), regEmail.getText(), regPhone.getText(),
                            regExtra1.getText(), regExtra2.getText(), regExtra3.getText());
                    break;
                default:
                    user = userService.registerCustomer(regUser.getText(), pass,
                            regName.getText(), regEmail.getText(), regPhone.getText(),
                            regExtra1.getText(), regExtra2.getText(), regExtra3.getText());
                    break;
            }
            JOptionPane.showMessageDialog(this,
                    "Account created for " + user.getUsername() + ". Please login.",
                    "Registration complete", JOptionPane.INFORMATION_MESSAGE);
            clearRegistration();
            showCard("login");
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(),
                    "Invalid input", JOptionPane.WARNING_MESSAGE);
        } catch (DatabaseException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(),
                    "Registration failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Clears both forms after logout / registration. */
    void reset() {
        usernameField.setText("");
        passwordField.setText("");
        clearRegistration();
    }

    private void clearRegistration() {
        regUser.setText("");
        regPass.setText("");
        regName.setText("");
        regEmail.setText("");
        regPhone.setText("");
        regExtra1.setText("");
        regExtra2.setText("");
        regExtra3.setText("");
        roleBox.setSelectedIndex(0);
    }
}

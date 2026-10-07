package com.agricsc.applet;

import com.agricsc.model.User;

import javax.swing.JApplet;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;

/**
 * Entry point of the UI - a real Java Applet (JApplet extends
 * java.applet.Applet) that can be opened with appletviewer.
 *
 * Navigation uses CardLayout: one card per role panel plus the login card.
 * MainApplet owns the session (the logged-in User) and switches cards
 * after login/logout. All database access happens inside the role panels,
 * ALWAYS through the service layer - never in this class.
 *
 * HOW TO RUN (details in README):
 *   mvn package
 *   run-applet.bat   (appletviewer -J-Djava.security.policy=applet.policy MainApplet.html)
 * or run-app.bat, which calls main() and hosts the same applet in a JFrame
 * (no browser, no sandbox - the easiest option for a demo).
 */
public class MainApplet extends JApplet {

    private static final long serialVersionUID = 1L;
    private static final String TITLE = "Agricultural Supply Chain Management System";
    private static final int WINDOW_WIDTH = 1050;
    private static final int WINDOW_HEIGHT = 720;

    static final String CARD_LOGIN = "login";
    static final String CARD_ADMIN = "admin";
    static final String CARD_FARMER = "farmer";
    static final String CARD_DISTRIBUTOR = "distributor";
    static final String CARD_CUSTOMER = "customer";

    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);

    private LoginPanel loginPanel;
    private AdminPanel adminPanel;
    private FarmerPanel farmerPanel;
    private DistributorPanel distributorPanel;
    private CustomerPanel customerPanel;

    /** The signed-in user; null while on the login card. */
    private User currentUser;

    @Override
    public void init() {
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(cardPanel, BorderLayout.CENTER);

        loginPanel = new LoginPanel(this);
        adminPanel = new AdminPanel(this);
        farmerPanel = new FarmerPanel(this);
        distributorPanel = new DistributorPanel(this);
        customerPanel = new CustomerPanel(this);

        cardPanel.add(loginPanel, CARD_LOGIN);
        cardPanel.add(adminPanel, CARD_ADMIN);
        cardPanel.add(farmerPanel, CARD_FARMER);
        cardPanel.add(distributorPanel, CARD_DISTRIBUTOR);
        cardPanel.add(customerPanel, CARD_CUSTOMER);

        cards.show(cardPanel, CARD_LOGIN);
    }

    /** Called by LoginPanel after a successful service.login(). */
    void onLogin(User user) {
        this.currentUser = user;
        String card;
        switch (user.getRole()) {
            case User.ROLE_ADMIN:       card = CARD_ADMIN; break;
            case User.ROLE_FARMER:      card = CARD_FARMER; break;
            case User.ROLE_DISTRIBUTOR: card = CARD_DISTRIBUTOR; break;
            default:                    card = CARD_CUSTOMER; break;
        }
        // Refresh data of the target panel each time it is shown.
        switch (card) {
            case CARD_ADMIN:       adminPanel.refresh(); break;
            case CARD_FARMER:      farmerPanel.refresh(); break;
            case CARD_DISTRIBUTOR: distributorPanel.refresh(); break;
            case CARD_CUSTOMER:    customerPanel.refresh(); break;
            default: break;
        }
        cards.show(cardPanel, card);
    }

    /** Called by any role panel's Logout button. */
    void onLogout() {
        currentUser = null;
        loginPanel.reset();
        cards.show(cardPanel, CARD_LOGIN);
    }

    User getCurrentUser() {
        return currentUser;
    }

    /** Desktop launcher: hosts the applet inside a window (no browser). */
    public static void main(String[] args) {
        // Swing components must be created on the Event Dispatch Thread.
        SwingUtilities.invokeLater(() -> {
            MainApplet applet = new MainApplet();
            applet.init();
            applet.start();

            JFrame frame = new JFrame(TITLE);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.getContentPane().add(applet);
            frame.setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}

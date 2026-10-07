# Architecture

How the Agricultural Supply Chain Management System is organised, how data
flows, and where each required Java concept lives. See also `docs/VIVA_QA.md`
and `README.md`.

## 1. Layer overview

```
+-------------------------------+
| UI layer        applet/       |  MainApplet + Login/Admin/Farmer/
| NO java.sql imports here.     |  Distributor/Customer panels.
+---------------+---------------+  Calls service methods only.
                | model objects + custom exceptions
+---------------v---------------+
| Service       service/        |  Validation, business rules, ownership
|                               |  checks, payment logic, starts threads.
+---------------+---------------+
                | model objects
+---------------v---------------+
| DAO           dao/            |  The ONLY layer with SQL.
|                               |  PreparedStatement + try-with-resources.
+---------------+---------------+
                | JDBC (DriverManager)
+---------------v---------------+
| Database      database/       |  DBConnection reads db.properties once,
|               MySQL           |  hands out fresh Connections.
+-------------------------------+
      threads/ cuts across Service -> DAO (background jobs)
```

Package root: `com.agricsc`. A class may call only the layer below it.

## 2. Data flows

### 2.1 Login

LoginPanel -> UserService.login() -> UserDAO.findByUsername() -> SELECT users.
Hash is compared in the service; unknown user / bad password throws
InvalidLoginException. MainApplet.onLogin() switches the CardLayout card.

### 2.2 Add product (farmer)

FarmerPanel -> ProductService.addProduct(): validates input, INSERTs the
products row (ProductDAO), INSERTs the inventory row (InventoryDAO),

## 3. Where each required concept lives

OOP encapsulation: model/ private fields + getters/setters. Inheritance:
MainApplet extends JApplet, panels extend JPanel, threads extend Thread,
exceptions extend Exception. Polymorphism: toString() overrides and the
Consumer callback in InventoryUpdateThread.

Collections: ArrayList from every DAO find*() and Order.items; HashMap as
the ProductDAO price cache, the cart map, and the OrderService.NEXT_STATUS
state machine; HashSet for the farmer ownership check, the tracker
delivered set, and ShipmentService.VALID_STATUSES.

Exceptions: try-with-resources in every DAO method; services throw the five
custom exceptions in exceptions/ (InvalidLogin, ProductNotFound,
InsufficientStock, Database, InvalidOrder); DAOs declare
throws DatabaseException wrapping SQLException.

Threads: threads/ holds OrderProcessing, InventoryUpdate, ShipmentTracking.
InventoryDAO guards stock with synchronized INVENTORY_LOCK plus a
conditional UPDATE; the tracker guards its set; volatile result fields give
the UI thread visibility. JDBC: DBConnection.getConnection(),
PreparedStatement everywhere, ResultSet mapping in DAOs.

## 4. Database summary

10 tables in agricultural_supply_chain (DDL in sql/schema.sql): users
(central login, role ENUM); farmers/distributors/customers (1:1 with users
via UNIQUE FK); products (N:1 to farmers); inventory (1:1 to products,
CHECK qty >= 0); orders (N:1 to customers, optional N:1 to distributors);
order_items (M:N resolver, UNIQUE per order+product); shipments (1:1 to
orders, UNIQUE tracking number); payments (1:1 to orders). Uses PRIMARY
KEY, FOREIGN KEY, NOT NULL, UNIQUE, CHECK and DEFAULT.

## 5. Config and running

src/main/resources/db.properties (git-ignored) holds the URL, user and
password; the app connects as limited user agri_app (sql/app_user.sql),
never root. Tests use src/test/resources/db.properties and a separate test
database rebuilt per test. run-app.bat hosts the applet in a JFrame;
run-applet.bat uses real appletviewer (JDK 8) with applet.policy.

invalidates the HashMap product cache.

### 2.3 Order pipeline (core workflow)

CustomerPanel -> OrderService.placeOrder(customer, {productId -> qty}):
validates, prices lines from the HashMap product cache, INSERTs the PENDING
order + items + PENDING payment, then starts OrderProcessingThread and
returns immediately.

OrderProcessingThread.run() calls processOrder(orderId) in background:
1. claimForProcessing(): one conditional UPDATE PENDING -> PROCESSING,
   so a second thread gets "already processed" (SKIPPED, a safe no-op).
2. per line: InventoryDAO.reduceStock() --
   UPDATE ... SET qty = qty - ? WHERE product_id = ? AND qty >= ?.
3. success: order CONFIRMED, payment PAID, shipment PENDING with TRK- number.
4. shortage: stock already taken is returned, order CANCELLED, payment FAILED.

### 2.4 Shipments

ShipmentService.updateStatus() validates against a status set, updates the
row; the order follows (IN_TRANSIT -> SHIPPED, DELIVERED -> DELIVERED).
ShipmentTrackingThread ticks every 2 s and walks shipments forward
PENDING -> IN_TRANSIT -> OUT_FOR_DELIVERY -> DELIVERED, guarded by a
synchronized HashSet of finished ids.

# Agricultural Supply Chain Management System

A complete Java academic (BTech OOP) project that demonstrates **Core Java,
OOP, Java Applet UI, JDBC + MySQL, the Collections Framework, Exception
Handling (including custom exceptions) and Multithreading** in one coherent
supply-chain application.

## Project Description

The system connects the four roles of an agricultural supply chain:

```
Farmer
  ↓  adds products
Agricultural Product
  ↓  stock tracked in
Inventory
  ↓  bought by
Distributor
  ↓  fulfils
Order
  ↓  shipped as
Shipment
  ↓  delivered to
Customer
```

A farmer publishes products with stock quantities, a distributor searches
and purchases them wholesale, customers browse and place orders, and every
order moves through a background processing pipeline that checks inventory,
updates stock and creates a shipment — all persisted in a normalised MySQL
database through a clean **UI → Service → DAO → JDBC** architecture.

## Objectives

1. Apply the four pillars of OOP — **encapsulation, inheritance
   (JApplet), polymorphism (interface/overridden methods), abstraction
   (DAO & service layers)** — in a real project.
2. Use **JDBC** with `PreparedStatement`, `ResultSet` and
   try-with-resources for all database access.
3. Use the **Collections Framework** meaningfully (`ArrayList`,
   `HashMap`, `HashSet`).
4. Demonstrate **exception handling**: try/catch/finally, try-with-resources,
   `throw`/`throws` and five **custom checked exceptions**.
5. Demonstrate **multithreading**: order processing, inventory updates and
   shipment tracking running in background threads with proper
   synchronization to avoid race conditions.
6. Build a **Java Applet** UI as required by the academic specification.

## Features

| Module | Features |
|---|---|
| **Admin** | Login, view/manage farmers, distributors, customers, products, inventory, orders, shipments, view reports & statistics |
| **Farmer** | Registration/login, add products, update product info, update available quantity, view orders, update order status, sales history |
| **Distributor** | Login, view & search products, purchase products, manage inventory, create shipments, update shipment status, order history, live shipment tracker thread |
| **Customer** | Registration/login, browse & search products, place orders, order history, track shipment, view order status |

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 8 (Core Java) |
| Build | Maven 3.9.x |
| UI | Java Applet (`javax.swing.JApplet`) — Swing widgets |
| Persistence | JDBC (`java.sql.*`, mysql-connector-j 8.0.33) |
| Database | MySQL 8.x |
| Tests | JUnit 4.13.2 (Surefire) |

> **Java version/environment:** the project compiles with
> `-source/-target 1.8`. **JDK 8 is required to run `appletviewer`**,
> because the Applet API is deprecated/removed in modern JDKs. Running the
> `main()` launcher also works on JDK 8 (recommended).

## Architecture

```
┌────────────────────────────────────────────────────────┐
│            Java Applet UI (Swing/AWT)                  │
│  MainApplet → LoginPanel / AdminPanel / FarmerPanel /  │
│               DistributorPanel / CustomerPanel         │
│       (NO SQL here - calls the Service layer only)     │
└──────────────────────┬─────────────────────────────────┘
                       │  Java method calls
┌──────────────────────▼─────────────────────────────────┐
│  SERVICE LAYER  (business rules + validation)          │
│  UserService, ProductService, InventoryService,        │
│  OrderService, ShipmentService                         │
│  • validates input, throws custom exceptions           │
│  • starts OrderProcessingThread / InventoryUpdateThread│
│    / ShipmentTrackingThread                            │
└──────────────────────┬─────────────────────────────────┘
                       │  model objects
┌──────────────────────▼─────────────────────────────────┐
│  DAO LAYER  (only place that writes SQL)               │
│  UserDAO, ProductDAO, InventoryDAO, OrderDAO,          │
│  ShipmentDAO                                           │
│  • PreparedStatement everywhere (no SQL injection)     │
│  • try-with-resources closes every resource            │
└──────────────────────┬─────────────────────────────────┘
                       │  JDBC API
┌──────────────────────▼─────────────────────────────────┐
│  database/DBConnection  (centralized db.properties)    │
└──────────────────────┬─────────────────────────────────┘
                       │
                 MySQL: agricultural_supply_chain
```

## Modules

1. **Admin** — admin login, manage farmers/distributors/customers/products,
   inventory, view orders, manage shipments, reports/statistics.
2. **Farmer** — registration/login, add & update products, update available
   quantity, view orders, advance order status, sales history.
3. **Distributor** — login, view/search products, purchase products,
   manage inventory, create shipments, update shipment status, order
   history, start/stop the live tracker thread.
4. **Customer** — registration/login, browse/search products, place orders,
   order history, track shipment, view order status.

## Database Design

Database: **`agricultural_supply_chain`** (scripts in `sql/`).

| Table | PK | Important constraints | Relationships |
|---|---|---|---|
| `users` | `user_id` | `username`/`email` **UNIQUE**, `role` ENUM **DEFAULT 'CUSTOMER'** | 1:1 → farmers / distributors / customers |
| `farmers` | `farmer_id` | `user_id` **UNIQUE FK**→users **CASCADE**, `registration_number` UNIQUE | N:1 → users; 1:N → products |
| `distributors` | `distributor_id` | `user_id` **UNIQUE FK**→users, `license_number` UNIQUE | 1:N → orders |
| `customers` | `customer_id` | `user_id` **UNIQUE FK**→users | 1:N → orders |
| `products` | `product_id` | `farmer_id` **FK**→farmers, `price` **CHECK (>0)**, `status` DEFAULT | 1:1 → inventory, 1:N → order_items |
| `inventory` | `inventory_id` | `product_id` **UNIQUE FK**, `quantity_available` **CHECK (>=0)** DEFAULT 0 | 1:1 → products |
| `orders` | `order_id` | `customer_id` **NOT NULL FK**, `distributor_id` FK **SET NULL**, `status` DEFAULT 'PENDING', `total_amount` CHECK (>=0) | 1:N → order_items, 1:1 → shipments & payments |
| `order_items` | `item_id` | `order_id` FK, `product_id` FK, **UNIQUE(order_id,product_id)**, `quantity` CHECK (>0) | N:1 → orders & products |
| `shipments` | `shipment_id` | `order_id` **UNIQUE FK** (1:1), `tracking_number` **UNIQUE** | 1:1 → orders |
| `payments` | `payment_id` | `order_id` **UNIQUE FK** (1:1), `amount` CHECK (>=0) | 1:1 → orders |

### ER diagram description

```
users 1 ──1 farmers 1 ──∞ products 1 ──1 inventory
  │
  ├──1 distributors 1 ──∞ orders ∞ ──1 customers (from users)
  │                     │
  │                     ├──1 shipments (1:1)
  │                     ├──1 payments  (1:1)
  │                     └──∞ order_items ∞──1 products
  └──1 customers
```

* **users ↔ role tables:** 1:1 (`user_id UNIQUE` in each role table).
* **farmers → products:** 1:N (`products.farmer_id` FK).
* **products ↔ inventory:** 1:1 (`inventory.product_id UNIQUE` FK).
* **customers → orders:** 1:N (`orders.customer_id` FK).
* **orders → order_items:** 1:N with `ON DELETE CASCADE`.
* **orders ↔ shipments/payments:** 1:1 (`order_id UNIQUE` FK).
* **distributors → orders:** 1:N, optional (`distributor_id` nullable,
  `ON DELETE SET NULL`).

* **products → order_items:** a product that has been ordered is never
  hard-deleted. `ProductService.deleteProduct()` marks it `INACTIVE`
  instead, because the `ON DELETE CASCADE` would otherwise erase order lines.
* **Distributor buyer account:** `orders.customer_id` is NOT NULL, so every
  distributor also has a `customers` row. Wholesale purchases are recorded
  against it and tagged with `distributor_id`.

## Java Package Structure

```
oops-project/
├── pom.xml                 Maven build (Java 8, mysql-connector-j, JUnit 4)
├── MainApplet.html         appletviewer launcher page
├── applet.policy           sandbox permissions for appletviewer
├── run-app.bat             desktop launcher (MainApplet.main in a JFrame)
├── run-applet.bat          appletviewer launcher
├── env.bat                 optional: JAVA_HOME + local Maven for one terminal
├── sql/                    schema.sql, seed_data.sql, app_user.sql,
│                          test_schema.sql, test_queries.sql
├── docs/VIVA_QA.md         the 15 viva questions answered, with file references
└── src/
    ├── main/java/com/agricsc/
    │   ├── applet/      MainApplet, LoginPanel, AdminPanel, FarmerPanel,
    │   │                DistributorPanel, CustomerPanel
    │   ├── model/       User, Farmer, Distributor, Customer, Product,
    │   │                Inventory, Order, OrderItem, Shipment
    │   ├── dao/         UserDAO, ProductDAO, InventoryDAO, OrderDAO, ShipmentDAO
    │   ├── service/     UserService, ProductService, InventoryService,
    │   │                OrderService, ShipmentService
    │   ├── database/    DBConnection
    │   ├── exceptions/  InvalidLoginException, ProductNotFoundException,
    │   │                InsufficientStockException, DatabaseException,
    │   │                InvalidOrderException
    │   └── threads/     OrderProcessingThread, InventoryUpdateThread,
    │                    ShipmentTrackingThread
    ├── main/resources/  db.properties (git-ignored) + db.properties.example
    ├── test/java/com/agricsc/test/
    │                    SupplyChainTest (15 required cases),
    │                    OrderIntegrityTest (7 extra cases), TestDatabase
    └── test/resources/  db.properties (git-ignored, points at the TEST database)
```

## JDBC Explanation

`DBConnection` loads `db.properties` **once** in a static initializer,
registers the driver with `Class.forName("com.mysql.cj.jdbc.Driver")`, and
returns a fresh `java.sql.Connection` from
`DriverManager.getConnection(url, user, password)`. Every DAO method then:

1. opens a connection,
2. prepares an SQL statement with **`?` placeholders**
   (`PreparedStatement ps = con.prepareStatement(sql)`),
3. binds values with `setString/setInt/setDouble`,
4. executes `executeQuery()` / `executeUpdate()`,
5. reads rows from a **`ResultSet`** (`rs.next()`, `rs.getInt(...)`),
6. closes **everything** with **try-with-resources**, so the Connection,
   PreparedStatement and ResultSet are closed even when an exception is thrown,
7. converts any **`SQLException`** into the custom checked
   **`DatabaseException`**, keeping the original as its `cause`.

`PreparedStatement` is used for every query, so user input can never change
the SQL text. Test 7 proves it: searching for `' OR '1'='1` returns 0 rows.

The explicit `Class.forName` matters in appletviewer. JDBC 4 auto-loading
only scans the system classpath, and inside an applet the driver jar is
loaded by the applet's class loader.

## Collections Usage

| Where | Collection | Why this one |
|---|---|---|
| Every DAO list method (`ProductDAO.findAll`, `OrderDAO.loadOrders`, ...) | `ArrayList<T>` | Ordered rows, fast index access for JTable models, grows as rows are read |
| `Order.items` | `ArrayList<OrderItem>` | One order owns many lines (1:N), in insertion order |
| `ProductDAO.getProductMap` → `OrderService.placeOrder` | `HashMap<Integer, Product>` | Prices every cart line with one O(1) lookup instead of one query per line |
| Customer / distributor cart | `HashMap<Integer, Integer>` | productId → quantity, so the same product can't appear twice |
| `OrderService.NEXT_STATUS` | `HashMap<String, String>` | Order state machine: current status → next status |
| `OrderService.advanceFarmerOrderStatus` | `HashSet<Integer>` | O(1) "does this order contain my products?" check |
| `ShipmentService.VALID_STATUSES` | unmodifiable `LinkedHashSet<String>` | Validates shipment status before any SQL; keeps a readable order in error messages |
| `ShipmentService.SHIPMENT_TO_ORDER_STATUS` | `HashMap<String, String>` | Shipment status → order status it implies |
| `ShipmentTrackingThread.delivered` | `HashSet<Integer>` (synchronized) | Shipments already finished, so the tracker skips them |
| `ProductDAO.search` | `ArrayList<Object>` | Builds the `?` parameter list for optional search filters |

## Exception Handling Explanation

| Construct | Where |
|---|---|
| `try / catch` | every UI action (`FarmerPanel.doAddProduct`, `CustomerPanel.doPlaceOrder`, ...) shows a friendly dialog |
| multi-catch | `catch (InsufficientStockException \| DatabaseException failure)` in `OrderService.processOrder` |
| `finally` | `OrderProcessingThread.run` (always sets `finished`), `InventoryUpdateThread.run` (always fires its callback), `ShipmentTrackingThread.run` (always marks the tracker stopped) |
| try-with-resources | every DAO method (Connection, PreparedStatement, ResultSet) |
| `throw` | services throw custom exceptions, e.g. `throw new InvalidOrderException(...)` |
| `throws` | every DAO/service signature declares its checked exceptions |
| rethrow + suppressed | `processOrder` undoes partial work, then rethrows the original error with any undo error attached via `addSuppressed` |

**Custom checked exceptions** (all `extends Exception`):

| Exception | Thrown when |
|---|---|
| `InvalidLoginException` | blank or wrong username/password (`UserService.login`) |
| `ProductNotFoundException` | unknown or inactive product id |
| `InsufficientStockException` | requested quantity > available (carries requested and available amounts) |
| `DatabaseException` | any `SQLException`, wrapped with a safe message (no credentials) |
| `InvalidOrderException` | empty order, bad quantity, illegal status move, someone else's order |

Invalid user input (short username, bad email, negative quantity) uses the
built-in `IllegalArgumentException`, and `NumberFormatException` is caught in
the UI for non-numeric fields.

## Multithreading Explanation

| Thread | Started by | Job |
|---|---|---|
| `OrderProcessingThread` | `OrderService.placeOrder` | runs the order pipeline in the background so the UI never freezes |
| `InventoryUpdateThread` | FarmerPanel "Set quantity" | writes the new stock in the background, then reports the result through a callback that hops back to the Swing thread with `SwingUtilities.invokeLater` |
| `ShipmentTrackingThread` | DistributorPanel "Start tracker" | simulates a carrier feed: every 2 s moves shipments PENDING → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED; stopped with `shutdown()` (volatile flag + `interrupt()`) |

**Order pipeline** (`OrderService.processOrder`, run by `OrderProcessingThread`):

```
placeOrder():  insert order PENDING + items + payment PENDING  →  start thread
thread:        claim order  (UPDATE ... SET status='PROCESSING' WHERE status='PENDING')
               for each item: conditional stock UPDATE (fails if not enough)
               order CONFIRMED, payment PAID, shipment created (PENDING)
on failure:    give back stock already taken, order CANCELLED, payment FAILED
```

**Why synchronization is needed:** two buyers can try to buy the last units
at the same moment. If both threads read "10 left" before either writes, both
sell 10 and stock goes negative. This is a race condition. Three layers prevent it:

1. **`synchronized (INVENTORY_LOCK)`** in `InventoryDAO`: inside one JVM,
   only one thread changes stock at a time.
2. **Conditional UPDATE**
   `UPDATE inventory SET quantity_available = quantity_available - ? WHERE product_id = ? AND quantity_available >= ?`.
   The check and the change happen in one SQL statement, so even a second
   app window (a separate JVM, where `synchronized` can't help) cannot oversell.
3. **`CHECK (quantity_available >= 0)`** in MySQL is the final safety net.

The same idea protects the order itself: `OrderDAO.claimForProcessing`
moves PENDING → PROCESSING in one conditional UPDATE, so an order can never be
processed (and charged stock) twice. `OrderIntegrityTest.concurrentOrdersNeverOversell`
fires 10 simultaneous orders of 15 units at 100 in stock and checks that exactly
6 are confirmed, 4 are cancelled, and 10 units remain.

`volatile` fields (`result`, `finished`, `running`) make values written by a
background thread immediately visible to the UI thread.

## Installation

1. **JDK 8** (required for `appletviewer`), e.g. Eclipse Temurin 8. Set
   `JAVA_HOME` and add `%JAVA_HOME%\bin` to `PATH`. Check: `javac -version` → `1.8.x`.
2. **Maven 3.9.x**. Either install it system-wide, or unzip it to
   `tools\apache-maven-3.9.9` and run `call env.bat` in each new terminal.
   Check: `mvn -v`.
3. **MySQL 8.0.16+** (CHECK constraints are enforced from 8.0.16). Make sure
   the MySQL service is running.

## MySQL Setup

Run from the project root (each command asks for the root password):

```bat
mysql -u root -p < sql\schema.sql
mysql -u root -p agricultural_supply_chain < sql\seed_data.sql
mysql -u root -p < sql\app_user.sql
```

`schema.sql` drops and recreates the database, so it is safe to re-run for a
fresh start. `app_user.sql` creates the low-privilege user `agri_app`; the
application never logs in as root.

**Test database** (used only by `mvn test`, wiped before every test):

```bat
mysql -u root -p < sql\test_schema.sql
```

**Credentials:** copy `src\main\resources\db.properties.example` to
`db.properties`, and `src\test\resources\db.properties.example` to
`db.properties`, then set `db.password` to the `agri_app` password from
`app_user.sql`. Both real files are git-ignored.

## How to Run

```bat
mvn package              :: compiles, runs all tests, builds target\*.jar + target\lib
run-app.bat              :: desktop window (recommended for the demo)
run-applet.bat           :: real applet in appletviewer (JDK 8)
```

`run-applet.bat` runs
`appletviewer -J-Djava.security.policy=applet.policy MainApplet.html`.
Without the policy file, the applet sandbox blocks the JDBC connection with an
`AccessControlException`. The policy grants permissions only to code loaded
from `target\`. Use `mvn package -DskipTests` to build without a test database.

**Demo logins** (from `seed_data.sql`):

| Role | Username | Password |
|---|---|---|
| Admin | `admin` | `admin123` |
| Farmer | `farmer1` | `farm123` |
| Distributor | `dist1` | `dist123` |
| Customer | `cust1` | `cust123` |

## Test Cases

`mvn test` runs 22 JUnit tests against `agricultural_supply_chain_test`.

| # | Test | Verifies |
|---|---|---|
| 1 | `test01_userRegistration` | user + role rows are created |
| 2 | `test02_loginSuccess` | correct credentials return the user |
| 3 | `test03_invalidLogin` | wrong/blank credentials → `InvalidLoginException` |
| 4 | `test04_addProduct` | product is created with its inventory row (stock 50) |
| 5 | `test05_updateProduct` | price and category changes are saved |
| 6 | `test06_deleteProduct` | never-ordered product and its inventory are removed |
| 7 | `test07_productSearch` | keyword search works; SQL injection returns 0 rows |
| 8 | `test08_placeOrderProcessesInBackground` | thread confirms the order, stock 100 → 95, shipment created |
| 9 | `test09_insufficientInventory` | oversized order is CANCELLED, stock untouched |
| 10 | `test10_inventoryUpdate` | restock / set quantity; negative rejected |
| 11 | `test11_orderProcessingWorkflow` | CONFIRMED → SHIPPED → DELIVERED; no move after DELIVERED |
| 12 | `test12_shipmentCreation` | one shipment with a `TRK-` number per confirmed order |
| 13 | `test13_shipmentTracking` | lookup by tracking number; unknown number → null |
| 14 | `test14_invalidInput` | bad registration / empty order / blank name rejected |
| 15 | `test15_databaseFailure` | unreachable server → `DatabaseException` with the SQLException as cause |
| 16 | `concurrentOrdersNeverOversell` | 10 parallel orders: exactly 6 confirmed, stock ends at 10 |
| 17 | `concurrentRestocksAreNotLost` | 5 parallel `InventoryUpdateThread`s: 100 + 5×20 = 200, no lost update |
| 18 | `processingAnOrderTwiceIsANoOp` | a second `processOrder` changes nothing |
| 19 | `paymentFollowsOrderOutcome` | payment PAID on confirm, FAILED on cancel |
| 20 | `shipmentProgressUpdatesOrderStatus` | shipment IN_TRANSIT/DELIVERED moves the order; bad status rejected |
| 21 | `deletingOrderedProductKeepsHistory` | ordered product becomes INACTIVE, order lines kept |
| 22 | `farmerCannotChangeAnotherFarmersData` | farmers only see/advance their own orders and products |

## Sample Workflow

1. **Farmer** logs in as `farmer1` → *My products* tab → adds "Potatoes",
   price 20, quantity 300. The new product and its inventory row appear.
2. **Customer** logs in as `cust1` → browses or searches products → places an
   order for product 1 (Wheat), quantity 10. The dialog says PENDING; the
   `OrderProcessingThread` confirms it in the background. *Refresh* shows
   CONFIRMED, Wheat stock drops from 500 to 490, and the *Tracking No.* column
   shows the new `TRK-…` number.
3. **Customer** orders 10000 units → the order ends CANCELLED
   (`InsufficientStockException`) and stock is unchanged.
4. **Distributor** logs in as `dist1` → searches products → makes a wholesale
   purchase (stock drops through the same thread pipeline) → on the *Shipments*
   tab sets a shipment to IN_TRANSIT; the order becomes SHIPPED. *Start
   tracker* lets `ShipmentTrackingThread` move shipments on to DELIVERED.
5. **Customer** pastes the tracking number into *Track* → sees status and location.
6. **Farmer** uses *Set quantity* (runs on `InventoryUpdateThread`; a dialog reports the result), then opens the *Orders* tab → sees only orders that contain their
   products and advances one to the next status.
7. **Admin** logs in as `admin` → views farmers, distributors, customers,
   products, inventory, orders and shipments → *Calculate statistics* shows
   counts, total stock and revenue.

## Known Limitations (honest scope for a college project)

* Passwords are stored as unsalted SHA-256 (matches MySQL `SHA2()` in the seed
  data). A production system would use a salted, slow hash such as bcrypt.
* Order steps use compensation (undo on failure) rather than a single JDBC
  transaction, because each DAO call opens its own connection.
* There is one shared inventory per product, so a distributor's purchase
  reduces the farmer's stock but is not moved into a separate distributor stock.
* Database calls from the UI run on the Swing event thread. That is fine for
  small data, but long queries would briefly freeze the window.

## Future Enhancements

* One transaction per order (`setAutoCommit(false)` / `commit()` / `rollback()`)
  by passing a shared `Connection` into the DAOs.
* Per-owner inventory, so distributors hold their own stock and resell it.
* Salted bcrypt password hashing, and account lock-out after failed logins.
* A connection pool (e.g. HikariCP) and an `ExecutorService` thread pool
  instead of one thread per order.
* Shipment history table for a full tracking timeline.
* A web or JavaFX front end (Applets are obsolete in modern browsers).
* Report export to CSV/PDF and charts on the admin dashboard.

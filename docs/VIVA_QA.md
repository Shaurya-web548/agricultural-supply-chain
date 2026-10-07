# Viva Questions & Answers

Each answer names the file and method to open during the viva.
Paths are under `src/main/java/com/agricsc/` unless stated otherwise.

---

### 1. Why did you use JDBC?

JDBC (Java Database Connectivity) is the standard Java API for talking to
relational databases. The project requirement is plain Core Java + MySQL with
no frameworks, and JDBC is exactly that layer: `java.sql.Connection`,
`PreparedStatement`, `ResultSet` and `SQLException`. The MySQL-specific part
is just the driver jar (`mysql-connector-j`). Our code only uses the standard
interfaces, so switching databases would mostly mean changing the driver and URL.

**Show:** `database/DBConnection.java` → `getConnection()`.

### 2. What is PreparedStatement?

A precompiled SQL statement with `?` placeholders. Values are bound
separately with `setInt`, `setString` and so on, so they are always treated
as data, never as SQL. That prevents SQL injection, and the database can reuse
the compiled statement.

```java
String sql = "SELECT ... FROM products WHERE product_name LIKE ?";
ps.setString(1, "%" + keyword + "%");
```

If a user types `' OR '1'='1`, it is searched for literally and matches
nothing. **Show:** `dao/ProductDAO.java` → `search()`, and test 7
`test07_productSearch` in `src/test/java/.../SupplyChainTest.java`.

### 3. Why use DAO?

DAO (Data Access Object) keeps **all SQL in one layer**. The UI calls
services, services hold the business rules, and only DAOs touch JDBC:

```
Applet UI → Service → DAO → JDBC → MySQL
```

Benefits: the UI never contains SQL, each class has one responsibility, a
table change only affects its DAO, and services can be tested without the UI.
**Show:** `dao/` (5 DAOs). No class in `applet/` imports `java.sql`.

### 4. Where did you use Collections?

| Collection | Where | Purpose |
|---|---|---|
| `ArrayList` | every DAO list method, `Order.items` | rows for tables; order lines |
| `HashMap<Integer, Product>` | `ProductDAO.getProductMap()` used in `OrderService.placeOrder()` | O(1) price lookup per cart line |
| `HashMap<Integer, Integer>` | cart in `CustomerPanel.doPlaceOrder()` | productId → quantity |
| `HashMap<String, String>` | `OrderService.NEXT_STATUS` | order status state machine |
| `HashSet<Integer>` | `OrderService.advanceFarmerOrderStatus()`, `ShipmentTrackingThread.delivered` | fast membership checks |
| `Set<String>` | `ShipmentService.VALID_STATUSES` | validate a status before SQL |

### 5. Why ArrayList?

Query results arrive in a fixed order (`ORDER BY`) and are shown row by row
in a `JTable`. `ArrayList` keeps insertion order, gives O(1) access by index,
and grows automatically, so we don't need to know the row count in advance.
We rarely insert in the middle, which is the one thing `LinkedList` would do better.

### 6. Why HashMap?

When pricing an order we need "give me the product with id X" for every cart
line. Searching a list is O(n) per line. A `HashMap<Integer, Product>` keyed by
`product_id` answers in O(1) on average, and it is loaded with **one** query
instead of one query per item. **Show:** `OrderService.placeOrder()` →
`catalog.get(productId)`.

### 7. Where did you use Exception Handling?

* **try-with-resources:** every DAO method closes its Connection,
  PreparedStatement and ResultSet automatically.
* **try / catch:** every UI action catches exceptions and shows a dialog
  (e.g. `CustomerPanel.doPlaceOrder()` catches `NumberFormatException`,
  `InvalidOrderException`, `ProductNotFoundException`, `DatabaseException`).
* **finally:** `OrderProcessingThread.run()`, `InventoryUpdateThread.run()`,
  `ShipmentTrackingThread.run()`. The final step runs on success and on failure.
* **throw / throws:** services `throw new InvalidOrderException(...)`, and
  signatures declare `throws DatabaseException`.
* **multi-catch + rethrow:** `OrderService.processOrder()` catches
  `InsufficientStockException | DatabaseException`, undoes partial work, then
  rethrows. A failure during the undo is attached with `addSuppressed`.
* **Wrapping:** each DAO converts `SQLException` into `DatabaseException`,
  keeping the original as its *cause*.

### 8. What custom exceptions did you create?

All five are checked (`extends Exception`), so the compiler forces callers to handle them:

| Exception | When |
|---|---|
| `InvalidLoginException` | blank or wrong credentials (`UserService.login`) |
| `ProductNotFoundException` | unknown/inactive product id; carries `productId` |
| `InsufficientStockException` | quantity requested > available; carries both numbers |
| `DatabaseException` | wraps any `SQLException` with a safe message |
| `InvalidOrderException` | empty order, bad quantity, illegal status move, not your order |

Checked rather than unchecked: these are expected business situations the
caller *must* react to, not programming bugs.

### 9. Where did you use Multithreading?

| Thread | Purpose |
|---|---|
| `OrderProcessingThread` | started by `OrderService.placeOrder()`; checks and reduces stock, confirms the order, marks payment PAID, creates the shipment, all off the UI thread |
| `InventoryUpdateThread` | FarmerPanel "Set quantity"; writes stock in the background and reports back with `SwingUtilities.invokeLater` |
| `ShipmentTrackingThread` | DistributorPanel "Start tracker"; every 2 s advances shipments like a carrier feed; stopped with `shutdown()` (volatile flag + `interrupt()`) |

Why: database work can be slow, and doing it on Swing's Event Dispatch Thread
freezes the window. `volatile` fields make results written by a background
thread visible to the UI thread.

### 10. Why is synchronization required?

Several threads change the same stock row. Without control, two threads could
both read "10 left", both sell 10, and stock becomes −10. This is a **race condition**.
We use three layers:

1. `synchronized (INVENTORY_LOCK)` in `InventoryDAO`: one thread at a time
   inside this JVM.
2. A **conditional UPDATE**, where the check and the change are one atomic SQL statement:
   `... SET quantity_available = quantity_available - ? WHERE product_id = ? AND quantity_available >= ?`.
   It protects even across two app windows (two JVMs), where `synchronized` can't reach.
3. `CHECK (quantity_available >= 0)` in MySQL as the final safety net.

**Proof:** `OrderIntegrityTest.concurrentOrdersNeverOversell`. 10 threads
order 15 units each with 100 in stock → exactly 6 confirmed, 4 cancelled,
10 left. `concurrentRestocksAreNotLost` shows that no restock is lost.

### 11. How is inventory updated?

* **Farmer adds a product:** `ProductService.addProduct()` inserts the product
  and its inventory row.
* **Farmer sets quantity:** `InventoryUpdateThread` → `InventoryService.setQuantity()`
  (validated: not negative, product exists, farmer owns it).
* **Restock:** `InventoryService.restock()` → `quantity_available + ?`.
* **Order:** `OrderService.processOrder()` → `InventoryService.takeFromStock()`
  → the conditional UPDATE. 0 rows updated means not enough stock, which raises
  `InsufficientStockException`.
* **Undo:** if a later step fails, `compensate()` returns the stock already taken.

### 12. How are foreign keys used?

A foreign key makes MySQL guarantee that a referenced row exists:

* `farmers.user_id`, `distributors.user_id`, `customers.user_id` → `users`
  (UNIQUE as well, so 1:1)
* `products.farmer_id` → `farmers`
* `inventory.product_id` → `products` (UNIQUE → 1:1)
* `orders.customer_id` → `customers`, `orders.distributor_id` → `distributors`
  (`ON DELETE SET NULL`, optional)
* `order_items.order_id` → `orders` (`ON DELETE CASCADE`), `order_items.product_id` → `products`
* `shipments.order_id`, `payments.order_id` → `orders` (UNIQUE → 1:1)

You cannot, for example, insert an order item for a product that doesn't exist.
Because `order_items.product_id` cascades, the app **never hard-deletes a
product that was ordered**: `ProductService.deleteProduct()` marks it INACTIVE
instead, so order history is kept. **Show:** `sql/schema.sql`.

### 13. Explain the database relationships.

```
users 1──1 farmers 1──∞ products 1──1 inventory
users 1──1 distributors 1──∞ orders (optional link)
users 1──1 customers   1──∞ orders 1──∞ order_items ∞──1 products
                              orders 1──1 shipments
                              orders 1──1 payments
```

* One user has exactly one role row (1:1).
* One farmer grows many products (1:N); each product has one stock row (1:1).
* One customer places many orders (1:N); one order has many lines (1:N).
* `order_items` resolves the many-to-many between orders and products, and
  stores `unit_price` at order time because prices change later.
* Each order has at most one shipment and one payment (1:1, `UNIQUE order_id`).

The schema is normalized (3NF): role-specific columns live in their own tables
instead of nullable columns in `users`.

### 14. Explain the complete order workflow.

1. Customer enters product id + quantity → `CustomerPanel.doPlaceOrder()`.
2. `OrderService.placeOrder()` validates the input, prices each line from the
   `HashMap` product cache, inserts the order (PENDING), its items and a
   payment (PENDING), then **starts `OrderProcessingThread`** and returns at once.
3. The thread calls `processOrder()`:
   * **claims** the order PENDING → PROCESSING in one conditional UPDATE
     (`OrderDAO.claimForProcessing`), so it can never be processed twice;
   * reduces stock for each line with the conditional UPDATE;
   * marks the order CONFIRMED and the payment PAID, and creates a shipment
     (PENDING, unique `TRK-` number).
   * **If anything fails:** stock already taken is returned, the order becomes
     CANCELLED and the payment FAILED.
4. The distributor (or `ShipmentTrackingThread`) moves the shipment
   IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED. `ShipmentService.updateStatus()`
   moves the order to SHIPPED, then DELIVERED.
5. The customer sees the status and tracking number in their order history and
   can track the shipment.

### 15. Explain the project architecture.

A layered architecture. Each layer only calls the one below it:

| Layer | Package | Responsibility |
|---|---|---|
| UI | `applet/` | `MainApplet` (a `JApplet`) with a `CardLayout` of Login/Admin/Farmer/Distributor/Customer panels; no SQL |
| Service | `service/` | validation, business rules, ownership checks, starting threads |
| Threads | `threads/` | background order processing, inventory updates, shipment tracking |
| DAO | `dao/` | all SQL via `PreparedStatement`, try-with-resources |
| Database | `database/` | `DBConnection`: one place for URL, user and password (from `db.properties`) |
| Model | `model/` | plain objects (`User`, `Product`, `Order`, ...) passed between layers |
| Exceptions | `exceptions/` | five custom checked exceptions |

It runs two ways: `run-app.bat` (applet hosted in a `JFrame` via
`MainApplet.main`) or `run-applet.bat` (real `appletviewer` with
`applet.policy`, which grants the sandbox permission to reach MySQL).

---

**Likely follow-up questions**

* *Statement vs PreparedStatement?* `Statement` concatenates values into the
  SQL text, which risks injection and recompiles every time. `PreparedStatement`
  binds them, which is safe and reusable.
* *execute vs executeQuery vs executeUpdate?* `executeQuery` returns a
  `ResultSet` (SELECT). `executeUpdate` returns the row count
  (INSERT/UPDATE/DELETE); we use that count to detect "not enough stock".
* *Thread vs Runnable?* Our thread classes extend `Thread`. Implementing
  `Runnable` is more flexible (the class can extend something else, and it
  works with thread pools); that is listed as a future enhancement.
* *Why is the applet run with a policy file?* Applets run in a sandbox that
  blocks network sockets. `applet.policy` grants permissions only to the
  project's jars in `target/`.
* *Why `Class.forName`?* It loads and registers the MySQL driver. JDBC 4
  auto-loading doesn't see jars loaded by the applet class loader.

-- =====================================================================
-- Verification queries for the Agricultural Supply Chain database.
-- Read-only (SELECT only): safe to run any time on a seeded database.
-- Run:  mysql -u agri_app -p agricultural_supply_chain < sql/test_queries.sql
-- =====================================================================
USE agricultural_supply_chain;

-- 1. Users per role ---------------------------------------------------
SELECT role, COUNT(*) AS users FROM users GROUP BY role;

-- 2. Products with current stock (product listing) --------------------
SELECT p.product_id, p.product_name, p.category, p.price, p.unit,
       i.quantity_available AS stock, p.status
FROM products p
LEFT JOIN inventory i ON i.product_id = p.product_id
ORDER BY p.product_id;

-- 3. Product search example (what ProductDAO.search() runs) -----------
SELECT product_id, product_name, price
FROM products
WHERE status = 'ACTIVE' AND product_name LIKE '%heat%';

-- 4. Orders with customer + item count --------------------------------
SELECT o.order_id, u.username AS customer, o.total_amount, o.status,
       COUNT(oi.item_id) AS line_count
FROM orders o
JOIN customers c ON c.customer_id = o.customer_id
JOIN users u ON u.user_id = c.user_id
LEFT JOIN order_items oi ON oi.order_id = o.order_id
GROUP BY o.order_id
ORDER BY o.order_id;

-- 5. Shipments with order status (tracking view) ----------------------
SELECT s.tracking_number, s.status AS shipment_status,
       s.current_location, o.status AS order_status
FROM shipments s
JOIN orders o ON o.order_id = s.order_id
ORDER BY s.shipment_id;

-- 6. Payments ----------------------------------------------------------
SELECT o.order_id, p.amount, p.payment_method, p.payment_status
FROM payments p
JOIN orders o ON o.order_id = p.order_id
ORDER BY o.order_id;

-- 7. Low-stock alert (stock < 50) --------------------------------------
SELECT p.product_name, i.quantity_available
FROM inventory i
JOIN products p ON p.product_id = i.product_id
WHERE i.quantity_available < 50;

-- 8. Farmer sales history (orders containing their products) -----------
SELECT f.farm_name, o.order_id, o.status, oi.quantity, oi.subtotal
FROM farmers f
JOIN products p ON p.farmer_id = f.farmer_id
JOIN order_items oi ON oi.product_id = p.product_id
JOIN orders o ON o.order_id = oi.order_id
ORDER BY f.farmer_id, o.order_id;

-- 9. Revenue from delivered orders -------------------------------------
SELECT COUNT(*) AS delivered_orders,
       COALESCE(SUM(total_amount), 0) AS revenue
FROM orders
WHERE status = 'DELIVERED';

-- 10. Foreign-key check: every order item points at a real product -----
SELECT COUNT(*) AS orphan_items
FROM order_items oi
LEFT JOIN products p ON p.product_id = oi.product_id
WHERE p.product_id IS NULL;

-- =====================================================================
-- Phase 2: Seed data for a fresh database
-- Run:  mysql -u root -p agricultural_supply_chain < sql/seed_data.sql
--
-- Passwords are SHA-256 hashes produced with MySQL SHA2('text',256),
-- identical to Java's MessageDigest("SHA-256") used in UserService.
-- Demo credentials (username / password):
--   admin       / admin123
--   farmer1     / farm123
--   dist1       / dist123
--   cust1       / cust123
-- =====================================================================
USE agricultural_supply_chain;

INSERT INTO users (username, password, full_name, email, phone, role) VALUES
 ('admin',   SHA2('admin123',256), 'System Admin',   'admin@agricsc.in',   '9000000001', 'ADMIN'),
 ('farmer1', SHA2('farm123',256),  'Ramesh Kumar',   'ramesh@farm.in',    '9000000002', 'FARMER'),
 ('dist1',   SHA2('dist123',256),  'Suresh Traders', 'suresh@dist.in',    '9000000003', 'DISTRIBUTOR'),
 ('cust1',   SHA2('cust123',256),  'Amit Sharma',    'amit@mail.in',      '9000000004', 'CUSTOMER');

INSERT INTO farmers (user_id, farm_name, farm_location, registration_number) VALUES
 (2, 'Green Valley Farm', 'Nashik, Maharashtra', 'FARM-REG-1001');

INSERT INTO distributors (user_id, company_name, warehouse_location, license_number) VALUES
 (3, 'Suresh Agro Distributors', 'Pune Warehouse', 'LIC-2001');

INSERT INTO customers (user_id, address, city, pincode) VALUES
 (4, '12 MG Road', 'Mumbai', '400001');

-- Buyer account for the distributor: orders.customer_id is NOT NULL, so
-- distributor wholesale purchases are recorded against this row
-- (UserService.registerDistributor creates the same row for new distributors).
INSERT INTO customers (user_id, address, city, pincode) VALUES
 (3, 'Pune Warehouse', 'Suresh Agro Distributors', '000000');

INSERT INTO products (farmer_id, product_name, category, unit, price, description) VALUES
 (1, 'Wheat',      'Grains',   'kg',  25.50, 'Farm fresh wheat grains'),
 (1, 'Rice',       'Grains',   'kg',  42.00, 'Basmati rice'),
 (1, 'Tomatoes',   'Vegetables','kg', 18.00, 'Farm fresh tomatoes'),
 (1, 'Onions',     'Vegetables','kg', 22.00, 'Red onions');

INSERT INTO inventory (product_id, quantity_available) VALUES
 (1, 500), (2, 300), (3, 150), (4, 200);

-- One completed sample order so reports/shipment history have data
INSERT INTO orders (customer_id, distributor_id, total_amount, status) VALUES
 (1, 1, 126.00, 'DELIVERED');

INSERT INTO order_items (order_id, product_id, quantity, unit_price, subtotal) VALUES
 (1, 2, 3, 42.00, 126.00);

INSERT INTO shipments (order_id, tracking_number, carrier, shipped_date,
                       expected_delivery, current_location, status) VALUES
 (1, 'TRK-2026-0001', 'Self Transport',
  DATE_ADD(NOW(), INTERVAL -3 DAY), DATE_ADD(NOW(), INTERVAL -1 DAY),
  'Mumbai Hub', 'DELIVERED');

INSERT INTO payments (order_id, amount, payment_method, payment_status) VALUES
 (1, 126.00, 'UPI', 'PAID');

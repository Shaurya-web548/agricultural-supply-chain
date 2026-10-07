-- =====================================================================
-- Agricultural Supply Chain Management System
-- Phase 2: Database schema
-- Run:  mysql -u root -p < sql/schema.sql
-- Then: mysql -u root -p agricultural_supply_chain < sql/seed_data.sql
-- Then: mysql -u root -p < sql/app_user.sql  (limited app user)
--
-- Demonstrates: PRIMARY KEY, FOREIGN KEY, NOT NULL, UNIQUE,
--               CHECK (MySQL 8 enforces), DEFAULT, normalised 1:1/1:N.
-- =====================================================================

DROP DATABASE IF EXISTS agricultural_supply_chain;
CREATE DATABASE agricultural_supply_chain
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
USE agricultural_supply_chain;

-- users : central login table for every role (1:1 with farmers/distributors/customers)
CREATE TABLE users (
    user_id       INT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password      VARCHAR(255) NOT NULL,   -- demo hash; see UserService.hashPassword()
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(100) NOT NULL UNIQUE,
    phone         VARCHAR(15),
    role          ENUM('ADMIN','FARMER','DISTRIBUTOR','CUSTOMER')
                  NOT NULL DEFAULT 'CUSTOMER',
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_role CHECK (role IN ('ADMIN','FARMER','DISTRIBUTOR','CUSTOMER'))
) ENGINE=InnoDB;

-- farmers : 1:1 with users
CREATE TABLE farmers (
    farmer_id           INT AUTO_INCREMENT PRIMARY KEY,
    user_id             INT NOT NULL UNIQUE,
    farm_name           VARCHAR(100) NOT NULL,
    farm_location       VARCHAR(150) NOT NULL,
    registration_number VARCHAR(50) NOT NULL UNIQUE,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_farmers_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;

-- distributors : 1:1 with users
CREATE TABLE distributors (
    distributor_id   INT AUTO_INCREMENT PRIMARY KEY,
    user_id          INT NOT NULL UNIQUE,
    company_name     VARCHAR(100) NOT NULL,
    warehouse_location VARCHAR(150) NOT NULL,
    license_number   VARCHAR(50) NOT NULL UNIQUE,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_distributors_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;

-- customers : 1:1 with users
CREATE TABLE customers (
    customer_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id     INT NOT NULL UNIQUE,
    address     VARCHAR(255) NOT NULL,
    city        VARCHAR(100) NOT NULL,
    pincode     VARCHAR(10)  NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_customers_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;

-- products : N products belong to 1 farmer
CREATE TABLE products (
    product_id   INT AUTO_INCREMENT PRIMARY KEY,
    farmer_id    INT NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    category     VARCHAR(50)  NOT NULL DEFAULT 'Grains',
    unit         VARCHAR(20)  NOT NULL DEFAULT 'kg',
    price        DECIMAL(10,2) NOT NULL,
    description  VARCHAR(500),
    status       ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_products_farmer
        FOREIGN KEY (farmer_id) REFERENCES farmers(farmer_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT chk_price CHECK (price > 0),
    CONSTRAINT chk_status CHECK (status IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB;

-- inventory : 1:1 with products; CHECK prevents negative stock at DB level
CREATE TABLE inventory (
    inventory_id       INT AUTO_INCREMENT PRIMARY KEY,
    product_id         INT NOT NULL UNIQUE,
    quantity_available INT NOT NULL DEFAULT 0,
    last_updated       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                               ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_inventory_product
        FOREIGN KEY (product_id) REFERENCES products(product_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT chk_quantity CHECK (quantity_available >= 0)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- orders : N orders per customer; distributor is optional (nullable FK)
-- ---------------------------------------------------------------------
CREATE TABLE orders (
    order_id       INT AUTO_INCREMENT PRIMARY KEY,
    customer_id    INT NOT NULL,
    distributor_id INT DEFAULT NULL,
    order_date     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_amount   DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    status         ENUM('PENDING','PROCESSING','CONFIRMED','SHIPPED','DELIVERED','CANCELLED')
                   NOT NULL DEFAULT 'PENDING',
    CONSTRAINT fk_orders_customer
        FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_orders_distributor
        FOREIGN KEY (distributor_id) REFERENCES distributors(distributor_id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT chk_total CHECK (total_amount >= 0),
    CONSTRAINT chk_order_status CHECK (status IN
        ('PENDING','PROCESSING','CONFIRMED','SHIPPED','DELIVERED','CANCELLED'))
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- order_items : N items per order, N items reference 1 product
-- UNIQUE(order_id, product_id) stops duplicate line items in one order
-- ---------------------------------------------------------------------
CREATE TABLE order_items (
    item_id    INT AUTO_INCREMENT PRIMARY KEY,
    order_id   INT NOT NULL,
    product_id INT NOT NULL,
    quantity   INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    subtotal   DECIMAL(10,2) NOT NULL,
    CONSTRAINT fk_items_order
        FOREIGN KEY (order_id) REFERENCES orders(order_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_items_product
        FOREIGN KEY (product_id) REFERENCES products(product_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT chk_item_qty CHECK (quantity > 0),
    CONSTRAINT chk_item_price CHECK (unit_price > 0 AND subtotal > 0),
    CONSTRAINT uq_order_product UNIQUE (order_id, product_id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- shipments : 1:1 with an order, unique tracking number
-- ---------------------------------------------------------------------
CREATE TABLE shipments (
    shipment_id       INT AUTO_INCREMENT PRIMARY KEY,
    order_id          INT NOT NULL UNIQUE,
    tracking_number   VARCHAR(50) NOT NULL UNIQUE,
    carrier           VARCHAR(80) NOT NULL DEFAULT 'Self Transport',
    shipped_date      DATETIME NULL,
    expected_delivery DATETIME NULL,
    current_location  VARCHAR(150) DEFAULT 'FARMER_WAREHOUSE',
    status            ENUM('PENDING','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','CANCELLED')
                      NOT NULL DEFAULT 'PENDING',
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_shipments_order
        FOREIGN KEY (order_id) REFERENCES orders(order_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT chk_ship_status CHECK (status IN
        ('PENDING','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','CANCELLED'))
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- payments : 1:1 with an order
-- ---------------------------------------------------------------------
CREATE TABLE payments (
    payment_id     INT AUTO_INCREMENT PRIMARY KEY,
    order_id       INT NOT NULL UNIQUE,
    amount         DECIMAL(10,2) NOT NULL,
    payment_method ENUM('COD','CARD','UPI','NET_BANKING') NOT NULL DEFAULT 'COD',
    payment_status ENUM('PENDING','PAID','FAILED','REFUNDED') NOT NULL DEFAULT 'PENDING',
    payment_date   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payments_order
        FOREIGN KEY (order_id) REFERENCES orders(order_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT chk_pay_amount CHECK (amount >= 0),
    CONSTRAINT chk_pay_method CHECK (payment_method IN ('COD','CARD','UPI','NET_BANKING')),
    CONSTRAINT chk_pay_status CHECK (payment_status IN ('PENDING','PAID','FAILED','REFUNDED'))
) ENGINE=InnoDB;

-- Indexes for common lookups (search / reporting)
CREATE INDEX idx_products_name   ON products(product_name);
CREATE INDEX idx_products_farmer ON products(farmer_id);
CREATE INDEX idx_orders_customer ON orders(customer_id);
CREATE INDEX idx_orders_status   ON orders(status);


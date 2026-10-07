-- =====================================================================
-- App-level MySQL user (run ONCE as root, after schema.sql):
--   mysql -u root -p < sql/app_user.sql
-- The application connects as agri_app, NOT as root.
-- agri_app only has read/write access to the project databases.
--
-- BEFORE RUNNING: replace CHANGE_ME below with a password of your choice,
-- and put the same password in both db.properties files.
-- =====================================================================
CREATE USER IF NOT EXISTS 'agri_app'@'localhost'
    IDENTIFIED BY 'CHANGE_ME';

GRANT SELECT, INSERT, UPDATE, DELETE
    ON agricultural_supply_chain.* TO 'agri_app'@'localhost';

-- Tests use a separate database (see src/test/resources/db.properties.example).
-- REFERENCES is needed to create FOREIGN KEYs when rebuilding that schema.
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, INDEX, ALTER, REFERENCES
    ON agricultural_supply_chain_test.* TO 'agri_app'@'localhost';

FLUSH PRIVILEGES;

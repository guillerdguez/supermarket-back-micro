CREATE TABLE IF NOT EXISTS branch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL,
    address VARCHAR(200) UNIQUE NOT NULL,
    is_warehouse BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS branch_inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    branch_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    stock INT NOT NULL,
    min_stock INT NOT NULL DEFAULT 5,
    last_restock_date DATETIME,
    version BIGINT,
    UNIQUE KEY uk_branch_product (branch_id, product_id),
    INDEX idx_inventory_product (product_id),
    FOREIGN KEY (branch_id) REFERENCES branch(id)
);

CREATE TABLE IF NOT EXISTS processed_stock_operations (
    idempotency_key VARCHAR(100) PRIMARY KEY,
    branch_id BIGINT NOT NULL,
    operation VARCHAR(20) NOT NULL,
    processed_at DATETIME NOT NULL
);

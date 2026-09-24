CREATE TABLE IF NOT EXISTS cash_registers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    branch_id BIGINT NOT NULL,
    branch_name VARCHAR(100),
    opening_balance DECIMAL(19, 2) NOT NULL,
    closing_balance DECIMAL(19, 2),
    opening_time DATETIME NOT NULL,
    closing_time DATETIME,
    status VARCHAR(20) NOT NULL,
    opened_by_id BIGINT NOT NULL,
    opened_by_username VARCHAR(50),
    closed_by_id BIGINT,
    closed_by_username VARCHAR(50),
    INDEX idx_cash_registers_branch_status (branch_id, status)
);

CREATE TABLE IF NOT EXISTS sale (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    date DATE,
    status VARCHAR(20),
    total DECIMAL(19, 2),
    branch_id BIGINT NOT NULL,
    branch_name VARCHAR(100),
    cash_register_id BIGINT NOT NULL,
    created_by_id BIGINT,
    created_by_username VARCHAR(50),
    created_by_email VARCHAR(100),
    created_at DATETIME,
    cancelled_by_id BIGINT,
    cancelled_by_username VARCHAR(50),
    cancellation_reason VARCHAR(255),
    cancelled_at DATETIME,
    operation_key VARCHAR(64),
    INDEX idx_sale_branch (branch_id),
    INDEX idx_sale_created_by (created_by_id),
    FOREIGN KEY (cash_register_id) REFERENCES cash_registers(id)
);

CREATE TABLE IF NOT EXISTS sale_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    quantity INT NOT NULL,
    price DECIMAL(19, 2),
    sale_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(100),
    product_category VARCHAR(50),
    INDEX idx_sale_detail_product (product_id),
    FOREIGN KEY (sale_id) REFERENCES sale(id)
);

CREATE TABLE IF NOT EXISTS payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sale_id BIGINT NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    payment_type VARCHAR(20) NOT NULL,
    payment_date DATETIME NOT NULL,
    reference VARCHAR(255),
    FOREIGN KEY (sale_id) REFERENCES sale(id)
);

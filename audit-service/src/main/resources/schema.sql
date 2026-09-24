CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    action VARCHAR(100) NOT NULL,
    details VARCHAR(1000),
    ip_address VARCHAR(50),
    timestamp DATETIME NOT NULL,
    status VARCHAR(20) NOT NULL,
    INDEX idx_audit_logs_timestamp (timestamp)
);

CREATE TABLE IF NOT EXISTS processed_events (
    event_id VARCHAR(64) PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    processed_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    username VARCHAR(50),
    type VARCHAR(50) NOT NULL,
    message VARCHAR(500) NOT NULL,
    data TEXT,
    reference_type VARCHAR(20),
    reference_id BIGINT,
    `read` BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL,
    INDEX idx_notifications_user (user_id, `read`)
);

CREATE TABLE IF NOT EXISTS processed_events (
    event_id VARCHAR(64) PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    processed_at DATETIME NOT NULL
);

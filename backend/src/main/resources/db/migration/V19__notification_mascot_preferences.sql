-- Per-account notification artwork. Missing preference means MELINA.
-- Manual rehearsal and deployment checks: docs/notification-mascot-settings.md.
CREATE TABLE IF NOT EXISTS notification_mascot_preferences (
    account_id BINARY(16) NOT NULL,
    mascot ENUM ('MELINA', 'NAILONG') NOT NULL,
    PRIMARY KEY (account_id),
    CONSTRAINT fk_notification_mascot_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

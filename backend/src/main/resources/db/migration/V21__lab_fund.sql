-- Single audited laboratory fund. Existing balances are not guessed or imported.
CREATE TABLE IF NOT EXISTS fund_accounts (
    id VARCHAR(16) NOT NULL PRIMARY KEY,
    opening_balance DECIMAL(19,2) NOT NULL DEFAULT 0,
    balance DECIMAL(19,2) NOT NULL DEFAULT 0,
    income DECIMAL(19,2) NOT NULL DEFAULT 0,
    expense DECIMAL(19,2) NOT NULL DEFAULT 0,
    opened_on DATE NULL,
    initialized_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS fund_entries (
    id BINARY(16) NOT NULL PRIMARY KEY,
    type VARCHAR(16) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    occurred_on DATE NOT NULL,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(1000) NULL,
    operator_account_id BINARY(16) NOT NULL,
    operator_name VARCHAR(80) NOT NULL,
    recorded_at DATETIME(6) NOT NULL,
    request_key VARCHAR(36) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    original_entry_id BINARY(16) NULL,
    reversal_reason VARCHAR(1000) NULL,
    CONSTRAINT uk_fund_request UNIQUE (request_key),
    CONSTRAINT uk_fund_reversal UNIQUE (original_entry_id),
    CONSTRAINT fk_fund_operator FOREIGN KEY (operator_account_id) REFERENCES accounts(id),
    CONSTRAINT fk_fund_original FOREIGN KEY (original_entry_id) REFERENCES fund_entries(id),
    INDEX idx_fund_date (occurred_on, recorded_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT IGNORE INTO fund_accounts(id) VALUES ('LAB');

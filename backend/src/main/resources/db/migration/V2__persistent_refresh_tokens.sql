CREATE TABLE refresh_tokens (
    remember_login BIT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6),
    account_id BINARY(16) NOT NULL,
    id BINARY(16) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    INDEX idx_refresh_tokens_account (account_id),
    INDEX idx_refresh_tokens_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE achievement_news MODIFY summary LONGTEXT NOT NULL;
ALTER TABLE competitions MODIFY description LONGTEXT NOT NULL;
ALTER TABLE homepage_content MODIFY content_json LONGTEXT NOT NULL;
ALTER TABLE member_profiles MODIFY profile_html LONGTEXT NOT NULL;
ALTER TABLE project_teams MODIFY description LONGTEXT NOT NULL;
ALTER TABLE project_teams MODIFY outcomes LONGTEXT NULL;
ALTER TABLE project_teams MODIFY progress_description LONGTEXT NULL;
ALTER TABLE recruitment_applications MODIFY experience LONGTEXT NULL;
ALTER TABLE recruitment_applications MODIFY interview_evaluation LONGTEXT NULL;

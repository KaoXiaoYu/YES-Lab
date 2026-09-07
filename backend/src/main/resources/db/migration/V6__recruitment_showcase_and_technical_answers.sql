ALTER TABLE recruitment_applications
    ADD COLUMN portfolio_introduction LONGTEXT NULL,
    ADD COLUMN media_links_json LONGTEXT NULL,
    ADD COLUMN technical_question_ids_json LONGTEXT NULL,
    ADD COLUMN technical_answers_json LONGTEXT NULL;

CREATE TABLE recruitment_portfolio_images (
    id BINARY(16) NOT NULL,
    application_id BINARY(16) NOT NULL,
    stored_name VARCHAR(100) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(80) NOT NULL,
    size_bytes BIGINT NOT NULL,
    display_order INTEGER NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_recruitment_portfolio_stored_name UNIQUE (stored_name),
    CONSTRAINT fk_recruitment_portfolio_application FOREIGN KEY (application_id) REFERENCES recruitment_applications (id),
    INDEX idx_recruitment_portfolio_application (application_id, display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

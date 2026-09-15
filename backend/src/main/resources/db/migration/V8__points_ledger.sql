CREATE TABLE point_grants (
    item_total_points INTEGER NOT NULL,
    occurred_on DATE NOT NULL,
    created_at DATETIME(6) NOT NULL,
    id BINARY(16) NOT NULL,
    operator_account_id BINARY(16) NOT NULL,
    reversal_of_grant_id BINARY(16),
    title VARCHAR(160) NOT NULL,
    source_reference VARCHAR(190) NOT NULL,
    evidence_url VARCHAR(1000) NOT NULL,
    description VARCHAR(1000),
    subcategory ENUM ('COMPETITION_AWARD','LAB_ACTIVITY','MEDIA_CONTENT','MEDIA_OPERATION','MEDIA_REACH','PROJECT_TASK') NOT NULL,
    type ENUM ('GRANT','REVERSAL') NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_point_grants_source UNIQUE (source_reference),
    CONSTRAINT uk_point_grants_reversal UNIQUE (reversal_of_grant_id),
    CONSTRAINT fk_point_grants_operator FOREIGN KEY (operator_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_point_grants_reversal FOREIGN KEY (reversal_of_grant_id) REFERENCES point_grants (id),
    INDEX idx_point_grants_occurred_on (occurred_on)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE point_entries (
    points INTEGER NOT NULL,
    requested_points INTEGER NOT NULL,
    created_at DATETIME(6) NOT NULL,
    grant_id BINARY(16) NOT NULL,
    id BINARY(16) NOT NULL,
    member_profile_id BINARY(16) NOT NULL,
    contribution VARCHAR(500) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_point_entries_grant_member UNIQUE (grant_id, member_profile_id),
    CONSTRAINT fk_point_entries_grant FOREIGN KEY (grant_id) REFERENCES point_grants (id),
    CONSTRAINT fk_point_entries_member FOREIGN KEY (member_profile_id) REFERENCES member_profiles (id),
    INDEX idx_point_entries_member (member_profile_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

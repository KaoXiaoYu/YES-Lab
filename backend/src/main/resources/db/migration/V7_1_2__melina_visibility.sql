CREATE TABLE melina_role_visibility (
    role ENUM ('CORE_STUDENT','MEMBER','TEACHER','VISITOR') NOT NULL,
    visible BOOLEAN NOT NULL,
    PRIMARY KEY (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO melina_role_visibility (role, visible) VALUES
    ('TEACHER', TRUE),
    ('CORE_STUDENT', TRUE),
    ('MEMBER', TRUE),
    ('VISITOR', TRUE);

CREATE TABLE melina_account_visibility (
    visible BOOLEAN NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    account_id BINARY(16) NOT NULL,
    PRIMARY KEY (account_id),
    CONSTRAINT fk_melina_visibility_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

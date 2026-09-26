-- 悬赏奖金线下履约台账：管理员登记已发放，获奖成员确认领取。
-- 只新增表并为已有奖金持有者回填待发放记录；不修改 V11—V15 与现有获奖状态。
-- 可重复执行：CREATE TABLE IF NOT EXISTS + 按接取对象唯一键 INSERT IGNORE。

CREATE TABLE IF NOT EXISTS bounty_prize_fulfillments (
    id BINARY(16) NOT NULL,
    task_assignment_id BINARY(16) NOT NULL,
    status ENUM ('PENDING','ISSUED','RECEIVED','REVOKED') NOT NULL DEFAULT 'PENDING',
    issued_at DATETIME(6),
    issued_by_account_id BINARY(16),
    received_at DATETIME(6),
    received_by_profile_id BINARY(16),
    revoked_at DATETIME(6),
    revoked_by_account_id BINARY(16),
    revoked_reason VARCHAR(1000),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_bounty_prize_fulfillments_assignment UNIQUE (task_assignment_id),
    CONSTRAINT fk_bounty_prize_fulfillments_assignment
        FOREIGN KEY (task_assignment_id) REFERENCES task_assignments (id),
    CONSTRAINT fk_bounty_prize_fulfillments_issued_by
        FOREIGN KEY (issued_by_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_bounty_prize_fulfillments_received_by
        FOREIGN KEY (received_by_profile_id) REFERENCES member_profiles (id),
    CONSTRAINT fk_bounty_prize_fulfillments_revoked_by
        FOREIGN KEY (revoked_by_account_id) REFERENCES accounts (id),
    CONSTRAINT ck_bounty_prize_fulfillments_state CHECK (
        (status = 'PENDING' AND issued_at IS NULL AND received_at IS NULL AND revoked_at IS NULL)
        OR (status = 'ISSUED' AND issued_at IS NOT NULL AND issued_by_account_id IS NOT NULL
            AND received_at IS NULL AND revoked_at IS NULL)
        OR (status = 'RECEIVED' AND issued_at IS NOT NULL AND issued_by_account_id IS NOT NULL
            AND received_at IS NOT NULL AND received_by_profile_id IS NOT NULL AND revoked_at IS NULL)
        OR (status = 'REVOKED' AND issued_at IS NULL AND received_at IS NULL
            AND revoked_at IS NOT NULL AND revoked_by_account_id IS NOT NULL)
    ),
    INDEX idx_bounty_prize_fulfillments_status (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 上线前已经获奖的对象也应进入待发放列表，且重复执行不能产生重复记录。
INSERT IGNORE INTO bounty_prize_fulfillments (id, task_assignment_id, status, created_at, updated_at)
SELECT UUID_TO_BIN(UUID()), assignment.id, 'PENDING', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM task_assignments assignment
JOIN tasks task ON task.id = assignment.task_id
WHERE task.task_type = 'BOUNTY' AND assignment.prize_awarded = TRUE;

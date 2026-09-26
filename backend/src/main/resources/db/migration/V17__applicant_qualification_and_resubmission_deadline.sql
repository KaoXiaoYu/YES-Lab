-- 报名者自填正式成员资料；新手/普通任务被驳回后记录个人 24 小时重交窗口。
-- 仅新增字段/表，不修改 V11—V16；采用信息架构探测，使预生产演练可安全重复。

SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'recruitment_applications' AND COLUMN_NAME = 'member_code') = 0,
    'ALTER TABLE recruitment_applications ADD COLUMN member_code VARCHAR(64) NULL',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'recruitment_applications' AND INDEX_NAME = 'uq_recruitment_applications_member_code') = 0,
    'CREATE UNIQUE INDEX uq_recruitment_applications_member_code ON recruitment_applications (member_code)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS recruitment_member_skill_tags (
    application_id BINARY(16) NOT NULL,
    tag VARCHAR(80) NOT NULL,
    CONSTRAINT fk_recruitment_member_skill_tags_application
        FOREIGN KEY (application_id) REFERENCES recruitment_applications (id),
    INDEX idx_recruitment_member_skill_tags_application (application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'task_assignments' AND COLUMN_NAME = 'resubmission_deadline_at') = 0,
    'ALTER TABLE task_assignments ADD COLUMN resubmission_deadline_at DATETIME(6) NULL',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

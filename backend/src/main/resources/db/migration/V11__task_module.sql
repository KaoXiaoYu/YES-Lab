-- 任务模块：普通任务与新手任务
-- 只新增表，不修改或删除任何现有表、账号与业务数据。
-- 统一模型：tasks 是大任务，task_subtasks 是它下面可增删的子任务，
-- task_assignments 是「发给谁 + 完成情况」；新手任务是唯一一条 ONBOARDING 大任务，所有技能测试阶段报名者共享它。

CREATE TABLE tasks (
    id BINARY(16) NOT NULL,
    task_type ENUM ('ONBOARDING','STANDARD') NOT NULL,
    title VARCHAR(160) NOT NULL,
    content_html LONGTEXT NOT NULL,
    start_date DATE,
    end_date DATE,
    points INTEGER NOT NULL,
    duration_days INTEGER,
    status ENUM ('CLOSED','DRAFT','PUBLISHED') NOT NULL,
    published_at DATETIME(6),
    created_by_account_id BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_tasks_creator FOREIGN KEY (created_by_account_id) REFERENCES accounts (id),
    INDEX idx_tasks_type_status (task_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE task_subtasks (
    id BINARY(16) NOT NULL,
    task_id BINARY(16) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content_html LONGTEXT,
    display_order INTEGER NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_task_subtasks_task FOREIGN KEY (task_id) REFERENCES tasks (id),
    INDEX idx_task_subtasks_task (task_id, display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE task_audience_rules (
    id BINARY(16) NOT NULL,
    task_id BINARY(16) NOT NULL,
    dimension ENUM ('GRADE','MEMBER_STATUS','ROLE','SKILL_TAG') NOT NULL,
    rule_value VARCHAR(120) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_task_audience_rules UNIQUE (task_id, dimension, rule_value),
    CONSTRAINT fk_task_audience_rules_task FOREIGN KEY (task_id) REFERENCES tasks (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE task_assignments (
    id BINARY(16) NOT NULL,
    task_id BINARY(16) NOT NULL,
    member_profile_id BINARY(16),
    recruitment_application_id BINARY(16),
    source ENUM ('CRITERIA','MANUAL','ONBOARDING') NOT NULL,
    status ENUM ('APPROVED','PENDING','REJECTED','SUBMITTED') NOT NULL,
    due_date DATE,
    completion_note LONGTEXT,
    submitted_at DATETIME(6),
    reviewed_by_account_id BINARY(16),
    reviewed_at DATETIME(6),
    review_comment VARCHAR(1000),
    exemption_reason VARCHAR(500),
    converted_profile_id BINARY(16),
    point_grant_id BINARY(16),
    awarded_points INTEGER,
    points_skipped_reason VARCHAR(200),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_task_assignments_target
        CHECK ((member_profile_id IS NULL) <> (recruitment_application_id IS NULL)),
    CONSTRAINT uk_task_assignments_member UNIQUE (task_id, member_profile_id),
    CONSTRAINT uk_task_assignments_application UNIQUE (task_id, recruitment_application_id),
    CONSTRAINT fk_task_assignments_task FOREIGN KEY (task_id) REFERENCES tasks (id),
    CONSTRAINT fk_task_assignments_member FOREIGN KEY (member_profile_id) REFERENCES member_profiles (id),
    CONSTRAINT fk_task_assignments_application FOREIGN KEY (recruitment_application_id) REFERENCES recruitment_applications (id),
    CONSTRAINT fk_task_assignments_reviewer FOREIGN KEY (reviewed_by_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_task_assignments_grant FOREIGN KEY (point_grant_id) REFERENCES point_grants (id),
    INDEX idx_task_assignments_member (member_profile_id, status),
    INDEX idx_task_assignments_application (recruitment_application_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE task_subtask_progress (
    id BINARY(16) NOT NULL,
    assignment_id BINARY(16) NOT NULL,
    subtask_id BINARY(16) NOT NULL,
    completed BIT NOT NULL,
    completed_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_task_subtask_progress UNIQUE (assignment_id, subtask_id),
    CONSTRAINT fk_task_subtask_progress_assignment FOREIGN KEY (assignment_id) REFERENCES task_assignments (id),
    CONSTRAINT fk_task_subtask_progress_subtask FOREIGN KEY (subtask_id) REFERENCES task_subtasks (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

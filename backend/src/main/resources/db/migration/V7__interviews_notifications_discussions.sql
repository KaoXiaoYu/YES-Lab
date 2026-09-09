CREATE TABLE notifications (
    aggregation_count INTEGER NOT NULL,
    created_at DATETIME(6) NOT NULL,
    read_at DATETIME(6),
    updated_at DATETIME(6) NOT NULL,
    id BINARY(16) NOT NULL,
    recipient_account_id BINARY(16) NOT NULL,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(160) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    target_path VARCHAR(300),
    group_key VARCHAR(190),
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_account_id) REFERENCES accounts (id),
    INDEX idx_notification_recipient_created (recipient_account_id, created_at),
    INDEX idx_notification_recipient_unread (recipient_account_id, read_at),
    INDEX idx_notification_group (recipient_account_id, group_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE interview_sessions (
    capacity INTEGER NOT NULL,
    next_queue_number INTEGER NOT NULL,
    version BIGINT NOT NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    id BINARY(16) NOT NULL,
    publisher_account_id BINARY(16) NOT NULL,
    location VARCHAR(240) NOT NULL,
    status ENUM ('ACTIVE','CANCELLED','COMPLETED','ENDED_EARLY','SCHEDULED') NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_interview_session_publisher FOREIGN KEY (publisher_account_id) REFERENCES accounts (id),
    INDEX idx_interview_session_start (start_at, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE interview_session_interviewers (
    session_id BINARY(16) NOT NULL,
    account_id BINARY(16) NOT NULL,
    PRIMARY KEY (session_id, account_id),
    CONSTRAINT fk_session_interviewer_session FOREIGN KEY (session_id) REFERENCES interview_sessions (id),
    CONSTRAINT fk_session_interviewer_account FOREIGN KEY (account_id) REFERENCES accounts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE interview_bookings (
    queue_number INTEGER NOT NULL,
    booked_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    id BINARY(16) NOT NULL,
    session_id BINARY(16) NOT NULL,
    application_id BINARY(16) NOT NULL,
    status ENUM ('CALLED','COMPLETED','IN_PROGRESS','WAITING') NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_interview_booking_application UNIQUE (application_id),
    CONSTRAINT uk_interview_booking_queue UNIQUE (session_id, queue_number),
    CONSTRAINT fk_interview_booking_session FOREIGN KEY (session_id) REFERENCES interview_sessions (id),
    CONSTRAINT fk_interview_booking_application FOREIGN KEY (application_id) REFERENCES recruitment_applications (id),
    INDEX idx_interview_booking_queue (session_id, status, queue_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE discussion_posts (
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    author_account_id BINARY(16) NOT NULL,
    id BINARY(16) NOT NULL,
    title VARCHAR(160) NOT NULL,
    content VARCHAR(5000) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_discussion_post_author FOREIGN KEY (author_account_id) REFERENCES accounts (id),
    INDEX idx_discussion_post_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE discussion_replies (
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    author_account_id BINARY(16) NOT NULL,
    id BINARY(16) NOT NULL,
    post_id BINARY(16) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_discussion_reply_author FOREIGN KEY (author_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_discussion_reply_post FOREIGN KEY (post_id) REFERENCES discussion_posts (id) ON DELETE CASCADE,
    INDEX idx_discussion_reply_post_created (post_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE discussion_post_likes (
    created_at DATETIME(6) NOT NULL,
    id BINARY(16) NOT NULL,
    account_id BINARY(16) NOT NULL,
    post_id BINARY(16) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_discussion_post_like UNIQUE (account_id, post_id),
    CONSTRAINT fk_discussion_post_like_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT fk_discussion_post_like_post FOREIGN KEY (post_id) REFERENCES discussion_posts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE discussion_reply_likes (
    created_at DATETIME(6) NOT NULL,
    id BINARY(16) NOT NULL,
    account_id BINARY(16) NOT NULL,
    reply_id BINARY(16) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_discussion_reply_like UNIQUE (account_id, reply_id),
    CONSTRAINT fk_discussion_reply_like_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT fk_discussion_reply_like_reply FOREIGN KEY (reply_id) REFERENCES discussion_replies (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

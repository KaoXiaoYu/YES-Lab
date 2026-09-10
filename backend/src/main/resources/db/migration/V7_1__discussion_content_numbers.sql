CREATE TABLE discussion_content_numbers (
    sequence_number BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    content_id BINARY(16) NOT NULL,
    content_type ENUM ('POST','REPLY') NOT NULL,
    PRIMARY KEY (sequence_number),
    CONSTRAINT uk_discussion_content_number_id UNIQUE (content_id),
    INDEX idx_discussion_content_type_number (content_type, sequence_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO discussion_content_numbers (content_type, content_id, created_at)
SELECT content_type, content_id, created_at
FROM (
    SELECT 'POST' AS content_type, id AS content_id, created_at
    FROM discussion_posts
    UNION ALL
    SELECT 'REPLY' AS content_type, id AS content_id, created_at
    FROM discussion_replies
) AS existing_discussion_content
ORDER BY created_at ASC, content_id ASC;

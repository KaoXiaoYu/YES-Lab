-- V23 preflight checks existing definitions before adding structure; no legacy data is rewritten.
-- Temporary metadata and the scoped assertion procedure are removed after success.
DROP TEMPORARY TABLE IF EXISTS yeslab_v23_expected_columns;
CREATE TEMPORARY TABLE yeslab_v23_expected_columns (
    table_name VARCHAR(64), column_name VARCHAR(64), column_type VARCHAR(64), is_nullable VARCHAR(3)
);
INSERT INTO yeslab_v23_expected_columns VALUES
    ('recruitment_form_versions','id','binary(16)','NO'),
    ('recruitment_form_versions','version_number','bigint','NO'),
    ('recruitment_form_versions','schema_json','longtext','NO'),
    ('recruitment_form_versions','published_by_account_id','binary(16)','YES'),
    ('recruitment_form_versions','published_by','varchar(190)','NO'),
    ('recruitment_form_versions','published_at','datetime(6)','NO'),
    ('recruitment_form_config','id','bigint','NO'),
    ('recruitment_form_config','draft_schema_json','longtext','NO'),
    ('recruitment_form_config','draft_revision','bigint','NO'),
    ('recruitment_form_config','published_draft_revision','bigint','NO'),
    ('recruitment_form_config','published_version_id','binary(16)','NO'),
    ('recruitment_form_config','updated_by','varchar(190)','NO'),
    ('recruitment_form_config','updated_at','datetime(6)','NO'),
    ('recruitment_form_config','lock_version','bigint','NO'),
    ('recruitment_form_attachments','id','binary(16)','NO'),
    ('recruitment_form_attachments','application_id','binary(16)','NO'),
    ('recruitment_form_attachments','owner_account_id','binary(16)','NO'),
    ('recruitment_form_attachments','module_id','binary(16)','NO'),
    ('recruitment_form_attachments','stored_name','varchar(255)','NO'),
    ('recruitment_form_attachments','original_name','varchar(255)','NO'),
    ('recruitment_form_attachments','content_type','varchar(80)','NO'),
    ('recruitment_form_attachments','size_bytes','bigint','NO'),
    ('recruitment_form_attachments','display_order','int','NO'),
    ('recruitment_form_attachments','active','bit(1)','NO'),
    ('recruitment_form_attachments','created_at','datetime(6)','NO'),
    ('recruitment_form_submissions','id','binary(16)','NO'),
    ('recruitment_form_submissions','account_id','binary(16)','NO'),
    ('recruitment_form_submissions','request_key','binary(16)','NO'),
    ('recruitment_form_submissions','request_fingerprint','varchar(64)','NO'),
    ('recruitment_form_submissions','application_id','binary(16)','NO'),
    ('recruitment_form_submissions','form_version_id','binary(16)','NO'),
    ('recruitment_form_submissions','result_json','longtext','NO'),
    ('recruitment_form_submissions','created_at','datetime(6)','NO'),
    ('recruitment_applications','form_version_id','binary(16)','YES'),
    ('recruitment_applications','form_answers_json','longtext','YES'),
    ('recruitment_applications','form_answers_revision','bigint','NO');

DROP TEMPORARY TABLE IF EXISTS yeslab_v23_expected_keys;
CREATE TEMPORARY TABLE yeslab_v23_expected_keys (
    table_name VARCHAR(64), constraint_name VARCHAR(64), constraint_type VARCHAR(16), column_names VARCHAR(128),
    ref_table VARCHAR(64), ref_columns VARCHAR(128)
);
INSERT INTO yeslab_v23_expected_keys VALUES
    ('recruitment_form_versions','PRIMARY','PRIMARY KEY','id','',''),
    ('recruitment_form_versions','uk_form_version_number','UNIQUE','version_number','',''),
    ('recruitment_form_versions','fk_form_version_publisher','FOREIGN KEY','published_by_account_id','accounts','id'),
    ('recruitment_form_config','PRIMARY','PRIMARY KEY','id','',''),
    ('recruitment_form_config','fk_form_config_publication','FOREIGN KEY','published_version_id','recruitment_form_versions','id'),
    ('recruitment_form_attachments','PRIMARY','PRIMARY KEY','id','',''),
    ('recruitment_form_attachments','uk_form_attachment_stored','UNIQUE','stored_name','',''),
    ('recruitment_form_attachments','fk_form_attachment_application','FOREIGN KEY','application_id','recruitment_applications','id'),
    ('recruitment_form_attachments','fk_form_attachment_owner','FOREIGN KEY','owner_account_id','accounts','id'),
    ('recruitment_form_submissions','PRIMARY','PRIMARY KEY','id','',''),
    ('recruitment_form_submissions','uk_form_submission_request','UNIQUE','account_id,request_key','',''),
    ('recruitment_form_submissions','fk_form_submission_account','FOREIGN KEY','account_id','accounts','id'),
    ('recruitment_form_submissions','fk_form_submission_application','FOREIGN KEY','application_id','recruitment_applications','id'),
    ('recruitment_form_submissions','fk_form_submission_version','FOREIGN KEY','form_version_id','recruitment_form_versions','id'),
    ('recruitment_applications','fk_application_form_version','FOREIGN KEY','form_version_id','recruitment_form_versions','id');

DROP PROCEDURE IF EXISTS yeslab_v23_assert_form_schema;
DELIMITER $$
CREATE PROCEDURE yeslab_v23_assert_form_schema(IN require_complete BOOLEAN)
BEGIN
    IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
        AND table_name IN ('accounts','recruitment_applications') AND engine='InnoDB') <> 2
        OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name IN ('accounts','recruitment_applications') AND column_name='id' AND column_type='binary(16)') <> 2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V23 requires existing InnoDB accounts/applications with BINARY(16) IDs';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE()
        AND table_name IN ('recruitment_form_config','recruitment_form_versions','recruitment_form_attachments','recruitment_form_submissions')
        AND (engine IS NULL OR engine <> 'InnoDB')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V23 incompatible existing recruitment form table engine';
    END IF;
    IF EXISTS (SELECT 1 FROM yeslab_v23_expected_columns e
        JOIN information_schema.columns c ON c.table_schema=DATABASE() AND c.table_name=e.table_name AND c.column_name=e.column_name
        WHERE c.column_type<>e.column_type OR c.is_nullable<>e.is_nullable) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V23 incompatible existing recruitment form column type/nullability';
    END IF;
    IF require_complete AND EXISTS (SELECT 1 FROM yeslab_v23_expected_columns e
        LEFT JOIN information_schema.columns c ON c.table_schema=DATABASE() AND c.table_name=e.table_name AND c.column_name=e.column_name
        WHERE c.column_name IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V23 incomplete existing recruitment form table: required column missing';
    END IF;
    IF require_complete AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='recruitment_applications' AND column_name='form_answers_revision' AND (column_default IS NULL OR column_default<>'0')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V23 form_answers_revision requires DEFAULT 0';
    END IF;
    IF EXISTS (SELECT 1 FROM yeslab_v23_expected_keys e
        JOIN information_schema.table_constraints t ON t.constraint_schema=DATABASE() AND t.table_name=e.table_name AND t.constraint_name=e.constraint_name
        LEFT JOIN (SELECT table_name, constraint_name, GROUP_CONCAT(column_name ORDER BY ordinal_position SEPARATOR ',') AS column_names,
            COALESCE(MAX(referenced_table_name),'') AS ref_table, COALESCE(GROUP_CONCAT(referenced_column_name ORDER BY ordinal_position SEPARATOR ','),'') AS ref_columns,
            MAX(referenced_table_schema) AS ref_schema
            FROM information_schema.key_column_usage WHERE constraint_schema=DATABASE() GROUP BY table_name,constraint_name) k
            ON k.table_name=e.table_name AND k.constraint_name=e.constraint_name
        WHERE t.constraint_type<>e.constraint_type OR COALESCE(k.column_names,'')<>e.column_names
            OR COALESCE(k.ref_table,'')<>e.ref_table OR COALESCE(k.ref_columns,'')<>e.ref_columns
            OR (e.ref_table<>'' AND k.ref_schema<>DATABASE())) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V23 incompatible existing recruitment form primary/unique/foreign key';
    END IF;
    IF require_complete AND EXISTS (SELECT 1 FROM yeslab_v23_expected_keys e
        LEFT JOIN information_schema.table_constraints t ON t.constraint_schema=DATABASE() AND t.table_name=e.table_name AND t.constraint_name=e.constraint_name
        WHERE t.constraint_name IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V23 incomplete recruitment form primary/unique/foreign key';
    END IF;
END$$
DELIMITER ;
CALL yeslab_v23_assert_form_schema(FALSE);

-- Modular recruitment form foundation. MySQL only.
-- Historical applications remain on the legacy form (form_version_id IS NULL).
-- Default schema/version are initialized transactionally by the application.
CREATE TABLE IF NOT EXISTS recruitment_form_versions (
    id BINARY(16) NOT NULL,
    version_number BIGINT NOT NULL,
    schema_json LONGTEXT NOT NULL,
    published_by_account_id BINARY(16) NULL,
    published_by VARCHAR(190) NOT NULL,
    published_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_form_version_number UNIQUE (version_number),
    CONSTRAINT fk_form_version_publisher FOREIGN KEY (published_by_account_id) REFERENCES accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS recruitment_form_config (
    id BIGINT NOT NULL,
    draft_schema_json LONGTEXT NOT NULL,
    draft_revision BIGINT NOT NULL DEFAULT 0,
    published_draft_revision BIGINT NOT NULL DEFAULT 0,
    published_version_id BINARY(16) NOT NULL,
    updated_by VARCHAR(190) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_form_config_publication FOREIGN KEY (published_version_id) REFERENCES recruitment_form_versions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='recruitment_applications' AND column_name='form_version_id')=0, 'ALTER TABLE recruitment_applications ADD COLUMN form_version_id BINARY(16) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='recruitment_applications' AND column_name='form_answers_json')=0, 'ALTER TABLE recruitment_applications ADD COLUMN form_answers_json LONGTEXT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='recruitment_applications' AND column_name='form_answers_revision')=0, 'ALTER TABLE recruitment_applications ADD COLUMN form_answers_revision BIGINT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND table_name='recruitment_applications' AND constraint_name='fk_application_form_version')=0, 'ALTER TABLE recruitment_applications ADD CONSTRAINT fk_application_form_version FOREIGN KEY (form_version_id) REFERENCES recruitment_form_versions(id)', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS recruitment_form_attachments (
    id BINARY(16) NOT NULL,
    application_id BINARY(16) NOT NULL,
    owner_account_id BINARY(16) NOT NULL,
    module_id BINARY(16) NOT NULL,
    stored_name VARCHAR(255) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(80) NOT NULL,
    size_bytes BIGINT NOT NULL,
    display_order INTEGER NOT NULL,
    active BIT(1) NOT NULL DEFAULT b'1',
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_form_attachment_stored UNIQUE (stored_name),
    CONSTRAINT fk_form_attachment_application FOREIGN KEY (application_id) REFERENCES recruitment_applications(id),
    CONSTRAINT fk_form_attachment_owner FOREIGN KEY (owner_account_id) REFERENCES accounts(id),
    INDEX idx_form_attachment_application (application_id, module_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS recruitment_form_submissions (
    id BINARY(16) NOT NULL,
    account_id BINARY(16) NOT NULL,
    request_key BINARY(16) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    application_id BINARY(16) NOT NULL,
    form_version_id BINARY(16) NOT NULL,
    result_json LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_form_submission_request UNIQUE (account_id, request_key),
    CONSTRAINT fk_form_submission_account FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT fk_form_submission_application FOREIGN KEY (application_id) REFERENCES recruitment_applications(id),
    CONSTRAINT fk_form_submission_version FOREIGN KEY (form_version_id) REFERENCES recruitment_form_versions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CALL yeslab_v23_assert_form_schema(TRUE);
DROP PROCEDURE yeslab_v23_assert_form_schema;
DROP TEMPORARY TABLE yeslab_v23_expected_columns;
DROP TEMPORARY TABLE yeslab_v23_expected_keys;

-- Manual MySQL rehearsal: docs/points-linkage-requirements.md section 5.
-- Existing rows, evidence and deterministic task source references remain unchanged.

SET @yeslab_point_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'point_grants' AND column_name = 'competition_id') = 0,
    'ALTER TABLE point_grants ADD COLUMN competition_id BINARY(16) NULL', 'SELECT 1');
PREPARE yeslab_point_stmt FROM @yeslab_point_ddl;
EXECUTE yeslab_point_stmt;
DEALLOCATE PREPARE yeslab_point_stmt;

SET @yeslab_point_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'point_grants' AND column_name = 'project_id') = 0,
    'ALTER TABLE point_grants ADD COLUMN project_id BINARY(16) NULL', 'SELECT 1');
PREPARE yeslab_point_stmt FROM @yeslab_point_ddl;
EXECUTE yeslab_point_stmt;
DEALLOCATE PREPARE yeslab_point_stmt;

SET @yeslab_point_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'point_grants' AND column_name = 'source_entity_reference') = 0,
    'ALTER TABLE point_grants ADD COLUMN source_entity_reference VARCHAR(36) NULL', 'SELECT 1');
PREPARE yeslab_point_stmt FROM @yeslab_point_ddl;
EXECUTE yeslab_point_stmt;
DEALLOCATE PREPARE yeslab_point_stmt;

SET @yeslab_point_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'point_grants' AND column_name = 'source_name') = 0,
    'ALTER TABLE point_grants ADD COLUMN source_name VARCHAR(180) NULL', 'SELECT 1');
PREPARE yeslab_point_stmt FROM @yeslab_point_ddl;
EXECUTE yeslab_point_stmt;
DEALLOCATE PREPARE yeslab_point_stmt;

SET @yeslab_point_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'point_grants' AND column_name = 'request_key') = 0,
    'ALTER TABLE point_grants ADD COLUMN request_key BINARY(16) NULL', 'SELECT 1');
PREPARE yeslab_point_stmt FROM @yeslab_point_ddl;
EXECUTE yeslab_point_stmt;
DEALLOCATE PREPARE yeslab_point_stmt;

SET @yeslab_point_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'point_grants' AND column_name = 'request_fingerprint') = 0,
    'ALTER TABLE point_grants ADD COLUMN request_fingerprint VARCHAR(64) NULL', 'SELECT 1');
PREPARE yeslab_point_stmt FROM @yeslab_point_ddl;
EXECUTE yeslab_point_stmt;
DEALLOCATE PREPARE yeslab_point_stmt;

SET @yeslab_point_ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'point_grants' AND index_name = 'uk_point_grants_request') = 0,
    'ALTER TABLE point_grants ADD UNIQUE INDEX uk_point_grants_request (request_key)', 'SELECT 1');
PREPARE yeslab_point_stmt FROM @yeslab_point_ddl;
EXECUTE yeslab_point_stmt;
DEALLOCATE PREPARE yeslab_point_stmt;

SET @yeslab_point_ddl = IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'point_grants' AND constraint_name = 'fk_point_grants_competition') = 0,
    'ALTER TABLE point_grants ADD CONSTRAINT fk_point_grants_competition FOREIGN KEY (competition_id) REFERENCES competitions (id) ON DELETE SET NULL', 'SELECT 1');
PREPARE yeslab_point_stmt FROM @yeslab_point_ddl;
EXECUTE yeslab_point_stmt;
DEALLOCATE PREPARE yeslab_point_stmt;

SET @yeslab_point_ddl = IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'point_grants' AND constraint_name = 'fk_point_grants_project') = 0,
    'ALTER TABLE point_grants ADD CONSTRAINT fk_point_grants_project FOREIGN KEY (project_id) REFERENCES project_teams (id) ON DELETE SET NULL', 'SELECT 1');
PREPARE yeslab_point_stmt FROM @yeslab_point_ddl;
EXECUTE yeslab_point_stmt;
DEALLOCATE PREPARE yeslab_point_stmt;

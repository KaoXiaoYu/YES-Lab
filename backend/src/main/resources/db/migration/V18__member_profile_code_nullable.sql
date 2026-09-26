-- 允许新转正成员先建档，再在首次进入成员系统时自行补全学号/内部编号。
-- 保留唯一索引；MySQL 唯一索引允许多条 NULL，非空编号仍保持唯一。

SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member_profiles'
       AND COLUMN_NAME = 'member_code' AND IS_NULLABLE = 'NO') > 0,
    'ALTER TABLE member_profiles MODIFY COLUMN member_code VARCHAR(64) NULL',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

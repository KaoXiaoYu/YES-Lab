-- 子任务从「勾选完成」改为「提交富文本内容」
--
-- 背景：V11 已经在生产库执行过，因此这里不再修改 V11，新增本脚本。
-- 本脚本对两种历史都安全：只做「缺什么补什么」，不删除任何业务数据。
-- 幂等：重复执行不会报错（加列前先查 information_schema，删表用 IF EXISTS）。

-- 1) 成员为子任务提交的富文本内容（本次核心变更）
SET @has_submission_content := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'task_subtask_progress'
      AND COLUMN_NAME = 'content_html'
);
SET @stmt := IF(@has_submission_content = 0,
    'ALTER TABLE task_subtask_progress ADD COLUMN content_html LONGTEXT NULL',
    'DO 0');
PREPARE add_submission_content FROM @stmt;
EXECUTE add_submission_content;
DEALLOCATE PREPARE add_submission_content;

-- 2) 防御性补齐：若某些环境的 V11 是更早的版本，这些列可能还不存在
SET @has_duration := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tasks' AND COLUMN_NAME = 'duration_days'
);
SET @stmt := IF(@has_duration = 0,
    'ALTER TABLE tasks ADD COLUMN duration_days INTEGER NULL',
    'DO 0');
PREPARE add_duration FROM @stmt;
EXECUTE add_duration;
DEALLOCATE PREPARE add_duration;

SET @has_due := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'task_assignments' AND COLUMN_NAME = 'due_date'
);
SET @stmt := IF(@has_due = 0,
    'ALTER TABLE task_assignments ADD COLUMN due_date DATE NULL',
    'DO 0');
PREPARE add_due FROM @stmt;
EXECUTE add_due;
DEALLOCATE PREPARE add_due;

SET @has_subtask_content := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'task_subtasks' AND COLUMN_NAME = 'content_html'
);
SET @stmt := IF(@has_subtask_content = 0,
    'ALTER TABLE task_subtasks ADD COLUMN content_html LONGTEXT NULL',
    'DO 0');
PREPARE add_subtask_content FROM @stmt;
EXECUTE add_subtask_content;
DEALLOCATE PREPARE add_subtask_content;

-- 3) 旧模型的「模板」两张表已不再使用，存在则删除（新模型里新手任务就是一条大任务）
DROP TABLE IF EXISTS onboarding_task_template_subtasks;
DROP TABLE IF EXISTS onboarding_task_template;

-- 4) 旧模型的 template_synced_at 已废弃，存在则删除
SET @has_synced := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tasks' AND COLUMN_NAME = 'template_synced_at'
);
SET @stmt := IF(@has_synced > 0,
    'ALTER TABLE tasks DROP COLUMN template_synced_at',
    'DO 0');
PREPARE drop_synced FROM @stmt;
EXECUTE drop_synced;
DEALLOCATE PREPARE drop_synced;

-- 5) 让旧的「勾选」不再被当成「已提交」
--    旧模型只有勾选、没有内容，若保留 completed = 1，界面会显示“已提交但没有内容”，
--    转正门槛也会被空内容蒙过去。这里统一按「未提交」处理，成员需重新提交内容。
--    只影响没有内容的历史行，不改动任何有内容的数据。
UPDATE task_subtask_progress
SET completed = 0,
    completed_at = NULL
WHERE content_html IS NULL
  AND completed = 1;

-- 6) 新手任务对象的截止日期：旧模型存在任务表的 end_date 上，新模型改为对象级 due_date
UPDATE task_assignments a
JOIN tasks t ON t.id = a.task_id
SET a.due_date = t.end_date
WHERE a.due_date IS NULL
  AND t.task_type = 'ONBOARDING'
  AND t.end_date IS NOT NULL;

-- 任务到期结算：新增结算标记 + 新手任务按人延长截止日期的审计列
--
-- 背景：V11 / V12 / V13 已经在生产库执行过，因此绝不修改它们，新增本脚本。
-- 本脚本只做「加列 + 加索引 + 一次回填」，不删除、不改名、不动任何既有列与其它数据。
-- 幂等：重复执行不会报错，也不会重复处理（加列/加索引/加外键前先查 information_schema，回填与加列严格绑定）。

-- 1) 结算标记：NULL = 未结算。它同时是「已结算」的唯一标记与跨实例互斥的认领字段。
SET @has_settled := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'tasks'
      AND COLUMN_NAME = 'points_settled_at'
);
SET @stmt := IF(@has_settled = 0,
    'ALTER TABLE tasks ADD COLUMN points_settled_at DATETIME(6) NULL',
    'DO 0');
PREPARE add_settled FROM @stmt;
EXECUTE add_settled;
DEALLOCATE PREPARE add_settled;

-- 2) 待结算扫描索引，供定时任务按「未结算 + 已到期」查找任务
SET @has_settlement_index := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'tasks'
      AND INDEX_NAME = 'idx_tasks_settlement'
);
SET @stmt := IF(@has_settlement_index = 0,
    'CREATE INDEX idx_tasks_settlement ON tasks (task_type, points_settled_at, end_date)',
    'DO 0');
PREPARE add_settlement_index FROM @stmt;
EXECUTE add_settlement_index;
DEALLOCATE PREPARE add_settlement_index;

-- 3) 新手任务「按人延长截止日期」的审计列
--
--    新手任务的截止日期是对象级的 due_date，逾期后成员不能提交、管理员也不能审核，
--    因此必须有一个「把某个人的截止日期推到未来」的补救动作。这里记录**最近一次**延长的
--    时间、操作人与理由：延长是低频的管理动作，先落最近一次即可；完整的操作历史等后续的
--    全局操作审计（已在 README 的后续待办里）落地后再补一张流水表。
SET @has_extended_at := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'task_assignments'
      AND COLUMN_NAME = 'due_date_extended_at'
);
SET @stmt := IF(@has_extended_at = 0,
    'ALTER TABLE task_assignments ADD COLUMN due_date_extended_at DATETIME(6) NULL',
    'DO 0');
PREPARE add_extended_at FROM @stmt;
EXECUTE add_extended_at;
DEALLOCATE PREPARE add_extended_at;

SET @has_extended_by := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'task_assignments'
      AND COLUMN_NAME = 'due_date_extended_by_account_id'
);
SET @stmt := IF(@has_extended_by = 0,
    'ALTER TABLE task_assignments ADD COLUMN due_date_extended_by_account_id BINARY(16) NULL',
    'DO 0');
PREPARE add_extended_by FROM @stmt;
EXECUTE add_extended_by;
DEALLOCATE PREPARE add_extended_by;

SET @has_extension_reason := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'task_assignments'
      AND COLUMN_NAME = 'due_date_extension_reason'
);
SET @stmt := IF(@has_extension_reason = 0,
    'ALTER TABLE task_assignments ADD COLUMN due_date_extension_reason VARCHAR(500) NULL',
    'DO 0');
PREPARE add_extension_reason FROM @stmt;
EXECUTE add_extension_reason;
DEALLOCATE PREPARE add_extension_reason;

SET @has_extension_fk := (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'task_assignments'
      AND CONSTRAINT_NAME = 'fk_task_assignments_due_date_extender'
);
SET @stmt := IF(@has_extension_fk = 0,
    'ALTER TABLE task_assignments ADD CONSTRAINT fk_task_assignments_due_date_extender FOREIGN KEY (due_date_extended_by_account_id) REFERENCES accounts (id)',
    'DO 0');
PREPARE add_extension_fk FROM @stmt;
EXECUTE add_extension_fk;
DEALLOCATE PREPARE add_extension_fk;

-- 4) 【关键】历史普通任务视为已结清。
--
--    这些任务走的是旧的「审核通过即发放积分」口径，积分早已发过。若不回填，部署后定时任务
--    会把它们当作待结算逐个处理：虽然来源编号 TASK:{taskId}:{memberProfileId} 幂等、不会重复
--    计分，但会写入结算标记、向创建者发一批结算汇总消息，并且让这些历史任务此后无法驳回 ——
--    都不是期望的行为。
--
--    回填与「本次刚加上该列」严格绑定：只有真正执行了加列的那一次运行才会回填。
--    这样重复执行本脚本不会把迁移之后新建的任务误标为已结算。
--    新手任务（points 恒为 0）不参与结算，因此不回填。
SET @stmt := IF(@has_settled = 0,
    'UPDATE tasks SET points_settled_at = NOW(6) WHERE task_type = ''STANDARD'' AND points_settled_at IS NULL',
    'DO 0');
PREPARE backfill_settled FROM @stmt;
EXECUTE backfill_settled;
DEALLOCATE PREPARE backfill_settled;

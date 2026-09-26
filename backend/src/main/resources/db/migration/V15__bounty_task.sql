-- 悬赏任务：第三类任务（成员自主接取、名额上限、先完成先得奖金、可选积分）
--
-- 背景：V11 已在生产库执行过，且 V14 已经引入结算标记与新手任务延长留痕。
-- 本脚本只做「ENUM 追加 + 加列」，不删除、不改名、不动任何既有列与数据，也不触碰 V11—V14。
-- 幂等：加列前先查 information_schema，可重复执行。
--
-- ⚠️ ENUM 新值**只能追加到末尾**：MySQL 按序号存储枚举，插到中间或调整顺序会让存量行
--    按新序号解释成别的值。三处 MODIFY 都写出了完整定义且新值在最后。

-- 1) 任务类型追加 BOUNTY
ALTER TABLE tasks
    MODIFY COLUMN task_type ENUM ('ONBOARDING','STANDARD','BOUNTY') NOT NULL;

-- 2) 接取人数上限：NULL = 不限人数；非空 = 最多 n 人接取（先到先得、接满即止）
SET @has_headcount := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tasks' AND COLUMN_NAME = 'headcount_limit'
);
SET @stmt := IF(@has_headcount = 0,
    'ALTER TABLE tasks ADD COLUMN headcount_limit INTEGER NULL',
    'DO 0');
PREPARE add_headcount FROM @stmt;
EXECUTE add_headcount;
DEALLOCATE PREPARE add_headcount;

-- 3) 奖金份数 m：NULL = 不设奖金；非空表示最先完成的 m 人获奖
SET @has_prize_slots := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tasks' AND COLUMN_NAME = 'prize_slots'
);
SET @stmt := IF(@has_prize_slots = 0,
    'ALTER TABLE tasks ADD COLUMN prize_slots INTEGER NULL',
    'DO 0');
PREPARE add_prize_slots FROM @stmt;
EXECUTE add_prize_slots;
DEALLOCATE PREPARE add_prize_slots;

-- 4) 奖金说明：一段统一说明（线下发放，系统只登记）
SET @has_prize_description := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tasks' AND COLUMN_NAME = 'prize_description'
);
SET @stmt := IF(@has_prize_description = 0,
    'ALTER TABLE tasks ADD COLUMN prize_description VARCHAR(500) NULL',
    'DO 0');
PREPARE add_prize_description FROM @stmt;
EXECUTE add_prize_description;
DEALLOCATE PREPARE add_prize_description;

-- 5) 对象来源追加 CLAIM（成员自主接取）
ALTER TABLE task_assignments
    MODIFY COLUMN source ENUM ('CRITERIA','MANUAL','ONBOARDING','CLAIM') NOT NULL;

-- 6) 对象状态追加 ABANDONED（本人放弃 / 管理员移除；归还接取名额，本人不可再接）
ALTER TABLE task_assignments
    MODIFY COLUMN status ENUM ('APPROVED','PENDING','REJECTED','SUBMITTED','ABANDONED') NOT NULL;

-- 7) 完成名次：完成时锁定「第几个完成」，一经分配不再重算
SET @has_rank := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'task_assignments' AND COLUMN_NAME = 'completion_rank'
);
SET @stmt := IF(@has_rank = 0,
    'ALTER TABLE task_assignments ADD COLUMN completion_rank INTEGER NULL',
    'DO 0');
PREPARE add_rank FROM @stmt;
EXECUTE add_rank;
DEALLOCATE PREPARE add_rank;

-- 8) 是否持有奖金份额：这是「奖金持有者 = 已完成对象里名次最靠前的 min(m, 已完成人数) 位」
--    这条不变量在库里的投影列，会在「完成」与「驳回」后重算（驳回后顺延给下一位）。
SET @has_prize_awarded := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'task_assignments' AND COLUMN_NAME = 'prize_awarded'
);
SET @stmt := IF(@has_prize_awarded = 0,
    'ALTER TABLE task_assignments ADD COLUMN prize_awarded BOOLEAN NOT NULL DEFAULT FALSE',
    'DO 0');
PREPARE add_prize_awarded FROM @stmt;
EXECUTE add_prize_awarded;
DEALLOCATE PREPARE add_prize_awarded;

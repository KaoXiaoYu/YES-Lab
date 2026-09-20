-- 停用试用期阶段：把仍停留在试用期的报名记录批量回退到技能测试阶段。
--
-- 设计要点：
-- 1. 只修正 recruitment_applications.stage，不删除任何账号、报名记录或成员档案。
-- 2. 不改动任何 ENUM 定义：PROBATION 继续作为合法枚举值存在，历史状态记录仍可反序列化。
-- 3. 先写状态历史再改阶段，保留可审计的回退依据；操作人使用系统保留值
--    （recruitment_status_history.operator_account_id 无外键约束，operator_username 为冗余展示字段）。
-- 4. 本脚本不生成新手任务：新手任务正文与子任务清单属于业务配置，复制到 SQL 会与代码内置默认模板
--    形成两份富文本内容。补发由管理端的「批量补发新手任务」幂等操作完成，见上线检查清单。

CREATE TEMPORARY TABLE tmp_probation_rollback (
    application_id BINARY(16) NOT NULL,
    PRIMARY KEY (application_id)
) ENGINE=InnoDB;

INSERT INTO tmp_probation_rollback (application_id)
SELECT id FROM recruitment_applications WHERE stage = 'PROBATION';

-- 状态历史：以系统身份记录本次批量回退
INSERT INTO recruitment_status_history
    (id, application_id, from_stage, to_stage, operator_account_id, operator_username, note, changed_at)
SELECT UUID_TO_BIN(UUID()),
       application_id,
       'PROBATION',
       'SKILL_TEST',
       UUID_TO_BIN('00000000-0000-0000-0000-000000000000'),
       'system',
       '试用期阶段已取消，系统批量回退至技能测试阶段；请在任务管理中批量补发新手任务',
       NOW(6)
FROM tmp_probation_rollback;

-- 阶段回退
UPDATE recruitment_applications
SET stage = 'SKILL_TEST'
WHERE stage = 'PROBATION';

DROP TEMPORARY TABLE tmp_probation_rollback;

package cn.openlims.platform.task.model;

/**
 * 任务发放条件维度，全部复用现有成员字段，不新增等级字段。
 *
 * <p>同一维度内多个取值取「或」，不同维度之间取「且」。</p>
 */
public enum TaskAudienceDimension {
    ROLE,
    MEMBER_STATUS,
    GRADE,
    SKILL_TAG
}

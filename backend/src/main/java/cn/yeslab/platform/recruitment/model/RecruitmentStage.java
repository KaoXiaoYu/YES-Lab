package cn.yeslab.platform.recruitment.model;

/**
 * 招新阶段。
 *
 * <p>流程为：报名 → 初筛 → 面试 → 技能测试 → 正式成员。</p>
 *
 * <p>{@link #PROBATION} 已停用：招新流程不再经过试用期，技能测试阶段完成新手任务并经管理员
 * 审核通过后直接转为正式成员。该枚举值仅为历史报名记录与历史状态记录的反序列化兼容而保留，
 * 状态机中没有任何路径可以进入它，只能被离开（打回技能测试阶段）。</p>
 */
public enum RecruitmentStage {
    SIGNUP,
    SCREENING,
    INTERVIEW,
    SKILL_TEST,
    /** 已停用，仅兼容历史数据读取；不可进入。 */
    PROBATION,
    FORMAL_MEMBER,
    REJECTED
}

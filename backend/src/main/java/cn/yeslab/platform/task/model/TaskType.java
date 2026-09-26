package cn.yeslab.platform.task.model;

/**
 * 任务类型。
 *
 * <p>{@link #ONBOARDING} 新手任务面向招新报名记录，由模板在面试通过后自动发放，
 * 不发放积分，审核通过即转为正式成员；{@link #STANDARD} 普通任务面向成员档案，
 * 由管理员按等级条件或指定个人发放；{@link #BOUNTY} 悬赏任务由成员**自主接取**，
 * 有接取名额上限，**最先完成的 m 人获得奖金**（线下发放，系统只登记说明），
 * 绑定的积分与普通任务一样等到期结算。</p>
 */
public enum TaskType {
    ONBOARDING,
    STANDARD,
    /** 悬赏：成员自主接取、名额上限、先完成先得奖金、到期结算积分。 */
    BOUNTY
}

package cn.yeslab.platform.task.model;

/**
 * 任务类型。
 *
 * <p>{@link #ONBOARDING} 新手任务面向招新报名记录，由模板在面试通过后自动发放，
 * 不发放积分，审核通过即转为正式成员；{@link #STANDARD} 普通任务面向成员档案，
 * 由管理员按等级条件或指定个人发放，审核通过后自动发放积分。</p>
 */
public enum TaskType {
    ONBOARDING,
    STANDARD
}

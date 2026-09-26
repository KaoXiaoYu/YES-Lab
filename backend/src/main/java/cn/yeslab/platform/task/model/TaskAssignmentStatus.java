package cn.yeslab.platform.task.model;

/**
 * 任务对象状态。全部由人工流转，不含任何自动判定。
 *
 * <pre>
 * PENDING 待完成 --对象提交--> SUBMITTED 待确认 --管理员通过--> APPROVED
 *                                              --管理员驳回--> REJECTED --> 可重新提交
 * </pre>
 *
 * <p>悬赏任务不用 {@code SUBMITTED}：接取后是 {@code PENDING}，提交完成说明即直接
 * {@code APPROVED}（提交即完成）；{@link #ABANDONED} 表示本人放弃或被管理员移除，
 * 归还接取名额且本人不可再接。</p>
 */
public enum TaskAssignmentStatus {
    PENDING,
    SUBMITTED,
    APPROVED,
    REJECTED,
    /** 悬赏专属：本人放弃 / 管理员移除。终态，归还名额，不可再接。 */
    ABANDONED
}

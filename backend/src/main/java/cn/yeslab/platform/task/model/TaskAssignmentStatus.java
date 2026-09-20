package cn.yeslab.platform.task.model;

/**
 * 任务对象状态。全部由人工流转，不含任何自动判定。
 *
 * <pre>
 * PENDING 待完成 --对象提交--> SUBMITTED 待确认 --管理员通过--> APPROVED
 *                                              --管理员驳回--> REJECTED --> 可重新提交
 * </pre>
 */
public enum TaskAssignmentStatus {
    PENDING,
    SUBMITTED,
    APPROVED,
    REJECTED
}

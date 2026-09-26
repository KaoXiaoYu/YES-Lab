package cn.yeslab.platform.task.model;

import java.time.LocalDate;
import java.time.Instant;

/**
 * 任务的有效截止时间与「到期」判定，三类任务共用一条代码路径。
 *
 * <p>有效截止时间的来源按任务类型区分：新手任务的截止是<b>对象级</b>的
 * （{@code due_date = 本人分配当天 + 大任务时长}，同一大任务下每个人不同），
 * 普通任务与悬赏用<b>任务级</b>的 {@code end_date}。这正是不能只写任务级判定、
 * 也不能只写对象级判定的原因。</p>
 *
     * <p>基准截止时间限制成员提交。新手/普通对象被驳回后，个人重交截止精确重置为驳回时刻后 24 小时；
     * 悬赏仍按原有硬截止冻结接取与管理结论。普通任务积分由结算服务发放（见 {@code docs/task-settlement-design.md}）。
 * 到期的两种情形是「截止时间已过」与「任务被结束为 {@link TaskStatus#CLOSED}」；
 * 截止时间为 {@code null} 表示不限、永不到期。</p>
 */
public final class TaskTiming {

    private TaskTiming() {
    }

    /**
     * 有效截止时间。新手任务优先取对象上的 {@code due_date}，为空时退回任务级 {@code end_date}
     * （兼容历史上把截止日期写在任务上的记录）；其余类型直接取任务级 {@code end_date}。
     * 返回 {@code null} 表示不限。
     */
    public static LocalDate deadlineOf(TaskAssignmentEntity assignment) {
        if (assignment.isOnboarding() && assignment.getDueDate() != null) {
            return assignment.getDueDate();
        }
        return assignment.getTask().getEndDate();
    }

    /**
     * 是否已到期：截止时间已过（当天仍算未到期），或任务已被管理员结束。
     * 只看时间与任务状态，不看对象状态——到期是任务/对象层面的时间事实。
     */
    public static boolean isExpired(TaskAssignmentEntity assignment, LocalDate today) {
        if (assignment.getTask().getStatus() == TaskStatus.CLOSED) {
            return true;
        }
        if (!assignment.getTask().isBounty()
                && assignment.getStatus() == TaskAssignmentStatus.REJECTED
                && assignment.getResubmissionDeadlineAt() != null) {
            return !Instant.now().isBefore(assignment.getResubmissionDeadlineAt());
        }
        LocalDate deadline = deadlineOf(assignment);
        return deadline != null && deadline.isBefore(today);
    }

    /**
     * 成员侧是否还能写（提交完成说明、提交子任务内容、勾选、放弃、接取）。
     * 条件：对象未通过 + 未到期 + 任务处于发布期 + 未到开始日期。
     *
     * @param onboardingStageOk 新手任务同样需要「仍在技能测试阶段」，由调用方判断后传入；
     *                          其它类型忽略该参数
     */
    public static boolean isMemberEditable(
            TaskAssignmentEntity assignment,
            LocalDate today,
            boolean onboardingStageOk
    ) {
        if (assignment.getStatus() == TaskAssignmentStatus.APPROVED) {
            return false;
        }
        if (isExpired(assignment, today)) {
            return false;
        }
        if (assignment.isOnboarding()) {
            return onboardingStageOk;
        }
        TaskEntity task = assignment.getTask();
        if (task.getStatus() != TaskStatus.PUBLISHED) {
            return false;
        }
        return task.getStartDate() == null || !today.isBefore(task.getStartDate());
    }

    /**
     * 任务级判定：任务是否已到期（用于悬赏的接取窗口、结算扫描等还没有对象或需要按任务判断的场景）。
     */
    public static boolean isTaskExpired(TaskEntity task, LocalDate today) {
        if (task.getStatus() == TaskStatus.CLOSED) {
            return true;
        }
        return task.getEndDate() != null && task.getEndDate().isBefore(today);
    }
}

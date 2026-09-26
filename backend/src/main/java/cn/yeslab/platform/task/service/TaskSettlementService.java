package cn.yeslab.platform.task.service;

import cn.yeslab.platform.notification.service.NotificationService;
import cn.yeslab.platform.points.service.PointService;
import cn.yeslab.platform.task.model.TaskAssignmentEntity;
import cn.yeslab.platform.task.model.TaskAssignmentStatus;
import cn.yeslab.platform.task.model.TaskEntity;
import cn.yeslab.platform.task.model.TaskStatus;
import cn.yeslab.platform.task.model.TaskTiming;
import cn.yeslab.platform.task.model.TaskType;
import cn.yeslab.platform.task.repository.TaskAssignmentRepository;
import cn.yeslab.platform.task.repository.TaskRepository;
import cn.yeslab.platform.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * 任务到期结算：把「已经通过的」对象的积分统一发放。
 *
 * <p>结算的口径（见 {@code docs/task-settlement-design.md}）：</p>
 * <ul>
 *   <li><b>只发已通过的</b>：结算那一刻状态为 {@code APPROVED} 的对象才获得积分；
 *       {@code SUBMITTED}（到期未审核）、{@code PENDING}、{@code REJECTED}、{@code ABANDONED}
 *       一律不发，也不改变状态。</li>
 *   <li><b>到期才结算</b>：截止日期已过，或任务已被结束为 {@code CLOSED}（两者同时发生，
 *       因此「结束 = 结算 = 终局」）。</li>
 *   <li><b>幂等</b>：任务级 {@code points_settled_at} 条件更新认领 + 每人来源编号唯一。</li>
 *   <li><b>操作人</b>：结算没有认证上下文，积分流水的 operator 记为任务创建者；
 *       教师 / 非正式成员 / 创建者本人三种情况按既有口径「跳过并记原因」。</li>
 * </ul>
 *
 * <p>事务边界：只有 {@link #settle(UUID)} 是事务方法，一个任务一个事务。调度器负责遍历与容错，
 * 不把整批放进同一个事务（否则一个任务失败会连累全部，且长事务持有大量行锁）。</p>
 */
@Service
public class TaskSettlementService {

    private static final ZoneId LAB_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    /** 普通任务的积分来源编号前缀，与历史记录保持一致，保证幂等兼容。 */
    static final String STANDARD_SOURCE_PREFIX = "TASK";

    /** 悬赏任务的积分来源编号前缀。 */
    static final String BOUNTY_SOURCE_PREFIX = "BOUNTY";

    private final TaskRepository tasks;
    private final TaskAssignmentRepository assignments;
    private final PointService pointService;
    private final NotificationService notifications;

    public TaskSettlementService(
            TaskRepository tasks,
            TaskAssignmentRepository assignments,
            PointService pointService,
            NotificationService notifications
    ) {
        this.tasks = tasks;
        this.assignments = assignments;
        this.pointService = pointService;
        this.notifications = notifications;
    }

    /** 待结算的任务 id（已到期或已结束、绑定了积分、尚未结算）。供调度器遍历。 */
    @Transactional(readOnly = true)
    public List<UUID> findDue(LocalDate today) {
        return tasks.findDueSettlementTaskIds(today, TaskType.ONBOARDING, TaskStatus.CLOSED);
    }

    /**
     * 结算单个任务。幂等、可重复调用，也由「结束任务」同步触发。
     *
     * @return 本次结算结果；{@code settled=false} 表示未执行（未绑定积分 / 未到期 / 已被结算过）
     */
    @Transactional
    public TaskSettlementSummary settle(UUID taskId) {
        TaskEntity task = tasks.findById(taskId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "任务不存在"));
        if (!task.hasBoundPoints()) {
            // 新手任务恒为 0，不参与结算，也不写结算标记。
            return TaskSettlementSummary.notApplicable(taskId, "该任务未绑定积分");
        }
        LocalDate today = LocalDate.now(LAB_TIME_ZONE);
        if (!TaskTiming.isTaskExpired(task, today)) {
            return TaskSettlementSummary.notApplicable(taskId, "任务尚未到期");
        }

        Instant settledAt = Instant.now();
        if (tasks.claimSettlement(taskId, settledAt) == 0) {
            // 已被本实例的上一次执行或另一个实例结算过：幂等跳过。
            return TaskSettlementSummary.alreadySettled(taskId);
        }
        // 认领用了条件更新并清空了持久化上下文，这里重新读取，拿到认领后的状态。
        TaskEntity claimed = tasks.findById(taskId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "任务不存在"));

        int granted = 0;
        int reused = 0;
        int skipped = 0;
        int notApproved = 0;
        for (TaskAssignmentEntity assignment : assignments.findByTaskIdOrderByCreatedAtAsc(taskId)) {
            if (assignment.getStatus() != TaskAssignmentStatus.APPROVED
                    || assignment.getMemberProfile() == null) {
                notApproved++;
                continue;
            }
            // 已经发过的人（例如延长截止日期后重新结算）会走幂等复用，不算「本次新发放」。
            boolean alreadyGranted = assignment.getPointGrantId() != null;
            boolean bounty = claimed.isBounty();
            PointService.TaskGrantResult result = pointService.grantForTaskSettlement(
                    taskId,
                    assignment.getMemberProfile().getId(),
                    claimed.getPoints(),
                    claimed.getCreatedBy(),
                    // 来源编号前缀区分任务类型：普通任务沿用历史上的 TASK: 前缀以保持幂等兼容。
                    bounty ? BOUNTY_SOURCE_PREFIX : STANDARD_SOURCE_PREFIX,
                    claimed.getTitle(),
                    today,
                    "/tasks/" + assignment.getId(),
                    settlementDescription(claimed),
                    bounty
                            ? "完成「" + claimed.getTitle() + "」并在截止前提交"
                            : "完成「" + claimed.getTitle() + "」并通过人工确认"
            );
            if (result.granted()) {
                assignment.recordPointsGrant(result.grantId(), result.creditedPoints());
                if (alreadyGranted) {
                    reused++;
                } else {
                    granted++;
                }
            } else {
                assignment.recordPointsSkipped(result.skippedReason());
                skipped++;
            }
            assignments.save(assignment);
        }

        notifications.send(
                claimed.getCreatedBy(),
                "TASK_UPDATED",
                "任务积分已结算",
                settlementSummaryText(claimed, granted, reused, skipped, notApproved),
                claimed.isBounty()
                        ? "/admin/bounties/" + taskId + "/claims"
                        : "/admin/tasks/" + taskId + "/progress"
        );
        return TaskSettlementSummary.settled(taskId, granted, reused, skipped, notApproved, settledAt);
    }

    private static String settlementDescription(TaskEntity task) {
        StringBuilder builder = new StringBuilder(task.getTitle()).append(" 到期结算");
        if (task.getStartDate() != null || task.getEndDate() != null) {
            builder.append("（").append(task.getStartDate() == null ? "不限" : task.getStartDate())
                    .append(" ~ ").append(task.getEndDate() == null ? "不限" : task.getEndDate()).append("）");
        }
        return builder.toString();
    }

    private static String settlementSummaryText(
            TaskEntity task,
            int granted,
            int reused,
            int skipped,
            int notApproved
    ) {
        return "「" + task.getTitle() + "」已完成积分结算："
                + granted + " 人获得积分"
                + (reused > 0 ? "，" + reused + " 人此前已发放（幂等跳过）" : "")
                + (skipped > 0 ? "，" + skipped + " 人按规则未计分" : "")
                + (notApproved > 0 ? "，" + notApproved + " 人未通过审核未计分" : "");
    }

    /**
     * 单个任务的结算结果；{@code settled=false} 时 {@code reason} 说明未执行的原因。
     *
     * <p>{@code grantedCount} 只统计**本次新发放**；延长截止日期后重新结算时，已发过的人会计入
     * {@code reusedCount}（幂等复用原批次），便于管理员区分「补发了多少人」与「重复跑了一次」。</p>
     */
    public record TaskSettlementSummary(
            UUID taskId,
            boolean settled,
            int grantedCount,
            int reusedCount,
            int skippedCount,
            int notApprovedCount,
            Instant settledAt,
            String reason
    ) {
        static TaskSettlementSummary notApplicable(UUID taskId, String reason) {
            return new TaskSettlementSummary(taskId, false, 0, 0, 0, 0, null, reason);
        }

        static TaskSettlementSummary alreadySettled(UUID taskId) {
            return new TaskSettlementSummary(taskId, false, 0, 0, 0, 0, null, "该任务已经结算过");
        }

        static TaskSettlementSummary settled(
                UUID taskId,
                int granted,
                int reused,
                int skipped,
                int notApproved,
                Instant settledAt
        ) {
            return new TaskSettlementSummary(
                    taskId, true, granted, reused, skipped, notApproved, settledAt, null);
        }
    }
}

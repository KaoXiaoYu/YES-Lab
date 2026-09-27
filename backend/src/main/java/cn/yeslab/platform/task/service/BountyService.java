package cn.yeslab.platform.task.service;

import cn.yeslab.platform.common.error.ApiException;
import cn.yeslab.platform.identity.model.AccountEntity;
import cn.yeslab.platform.identity.model.MemberProfileEntity;
import cn.yeslab.platform.identity.repository.MemberProfileRepository;
import cn.yeslab.platform.identity.service.AuthService;
import cn.yeslab.platform.notification.service.NotificationService;
import cn.yeslab.platform.task.api.TaskModels;
import cn.yeslab.platform.task.model.TaskAssignmentEntity;
import cn.yeslab.platform.task.model.TaskAssignmentSource;
import cn.yeslab.platform.task.model.TaskAssignmentStatus;
import cn.yeslab.platform.task.model.BountyPrizeFulfillmentEntity;
import cn.yeslab.platform.task.model.BountyPrizeFulfillmentStatus;
import cn.yeslab.platform.task.model.TaskAudienceDimension;
import cn.yeslab.platform.task.model.TaskEntity;
import cn.yeslab.platform.task.model.TaskStatus;
import cn.yeslab.platform.task.model.TaskSubtaskEntity;
import cn.yeslab.platform.task.model.TaskTiming;
import cn.yeslab.platform.task.repository.TaskAssignmentRepository;
import cn.yeslab.platform.task.repository.BountyPrizeFulfillmentRepository;
import cn.yeslab.platform.task.repository.TaskAudienceRuleRepository;
import cn.yeslab.platform.task.repository.TaskRepository;
import cn.yeslab.platform.task.repository.TaskSubtaskProgressRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 悬赏任务：成员自主接取、名额上限、先完成先得奖金、到期结算积分。
 *
 * <p>与另外两类任务的根本差别：</p>
 * <ul>
 *   <li><b>对象由成员自己产生</b>：发布时不展开对象，成员在悬赏榜上接取（{@code source = CLAIM}）；</li>
 *   <li><b>提交即完成</b>：不经过「待确认」，提交完成说明直接 {@code APPROVED} 并锁定完成名次；</li>
 *   <li><b>奖金按名次分配并可顺延</b>：见 {@link #recomputePrizeHolders};</li>
 *   <li><b>接取与完成都要串行化</b>：都先对任务行加悲观写锁，避免超发名额或重复名次。</li>
 * </ul>
 *
 * <p>积分不在这里发放：与普通任务一样由 {@code TaskSettlementService} 在到期时统一结算，
 * 只发给结算那一刻状态为「已通过」的对象。</p>
 */
@Service
public class BountyService {

    private static final ZoneId LAB_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    /** 占用接取名额的状态：进行中与已完成。已放弃 / 已驳回会把名额还回去。 */
    private static final Set<TaskAssignmentStatus> OCCUPYING_STATUSES =
            EnumSet.of(TaskAssignmentStatus.PENDING, TaskAssignmentStatus.APPROVED);

    private final TaskRepository tasks;
    private final TaskAssignmentRepository assignments;
    private final BountyPrizeFulfillmentRepository fulfillments;
    private final TaskAudienceRuleRepository audienceRules;
    private final MemberProfileRepository profiles;
    private final TaskSubtaskProgressRepository subtaskProgress;
    private final AuthService authService;
    private final NotificationService notifications;

    public BountyService(
            TaskRepository tasks,
            TaskAssignmentRepository assignments,
            BountyPrizeFulfillmentRepository fulfillments,
            TaskAudienceRuleRepository audienceRules,
            MemberProfileRepository profiles,
            TaskSubtaskProgressRepository subtaskProgress,
            AuthService authService,
            NotificationService notifications
    ) {
        this.tasks = tasks;
        this.assignments = assignments;
        this.fulfillments = fulfillments;
        this.audienceRules = audienceRules;
        this.profiles = profiles;
        this.subtaskProgress = subtaskProgress;
        this.authService = authService;
        this.notifications = notifications;
    }

    // ---------- 成员端 ----------

    /** 悬赏榜：所有可接取的悬赏 + 我已接取/已完成的悬赏。 */
    @Transactional(readOnly = true)
    public List<TaskModels.BountyBoardItemView> board(Authentication authentication) {
        MemberProfileEntity profile = requireOwnProfile(authService.requireAccount(authentication));
        LocalDate today = LocalDate.now(LAB_TIME_ZONE);
        return tasks.findByTaskTypeOrderByCreatedAtDesc(cn.yeslab.platform.task.model.TaskType.BOUNTY).stream()
                .filter(task -> task.getStatus() != TaskStatus.DRAFT)
                .map(task -> toBoardItem(task, profile, today))
                .toList();
    }

    /** 悬赏详情：正文、子任务清单（不带子任务正文）、奖金与名额、我能否接取及原因。 */
    @Transactional(readOnly = true)
    public TaskModels.BountyDetailView detail(Authentication authentication, UUID taskId) {
        MemberProfileEntity profile = requireOwnProfile(authService.requireAccount(authentication));
        TaskEntity task = requireBounty(taskId);
        LocalDate today = LocalDate.now(LAB_TIME_ZONE);
        TaskAssignmentEntity mine = assignments.findByTaskIdAndMemberProfile_Id(taskId, profile.getId())
                .orElse(null);
        String blocked = claimBlockedReason(task, profile, mine, today);
        List<TaskModels.SubtaskView> subtasks = task.getSubtasks().stream()
                .map(item -> new TaskModels.SubtaskView(
                        item.getId(), item.getTitle(), item.getDisplayOrder(), false, null, item.hasContent()))
                .toList();
        return new TaskModels.BountyDetailView(
                task.getId(),
                task.getTitle(),
                task.getContentHtml(),
                prizeView(task),
                task.getStartDate(),
                task.getEndDate(),
                daysRemaining(task.getEndDate(), today),
                blocked == null,
                blocked,
                mine == null ? null : mine.getId(),
                mine == null ? null : mine.getStatus(),
                mine == null ? null : mine.getCompletionRank(),
                mine != null && mine.isPrizeAwarded(),
                blocked == null,
                blocked,
                subtasks
        );
    }

    /** 接取：占用一个名额（若设了上限）。先到先得，同一人只能接一次。 */
    @Transactional
    public TaskModels.BountyClaimResult claim(Authentication authentication, UUID taskId) {
        MemberProfileEntity profile = requireOwnProfile(authService.requireAccount(authentication));
        LocalDate today = LocalDate.now(LAB_TIME_ZONE);
        TaskEntity task = requireBountyForUpdate(taskId);
        TaskAssignmentEntity mine = assignments.findByTaskIdAndMemberProfile_Id(taskId, profile.getId())
                .orElse(null);
        String blocked = claimBlockedReason(task, profile, mine, today);
        if (blocked != null) {
            throw new ApiException(HttpStatus.CONFLICT, blocked);
        }
        TaskAssignmentEntity created = assignments.save(
                new TaskAssignmentEntity(task, profile, null, TaskAssignmentSource.CLAIM));
        task.addAssignment(created);
        long claimed = occupiedCount(taskId);
        notifications.send(profile.getAccount(), "TASK_ASSIGNED", "已接取悬赏",
                "你已接取「" + task.getTitle() + "」，在截止日期前提交完成说明即可。",
                "/tasks/" + created.getId());
        if (task.hasHeadcountLimit() && claimed >= task.getHeadcountLimit()) {
            notifications.send(task.getCreatedBy(), "TASK_UPDATED", "悬赏接取名额已满",
                    "「" + task.getTitle() + "」的接取名额已满，可结束任务或上调名额。",
                    "/admin/bounties/" + taskId + "/claims");
        }
        return new TaskModels.BountyClaimResult(
                created.getId(), created.getStatus(), claimed, task.getHeadcountLimit());
    }

    /** 放弃：本人未完成且未截止时可以放弃，归还名额，且本人不可再接。 */
    @Transactional
    public TaskModels.BountyAbandonResult abandon(Authentication authentication, UUID taskId) {
        MemberProfileEntity profile = requireOwnProfile(authService.requireAccount(authentication));
        LocalDate today = LocalDate.now(LAB_TIME_ZONE);
        TaskEntity task = requireBountyForUpdate(taskId);
        TaskAssignmentEntity mine = assignments.findByTaskIdAndMemberProfile_Id(taskId, profile.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "你还没有接取这条悬赏"));
        if (mine.getStatus() != TaskAssignmentStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "只有进行中的接取可以放弃");
        }
        if (TaskTiming.isExpired(mine, today)) {
            throw new ApiException(HttpStatus.CONFLICT, "任务已截止，不能再放弃");
        }
        mine.abandon();
        assignments.save(mine);
        return new TaskModels.BountyAbandonResult(occupiedCount(taskId), task.getHeadcountLimit());
    }

    /**
     * 提交完成说明（悬赏的「提交即完成」入口）。
     *
     * <p>由 {@code TaskService.submitMyTask} 按任务类型分派到这里：要求对象处于进行中、任务未到期，
     * 提交后状态直接为已通过，并锁定完成名次、重算奖金持有者。</p>
     */
    @Transactional
    public TaskAssignmentEntity complete(TaskAssignmentEntity assignment, String note) {
        TaskEntity task = requireBountyForUpdate(assignment.getTask().getId());
        LocalDate today = LocalDate.now(LAB_TIME_ZONE);
        if (assignment.getStatus() != TaskAssignmentStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "只有进行中的接取可以提交完成说明");
        }
        if (TaskTiming.isExpired(assignment, today)) {
            throw new ApiException(HttpStatus.CONFLICT, "任务已截止，不能再提交；如需继续请联系管理员延长截止日期");
        }
        int rank = (int) assignments.countByTaskIdAndCompletionRankIsNotNull(task.getId()) + 1;
        assignment.completeWithRank(rank, note);
        TaskAssignmentEntity saved = assignments.save(assignment);
        List<Notification> prizeNotices = recomputePrizeHolders(task);
        String summary = "你已完成「" + task.getTitle() + "」，完成名次第 " + rank + " 名。"
                + (task.hasPrize()
                ? (saved.isPrizeAwarded()
                ? "你已获得奖金（" + describePrize(task) + "）。"
                : "奖金共 " + task.getPrizeSlots() + " 份，本次未进入获奖名次。")
                : "")
                + (task.getPoints() > 0 ? "积分将在任务到期后统一结算。" : "");
        notifications.send(assignment.getMemberProfile().getAccount(), "TASK_APPROVED", "悬赏已完成", summary,
                "/tasks/" + saved.getId());
        if (task.hasPrize() && issuedPrizeCount(task.getId()) >= task.getPrizeSlots()) {
            notifications.send(task.getCreatedBy(), "TASK_UPDATED", "悬赏奖金已全部产生",
                    "「" + task.getTitle() + "」的 " + task.getPrizeSlots() + " 份奖金已全部产生，可结束任务。",
                    "/admin/bounties/" + task.getId() + "/claims");
        }
        prizeNotices.forEach(item -> notifications.send(
                item.account(), "TASK_UPDATED", "奖金顺延给你",
                "「" + task.getTitle() + "」的奖金顺延给你（" + describePrize(task) + "）。",
                "/tasks/" + item.assignmentId()));
        return saved;
    }

    // ---------- 管理端 ----------

    @Transactional
    public TaskModels.TaskView create(Authentication authentication, TaskModels.CreateBountyRequest request) {
        AccountEntity operator = authService.requireAccount(authentication);
        validateRewards(request);
        TaskEntity task = new TaskEntity(
                cn.yeslab.platform.task.model.TaskType.BOUNTY,
                request.title().trim(),
                TaskContentSanitizer.cleanContent(request.contentHtml()),
                request.startDate(),
                request.endDate(),
                request.points(),
                TaskStatus.DRAFT,
                operator);
        applyBountyDetails(task, request);
        task.replaceSubtasks(normalizeSubtasks(request.subtasks()));
        TaskEntity saved = tasks.save(task);
        return toTaskView(saved);
    }

    @Transactional
    public TaskModels.TaskView update(UUID taskId, TaskModels.CreateBountyRequest request) {
        TaskEntity task = requireBountyForUpdate(taskId);
        if (task.getStatus() == TaskStatus.CLOSED) {
            throw new ApiException(HttpStatus.CONFLICT, "已结束的悬赏不能再修改");
        }
        if (task.getStatus() == TaskStatus.PUBLISHED) {
            // 已发布：奖励口径锁定、接取条件不可改；人数与份数只增不减、截止日期只能延长。
            // 请求里为 null 的奖励字段表示「保持原值」，避免部分更新把奖金说明误清。
            TaskEntity updated = updatePublished(task, request);
            List<Notification> promoted = recomputePrizeHolders(updated);
            promoted.forEach(item -> notifications.send(item.account(), "TASK_UPDATED", "奖金顺延给你",
                    "「" + updated.getTitle() + "」的奖金名额增加后，你已获得奖金（" + describePrize(updated) + "）。",
                    "/tasks/" + item.assignmentId()));
            return toTaskView(updated);
        }
        validateRewards(request);
        applyBountyDetails(task, request);
        task.replaceSubtasks(normalizeSubtasks(request.subtasks()));
        task.replaceAudienceRules(buildRules(task, request.rules()));
        return toTaskView(tasks.save(task));
    }

    /**
     * 发布：不展开对象（对象由成员接取产生），只锁定奖励与名额。
     * 与普通任务不同，这里没有「命中零人」的概念。
     */
    @Transactional
    public TaskModels.TaskView publish(UUID taskId) {
        TaskEntity task = requireBounty(taskId);
        if (task.getStatus() == TaskStatus.PUBLISHED) {
            return toTaskView(task);
        }
        if (task.getStatus() != TaskStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "只有草稿可以发布");
        }
        if (!task.hasPrize() && !task.hasBoundPoints()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "悬赏至少要设置奖金份数或积分");
        }
        task.markPublished();
        return toTaskView(tasks.save(task));
    }

    /** 结束悬赏：成员侧只读；到期（含 CLOSED）后不能再审核或驳回。积分结算由调用方同步触发。 */
    @Transactional
    public TaskModels.TaskView close(UUID taskId) {
        TaskEntity task = requireBounty(taskId);
        if (task.getStatus() != TaskStatus.PUBLISHED) {
            throw new ApiException(HttpStatus.CONFLICT, "只有已发布悬赏可以结束");
        }
        task.markClosed();
        return toTaskView(tasks.save(task));
    }

    @Transactional
    public void deleteDraft(UUID taskId) {
        TaskEntity task = requireBounty(taskId);
        if (task.getStatus() != TaskStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "只有草稿可以删除");
        }
        tasks.delete(task);
    }

    /** 悬赏名单与汇总。 */
    @Transactional(readOnly = true)
    public TaskModels.BountyClaimsView claims(UUID taskId) {
        TaskEntity task = requireBounty(taskId);
        LocalDate today = LocalDate.now(LAB_TIME_ZONE);
        List<TaskAssignmentEntity> rows = assignments.findByTaskIdOrderByCreatedAtAsc(taskId);
        Map<UUID, BountyPrizeFulfillmentEntity> fulfillmentByAssignment = fulfillments
                .findByAssignment_IdIn(rows.stream().map(TaskAssignmentEntity::getId).toList()).stream()
                .collect(Collectors.toMap(item -> item.getAssignment().getId(), item -> item));
        List<TaskModels.BountyClaimRowView> views = new ArrayList<>();
        for (TaskAssignmentEntity row : rows) {
            MemberProfileEntity member = row.getMemberProfile();
            if (member == null) continue;
            AccountEntity reviewer = row.getReviewedBy();
            BountyPrizeFulfillmentEntity fulfillment = fulfillmentByAssignment.get(row.getId());
            views.add(new TaskModels.BountyClaimRowView(
                    row.getId(),
                    member.getId(),
                    member.getName(),
                    member.getMemberCode(),
                    member.getGrade(),
                    member.getStatus() == null ? null : member.getStatus().name(),
                    member.getAccount().getRole().name(),
                    row.getStatus(),
                    row.getCompletionRank(),
                    row.isPrizeAwarded(),
                    row.getCompletionNote(),
                    row.getCreatedAt(),
                    row.getSubmittedAt(),
                    reviewer == null ? null : reviewer.getUsername(),
                    row.getReviewedAt(),
                    row.getReviewComment(),
                    row.getAwardedPoints(),
                    row.getPointsSkippedReason(),
                    TaskTiming.isExpired(row, today),
                    fulfillment == null ? null : fulfillment.getStatus(),
                    fulfillment == null ? null : fulfillment.getIssuedAt(),
                    fulfillment == null || fulfillment.getIssuedBy() == null ? null : fulfillment.getIssuedBy().getUsername(),
                    fulfillment == null ? null : fulfillment.getReceivedAt(),
                    fulfillment == null || fulfillment.getReceivedBy() == null ? null : fulfillment.getReceivedBy().getName(),
                    fulfillment == null ? null : fulfillment.getRevokedAt(),
                    fulfillment == null || fulfillment.getRevokedBy() == null ? null : fulfillment.getRevokedBy().getUsername(),
                    fulfillment == null ? null : fulfillment.getRevokedReason()
            ));
        }
        return new TaskModels.BountyClaimsView(
                toTaskView(task),
                task.getHeadcountLimit(),
                rows.size(),
                occupiedCount(taskId),
                countByStatus(rows, TaskAssignmentStatus.APPROVED),
                countByStatus(rows, TaskAssignmentStatus.ABANDONED),
                countByStatus(rows, TaskAssignmentStatus.REJECTED),
                task.getPrizeSlots(),
                issuedPrizeCount(taskId),
                views
        );
    }

    @Transactional(readOnly = true)
    public List<TaskModels.BountySummaryView> list() {
        LocalDate today = LocalDate.now(LAB_TIME_ZONE);
        return tasks.findByTaskTypeOrderByCreatedAtDesc(cn.yeslab.platform.task.model.TaskType.BOUNTY).stream()
                .map(task -> new TaskModels.BountySummaryView(
                        task.getId(),
                        task.getTitle(),
                        task.getStatus(),
                        task.getStartDate(),
                        task.getEndDate(),
                        task.getHeadcountLimit(),
                        occupiedCount(task.getId()),
                        countByStatus(task.getAssignments(), TaskAssignmentStatus.APPROVED),
                        task.getPrizeSlots(),
                        task.getPrizeDescription(),
                        issuedPrizeCount(task.getId()),
                        task.getPoints(),
                        task.getPointsSettledAt(),
                        TaskTiming.isTaskExpired(task, today)))
                .toList();
    }

    /**
     * 事后驳回：{@code APPROVED -> REJECTED}。归还接取名额、触发奖金顺延；到期后禁止。
     *
     * <p>积分不回收（更正走积分管理的反向流水），奖金份额由不变量重算顺延给下一位完成者。</p>
     */
    @Transactional
    public TaskModels.BountyClaimRowView revoke(Authentication authentication, UUID taskId, UUID assignmentId,
            String comment) {
        AccountEntity operator = authService.requireAccount(authentication);
        TaskEntity task = requireBountyForUpdate(taskId);
        TaskAssignmentEntity assignment = requireClaim(taskId, assignmentId);
        if (assignment.getStatus() != TaskAssignmentStatus.APPROVED) {
            throw new ApiException(HttpStatus.CONFLICT, "只有已完成的接取可以驳回");
        }
        BountyPrizeFulfillmentEntity fulfillment = fulfillments.findByAssignment_Id(assignmentId).orElse(null);
        if (assignment.isPrizeAwarded()) {
            if (fulfillment == null) {
                throw new ApiException(HttpStatus.CONFLICT, "缺少奖金履约记录，请联系管理员核查数据");
            }
            if (fulfillment.getStatus() != BountyPrizeFulfillmentStatus.PENDING) {
                throw new ApiException(HttpStatus.CONFLICT, "奖金已线下发放或领取，不能驳回；请走人工审计更正");
            }
        }
        String reason = comment == null ? "" : comment.trim();
        if (reason.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "驳回必须填写审核意见");
        }
        if (TaskTiming.isExpired(assignment, LocalDate.now(LAB_TIME_ZONE))) {
            throw new ApiException(HttpStatus.CONFLICT, "任务已截止，不能再驳回");
        }
        assignment.revokeCompletion(operator, reason);
        TaskAssignmentEntity saved = assignments.save(assignment);
        List<Notification> promoted = recomputePrizeHolders(task);
        notifications.send(saved.getMemberProfile().getAccount(), "TASK_REJECTED", "悬赏被驳回",
                "「" + task.getTitle() + "」的完成结果被驳回：" + reason, "/bounties/" + taskId);
        promoted.forEach(item -> notifications.send(
                item.account(), "TASK_UPDATED", "奖金顺延给你",
                "「" + task.getTitle() + "」的奖金顺延给你（" + describePrize(task) + "）。",
                "/tasks/" + item.assignmentId()));
        return claims(taskId).rows().stream()
                .filter(row -> row.assignmentId().equals(assignmentId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "接取记录不存在"));
    }

    /** 管理员登记线下实际发放；不受截止日期和悬赏关闭状态限制。 */
    @Transactional
    public TaskModels.BountyClaimRowView issuePrize(Authentication authentication, UUID taskId, UUID assignmentId) {
        AccountEntity operator = authService.requireAccount(authentication);
        requireBountyForUpdate(taskId);
        TaskAssignmentEntity assignment = requireClaim(taskId, assignmentId);
        if (assignment.getStatus() != TaskAssignmentStatus.APPROVED || !assignment.isPrizeAwarded()) {
            throw new ApiException(HttpStatus.CONFLICT, "该成员当前没有有效的已完成奖金资格");
        }
        BountyPrizeFulfillmentEntity fulfillment = fulfillments.findByAssignment_Id(assignmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "缺少奖金履约记录，请联系管理员核查数据"));
        if (fulfillment.getStatus() != BountyPrizeFulfillmentStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "奖金不是待发放状态");
        }
        fulfillment.markIssued(operator);
        fulfillments.save(fulfillment);
        notifications.send(assignment.getMemberProfile().getAccount(), "TASK_UPDATED", "悬赏奖金已发放",
                "「" + assignment.getTask().getTitle() + "」的线下奖金已登记发放，请确认领取。",
                "/tasks/" + assignmentId);
        return claims(taskId).rows().stream().filter(row -> row.assignmentId().equals(assignmentId)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "接取记录不存在"));
    }

    /** 获奖成员本人确认收到线下奖金；关闭或到期后仍可登记。 */
    @Transactional
    public void confirmPrizeReceived(MemberProfileEntity recipient, UUID assignmentId) {
        TaskAssignmentEntity assignment = assignments.findByIdAndMemberProfile_Id(assignmentId, recipient.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "任务不存在"));
        requireBountyForUpdate(assignment.getTask().getId());
        if (assignment.getStatus() != TaskAssignmentStatus.APPROVED || !assignment.isPrizeAwarded()) {
            throw new ApiException(HttpStatus.CONFLICT, "该任务当前没有有效的奖金资格");
        }
        BountyPrizeFulfillmentEntity fulfillment = fulfillments.findByAssignment_Id(assignmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "缺少奖金履约记录，请联系管理员核查数据"));
        if (fulfillment.getStatus() != BountyPrizeFulfillmentStatus.ISSUED) {
            throw new ApiException(HttpStatus.CONFLICT, "奖金尚未登记发放，暂不能确认领取");
        }
        fulfillment.markReceived(recipient);
        fulfillments.save(fulfillment);
        notifications.send(assignment.getTask().getCreatedBy(), "TASK_UPDATED", "悬赏奖金已确认领取",
                "「" + assignment.getTask().getTitle() + "」的获奖成员已确认领取线下奖金。",
                "/admin/bounties/" + assignment.getTask().getId() + "/claims");
    }

    /** 移除接取者：{@code PENDING -> ABANDONED}，归还名额；到期后仍可执行（清理动作）。 */
    @Transactional
    public TaskModels.BountyClaimsView removeClaim(UUID taskId, UUID assignmentId) {
        requireBountyForUpdate(taskId);
        TaskAssignmentEntity assignment = requireClaim(taskId, assignmentId);
        if (assignment.getStatus() != TaskAssignmentStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "只有进行中的接取可以移除");
        }
        assignment.abandon();
        assignments.save(assignment);
        return claims(taskId);
    }

    // ---------- 奖金不变量 ----------

    /**
     * 重算奖金持有者。
     *
     * <p><b>不变量</b>：奖金持有者集合 = 该任务下状态为「已通过」的对象中，按完成名次升序的
     * 前 {@code min(m, 已完成人数)} 位。{@code prizeAwarded} 只是这条不变量的投影列。</p>
     *
     * <p>「顺延」因此不需要额外规则：驳回一位持有者后他离开集合，后面的名次整体前移，
     * 名次最靠前的未获奖完成者自动补位；若当时无人可补，份额空置，等下一个完成者自动获得。</p>
     *
     * <p>必须在持有任务行锁的事务里调用，否则并发的「完成」与「驳回」交错会出现两人同时补位。</p>
     *
     * @return 本次**新获得**奖金的记录（用于发顺延通知）
     */
    private List<Notification> recomputePrizeHolders(TaskEntity task) {
        if (!task.hasPrize()) {
            return List.of();
        }
        List<TaskAssignmentEntity> ranked =
                assignments.findByTaskIdAndCompletionRankIsNotNullOrderByCompletionRankAsc(task.getId());
        List<TaskAssignmentEntity> approved = ranked.stream()
                .filter(row -> row.getStatus() == TaskAssignmentStatus.APPROVED)
                .toList();
        int holderCount = Math.min(task.getPrizeSlots(), approved.size());
        Set<UUID> holders = approved.stream().limit(holderCount)
                .map(TaskAssignmentEntity::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Notification> promoted = new ArrayList<>();
        for (TaskAssignmentEntity row : ranked) {
            boolean should = holders.contains(row.getId());
            if (should && !row.isPrizeAwarded()) {
                row.markPrize(true);
                assignments.save(row);
                ensurePendingFulfillment(row);
                if (row.getMemberProfile() != null) {
                    promoted.add(new Notification(row.getMemberProfile().getAccount(), row.getId()));
                }
            } else if (!should && row.isPrizeAwarded()) {
                // 只有刚被驳回的持有者会走到这里（其余人的相对名次没变，截断位置也不会前移）。
                BountyPrizeFulfillmentEntity fulfillment = fulfillments.findByAssignment_Id(row.getId()).orElse(null);
                if (fulfillment == null) {
                    throw new ApiException(HttpStatus.CONFLICT, "缺少奖金履约记录，请联系管理员核查数据");
                }
                if (fulfillment.getStatus() != BountyPrizeFulfillmentStatus.PENDING) {
                    throw new ApiException(HttpStatus.CONFLICT, "已发放或领取奖金的完成记录不能撤销");
                }
                row.markPrize(false);
                assignments.save(row);
                if (fulfillment != null) {
                    fulfillment.revokePending(row.getReviewedBy(), row.getReviewComment());
                    fulfillments.save(fulfillment);
                }
            } else if (should) {
                ensurePendingFulfillment(row);
            }
        }
        return promoted;
    }

    private void ensurePendingFulfillment(TaskAssignmentEntity assignment) {
        fulfillments.findByAssignment_Id(assignment.getId()).orElseGet(() ->
                fulfillments.save(new BountyPrizeFulfillmentEntity(assignment)));
    }

    private String claimBlockedReason(
            TaskEntity task,
            MemberProfileEntity profile,
            TaskAssignmentEntity mine,
            LocalDate today
    ) {
        if (task.getStatus() != TaskStatus.PUBLISHED) {
            return task.getStatus() == TaskStatus.DRAFT ? "悬赏尚未发布" : "悬赏已结束";
        }
        if (mine != null) {
            return switch (mine.getStatus()) {
                case ABANDONED -> "你曾接取后放弃或被移除，同一条悬赏只能接取一次";
                case REJECTED -> "你的完成结果已被驳回，同一条悬赏只能接取一次";
                case APPROVED -> "你已完成这条悬赏";
                default -> "你已接取这条悬赏";
            };
        }
        if (today.isBefore(task.getStartDate() == null ? today : task.getStartDate())) {
            return "接取尚未开始";
        }
        if (TaskTiming.isTaskExpired(task, today)) {
            return "已过接取期";
        }
        if (task.hasHeadcountLimit() && occupiedCount(task.getId()) >= task.getHeadcountLimit()) {
            return "接取名额已满";
        }
        if (!matchesAudience(task, profile)) {
            return "你不符合这条悬赏的接取资格";
        }
        return null;
    }

    /** 接取资格：条件为空 = 全体成员可接；同维度取「或」、跨维度取「且」。 */
    private boolean matchesAudience(TaskEntity task, MemberProfileEntity profile) {
        var rules = audienceRules.findByTaskId(task.getId());
        if (rules.isEmpty()) {
            return true;
        }
        Map<TaskAudienceDimension, Set<String>> grouped = rules.stream().collect(Collectors.groupingBy(
                rule -> rule.getDimension(),
                Collectors.mapping(rule -> rule.getRuleValue(), Collectors.toSet())));
        for (Map.Entry<TaskAudienceDimension, Set<String>> entry : grouped.entrySet()) {
            String value = switch (entry.getKey()) {
                case ROLE -> profile.getAccount().getRole().name();
                case MEMBER_STATUS -> profile.getStatus() == null ? null : profile.getStatus().name();
                case GRADE -> profile.getGrade();
                case SKILL_TAG -> null;
            };
            if (entry.getKey() == TaskAudienceDimension.SKILL_TAG) {
                boolean hit = profile.getSkillTags().stream().anyMatch(entry.getValue()::contains);
                if (!hit) return false;
                continue;
            }
            if (value == null || !entry.getValue().contains(value)) {
                return false;
            }
        }
        return true;
    }

    // ---------- 内部工具 ----------

    private record Notification(AccountEntity account, UUID assignmentId) {
    }

    private TaskModels.BountyBoardItemView toBoardItem(
            TaskEntity task, MemberProfileEntity profile, LocalDate today) {
        TaskAssignmentEntity mine = assignments.findByTaskIdAndMemberProfile_Id(task.getId(), profile.getId())
                .orElse(null);
        String blocked = claimBlockedReason(task, profile, mine, today);
        return new TaskModels.BountyBoardItemView(
                task.getId(),
                task.getTitle(),
                summaryOf(task.getContentHtml()),
                prizeView(task),
                task.getStartDate(),
                task.getEndDate(),
                daysRemaining(task.getEndDate(), today),
                blocked == null,
                blocked,
                mine == null ? null : mine.getId(),
                mine == null ? null : mine.getStatus(),
                mine == null ? null : mine.getCompletionRank(),
                mine != null && mine.isPrizeAwarded(),
                blocked == null,
                blocked
        );
    }

    private TaskModels.BountyPrizeView prizeView(TaskEntity task) {
        return new TaskModels.BountyPrizeView(
                task.getPrizeDescription(),
                task.getPrizeSlots(),
                issuedPrizeCount(task.getId()),
                task.getHeadcountLimit(),
                occupiedCount(task.getId()),
                task.getPoints(),
                task.isPointsSettled()
        );
    }

    private long occupiedCount(UUID taskId) {
        return assignments.countByTaskIdAndStatusIn(taskId, OCCUPYING_STATUSES);
    }

    private long issuedPrizeCount(UUID taskId) {
        return assignments.findByTaskIdAndCompletionRankIsNotNullOrderByCompletionRankAsc(taskId).stream()
                .filter(TaskAssignmentEntity::isPrizeAwarded)
                .count();
    }

    private static long countByStatus(List<TaskAssignmentEntity> rows, TaskAssignmentStatus status) {
        return rows.stream().filter(row -> row.getStatus() == status).count();
    }

    private static long daysRemaining(LocalDate endDate, LocalDate today) {
        return endDate == null ? 0 : ChronoUnit.DAYS.between(today, endDate);
    }

    private static String describePrize(TaskEntity task) {
        return task.getPrizeDescription() == null ? "奖金" : task.getPrizeDescription();
    }

    private static String summaryOf(String contentHtml) {
        String text = contentHtml == null ? "" : contentHtml.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return text.length() <= 80 ? text : text.substring(0, 80) + "…";
    }

    private void applyBountyDetails(TaskEntity task, TaskModels.CreateBountyRequest request) {
        String description = request.prizeDescription() == null || request.prizeDescription().isBlank()
                ? null
                : request.prizeDescription().trim();
        task.updateBountyDetails(
                request.title().trim(),
                TaskContentSanitizer.cleanContent(request.contentHtml()),
                description,
                request.prizeSlots(),
                request.headcountLimit(),
                request.startDate(),
                request.endDate());
    }

    private TaskEntity updatePublished(TaskEntity task, TaskModels.CreateBountyRequest request) {
        requireNotDecreased("接取人数上限", task.getHeadcountLimit(), request.headcountLimit());
        requireNotDecreased("奖金份数", task.getPrizeSlots(), request.prizeSlots());
        requireNotShortened(task.getEndDate(), request.endDate());
        String description = request.prizeDescription() == null || request.prizeDescription().isBlank()
                ? task.getPrizeDescription()
                : request.prizeDescription().trim();
        task.updateBountyDetails(
                request.title().trim(),
                TaskContentSanitizer.cleanContent(request.contentHtml()),
                description,
                request.prizeSlots() == null ? task.getPrizeSlots() : request.prizeSlots(),
                request.headcountLimit() == null ? task.getHeadcountLimit() : request.headcountLimit(),
                request.startDate(),
                request.endDate());
        // 悬赏除奖励口径外与普通任务同口径：已发布也能改子任务，按 id 同步、保留成员勾选记录。
        replaceSubtasksForPublished(task, normalizeSubtasks(request.subtasks()));
        return tasks.save(task);
    }

    /** 已发布任务的子任务可以增删；按 id 同步，删除会一并移除对应勾选记录（勾选记录先删，绕开外键）。 */
    private void replaceSubtasksForPublished(TaskEntity task, List<TaskEntity.SubtaskDraft> drafts) {
        requireSubtasksBelongTo(task, drafts);
        Set<UUID> keep = drafts.stream()
                .map(TaskEntity.SubtaskDraft::id)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        task.getSubtasks().stream()
                .filter(item -> !keep.contains(item.getId()))
                .forEach(item -> subtaskProgress.deleteBySubtaskId(item.getId()));
        subtaskProgress.flush();
        task.syncSubtasks(drafts);
    }

    /** 提交里带 {@code id} 的子任务必须属于该任务，避免把别的任务的子任务挂过来。 */
    private static void requireSubtasksBelongTo(TaskEntity task, List<TaskEntity.SubtaskDraft> drafts) {
        Set<UUID> owned = task.getSubtasks().stream().map(TaskSubtaskEntity::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        boolean foreign = drafts.stream()
                .map(TaskEntity.SubtaskDraft::id)
                .filter(java.util.Objects::nonNull)
                .anyMatch(id -> !owned.contains(id));
        if (foreign) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "提交的子任务不属于当前任务");
        }
    }

    private void validateRewards(TaskModels.CreateBountyRequest request) {
        boolean hasPrize = request.prizeSlots() != null;
        boolean hasPoints = request.points() > 0;
        if (!hasPrize && !hasPoints) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "悬赏至少要设置奖金份数或积分");
        }
        boolean hasDescription = request.prizeDescription() != null && !request.prizeDescription().isBlank();
        if (hasPrize && !hasDescription) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "设置奖金份数时必须填写奖金说明");
        }
        if (!hasPrize && hasDescription) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "没有设置奖金份数时不能填写奖金说明");
        }
        if (request.headcountLimit() != null && hasPrize
                && request.prizeSlots() > request.headcountLimit()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "奖金份数不能多于接取人数上限");
        }
        if (request.startDate() != null && request.endDate() != null
                && request.endDate().isBefore(request.startDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "截止日期不能早于开始日期");
        }
    }

    /** 已发布悬赏的人数与份数只增不减；请求里为 {@code null} 表示「保持原值」，不算下调。 */
    private static void requireNotDecreased(String label, Integer previous, Integer current) {
        if (previous == null || current == null) {
            return;
        }
        if (current < previous) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + "发布后只能上调，不能下调");
        }
    }

    private static void requireNotShortened(LocalDate previous, LocalDate current) {
        if (previous == null || current == null) {
            return;
        }
        if (current.isBefore(previous)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "截止日期只能延长，不能提前");
        }
    }

    private static List<TaskEntity.SubtaskDraft> normalizeSubtasks(List<TaskModels.SubtaskInput> subtasks) {
        return OnboardingTaskService.normalizeSubtasks(subtasks);
    }

    private List<cn.yeslab.platform.task.model.TaskAudienceRuleEntity> buildRules(
            TaskEntity task, List<TaskModels.AudienceRuleRequest> requests) {
        List<cn.yeslab.platform.task.model.TaskAudienceRuleEntity> rules = new ArrayList<>();
        if (requests == null) {
            return rules;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (TaskModels.AudienceRuleRequest request : requests) {
            String value = request.value() == null ? "" : request.value().trim();
            if (value.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "接取条件的取值不能为空");
            }
            TaskService.validateRuleValue(request.dimension(), value);
            if (seen.add(request.dimension() + "|" + value)) {
                rules.add(new cn.yeslab.platform.task.model.TaskAudienceRuleEntity(task, request.dimension(), value));
            }
        }
        return rules;
    }

    private MemberProfileEntity requireOwnProfile(AccountEntity account) {
        return profiles.findByAccountId(account.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "当前账号还没有成员资料"));
    }

    private TaskEntity requireBounty(UUID taskId) {
        TaskEntity task = tasks.findById(taskId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "悬赏不存在"));
        if (!task.isBounty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "悬赏不存在");
        }
        return task;
    }

    private TaskEntity requireBountyForUpdate(UUID taskId) {
        TaskEntity task = tasks.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "悬赏不存在"));
        if (!task.isBounty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "悬赏不存在");
        }
        return task;
    }

    private TaskAssignmentEntity requireClaim(UUID taskId, UUID assignmentId) {
        TaskAssignmentEntity assignment = assignments.findById(assignmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "接取记录不存在"));
        if (!assignment.getTask().getId().equals(taskId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "接取记录不存在");
        }
        return assignment;
    }

    private TaskModels.TaskView toTaskView(TaskEntity task) {
        List<TaskModels.SubtaskView> subtasks = task.getSubtasks().stream()
                .map(item -> new TaskModels.SubtaskView(
                        item.getId(), item.getTitle(), item.getDisplayOrder(), false, null, item.hasContent()))
                .toList();
        List<TaskModels.AudienceRuleRequest> rules = task.getAudienceRules().stream()
                .map(item -> new TaskModels.AudienceRuleRequest(item.getDimension(), item.getRuleValue()))
                .toList();
        return new TaskModels.TaskView(
                task.getId(),
                task.getTaskType(),
                task.getTitle(),
                task.getContentHtml(),
                task.getStartDate(),
                task.getEndDate(),
                task.getPoints(),
                task.getStatus(),
                task.getPublishedAt(),
                task.getAssignments().size(),
                task.getPointsSettledAt(),
                TaskTiming.isTaskExpired(task, LocalDate.now(LAB_TIME_ZONE)),
                subtasks,
                rules,
                List.of()
        );
    }
}

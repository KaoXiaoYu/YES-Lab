package cn.yeslab.platform.task.service;

import cn.yeslab.platform.common.error.ApiException;
import cn.yeslab.platform.identity.model.AccountEntity;
import cn.yeslab.platform.identity.model.MemberProfileEntity;
import cn.yeslab.platform.identity.model.MemberStatus;
import cn.yeslab.platform.identity.model.Role;
import cn.yeslab.platform.identity.repository.MemberProfileRepository;
import cn.yeslab.platform.identity.service.AuthService;
import cn.yeslab.platform.notification.service.NotificationService;
import cn.yeslab.platform.recruitment.model.RecruitmentApplicationEntity;
import cn.yeslab.platform.recruitment.model.RecruitmentStage;
import cn.yeslab.platform.recruitment.repository.RecruitmentApplicationRepository;
import cn.yeslab.platform.recruitment.service.RecruitmentService;
import cn.yeslab.platform.task.api.TaskModels;
import cn.yeslab.platform.task.model.TaskAssignmentEntity;
import cn.yeslab.platform.task.model.BountyPrizeFulfillmentEntity;
import cn.yeslab.platform.task.model.TaskAssignmentSource;
import cn.yeslab.platform.task.model.TaskAssignmentStatus;
import cn.yeslab.platform.task.model.TaskAudienceDimension;
import cn.yeslab.platform.task.model.TaskAudienceRuleEntity;
import cn.yeslab.platform.task.model.TaskEntity;
import cn.yeslab.platform.task.model.TaskStatus;
import cn.yeslab.platform.task.model.TaskSubtaskEntity;
import cn.yeslab.platform.task.model.TaskTiming;
import cn.yeslab.platform.task.model.TaskType;
import cn.yeslab.platform.task.repository.TaskAssignmentRepository;
import cn.yeslab.platform.task.repository.BountyPrizeFulfillmentRepository;
import cn.yeslab.platform.task.repository.TaskRepository;
import cn.yeslab.platform.task.repository.TaskSubtaskProgressRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 新手任务：发放、报名者提交、管理员审核与转正。
 *
 * <p>新手任务只作转正门槛，不发放积分；确认通过由 {@link RecruitmentService#convertApplicantToMember}
 * 完成转正，保证建档案与状态流转只有一份实现。</p>
 */
@Service
public class TaskService {

    private static final ZoneId LAB_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    /** 可选的成员状态：与成员管理的可选值保持一致，已停用状态不能作为发放条件。 */
    private static final Set<MemberStatus> SELECTABLE_STATUSES = java.util.EnumSet.of(
            MemberStatus.TRIAL, MemberStatus.OFFICIAL);

    private final TaskRepository tasks;
    private final TaskAssignmentRepository assignments;
    private final BountyPrizeFulfillmentRepository prizeFulfillments;
    private final TaskSubtaskProgressRepository subtaskProgressRepository;
    private final MemberProfileRepository profiles;
    private final RecruitmentApplicationRepository applications;
    private final OnboardingTaskService onboardingTaskService;
    private final OnboardingTaskIssuerService issuer;
    private final RecruitmentService recruitmentService;
    private final TaskSettlementService taskSettlementService;
    private final BountyService bountyService;
    private final AuthService authService;
    private final NotificationService notifications;

    public TaskService(
            TaskRepository tasks,
            TaskAssignmentRepository assignments,
            BountyPrizeFulfillmentRepository prizeFulfillments,
            TaskSubtaskProgressRepository subtaskProgressRepository,
            MemberProfileRepository profiles,
            RecruitmentApplicationRepository applications,
            OnboardingTaskService onboardingTaskService,
            OnboardingTaskIssuerService issuer,
            RecruitmentService recruitmentService,
            TaskSettlementService taskSettlementService,
            BountyService bountyService,
            AuthService authService,
            NotificationService notifications
    ) {
        this.tasks = tasks;
        this.assignments = assignments;
        this.prizeFulfillments = prizeFulfillments;
        this.subtaskProgressRepository = subtaskProgressRepository;
        this.profiles = profiles;
        this.applications = applications;
        this.onboardingTaskService = onboardingTaskService;
        this.issuer = issuer;
        this.recruitmentService = recruitmentService;
        this.taskSettlementService = taskSettlementService;
        this.bountyService = bountyService;
        this.authService = authService;
        this.notifications = notifications;
    }

    // ---------- 报名者端 ----------

    @PreAuthorize("hasAuthority('RECRUITMENT_SELF_VIEW')")
    @Transactional(readOnly = true)
    public TaskModels.OnboardingTaskView ownOnboardingTask(Authentication authentication) {
        AccountEntity account = authService.requireAccount(authentication);
        return ownAssignment(account)
                .map(this::toOnboardingView)
                .orElse(null);
    }

    @PreAuthorize("hasAuthority('RECRUITMENT_SELF_EDIT')")
    @Transactional
    public TaskModels.OnboardingTaskView submitOwnSubtask(
            Authentication authentication,
            UUID subtaskId,
            String contentHtml
    ) {
        AccountEntity account = authService.requireAccount(authentication);
        TaskAssignmentEntity assignment = requireOwnAssignment(account);
        requireEditable(assignment);
        TaskSubtaskEntity subtask = requireSubtask(assignment.getTask(), subtaskId);
        assignment.submitSubtaskProgress(subtask, TaskContentSanitizer.cleanSubmissionContent(contentHtml));
        // 待确认状态下改动内容即视为重新编辑，退回「待完成」后需再次提交大任务。
        assignment.reopenForEdit();
        return toOnboardingView(assignments.save(assignment));
    }

    @PreAuthorize("hasAuthority('RECRUITMENT_SELF_EDIT')")
    @Transactional
    public TaskModels.OnboardingTaskView submitOwnOnboarding(Authentication authentication, String completionNote) {
        AccountEntity account = authService.requireAccount(authentication);
        TaskAssignmentEntity assignment = requireOwnAssignment(account);
        requireEditable(assignment);
        requireAllSubtasksCompleted(assignment);
        assignment.submit(completionNote.trim());
        return toOnboardingView(assignments.save(assignment));
    }

    private void requireEditable(TaskAssignmentEntity assignment) {
        if (assignment.getStatus() == TaskAssignmentStatus.APPROVED) {
            throw new ApiException(HttpStatus.CONFLICT, "新手任务已通过，不能再修改");
        }
        if (TaskTiming.isExpired(assignment, LocalDate.now(LAB_TIME_ZONE))) {
            throw new ApiException(HttpStatus.CONFLICT, "新手任务已截止，不能再提交；如需继续请联系管理员延长截止日期");
        }
        RecruitmentApplicationEntity application = assignment.getRecruitmentApplication();
        if (application != null && application.getStage() != RecruitmentStage.SKILL_TEST
                && application.getStage() != RecruitmentStage.PROBATION) {
            throw new ApiException(HttpStatus.CONFLICT, "当前招新阶段不能修改新手任务");
        }
    }

    private Optional<TaskAssignmentEntity> ownAssignment(AccountEntity account) {
        return applications.findByApplicantId(account.getId())
                .flatMap(application -> assignments
                        .findFirstByRecruitmentApplication_IdAndTask_TaskTypeOrderByCreatedAtDesc(
                                application.getId(), TaskType.ONBOARDING));
    }

    private TaskAssignmentEntity requireOwnAssignment(AccountEntity account) {
        return ownAssignment(account)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "你目前没有新手任务"));
    }

    // ---------- 管理端：总览与补发 ----------

    @PreAuthorize("hasAuthority('TASK_MANAGE')")
    @Transactional(readOnly = true)
    public TaskModels.OnboardingOverviewView onboardingOverview() {
        List<RecruitmentApplicationEntity> skillTestApplications = applications.findAll().stream()
                .filter(item -> item.getStage() == RecruitmentStage.SKILL_TEST
                        || item.getStage() == RecruitmentStage.PROBATION)
                .toList();
        Map<UUID, RecruitmentApplicationEntity> byId = new LinkedHashMap<>();
        skillTestApplications.forEach(item -> byId.put(item.getId(), item));

        List<TaskModels.OnboardingRowView> rows = new ArrayList<>();
        int missing = 0;
        for (RecruitmentApplicationEntity application : skillTestApplications) {
            TaskAssignmentEntity assignment = assignments
                    .findFirstByRecruitmentApplication_IdAndTask_TaskTypeOrderByCreatedAtDesc(
                            application.getId(), TaskType.ONBOARDING)
                    .orElse(null);
            if (assignment == null) {
                missing++;
                rows.add(new TaskModels.OnboardingRowView(
                        null, null, application.getId(), application.getName(), application.getApplicant().getUsername(),
                        application.getMemberCode(), application.getSkillTags(), application.getStage(), null,
                        0, 0, null, null, null, null, null, null, null,
                        null, null, false, null, null, null, null));
                continue;
            }
            rows.add(toOnboardingRow(assignment));
        }
        rows.sort(Comparator.comparing(TaskModels.OnboardingRowView::applicantName,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return new TaskModels.OnboardingOverviewView(
                onboardingTaskService.current(), skillTestApplications.size(), missing, rows);
    }

    @PreAuthorize("hasAuthority('TASK_MANAGE')")
    @Transactional
    public TaskModels.BackfillResult backfillOnboardingTasks(Authentication authentication) {
        AccountEntity operator = authService.requireAccount(authentication);
        List<RecruitmentApplicationEntity> pending = applications.findAll().stream()
                .filter(item -> item.getStage() == RecruitmentStage.SKILL_TEST)
                .toList();
        int issued = 0;
        int skipped = 0;
        for (RecruitmentApplicationEntity application : pending) {
            if (issuer.hasAssignment(application.getId())) {
                skipped++;
                continue;
            }
            issuer.issue(application, operator);
            issued++;
        }
        return new TaskModels.BackfillResult(issued, skipped);
    }

    // ---------- 管理端：人工审核 ----------

    /** 人工审核入口：按任务类型分派，普通任务通过时计分，新手任务通过时转正。 */
    @PreAuthorize("hasAuthority('TASK_MANAGE')")
    @Transactional
    public TaskModels.ReviewResultView review(
            Authentication authentication,
            UUID taskId,
            UUID assignmentId,
            TaskModels.ReviewRequest request
    ) {
        TaskAssignmentEntity assignment = requireAssignment(taskId, assignmentId);
        TaskAssignmentEntity reviewed = assignment.isOnboarding()
                ? reviewOnboarding(authentication, assignment, request)
                : reviewStandard(authentication, assignment, request);
        return toReviewResult(reviewed);
    }

    private TaskAssignmentEntity reviewOnboarding(
            Authentication authentication,
            TaskAssignmentEntity assignment,
            TaskModels.ReviewRequest request
    ) {
        AccountEntity operator = authService.requireAccount(authentication);
        if (assignment.getStatus() == TaskAssignmentStatus.APPROVED) {
            throw new ApiException(HttpStatus.CONFLICT, "该新手任务已经通过");
        }
        requireTaskNotClosed(assignment);
        String comment = normalize(request.comment());
        String exemptionReason = normalize(request.exemptionReason());

        if (request.decision() == TaskModels.ReviewDecision.REJECTED) {
            if (!assignment.isReviewable()) {
                throw new ApiException(HttpStatus.CONFLICT, "该对象尚未提交完成说明，暂不能驳回");
            }
            if (comment == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "驳回必须填写审核意见");
            }
            assignment.reject(operator, comment, java.time.Instant.now());
            TaskAssignmentEntity saved = assignments.save(assignment);
            notifyApplicant(saved, "TASK_REJECTED", "新手任务需要补充",
                    comment + "（从打回时起有 24 小时补交时间）", "/application");
            return saved;
        }

        // 通过：先落审核结果，使转正守卫看到「已通过」，再在同一事务内转正。
        if (!assignment.isReviewable() && exemptionReason == null) {
            throw new ApiException(HttpStatus.CONFLICT, "该对象尚未提交完成说明；如确认免修，请填写豁免理由");
        }
        if (exemptionReason == null) {
            // 转正门槛：大任务下的子任务必须全部勾选，豁免路径不受此限制。
            requireAllSubtasksCompleted(assignment);
        }
        assignment.approve(operator, comment == null ? "新手任务审核通过" : comment);
        assignments.save(assignment);

        RecruitmentApplicationEntity application = assignment.getRecruitmentApplication();
        if (application == null) {
            throw new ApiException(HttpStatus.CONFLICT, "新手任务没有关联的报名记录");
        }
        recruitmentService.convertApplicantToMember(
                application, operator, exemptionReason,
                "新手任务审核通过，通过技能测试直接转为正式成员");

        assignments.flush();
        TaskAssignmentEntity refreshed = requireAssignment(assignment.getTask().getId(), assignment.getId());
        notifyApplicant(refreshed, "TASK_APPROVED", "新手任务已通过",
                "你的新手任务已通过审核，账号已转为正式成员，请重新登录以获取新的权限。", "/profile");
        return refreshed;
    }

    private void notifyApplicant(TaskAssignmentEntity assignment, String type, String title, String summary, String path) {
        RecruitmentApplicationEntity application = assignment.getRecruitmentApplication();
        if (application != null) {
            notifications.send(application.getApplicant(), type, title, summary, path);
        }
    }

    /** 提交与审核通过的前置条件：大任务下的子任务全部已提交内容。 */
    private void requireAllSubtasksCompleted(TaskAssignmentEntity assignment) {
        if (assignment.hasSubmittedAllSubtasks()) return;
        throw new ApiException(HttpStatus.BAD_REQUEST,
                "请先提交全部子任务的内容（已提交 " + assignment.submittedSubtaskCount() + " / "
                        + assignment.getTask().getSubtasks().size() + " 项）");
    }

    // ---------- 视图 ----------

    /** 截止日后仍可审核待审对象；管理员手动结束任务则仍是终态。 */
    private void requireTaskNotClosed(TaskAssignmentEntity assignment) {
        if (assignment.getTask().getStatus() == TaskStatus.CLOSED) {
            throw new ApiException(HttpStatus.CONFLICT, "任务已被管理员结束，不能再审核或驳回");
        }
    }

    private TaskAssignmentEntity requireAssignment(UUID taskId, UUID assignmentId) {
        TaskAssignmentEntity assignment = assignments.findById(assignmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "任务对象不存在"));
        if (!assignment.getTask().getId().equals(taskId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "任务对象不存在");
        }
        return assignment;
    }

    private TaskModels.OnboardingTaskView toOnboardingView(TaskAssignmentEntity assignment) {
        TaskEntity task = assignment.getTask();
        LocalDate today = LocalDate.now(LAB_TIME_ZONE);
        LocalDate dueDate = dueDateOf(assignment);
        return new TaskModels.OnboardingTaskView(
                assignment.getId(),
                task.getId(),
                task.getTitle(),
                task.getContentHtml(),
                assignment.issuedOn(),
                dueDate,
                assignment.getResubmissionDeadlineAt(),
                daysRemaining(dueDate, today),
                isOverdue(assignment, today),
                assignment.getStatus(),
                assignment.getCompletionNote(),
                assignment.getSubmittedAt(),
                assignment.getReviewComment(),
                assignment.getExemptionReason(),
                assignment.getConvertedProfileId(),
                assignment.submittedSubtaskCount(),
                task.getSubtasks().size(),
                assignment.hasSubmittedAllSubtasks(),
                TaskTiming.isMemberEditable(assignment, today, true),
                subtaskViews(task, assignment)
        );
    }

    private TaskModels.OnboardingRowView toOnboardingRow(TaskAssignmentEntity assignment) {
        TaskEntity task = assignment.getTask();
        RecruitmentApplicationEntity application = assignment.getRecruitmentApplication();
        LocalDate today = LocalDate.now(LAB_TIME_ZONE);
        AccountEntity reviewer = assignment.getReviewedBy();
        LocalDate dueDate = dueDateOf(assignment);
        AccountEntity extender = assignment.getDueDateExtendedBy();
        return new TaskModels.OnboardingRowView(
                assignment.getId(),
                task.getId(),
                application == null ? null : application.getId(),
                application == null ? null : application.getName(),
                application == null ? null : application.getApplicant().getUsername(),
                application == null ? null : application.getMemberCode(),
                application == null ? List.of() : application.getSkillTags(),
                application == null ? null : application.getStage(),
                assignment.getStatus(),
                assignment.submittedSubtaskCount(),
                task.getSubtasks().size(),
                assignment.getCompletionNote(),
                assignment.getSubmittedAt(),
                reviewer == null ? null : reviewer.getUsername(),
                assignment.getReviewedAt(),
                assignment.getReviewComment(),
                assignment.getExemptionReason(),
                assignment.getConvertedProfileId(),
                assignment.issuedOn(),
                dueDate,
                isOverdue(assignment, today),
                assignment.getResubmissionDeadlineAt(),
                assignment.getDueDateExtendedAt(),
                extender == null ? null : extender.getUsername(),
                assignment.getDueDateExtensionReason()
        );
    }

    static List<TaskModels.SubtaskView> subtaskViews(TaskEntity task, TaskAssignmentEntity assignment) {
        List<TaskModels.SubtaskView> views = new ArrayList<>();
        for (TaskSubtaskEntity subtask : task.getSubtasks()) {
            var progress = assignment.progressOf(subtask.getId()).orElse(null);
            views.add(new TaskModels.SubtaskView(
                    subtask.getId(),
                    subtask.getTitle(),
                    subtask.getDisplayOrder(),
                    progress != null && progress.isSubmitted(),
                    progress == null ? null : progress.getSubmittedAt(),
                    subtask.hasContent()
            ));
        }
        return views;
    }

    static long daysRemaining(LocalDate endDate, LocalDate today) {
        if (endDate == null) return 0;
        return ChronoUnit.DAYS.between(today, endDate);
    }

    /**
     * 对象的截止日期：新手任务用「本人发放当天 + 大任务时长」记录在对象上的 due_date；
     * 普通任务与悬赏继续使用任务级的结束日期。统一由 {@link TaskTiming#deadlineOf} 计算。
     */
    static LocalDate dueDateOf(TaskAssignmentEntity assignment) {
        return TaskTiming.deadlineOf(assignment);
    }

    /**
     * 逾期标记：到期日期已过且对象未通过。到期本身是硬边界（成员不可写、管理员不可下结论），
     * 这里只负责界面上的显示层派生，不写库、不改状态、不自动处理。
     */
    static boolean isOverdue(TaskAssignmentEntity assignment, LocalDate today) {
        LocalDate dueDate = dueDateOf(assignment);
        return dueDate != null
                && assignment.getStatus() != TaskAssignmentStatus.APPROVED
                && dueDate.isBefore(today);
    }

    /** 新手任务的阶段条件：仍停留在技能测试阶段（试用期仅为历史兼容）才能写与下结论。 */
    private static boolean onboardingStageOpen(TaskAssignmentEntity assignment) {
        RecruitmentApplicationEntity application = assignment.getRecruitmentApplication();
        return application != null
                && (application.getStage() == RecruitmentStage.SKILL_TEST
                || application.getStage() == RecruitmentStage.PROBATION);
    }

    private MemberProfileEntity requireOwnProfile(AccountEntity account) {
        return profiles.findByAccountId(account.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "当前账号还没有成员资料"));
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String clean = value.trim();
        return clean.isEmpty() ? null : clean;
    }

    // ==================== 普通任务 ====================

    @PreAuthorize("hasAuthority('TASK_MANAGE')")
    @Transactional
    public TaskModels.TaskView createStandardTask(Authentication authentication, TaskModels.CreateTaskRequest request) {
        AccountEntity operator = authService.requireAccount(authentication);
        validateSchedule(request.startDate(), request.endDate());
        String contentHtml = TaskContentSanitizer.cleanContent(request.contentHtml());
        TaskEntity task = new TaskEntity(
                TaskType.STANDARD,
                request.title().trim(),
                contentHtml,
                request.startDate(),
                request.endDate(),
                request.points(),
                TaskStatus.DRAFT,
                operator
        );
        task.replaceSubtasks(normalizeSubtasks(request.subtasks()));
        TaskEntity saved = tasks.save(task);
        saved.replaceAudienceRules(buildRules(saved, request.rules()));
        tasks.save(saved);
        syncManualAssignments(saved, request.memberProfileIds());
        return toTaskView(requireTask(saved.getId()));
    }

    @PreAuthorize("hasAuthority('TASK_MANAGE')")
    @Transactional
    public TaskModels.TaskView updateStandardTask(
            Authentication authentication,
            UUID taskId,
            TaskModels.CreateTaskRequest request
    ) {
        TaskEntity task = requireStandardTask(taskId);
        if (task.getStatus() == TaskStatus.CLOSED) {
            throw new ApiException(HttpStatus.CONFLICT, "已结束的任务不能再修改");
        }
        validateSchedule(request.startDate(), request.endDate());
        String contentHtml = TaskContentSanitizer.cleanContent(request.contentHtml());
        boolean published = task.getStatus() == TaskStatus.PUBLISHED;
        if (published) {
            // 发布时绑定积分：已发布任务的条件、对象与积分值锁定，只能改内容与时间。
            LocalDate previousEndDate = task.getEndDate();
            task.updateContentOnly(request.title().trim(), contentHtml, request.startDate(), request.endDate());
            replaceSubtasksForPublished(task, normalizeSubtasks(request.subtasks()));
            // 延长截止日期后，已结算的任务要重新进入待结算队列：否则延长期内新完成的人永远拿不到积分。
            // 已发过的对象靠来源编号幂等跳过，因此重新结算是安全的。
            if (isDeadlineExtended(previousEndDate, task.getEndDate()) && task.isPointsSettled()) {
                task.resetPointsSettlement();
            }
        } else {
            task.updateDetails(request.title().trim(), contentHtml, request.startDate(), request.endDate(),
                    request.points());
            List<TaskEntity.SubtaskDraft> draftSubtasks = normalizeSubtasks(request.subtasks());
            requireSubtasksBelongTo(task, draftSubtasks);
            task.syncSubtasks(draftSubtasks);
            List<TaskAudienceRuleEntity> nextRules = buildRules(task, request.rules());
            // 先落库删除旧条件，避免 Hibernate 先 INSERT 后 DELETE 触发 V11 的唯一约束。
            task.replaceAudienceRules(List.of());
            tasks.flush();
            task.replaceAudienceRules(nextRules);
            syncManualAssignments(task, request.memberProfileIds());
        }
        tasks.save(task);
        return toTaskView(requireTask(taskId));
    }

    /**
     * 截止日期是否被延长（新值晚于旧值；旧值为空视为延长到有期限）。
     *
     * <p>用于已结算任务的「重新进入待结算」判断。缩短或不改动不影响幂等，因此不需要处理。</p>
     */
    private static boolean isDeadlineExtended(LocalDate previous, LocalDate current) {
        if (current == null) return false;
        return previous == null || current.isAfter(previous);
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
                .forEach(item -> subtaskProgressRepository.deleteBySubtaskId(item.getId()));
        subtaskProgressRepository.flush();
        task.syncSubtasks(drafts);
    }

    @PreAuthorize("hasAuthority('TASK_MANAGE')")
    @Transactional
    public TaskModels.TaskView publishStandardTask(Authentication authentication, UUID taskId) {
        TaskEntity task = requireStandardTask(taskId);
        if (task.getStatus() != TaskStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "只有草稿任务可以发布");
        }
        if (task.getAudienceRules().isEmpty() && task.getAssignments().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请先选择等级条件或指定成员");
        }
        List<MemberProfileEntity> matched = matchStoredAudience(task.getAudienceRules());
        Set<UUID> existing = task.getAssignments().stream()
                .map(item -> item.getMemberProfile().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<TaskAssignmentEntity> created = new ArrayList<>();
        for (MemberProfileEntity profile : matched) {
            if (existing.add(profile.getId())) {
                created.add(new TaskAssignmentEntity(task, profile, null, TaskAssignmentSource.CRITERIA));
            }
        }
        if (created.isEmpty() && task.getAssignments().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "当前条件没有命中任何成员，请调整条件或直接指定成员");
        }
        created.forEach(task::addAssignment);
        task.markPublished();
        tasks.save(task);
        notifyAssignees(task);
        return toTaskView(requireTask(taskId));
    }

    @PreAuthorize("hasAuthority('TASK_MANAGE')")
    @Transactional
    public TaskModels.TaskView closeStandardTask(UUID taskId) {
        TaskEntity task = requireStandardTask(taskId);
        if (task.getStatus() != TaskStatus.PUBLISHED) {
            throw new ApiException(HttpStatus.CONFLICT, "只有已发布任务可以结束");
        }
        task.markClosed();
        tasks.save(task);
        tasks.flush();
        // 结束即到期：同步结算一次，不必等下一个调度周期。CLOSED 是终局（不能再审核或驳回）。
        taskSettlementService.settle(taskId);
        return toTaskView(requireTask(taskId));
    }

    @PreAuthorize("hasAuthority('TASK_MANAGE')")
    @Transactional
    public void deleteStandardTask(UUID taskId) {
        TaskEntity task = requireStandardTask(taskId);
        if (task.getStatus() != TaskStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "只有草稿任务可以删除");
        }
        assignments.deleteAll(assignments.findByTaskIdOrderByCreatedAtAsc(taskId));
        tasks.delete(task);
    }

    @PreAuthorize("hasAuthority('TASK_MANAGE')")
    @Transactional
    public TaskModels.AudiencePreviewView previewAudience(Authentication authentication, TaskModels.AudienceRequest request) {
        AccountEntity operator = authService.requireAccount(authentication);
        List<Rule> rules = normalizeRules(request == null ? null : request.rules());
        Set<UUID> explicit = request == null || request.memberProfileIds() == null
                ? Set.of() : new LinkedHashSet<>(request.memberProfileIds());
        List<MemberProfileEntity> matched = matchAudience(rules);
        Map<UUID, String> sources = new LinkedHashMap<>();
        matched.forEach(profile -> sources.put(profile.getId(), "CRITERIA"));
        for (UUID id : explicit) {
            if (sources.containsKey(id)) continue;
            profiles.findById(id).ifPresent(profile -> sources.put(profile.getId(), "MANUAL"));
        }
        List<TaskModels.AudienceMemberView> members = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : sources.entrySet()) {
            profiles.findById(entry.getKey()).ifPresent(profile -> members.add(toAudienceMember(profile, entry.getValue(), operator)));
        }
        members.sort(Comparator.comparing(TaskModels.AudienceMemberView::name, String.CASE_INSENSITIVE_ORDER));
        int eligible = (int) members.stream().filter(TaskModels.AudienceMemberView::pointEligible).count();
        return new TaskModels.AudiencePreviewView(members.size(), eligible, false, members);
    }

    @PreAuthorize("hasAuthority('TASK_MANAGE')")
    @Transactional
    public TaskModels.TaskView supplementAssignments(UUID taskId, TaskModels.AudienceRequest request) {
        TaskEntity task = tasks.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "任务不存在"));
        if (task.getTaskType() != TaskType.STANDARD) {
            throw new ApiException(HttpStatus.NOT_FOUND, "任务不存在");
        }
        if (task.getStatus() != TaskStatus.PUBLISHED) {
            throw new ApiException(HttpStatus.CONFLICT, "只有已发布任务可以补充发放");
        }
        List<Rule> rules = normalizeRules(request == null ? null : request.rules());
        Set<UUID> desired = new LinkedHashSet<>();
        matchAudience(rules).forEach(profile -> desired.add(profile.getId()));
        Set<UUID> explicit = request == null || request.memberProfileIds() == null
                ? Set.of() : new LinkedHashSet<>(request.memberProfileIds());
        desired.addAll(explicit);
        if (desired.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请选择要补发的成员，或调整发放条件");
        }
        Map<UUID, MemberProfileEntity> recipients = new LinkedHashMap<>();
        for (UUID id : desired) {
            if (id == null) throw new ApiException(HttpStatus.BAD_REQUEST, "指定的成员不存在");
            MemberProfileEntity profile = profiles.findById(id)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "指定的成员不存在"));
            if (profile.getAccount().getRole() == Role.VISITOR) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "游客不能作为普通任务的发放对象");
            }
            recipients.put(id, profile);
        }
        Set<UUID> existing = task.getAssignments().stream()
                .map(item -> item.getMemberProfile().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<TaskAssignmentEntity> created = new ArrayList<>();
        for (UUID id : desired) {
            if (existing.contains(id)) continue;
            created.add(new TaskAssignmentEntity(task, recipients.get(id), null,
                    explicit.contains(id) ? TaskAssignmentSource.MANUAL : TaskAssignmentSource.CRITERIA));
        }
        created.forEach(task::addAssignment);
        tasks.saveAndFlush(task);
        // merge 的级联可能用托管副本替换新对象；从持久化结果取带真实 ID 的通知目标。
        List<TaskAssignmentEntity> added = assignments.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
                .filter(item -> !existing.contains(item.getMemberProfile().getId()))
                .toList();
        notifyAssignees(task, added);
        return toTaskView(task, new TaskModels.SupplementResult(created.size(), desired.size() - created.size()));
    }

    @PreAuthorize("hasAuthority('TASK_MANAGE')")
    @Transactional
    public TaskModels.TaskView removeAssignment(UUID taskId, UUID assignmentId) {
        TaskEntity task = requireStandardTask(taskId);
        TaskAssignmentEntity assignment = requireAssignment(taskId, assignmentId);
        if (assignment.isOnboarding()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "新手任务对象不能移除");
        }
        if (assignment.getStatus() != TaskAssignmentStatus.PENDING || assignment.getPointGrantId() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "该对象已提交或已计分，不能移除");
        }
        task.removeAssignment(assignment);
        tasks.save(task);
        tasks.flush();
        return toTaskView(requireTask(taskId));
    }

    private TaskAssignmentEntity reviewStandard(
            Authentication authentication,
            TaskAssignmentEntity assignment,
            TaskModels.ReviewRequest request
    ) {
        AccountEntity operator = authService.requireAccount(authentication);
        if (assignment.getStatus() == TaskAssignmentStatus.APPROVED) {
            throw new ApiException(HttpStatus.CONFLICT, "该任务对象已经通过");
        }
        requireTaskNotClosed(assignment);
        TaskEntity task = assignment.getTask();
        String comment = normalize(request.comment());

        if (request.decision() == TaskModels.ReviewDecision.REJECTED) {
            if (comment == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "驳回必须填写审核意见");
            }
            assignment.reject(operator, comment, java.time.Instant.now());
            assignments.save(assignment);
            notifications.send(assignment.getMemberProfile().getAccount(), "TASK_REJECTED",
                    "任务需要补充", task.getTitle() + "：" + comment + "（从打回时起有 24 小时补交时间）",
                    "/tasks/" + assignment.getId());
            return assignment;
        }

        if (!assignment.isReviewable()) {
            throw new ApiException(HttpStatus.CONFLICT, "该对象尚未提交完成说明，不能确认通过");
        }
        boolean lateApproval = TaskTiming.isExpired(assignment, LocalDate.now(LAB_TIME_ZONE));
        assignment.approve(operator, comment);
        assignments.save(assignment);
        if (lateApproval && task.getPoints() > 0) {
            taskSettlementService.settleLateApproval(task.getId(), assignment.getId());
            assignment = assignments.findWithMemberAndTaskById(assignment.getId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "任务对象不存在"));
        }
        notifications.send(assignment.getMemberProfile().getAccount(), "TASK_APPROVED",
                "任务已确认通过",
                task.getTitle() + (task.getPoints() > 0
                        ? lateApproval ? "：截止后审核通过，积分已计入或记录了跳过原因"
                                : "：已确认通过，" + task.getPoints() + " 积分将在任务到期后统一结算"
                        : "：已确认通过"),
                "/tasks/" + assignment.getId());
        return assignment;
    }

    private TaskModels.ReviewResultView toReviewResult(TaskAssignmentEntity assignment) {
        TaskEntity task = assignment.getTask();
        LocalDate today = LocalDate.now(LAB_TIME_ZONE);
        return new TaskModels.ReviewResultView(
                assignment.getId(),
                task.getId(),
                task.getTaskType(),
                assignment.getStatus(),
                assignment.getCompletionNote(),
                assignment.getSubmittedAt(),
                assignment.getReviewComment(),
                assignment.getExemptionReason(),
                assignment.getConvertedProfileId(),
                assignment.getAwardedPoints(),
                assignment.getPointsSkippedReason(),
                assignment.issuedOn(),
                dueDateOf(assignment),
                daysRemaining(dueDateOf(assignment), today),
                isOverdue(assignment, today),
                assignment.getResubmissionDeadlineAt(),
                subtaskViews(task, assignment)
        );
    }

    // ---------- 管理端：列表、详情与完成情况 ----------

    @PreAuthorize("hasAuthority('TASK_MANAGE')")
    @Transactional(readOnly = true)
    public List<TaskModels.TaskSummaryView> listStandardTasks(TaskStatus status, String keyword) {
        String needle = keyword == null ? "" : keyword.trim().toLowerCase();
        return tasks.findByTaskTypeOrderByCreatedAtDesc(TaskType.STANDARD).stream()
                .filter(task -> status == null || task.getStatus() == status)
                .filter(task -> needle.isEmpty() || task.getTitle().toLowerCase().contains(needle))
                .map(this::toSummaryView)
                .toList();
    }

    @PreAuthorize("hasAuthority('TASK_MANAGE')")
    @Transactional(readOnly = true)
    public List<TaskModels.MemberOptionView> memberOptions() {
        return profiles.findAll().stream()
                .filter(profile -> profile.getAccount().getRole() != Role.VISITOR)
                .sorted(Comparator.comparing(MemberProfileEntity::getName, String.CASE_INSENSITIVE_ORDER))
                .map(profile -> new TaskModels.MemberOptionView(
                        profile.getId(),
                        profile.getName(),
                        profile.getMemberCode(),
                        profile.getGrade(),
                        profile.getStatus().name(),
                        profile.getAccount().getRole().name()))
                .toList();
    }

    @PreAuthorize("hasAuthority('TASK_MANAGE')")
    @Transactional(readOnly = true)
    public TaskModels.TaskView standardTask(UUID taskId) {
        return toTaskView(requireStandardTask(taskId));
    }

    @PreAuthorize("hasAuthority('TASK_MANAGE')")
    @Transactional(readOnly = true)
    public TaskModels.TaskProgressView taskProgress(UUID taskId) {
        TaskEntity task = requireStandardTask(taskId);
        List<TaskAssignmentEntity> rows = assignments.findByTaskIdOrderByCreatedAtAsc(taskId);
        LocalDate today = LocalDate.now(LAB_TIME_ZONE);
        Map<TaskAssignmentStatus, Integer> counts = new EnumMap<>(TaskAssignmentStatus.class);
        int awarded = 0;
        List<TaskModels.AssignmentRowView> assignmentRows = new ArrayList<>();
        for (TaskAssignmentEntity assignment : rows) {
            counts.merge(assignment.getStatus(), 1, Integer::sum);
            if (assignment.getAwardedPoints() != null) awarded += assignment.getAwardedPoints();
            assignmentRows.add(toAssignmentRow(assignment, today));
        }
        List<TaskModels.SubtaskProgressView> subtaskProgress = new ArrayList<>();
        for (TaskSubtaskEntity subtask : task.getSubtasks()) {
            int completed = (int) rows.stream()
                    .filter(row -> row.progressOf(subtask.getId()).map(item -> item.isSubmitted()).orElse(false))
                    .count();
            subtaskProgress.add(new TaskModels.SubtaskProgressView(subtask.getId(), subtask.getTitle(), completed, rows.size()));
        }
        return new TaskModels.TaskProgressView(
                toTaskView(task),
                rows.size(),
                counts.getOrDefault(TaskAssignmentStatus.APPROVED, 0),
                counts.getOrDefault(TaskAssignmentStatus.SUBMITTED, 0),
                counts.getOrDefault(TaskAssignmentStatus.PENDING, 0),
                counts.getOrDefault(TaskAssignmentStatus.REJECTED, 0),
                awarded,
                subtaskProgress,
                assignmentRows
        );
    }

    // ---------- 成员端 ----------

    @PreAuthorize("hasAnyRole('TEACHER', 'CORE_STUDENT', 'MEMBER')")
    @Transactional(readOnly = true)
    public List<TaskModels.MyTaskView> myTasks(Authentication authentication) {
        MemberProfileEntity profile = requireOwnProfile(authService.requireAccount(authentication));
        LocalDate today = LocalDate.now(LAB_TIME_ZONE);
        List<TaskAssignmentEntity> rows = assignments.findByMemberProfile_IdOrderByTask_CreatedAtDesc(profile.getId()).stream()
                .filter(assignment -> assignment.getTask().getStatus() != TaskStatus.DRAFT)
                .toList();
        Map<UUID, BountyPrizeFulfillmentEntity> fulfillmentByAssignment = prizeFulfillments
                .findByAssignment_IdIn(rows.stream().map(TaskAssignmentEntity::getId).toList()).stream()
                .collect(Collectors.toMap(item -> item.getAssignment().getId(), item -> item));
        return rows.stream()
                .map(assignment -> toMyTaskView(assignment, today, fulfillmentByAssignment.get(assignment.getId())))
                .toList();
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'CORE_STUDENT', 'MEMBER')")
    @Transactional(readOnly = true)
    public TaskModels.MyTaskDetailView myTaskDetail(Authentication authentication, UUID assignmentId) {
        MemberProfileEntity profile = requireOwnProfile(authService.requireAccount(authentication));
        TaskAssignmentEntity assignment = assignments.findByIdAndMemberProfile_Id(assignmentId, profile.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "任务不存在"));
        return toMyTaskDetail(assignment, LocalDate.now(LAB_TIME_ZONE), prizeFulfillment(assignment));
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'CORE_STUDENT', 'MEMBER')")
    @Transactional
    public TaskModels.MyTaskDetailView confirmBountyPrizeReceived(Authentication authentication, UUID assignmentId) {
        MemberProfileEntity profile = requireOwnProfile(authService.requireAccount(authentication));
        TaskAssignmentEntity assignment = requireOwnAssignment(assignmentId, profile);
        if (!assignment.getTask().isBounty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "悬赏任务不存在");
        }
        bountyService.confirmPrizeReceived(profile, assignmentId);
        return toMyTaskDetail(assignment, LocalDate.now(LAB_TIME_ZONE), prizeFulfillment(assignment));
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'CORE_STUDENT', 'MEMBER')")
    @Transactional
    public TaskModels.MyTaskDetailView submitMySubtask(
            Authentication authentication,
            UUID assignmentId,
            UUID subtaskId,
            String contentHtml
    ) {
        MemberProfileEntity profile = requireOwnProfile(authService.requireAccount(authentication));
        TaskAssignmentEntity assignment = requireOwnAssignment(assignmentId, profile);
        requireStandardEditable(assignment);
        TaskSubtaskEntity subtask = requireSubtask(assignment.getTask(), subtaskId);
        assignment.submitSubtaskProgress(subtask, TaskContentSanitizer.cleanSubmissionContent(contentHtml));
        assignment = assignments.save(assignment);
        return toMyTaskDetail(assignment, LocalDate.now(LAB_TIME_ZONE), prizeFulfillment(assignment));
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'CORE_STUDENT', 'MEMBER')")
    @Transactional
    public TaskModels.MyTaskDetailView submitMyTask(Authentication authentication, UUID assignmentId, String note) {
        MemberProfileEntity profile = requireOwnProfile(authService.requireAccount(authentication));
        TaskAssignmentEntity assignment = requireOwnAssignment(assignmentId, profile);
        if (assignment.getTask().isBounty()) {
            // 悬赏是「提交即完成」：直接进入已通过并锁定完成名次，没有「待确认」环节；积分仍等到期结算。
            TaskAssignmentEntity completed = bountyService.complete(assignment, note.trim());
            return toMyTaskDetail(completed, LocalDate.now(LAB_TIME_ZONE), prizeFulfillment(completed));
        }
        requireStandardEditable(assignment);
        assignment.submit(note.trim());
        assignment = assignments.save(assignment);
        return toMyTaskDetail(assignment, LocalDate.now(LAB_TIME_ZONE), prizeFulfillment(assignment));
    }

    /** 成员打开某个子任务：返回该子任务的富文本正文、本人完成状态与大任务上下文。 */
    @PreAuthorize("hasAnyRole('TEACHER', 'CORE_STUDENT', 'MEMBER')")
    @Transactional(readOnly = true)
    public TaskModels.SubtaskDetailView mySubtaskDetail(
            Authentication authentication,
            UUID assignmentId,
            UUID subtaskId
    ) {
        MemberProfileEntity profile = requireOwnProfile(authService.requireAccount(authentication));
        TaskAssignmentEntity assignment = requireOwnAssignment(assignmentId, profile);
        return toSubtaskDetail(assignment, requireSubtask(assignment.getTask(), subtaskId),
                isMemberEditable(assignment));
    }

    /** 报名者打开本人的某个新手任务子任务。 */
    @PreAuthorize("hasAuthority('RECRUITMENT_SELF_VIEW')")
    @Transactional(readOnly = true)
    public TaskModels.SubtaskDetailView ownOnboardingSubtask(Authentication authentication, UUID subtaskId) {
        AccountEntity account = authService.requireAccount(authentication);
        TaskAssignmentEntity assignment = requireOwnAssignment(account);
        return toSubtaskDetail(assignment, requireSubtask(assignment.getTask(), subtaskId),
                isMemberEditable(assignment));
    }

    /** 管理端编辑前读取子任务正文（管理员本来就是编辑者，但仍按需加载，列表接口不带正文）。 */
    @PreAuthorize("hasAuthority('TASK_MANAGE')")
    @Transactional(readOnly = true)
    public TaskModels.SubtaskDetailView adminSubtask(UUID taskId, UUID subtaskId) {
        TaskEntity task = requireTask(taskId);
        return toSubtaskDetail(null, requireSubtask(task, subtaskId), false);
    }

    private TaskSubtaskEntity requireSubtask(TaskEntity task, UUID subtaskId) {
        return task.getSubtasks().stream()
                .filter(item -> item.getId().equals(subtaskId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "子任务不存在"));
    }

    private TaskModels.SubtaskDetailView toSubtaskDetail(
            TaskAssignmentEntity assignment,
            TaskSubtaskEntity subtask,
            boolean editable
    ) {
        TaskEntity task = subtask.getTask();
        LocalDate today = LocalDate.now(LAB_TIME_ZONE);
        var progress = assignment == null ? null : assignment.progressOf(subtask.getId()).orElse(null);
        LocalDate dueDate = assignment == null ? task.getEndDate() : dueDateOf(assignment);
        return new TaskModels.SubtaskDetailView(
                subtask.getId(),
                task.getId(),
                assignment == null ? null : assignment.getId(),
                task.getTitle(),
                task.getTaskType(),
                subtask.getTitle(),
                subtask.getContentHtml(),
                progress != null && progress.isSubmitted(),
                progress == null ? null : progress.getSubmittedAt(),
                progress == null ? null : progress.getContentHtml(),
                assignment == null ? 0 : assignment.submittedSubtaskCount(),
                task.getSubtasks().size(),
                dueDate,
                assignment != null && isOverdue(assignment, today),
                editable
        );
    }

    /** 管理端逐条查看某个对象的子任务提交内容（审核时展开才请求，避免列表接口带一堆正文）。 */
    @PreAuthorize("hasAuthority('TASK_MANAGE')")
    @Transactional(readOnly = true)
    public List<TaskModels.SubtaskSubmissionView> adminAssignmentSubtasks(UUID taskId, UUID assignmentId) {
        TaskAssignmentEntity assignment = requireAssignment(taskId, assignmentId);
        List<TaskModels.SubtaskSubmissionView> rows = new ArrayList<>();
        for (TaskSubtaskEntity subtask : assignment.getTask().getSubtasks()) {
            var progress = assignment.progressOf(subtask.getId()).orElse(null);
            rows.add(new TaskModels.SubtaskSubmissionView(
                    subtask.getId(),
                    subtask.getTitle(),
                    subtask.getContentHtml(),
                    progress != null && progress.isSubmitted(),
                    progress == null ? null : progress.getSubmittedAt(),
                    progress == null ? null : progress.getContentHtml()
            ));
        }
        return rows;
    }

    /** 成员侧是否还能改：到期即冻结（见 {@link TaskTiming}），新手任务另需仍在技能测试阶段。 */
    private boolean isMemberEditable(TaskAssignmentEntity assignment) {
        return TaskTiming.isMemberEditable(
                assignment, LocalDate.now(LAB_TIME_ZONE), onboardingStageOpen(assignment));
    }

    private TaskAssignmentEntity requireOwnAssignment(UUID assignmentId, MemberProfileEntity profile) {
        return assignments.findByIdAndMemberProfile_Id(assignmentId, profile.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "任务不存在"));
    }

    private void requireStandardEditable(TaskAssignmentEntity assignment) {
        if (assignment.getStatus() == TaskAssignmentStatus.APPROVED) {
            throw new ApiException(HttpStatus.CONFLICT, "任务已通过，不能再修改");
        }
        if (assignment.getTask().getStatus() != TaskStatus.PUBLISHED) {
            throw new ApiException(HttpStatus.CONFLICT, "任务已结束，不能再修改");
        }
        if (TaskTiming.isExpired(assignment, LocalDate.now(LAB_TIME_ZONE))) {
            throw new ApiException(HttpStatus.CONFLICT, "任务已截止，不能再提交；如需继续请联系管理员延长截止日期");
        }
    }

    // ---------- 条件匹配 ----------

    private record Rule(TaskAudienceDimension dimension, String value) {
    }

    private List<Rule> normalizeRules(List<TaskModels.AudienceRuleRequest> requests) {
        if (requests == null) return List.of();
        List<Rule> rules = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (TaskModels.AudienceRuleRequest request : requests) {
            String value = request.value() == null ? "" : request.value().trim();
            if (value.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "发放条件的取值不能为空");
            validateRuleValue(request.dimension(), value);
            if (seen.add(request.dimension() + "|" + value)) {
                rules.add(new Rule(request.dimension(), value));
            }
        }
        return rules;
    }

    static void validateRuleValue(TaskAudienceDimension dimension, String value) {
        switch (dimension) {
            case ROLE -> {
                if (!List.of(Role.TEACHER.name(), Role.CORE_STUDENT.name(), Role.MEMBER.name()).contains(value)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "角色条件只支持教师、核心学生或普通成员");
                }
            }
            case MEMBER_STATUS -> {
                MemberStatus status;
                try {
                    status = MemberStatus.valueOf(value);
                } catch (IllegalArgumentException error) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "成员状态取值不合法");
                }
                if (!SELECTABLE_STATUSES.contains(status)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "成员状态条件只支持「试用」或「正式」");
                }
            }
            case GRADE, SKILL_TAG -> {
                if (value.length() > 120) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "发放条件的取值不能超过 120 个字符");
                }
            }
        }
    }

    private List<TaskAudienceRuleEntity> buildRules(TaskEntity task, List<TaskModels.AudienceRuleRequest> requests) {
        return normalizeRules(requests).stream()
                .map(rule -> new TaskAudienceRuleEntity(task, rule.dimension(), rule.value()))
                .toList();
    }

    /** 同一维度内取「或」，不同维度之间取「且」。 */
    private List<MemberProfileEntity> matchStoredAudience(List<TaskAudienceRuleEntity> ruleEntities) {
        List<Rule> rules = ruleEntities.stream()
                .map(item -> new Rule(item.getDimension(), item.getRuleValue()))
                .toList();
        return matchAudience(rules);
    }

    private List<MemberProfileEntity> matchAudience(List<Rule> rules) {
        Map<TaskAudienceDimension, Set<String>> grouped = new EnumMap<>(TaskAudienceDimension.class);
        for (Rule rule : rules) {
            grouped.computeIfAbsent(rule.dimension(), key -> new LinkedHashSet<>()).add(rule.value());
        }
        if (grouped.isEmpty()) return List.of();
        return profiles.findAll().stream()
                .filter(profile -> matches(profile, grouped))
                .sorted(Comparator.comparing(MemberProfileEntity::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private boolean matches(MemberProfileEntity profile, Map<TaskAudienceDimension, Set<String>> grouped) {
        for (Map.Entry<TaskAudienceDimension, Set<String>> entry : grouped.entrySet()) {
            Set<String> values = entry.getValue();
            boolean hit = switch (entry.getKey()) {
                case ROLE -> values.contains(profile.getAccount().getRole().name());
                case MEMBER_STATUS -> values.contains(profile.getStatus().name());
                case GRADE -> profile.getGrade() != null && values.contains(profile.getGrade());
                case SKILL_TAG -> profile.getSkillTags().stream().anyMatch(values::contains);
            };
            if (!hit) return false;
        }
        return true;
    }

    // ---------- 视图与校验 ----------

    private TaskEntity requireTask(UUID taskId) {
        return tasks.findById(taskId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "任务不存在"));
    }

    /**
     * 只接受普通任务。
     *
     * <p>新手任务与悬赏都必须在这里被挡住：否则悬赏会混进「按等级条件发放」的列表、预览与补充发放，
     * 而悬赏的对象只能由成员自主接取产生。</p>
     */
    private TaskEntity requireStandardTask(UUID taskId) {
        TaskEntity task = requireTask(taskId);
        if (task.getTaskType() != TaskType.STANDARD) {
            throw new ApiException(HttpStatus.NOT_FOUND, "任务不存在");
        }
        return task;
    }

    private static void validateSchedule(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "结束日期不能早于开始日期");
        }
    }

    /**
     * 子任务写入规范化：标题去空按标题去重、正文走白名单清洗（可为空）、限定条数。
     * {@code id} 原样保留，由 {@link TaskEntity#syncSubtasks} 决定更新还是新增。
     */
    private static List<TaskEntity.SubtaskDraft> normalizeSubtasks(List<TaskModels.SubtaskInput> subtasks) {
        Map<String, TaskEntity.SubtaskDraft> distinct = new LinkedHashMap<>();
        if (subtasks != null) {
            for (TaskModels.SubtaskInput item : subtasks) {
                if (item == null) continue;
                String title = item.title() == null ? "" : item.title().trim();
                if (title.isEmpty()) continue;
                String content = TaskContentSanitizer.cleanSubtaskContent(item.contentHtml());
                distinct.putIfAbsent(title, new TaskEntity.SubtaskDraft(item.id(), title, content));
            }
        }
        if (distinct.size() > TaskContentSanitizer.MAX_SUBTASKS) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "子任务不能超过 " + TaskContentSanitizer.MAX_SUBTASKS + " 项");
        }
        return List.copyOf(distinct.values());
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

    private void syncManualAssignments(TaskEntity task, List<UUID> memberProfileIds) {
        Set<UUID> desired = memberProfileIds == null ? Set.of() : new LinkedHashSet<>(memberProfileIds);
        List<TaskAssignmentEntity> current = assignments.findByTaskIdOrderByCreatedAtAsc(task.getId()).stream()
                .filter(item -> item.getSource() == TaskAssignmentSource.MANUAL)
                .toList();
        Map<UUID, TaskAssignmentEntity> existing = new LinkedHashMap<>();
        current.forEach(item -> existing.put(item.getMemberProfile().getId(), item));
        for (TaskAssignmentEntity item : current) {
            if (!desired.contains(item.getMemberProfile().getId())) task.removeAssignment(item);
        }
        for (UUID id : desired) {
            if (existing.containsKey(id)) continue;
            MemberProfileEntity profile = profiles.findById(id)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "指定的成员不存在"));
            task.addAssignment(new TaskAssignmentEntity(task, profile, null, TaskAssignmentSource.MANUAL));
        }
    }

    private void notifyAssignees(TaskEntity task) {
        List<TaskAssignmentEntity> rows = assignments.findByTaskIdOrderByCreatedAtAsc(task.getId());
        notifyAssignees(task, rows);
    }

    private void notifyAssignees(TaskEntity task, List<TaskAssignmentEntity> rows) {
        for (TaskAssignmentEntity assignment : rows) {
            if (assignment.getMemberProfile() == null) continue;
            notifications.send(
                    assignment.getMemberProfile().getAccount(),
                    "TASK_ASSIGNED",
                    "你有新任务",
                    task.getTitle() + (task.getEndDate() == null ? "" : "，截止日期 " + task.getEndDate()),
                    "/tasks/" + assignment.getId()
            );
        }
    }

    private TaskModels.AudienceMemberView toAudienceMember(
            MemberProfileEntity profile,
            String source,
            AccountEntity operator
    ) {
        String reason = pointIneligibleReason(profile, operator);
        return new TaskModels.AudienceMemberView(
                profile.getId(),
                profile.getName(),
                profile.getMemberCode(),
                profile.getGrade(),
                profile.getStatus().name(),
                profile.getAccount().getRole().name(),
                source,
                reason == null,
                reason
        );
    }

    /** 与积分模块的发放规则保持一致，用于发布前提示哪些对象无法计分。 */
    private String pointIneligibleReason(MemberProfileEntity profile, AccountEntity operator) {
        if (profile.getAccount().getRole() == Role.TEACHER) return "指导教师不参与成员积分统计";
        if (profile.getStatus() != MemberStatus.OFFICIAL) return "只能给正式成员发放积分";
        if (profile.getAccount().getId().equals(operator.getId())) return "积分管理员不能给自己发放积分";
        return null;
    }

    private TaskModels.TaskView toTaskView(TaskEntity task) {
        return toTaskView(task, null);
    }

    private TaskModels.TaskView toTaskView(TaskEntity task, TaskModels.SupplementResult supplementResult) {
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
                task.getAssignments().stream()
                        .filter(item -> item.getSource() == TaskAssignmentSource.MANUAL)
                        .filter(item -> item.getMemberProfile() != null)
                        .map(item -> item.getMemberProfile().getId())
                        .toList(),
                supplementResult
        );
    }

    private TaskModels.TaskSummaryView toSummaryView(TaskEntity task) {
        List<TaskAssignmentEntity> rows = task.getAssignments();
        int approved = 0;
        int submitted = 0;
        int pending = 0;
        int rejected = 0;
        for (TaskAssignmentEntity assignment : rows) {
            switch (assignment.getStatus()) {
                case APPROVED -> approved++;
                case SUBMITTED -> submitted++;
                case REJECTED -> rejected++;
                default -> pending++;
            }
        }
        return new TaskModels.TaskSummaryView(
                task.getId(),
                task.getTaskType(),
                task.getTitle(),
                task.getPoints(),
                task.getStatus(),
                task.getStartDate(),
                task.getEndDate(),
                rows.size(),
                approved,
                submitted,
                pending,
                rejected,
                task.getPointsSettledAt(),
                TaskTiming.isTaskExpired(task, LocalDate.now(LAB_TIME_ZONE))
        );
    }

    private TaskModels.AssignmentRowView toAssignmentRow(TaskAssignmentEntity assignment) {
        return toAssignmentRow(assignment, LocalDate.now(LAB_TIME_ZONE));
    }

    private TaskModels.AssignmentRowView toAssignmentRow(TaskAssignmentEntity assignment, LocalDate today) {
        MemberProfileEntity profile = assignment.getMemberProfile();
        TaskEntity task = assignment.getTask();
        AccountEntity reviewer = assignment.getReviewedBy();
        return new TaskModels.AssignmentRowView(
                assignment.getId(),
                profile.getId(),
                profile.getName(),
                profile.getMemberCode(),
                profile.getGrade(),
                profile.getStatus().name(),
                profile.getAccount().getRole().name(),
                assignment.getSource().name(),
                assignment.getStatus(),
                assignment.submittedSubtaskCount(),
                task.getSubtasks().size(),
                assignment.getCompletionNote(),
                assignment.getSubmittedAt(),
                reviewer == null ? null : reviewer.getUsername(),
                assignment.getReviewedAt(),
                assignment.getReviewComment(),
                assignment.getAwardedPoints(),
                assignment.getPointsSkippedReason(),
                isOverdue(assignment, today)
        );
    }

    private TaskModels.MyTaskView toMyTaskView(TaskAssignmentEntity assignment, LocalDate today,
            BountyPrizeFulfillmentEntity fulfillment) {
        TaskEntity task = assignment.getTask();
        return new TaskModels.MyTaskView(
                assignment.getId(),
                task.getId(),
                task.getTitle(),
                task.getStartDate(),
                task.getEndDate(),
                task.getPoints(),
                task.getStatus(),
                assignment.getStatus(),
                assignment.submittedSubtaskCount(),
                task.getSubtasks().size(),
                assignment.getAwardedPoints(),
                assignment.getPointsSkippedReason(),
                assignment.getReviewComment(),
                isOverdue(assignment, today),
                TaskTiming.isExpired(assignment, today),
                isMemberEditable(assignment),
                assignment.getResubmissionDeadlineAt(),
                task.isPointsSettled(),
                task.getTaskType(),
                task.getPrizeDescription(),
                task.getPrizeSlots(),
                assignment.getCompletionRank(),
                assignment.isPrizeAwarded(),
                fulfillment == null ? null : fulfillment.getStatus(),
                fulfillment == null ? null : fulfillment.getIssuedAt(),
                fulfillment == null ? null : fulfillment.getReceivedAt()
        );
    }

    private TaskModels.MyTaskDetailView toMyTaskDetail(TaskAssignmentEntity assignment, LocalDate today,
            BountyPrizeFulfillmentEntity fulfillment) {
        TaskEntity task = assignment.getTask();
        return new TaskModels.MyTaskDetailView(
                assignment.getId(),
                task.getId(),
                task.getTitle(),
                task.getContentHtml(),
                task.getStartDate(),
                task.getEndDate(),
                task.getPoints(),
                task.getStatus(),
                assignment.getStatus(),
                assignment.getCompletionNote(),
                assignment.getSubmittedAt(),
                assignment.getReviewComment(),
                assignment.getAwardedPoints(),
                assignment.getPointsSkippedReason(),
                isOverdue(assignment, today),
                TaskTiming.isExpired(assignment, today),
                isMemberEditable(assignment),
                assignment.getResubmissionDeadlineAt(),
                task.isPointsSettled(),
                task.getTaskType(),
                task.getPrizeDescription(),
                task.getPrizeSlots(),
                assignment.getCompletionRank(),
                assignment.isPrizeAwarded(),
                fulfillment == null ? null : fulfillment.getStatus(),
                fulfillment == null ? null : fulfillment.getIssuedAt(),
                fulfillment == null ? null : fulfillment.getReceivedAt(),
                subtaskViews(task, assignment)
        );
    }

    private BountyPrizeFulfillmentEntity prizeFulfillment(TaskAssignmentEntity assignment) {
        return assignment.getTask().isBounty()
                ? prizeFulfillments.findByAssignment_Id(assignment.getId()).orElse(null)
                : null;
    }
}

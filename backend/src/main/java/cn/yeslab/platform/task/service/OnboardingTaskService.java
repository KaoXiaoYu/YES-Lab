package cn.yeslab.platform.task.service;

import cn.yeslab.platform.common.error.ApiException;
import cn.yeslab.platform.identity.model.AccountEntity;
import cn.yeslab.platform.identity.service.AuthService;
import cn.yeslab.platform.notification.service.NotificationService;
import cn.yeslab.platform.recruitment.model.RecruitmentApplicationEntity;
import cn.yeslab.platform.task.api.TaskModels;
import cn.yeslab.platform.task.model.TaskAssignmentEntity;
import cn.yeslab.platform.task.model.TaskAssignmentStatus;
import cn.yeslab.platform.task.model.TaskEntity;
import cn.yeslab.platform.task.model.TaskStatus;
import cn.yeslab.platform.task.model.TaskSubtaskEntity;
import cn.yeslab.platform.task.model.TaskType;
import cn.yeslab.platform.task.repository.TaskAssignmentRepository;
import cn.yeslab.platform.task.repository.TaskRepository;
import cn.yeslab.platform.task.repository.TaskSubtaskProgressRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 新手任务大任务：所有处于技能测试阶段的报名者共享同一个大任务。
 *
 * <p>管理员在大任务上直接添加/删除子任务，改动即时对所有在途对象生效；不再有「模板」与
 * 「同步到在途任务」这套复制机制。大任务不存在时使用代码内置的默认内容初始化，内置内容只在此维护一份。</p>
 */
@Service
public class OnboardingTaskService {

    static final ZoneId LAB_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    static final String DEFAULT_TITLE = "新手入门任务";
    static final int DEFAULT_DURATION_DAYS = 7;
    static final String DEFAULT_CONTENT_HTML = """
            <p>欢迎通过面试！请在截止日期前完成下列基础训练，逐项勾选全部子任务后提交完成说明，管理员确认后你将成为实验室正式成员。</p>
            <h3>完成说明</h3>
            <ul>
            <li>不需要额外提交材料，按子任务逐项完成并在页面勾选即可。</li>
            <li>遇到问题可以在讨论板提问，或直接联系带你入门的老成员。</li>
            <li>逾期不会自动处理；如确有困难，请联系管理员延长时长。</li>
            </ul>
            """;
    /** 内置默认子任务：标题 + 可选说明（子任务说明为富文本，可为空）。 */
    static final List<TaskEntity.SubtaskDraft> DEFAULT_SUBTASKS = List.of(
            new TaskEntity.SubtaskDraft(null, "配置开发环境（Git、Python 或 Java、代码编辑器）",
                    "<p>安装 Git、JDK 21 或 Python 3，并确认 <code>git --version</code> 与 <code>java -version</code> 能正常输出版本号。</p>"),
            new TaskEntity.SubtaskDraft(null, "阅读实验室新人手册与安全规范",
                    "<p>重点看实验室用电、飞行与设备借用章节，读完后在完成说明里写一句你印象最深的安全要求。</p>"),
            new TaskEntity.SubtaskDraft(null, "认识实验室常用的无人机与机器狗设备，了解基本安全操作",
                    "<p>找到带你入门的老成员，请他带你认一遍常用设备与充电、收纳位置。</p>"),
            new TaskEntity.SubtaskDraft(null, "跑通一个示例程序或仿真环境",
                    "<p>可以任选一个仓库里的示例工程或官方仿真教程，把运行命令与结果记下来。</p>"),
            new TaskEntity.SubtaskDraft(null, "在讨论板发布一条自我介绍",
                    "<p>在讨论板发一条自我介绍，说明你的方向兴趣与想学的技术，方便大家认识你。</p>")
    );

    private final TaskRepository tasks;
    private final TaskAssignmentRepository assignments;
    private final TaskSubtaskProgressRepository progress;
    private final AuthService authService;
    private final NotificationService notifications;

    public OnboardingTaskService(
            TaskRepository tasks,
            TaskAssignmentRepository assignments,
            TaskSubtaskProgressRepository progress,
            AuthService authService,
            NotificationService notifications
    ) {
        this.tasks = tasks;
        this.assignments = assignments;
        this.progress = progress;
        this.authService = authService;
        this.notifications = notifications;
    }

    /** 读取新手任务大任务；尚未落库时返回内置默认内容，不写库（GET 保持只读）。 */
    @Transactional(readOnly = true)
    public TaskModels.OnboardingTaskAdminView current() {
        return tasks.findFirstByTaskTypeOrderByCreatedAtAsc(TaskType.ONBOARDING)
                .map(this::toAdminView)
                .orElseGet(() -> new TaskModels.OnboardingTaskAdminView(
                        null,
                        DEFAULT_TITLE,
                        DEFAULT_CONTENT_HTML,
                        DEFAULT_DURATION_DAYS,
                        builtInDefaultSubtasks(),
                        null,
                        true
                ));
    }

    /** 取新手任务大任务；不存在时用内置默认内容落库，供发放（面试通过）与补发使用。 */
    @Transactional
    public TaskEntity requireOrCreate(AccountEntity operator) {
        return tasks.findFirstByTaskTypeOrderByCreatedAtAsc(TaskType.ONBOARDING)
                .orElseGet(() -> {
                    TaskEntity created = new TaskEntity(
                            TaskType.ONBOARDING, DEFAULT_TITLE, DEFAULT_CONTENT_HTML,
                            null, null, 0, TaskStatus.PUBLISHED, operator);
                    created.updateOnboardingDetails(DEFAULT_TITLE, DEFAULT_CONTENT_HTML, DEFAULT_DURATION_DAYS);
                    created.replaceSubtasks(DEFAULT_SUBTASKS);
                    created.markPublished();
                    return tasks.save(created);
                });
    }

    /**
     * 保存新手任务大任务：标题、正文、时长与子任务都即时生效。
     *
     * <ul>
     *   <li>删除的子任务连同勾选记录一起删除；</li>
     *   <li>新增子任务会把「已提交待确认」的对象退回「待完成」并发送站内消息，需勾选新项后重新提交；</li>
     *   <li>时长变化时，尚未通过的对象按各自的发放日期重新计算截止日期。</li>
     * </ul>
     */
    @Transactional
    public TaskModels.SaveOnboardingTaskResult save(
            Authentication authentication,
            TaskModels.SaveOnboardingTaskRequest request
    ) {
        AccountEntity operator = authService.requireAccount(authentication);
        List<TaskEntity.SubtaskDraft> drafts = normalizeSubtasks(request.subtasks());
        if (drafts.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请至少添加一项子任务");
        }
        String contentHtml = TaskContentSanitizer.cleanContent(request.contentHtml());

        TaskEntity task = requireOrCreate(operator);
        requireSubtasksBelongTo(task, drafts);
        Integer previousDuration = task.getDurationDays();

        // 按 id 同步：未出现的 id 视为删除，删除前先清掉对应勾选记录（外键顺序），显式 flush 保证顺序。
        Set<UUID> keep = drafts.stream()
                .map(TaskEntity.SubtaskDraft::id)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<TaskSubtaskEntity> removing = task.getSubtasks().stream()
                .filter(item -> !keep.contains(item.getId()))
                .toList();
        if (!removing.isEmpty()) {
            removing.forEach(item -> progress.deleteBySubtaskId(item.getId()));
            progress.flush();
        }
        List<String> added = task.syncSubtasks(drafts);
        task.updateOnboardingDetails(request.title().trim(), contentHtml, request.durationDays());
        TaskEntity saved = tasks.save(task);
        tasks.flush();

        int reopened = added.isEmpty() ? 0 : reopenSubmitted(saved, added);
        int rescheduled = previousDuration != null && previousDuration != request.durationDays()
                ? reschedulePending(saved)
                : 0;
        TaskEntity refreshed = requireOnboardingTask();
        return new TaskModels.SaveOnboardingTaskResult(toAdminView(refreshed), reopened, rescheduled);
    }

    /** 新增子任务后，把已提交待确认的对象退回待完成，并通知本人重新提交。 */
    private int reopenSubmitted(TaskEntity task, List<String> addedTitles) {
        String summary = "管理员新增了子任务：" + String.join("、", addedTitles) + "；请完成新子任务后重新提交。";
        int count = 0;
        for (TaskAssignmentEntity assignment : assignments.findByTaskIdOrderByCreatedAtAsc(task.getId())) {
            if (!assignment.reopenForNewSubtasks()) continue;
            assignments.save(assignment);
            count++;
            notifyApplicant(assignment, "TASK_UPDATED", "新手任务新增了子任务", summary);
        }
        return count;
    }

    /**
     * 按人延长新手任务的截止日期。
     *
     * <p>这是到期冻结之后唯一的解锁动作：某个报名者逾期后本人不能提交、管理员也不能审核，
     * 必须先把他的 {@code due_date} 推到未来。延长只影响这一位报名者，其他人不受影响；
     * 与「调整大任务时长」（按各自发放日重算所有人）互为补充。</p>
     *
     * <p>校验：对象必须属于新手任务大任务、尚未通过、属于仍在技能测试阶段的报名记录；
     * 新日期必须晚于今天（否则解锁没有意义）且晚于原日期（不允许变相缩短）。</p>
     */
    @Transactional
    public TaskModels.DueDateExtensionView extendDueDate(
            Authentication authentication,
            UUID assignmentId,
            TaskModels.ExtendOnboardingDueDateRequest request
    ) {
        AccountEntity operator = authService.requireAccount(authentication);
        TaskAssignmentEntity assignment = assignments.findById(assignmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "新手任务对象不存在"));
        if (!assignment.isOnboarding()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "只能延长新手任务的截止日期");
        }
        if (assignment.getStatus() == TaskAssignmentStatus.APPROVED) {
            throw new ApiException(HttpStatus.CONFLICT, "该报名者已经通过新手任务，无需延长");
        }
        RecruitmentApplicationEntity application = assignment.getRecruitmentApplication();
        if (application == null) {
            throw new ApiException(HttpStatus.CONFLICT, "新手任务没有关联的报名记录");
        }

        LocalDate today = LocalDate.now(LAB_TIME_ZONE);
        LocalDate newDueDate = request.dueDate();
        if (!newDueDate.isAfter(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "新的截止日期必须晚于今天");
        }
        LocalDate previousDueDate = assignment.getDueDate();
        if (previousDueDate != null && !newDueDate.isAfter(previousDueDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "新的截止日期必须晚于原截止日期");
        }
        String reason = request.reason() == null || request.reason().isBlank()
                ? null
                : request.reason().trim();

        assignment.extendDueDate(newDueDate, operator, reason);
        TaskAssignmentEntity saved = assignments.save(assignment);
        notifyApplicant(saved, "TASK_UPDATED", "新手任务截止日期已延长",
                "管理员已将你的新手任务截止日期延长至 " + newDueDate + "，请在新的截止日期前完成。");
        return new TaskModels.DueDateExtensionView(
                saved.getId(),
                application.getId(),
                application.getName(),
                previousDueDate,
                newDueDate,
                saved.getDueDateExtendedAt(),
                operator.getUsername(),
                reason
        );
    }

    /** 时长变化后，按每个对象自己的发放日期重算截止日期（已通过的对象不动）。 */
    private int reschedulePending(TaskEntity task) {
        int days = task.getDurationDays();
        int count = 0;
        for (TaskAssignmentEntity assignment : assignments.findByTaskIdOrderByCreatedAtAsc(task.getId())) {
            if (assignment.getStatus() == TaskAssignmentStatus.APPROVED) continue;
            LocalDate due = assignment.issuedOn().plusDays(days);
            if (due.equals(assignment.getDueDate())) continue;
            assignment.assignDueDate(due);
            assignments.save(assignment);
            count++;
            notifyApplicant(assignment, "TASK_UPDATED", "新手任务截止日期已调整",
                    "新手任务时长已调整为 " + days + " 天，你的截止日期为 " + due + "。");
        }
        return count;
    }

    /** 子任务写入规范化：标题去空按标题去重、正文走白名单清洗（可为空）、限定条数。 */
    static List<TaskEntity.SubtaskDraft> normalizeSubtasks(List<TaskModels.SubtaskInput> subtasks) {
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

    /** 提交里带 id 的子任务必须属于该大任务。 */
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

    private void notifyApplicant(TaskAssignmentEntity assignment, String type, String title, String summary) {
        RecruitmentApplicationEntity application = assignment.getRecruitmentApplication();
        if (application != null) {
            notifications.send(application.getApplicant(), type, title, summary, "/application");
        }
    }

    private TaskEntity requireOnboardingTask() {
        return tasks.findFirstByTaskTypeOrderByCreatedAtAsc(TaskType.ONBOARDING)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "新手任务尚未创建"));
    }

    private TaskModels.OnboardingTaskAdminView toAdminView(TaskEntity task) {
        List<TaskModels.SubtaskDefinitionView> subtasks = new ArrayList<>();
        for (TaskSubtaskEntity item : task.getSubtasks()) {
            subtasks.add(new TaskModels.SubtaskDefinitionView(
                    item.getId(), item.getTitle(), item.getDisplayOrder(), item.hasContent()));
        }
        return new TaskModels.OnboardingTaskAdminView(
                task.getId(),
                task.getTitle(),
                task.getContentHtml(),
                task.getDurationDays() == null ? DEFAULT_DURATION_DAYS : task.getDurationDays(),
                subtasks,
                task.getUpdatedAt(),
                false
        );
    }

    private List<TaskModels.SubtaskDefinitionView> builtInDefaultSubtasks() {
        List<TaskModels.SubtaskDefinitionView> views = new ArrayList<>();
        int order = 0;
        for (TaskEntity.SubtaskDraft sub : DEFAULT_SUBTASKS) {
            views.add(new TaskModels.SubtaskDefinitionView(null, sub.title(), order++, sub.contentHtml() != null));
        }
        return views;
    }
}

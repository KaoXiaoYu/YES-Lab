package cn.openlims.platform.task.service;

import cn.openlims.platform.identity.model.AccountEntity;
import cn.openlims.platform.notification.service.NotificationService;
import cn.openlims.platform.recruitment.model.RecruitmentApplicationEntity;
import cn.openlims.platform.recruitment.service.OnboardingTaskIssuer;
import cn.openlims.platform.task.model.TaskAssignmentEntity;
import cn.openlims.platform.task.model.TaskAssignmentSource;
import cn.openlims.platform.task.model.TaskEntity;
import cn.openlims.platform.task.model.TaskType;
import cn.openlims.platform.task.repository.TaskAssignmentRepository;
import cn.openlims.platform.task.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

/**
 * 新手任务发放：在共享的新手任务大任务上为报名者建立一条对象（进度按人记录）。
 *
 * <p>刻意独立成一个不依赖 {@code RecruitmentService} 的 bean：招新服务通过
 * {@link OnboardingTaskIssuer} 接口调用它，而 {@code TaskService} 反过来依赖招新服务完成转正，
 * 若把发放逻辑放在 {@code TaskService} 中会形成 Spring 循环依赖。</p>
 */
@Service
public class OnboardingTaskIssuerService implements OnboardingTaskIssuer {

    private static final ZoneId LAB_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final TaskRepository tasks;
    private final TaskAssignmentRepository assignments;
    private final OnboardingTaskService onboardingTasks;
    private final NotificationService notifications;

    public OnboardingTaskIssuerService(
            TaskRepository tasks,
            TaskAssignmentRepository assignments,
            OnboardingTaskService onboardingTasks,
            NotificationService notifications
    ) {
        this.tasks = tasks;
        this.assignments = assignments;
        this.onboardingTasks = onboardingTasks;
        this.notifications = notifications;
    }

    @Override
    @Transactional
    public void issueIfAbsent(RecruitmentApplicationEntity application, AccountEntity operator) {
        if (hasAssignment(application.getId())) return;
        issue(application, operator);
    }

    /** 该报名记录是否已经在新手任务大任务上有对象（任何状态都算，含已驳回）。 */
    boolean hasAssignment(UUID applicationId) {
        return assignments.existsByRecruitmentApplication_IdAndTask_TaskType(
                applicationId, TaskType.ONBOARDING);
    }

    /**
     * 在共享大任务上为报名者建立对象：截止日期 = 发放当天 + 大任务时长，每人各自计算。
     * 幂等：同一报名记录在同一大任务上只会有一条对象。
     */
    @Transactional
    public Optional<TaskAssignmentEntity> issue(RecruitmentApplicationEntity application, AccountEntity operator) {
        TaskEntity task = onboardingTasks.requireOrCreate(operator);
        Optional<TaskAssignmentEntity> existing =
                assignments.findByTaskIdAndRecruitmentApplication_Id(task.getId(), application.getId());
        if (existing.isPresent()) {
            return existing;
        }
        int durationDays = task.getDurationDays() == null
                ? OnboardingTaskService.DEFAULT_DURATION_DAYS
                : task.getDurationDays();
        LocalDate dueDate = LocalDate.now(LAB_TIME_ZONE).plusDays(durationDays);

        TaskAssignmentEntity assignment = new TaskAssignmentEntity(
                task, null, application, TaskAssignmentSource.ONBOARDING);
        assignment.assignDueDate(dueDate);
        task.addAssignment(assignment);
        TaskEntity saved = tasks.save(task);

        notifications.send(
                application.getApplicant(),
                "TASK_ASSIGNED",
                "新手任务已发放",
                "你已通过面试，请在 " + dueDate + " 前完成全部子任务并提交，管理员确认后即可转为正式成员。",
                "/application"
        );
        // saved 的聚合集合内即本次新增的对象；重新按唯一键读取，避免依赖集合顺序。
        return assignments.findByTaskIdAndRecruitmentApplication_Id(saved.getId(), application.getId());
    }
}

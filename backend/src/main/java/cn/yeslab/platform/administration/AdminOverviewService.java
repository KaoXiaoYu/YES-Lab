package cn.yeslab.platform.administration;

import cn.yeslab.platform.achievement.model.VerificationStatus;
import cn.yeslab.platform.recruitment.model.RecruitmentStage;
import cn.yeslab.platform.task.model.BountyPrizeFulfillmentStatus;
import cn.yeslab.platform.task.model.TaskAssignmentStatus;
import cn.yeslab.platform.task.model.TaskType;
import cn.yeslab.platform.task.model.TaskStatus;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Read-only counts of work waiting for administrators. Each item links to the page where it is handled;
 * nothing here changes state, so it never needs its own permission beyond reaching /api/v1/admin.
 */
@Service
public class AdminOverviewService {
    private final EntityManager entityManager;

    public AdminOverviewService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public record OverviewItem(String key, String label, String hint, long count, String href) { }

    public record OverviewView(List<OverviewItem> items, long totalPending, Instant generatedAt) { }

    @Transactional(readOnly = true)
    public OverviewView overview() {
        List<OverviewItem> items = List.of(
                new OverviewItem("RECRUITMENT_SIGNUP", "待初筛报名", "已提交报名表，等待进入初筛",
                        countApplications(RecruitmentStage.SIGNUP), "/admin/recruitment"),
                new OverviewItem("RECRUITMENT_SCREENING", "初筛中", "需要给出初筛结论",
                        countApplications(RecruitmentStage.SCREENING), "/admin/recruitment"),
                new OverviewItem("RECRUITMENT_INTERVIEW", "面试阶段", "预约、面试或等待面试结论",
                        countApplications(RecruitmentStage.INTERVIEW), "/admin/recruitment"),
                new OverviewItem("ONBOARDING_REVIEW", "新手任务待审核", "审核通过即转为正式成员",
                        countSubmitted(TaskType.ONBOARDING), "/admin/tasks/onboarding"),
                new OverviewItem("TASK_REVIEW", "普通任务待审核", "成员已提交完成说明",
                        countSubmitted(TaskType.STANDARD), "/admin/tasks"),
                new OverviewItem("BOUNTY_PRIZE", "悬赏奖金待发放", "获奖成员等待线下发放登记",
                        countPendingPrizes(), "/admin/bounties"),
                new OverviewItem("ACHIEVEMENT_REVIEW", "获奖成果待审核", "审核通过后在首页公开展示",
                        countPendingCompetitions(), "/admin/achievements")
        );
        long total = items.stream().mapToLong(OverviewItem::count).sum();
        return new OverviewView(items, total, Instant.now());
    }

    private long countApplications(RecruitmentStage stage) {
        return entityManager.createQuery(
                        "select count(a) from RecruitmentApplicationEntity a where a.stage = :stage", Long.class)
                .setParameter("stage", stage)
                .getSingleResult();
    }

    private long countSubmitted(TaskType type) {
        return entityManager.createQuery(
                        "select count(a) from TaskAssignmentEntity a where a.status = :status and a.task.taskType = :type and a.task.status <> :closed",
                        Long.class)
                .setParameter("status", TaskAssignmentStatus.SUBMITTED)
                .setParameter("type", type)
                .setParameter("closed", TaskStatus.CLOSED)
                .getSingleResult();
    }

    private long countPendingPrizes() {
        return entityManager.createQuery(
                        "select count(f) from BountyPrizeFulfillmentEntity f where f.status = :status", Long.class)
                .setParameter("status", BountyPrizeFulfillmentStatus.PENDING)
                .getSingleResult();
    }

    private long countPendingCompetitions() {
        return entityManager.createQuery(
                        "select count(c) from CompetitionEntity c where c.verificationStatus = :status", Long.class)
                .setParameter("status", VerificationStatus.PENDING)
                .getSingleResult();
    }
}

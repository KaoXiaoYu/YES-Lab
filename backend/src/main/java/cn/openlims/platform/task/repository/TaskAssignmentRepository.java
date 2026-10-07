package cn.openlims.platform.task.repository;

import cn.openlims.platform.task.model.TaskAssignmentEntity;
import cn.openlims.platform.task.model.TaskAssignmentStatus;
import cn.openlims.platform.task.model.TaskType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskAssignmentRepository extends JpaRepository<TaskAssignmentEntity, UUID> {

    @EntityGraph(attributePaths = {"memberProfile.account", "task"})
    Optional<TaskAssignmentEntity> findWithMemberAndTaskById(UUID id);

    List<TaskAssignmentEntity> findByTaskIdOrderByCreatedAtAsc(UUID taskId);

    List<TaskAssignmentEntity> findByMemberProfile_IdOrderByTask_CreatedAtDesc(UUID memberProfileId);

    Optional<TaskAssignmentEntity> findByIdAndMemberProfile_Id(UUID id, UUID memberProfileId);

    Optional<TaskAssignmentEntity> findByTaskIdAndMemberProfile_Id(UUID taskId, UUID memberProfileId);

    Optional<TaskAssignmentEntity> findByTaskIdAndRecruitmentApplication_Id(UUID taskId, UUID applicationId);

    Optional<TaskAssignmentEntity> findFirstByRecruitmentApplication_IdAndTask_TaskTypeOrderByCreatedAtDesc(
            UUID applicationId, TaskType taskType);

    boolean existsByRecruitmentApplication_IdAndTask_TaskTypeAndStatusIn(
            UUID applicationId, TaskType taskType, Collection<TaskAssignmentStatus> statuses);

    boolean existsByRecruitmentApplication_IdAndTask_TaskType(UUID applicationId, TaskType taskType);

    long countByTaskIdAndStatus(UUID taskId, TaskAssignmentStatus status);

    /** 悬赏：占用接取名额的数量（进行中 + 已完成；已放弃与已驳回不占名额）。 */
    long countByTaskIdAndStatusIn(UUID taskId, Collection<TaskAssignmentStatus> statuses);

    /** 悬赏：历史第几个完成 = 已有名次的对象数（名次只增不复用，驳回也不释放）。 */
    long countByTaskIdAndCompletionRankIsNotNull(UUID taskId);

    /** 悬赏：按完成名次升序取出所有已完成过的对象，用于重算奖金不变量。 */
    List<TaskAssignmentEntity> findByTaskIdAndCompletionRankIsNotNullOrderByCompletionRankAsc(UUID taskId);

    interface OnboardingDeadline {
        UUID getTaskId();
        long getParticipantCount();
        java.time.LocalDate getUpcomingDeadline();
        java.time.LocalDate getLatestDeadline();
    }
    @org.springframework.data.jpa.repository.Query("""
        select a.task.id as taskId,
            count(a) as participantCount,
            min(case when coalesce(a.dueDate, a.task.endDate) >= :today then coalesce(a.dueDate, a.task.endDate) else null end) as upcomingDeadline,
            max(coalesce(a.dueDate, a.task.endDate)) as latestDeadline
        from TaskAssignmentEntity a
        where a.task.taskType = :type and a.task.status = :published
            and a.status not in :completed
        group by a.task.id
        """)
    List<OnboardingDeadline> aggregateOnboardingDeadlines(
        @org.springframework.data.repository.query.Param("today") java.time.LocalDate today,
        @org.springframework.data.repository.query.Param("type") TaskType type,
        @org.springframework.data.repository.query.Param("published") cn.openlims.platform.task.model.TaskStatus published,
        @org.springframework.data.repository.query.Param("completed") Collection<TaskAssignmentStatus> completed);

    interface DeadlineParticipant {
        UUID getTaskId();
        UUID getProfileId();
        String getName();
        TaskType getTaskType();
        TaskAssignmentStatus getStatus();
    }
    @org.springframework.data.jpa.repository.Query("""
        select a.task.id as taskId, m.id as profileId, m.name as name,
            a.task.taskType as taskType, a.status as status
        from TaskAssignmentEntity a join a.memberProfile m
        where a.task.status = :published and a.task.taskType <> :onboarding
        order by a.createdAt, a.id
        """)
    List<DeadlineParticipant> findDeadlineParticipants(
        @org.springframework.data.repository.query.Param("published") cn.openlims.platform.task.model.TaskStatus published,
        @org.springframework.data.repository.query.Param("onboarding") TaskType onboarding);

    @org.springframework.data.jpa.repository.Query("""
        select a from TaskAssignmentEntity a
        left join a.memberProfile m left join a.recruitmentApplication r
        where m.id = :profileId or r.applicant.id = :accountId
        """)
    List<TaskAssignmentEntity> findRelatedAssignments(
        @org.springframework.data.repository.query.Param("profileId") UUID profileId,
        @org.springframework.data.repository.query.Param("accountId") UUID accountId);
}

package cn.yeslab.platform.task.repository;

import cn.yeslab.platform.task.model.TaskAssignmentEntity;
import cn.yeslab.platform.task.model.TaskAssignmentStatus;
import cn.yeslab.platform.task.model.TaskType;
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
}

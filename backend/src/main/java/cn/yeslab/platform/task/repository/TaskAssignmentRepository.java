package cn.yeslab.platform.task.repository;

import cn.yeslab.platform.task.model.TaskAssignmentEntity;
import cn.yeslab.platform.task.model.TaskAssignmentStatus;
import cn.yeslab.platform.task.model.TaskType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskAssignmentRepository extends JpaRepository<TaskAssignmentEntity, UUID> {

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
}

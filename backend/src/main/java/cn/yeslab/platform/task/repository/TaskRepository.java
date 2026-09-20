package cn.yeslab.platform.task.repository;

import cn.yeslab.platform.task.model.TaskEntity;
import cn.yeslab.platform.task.model.TaskStatus;
import cn.yeslab.platform.task.model.TaskType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<TaskEntity, UUID> {

    List<TaskEntity> findByTaskTypeOrderByCreatedAtDesc(TaskType taskType);

    /** 新手任务是唯一一条 ONBOARDING 大任务。 */
    Optional<TaskEntity> findFirstByTaskTypeOrderByCreatedAtAsc(TaskType taskType);

    List<TaskEntity> findByTaskTypeAndStatusOrderByCreatedAtDesc(TaskType taskType, TaskStatus status);

    List<TaskEntity> findAllByOrderByCreatedAtDesc();
}

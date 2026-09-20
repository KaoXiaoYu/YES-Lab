package cn.yeslab.platform.task.repository;

import cn.yeslab.platform.task.model.TaskSubtaskProgressEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaskSubtaskProgressRepository extends JpaRepository<TaskSubtaskProgressEntity, UUID> {

    List<TaskSubtaskProgressEntity> findByAssignmentId(UUID assignmentId);

    void deleteBySubtaskId(UUID subtaskId);
}

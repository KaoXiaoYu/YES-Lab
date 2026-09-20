package cn.yeslab.platform.task.repository;

import cn.yeslab.platform.task.model.TaskSubtaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaskSubtaskRepository extends JpaRepository<TaskSubtaskEntity, UUID> {

    List<TaskSubtaskEntity> findByTaskIdOrderByDisplayOrderAsc(UUID taskId);

    void deleteByTaskId(UUID taskId);
}

package cn.yeslab.platform.task.repository;

import cn.yeslab.platform.task.model.TaskAudienceRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaskAudienceRuleRepository extends JpaRepository<TaskAudienceRuleEntity, UUID> {

    List<TaskAudienceRuleEntity> findByTaskId(UUID taskId);

    void deleteByTaskId(UUID taskId);
}

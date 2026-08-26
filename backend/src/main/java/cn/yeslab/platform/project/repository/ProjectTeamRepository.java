package cn.yeslab.platform.project.repository;

import cn.yeslab.platform.project.model.ProjectTeamEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProjectTeamRepository extends JpaRepository<ProjectTeamEntity, UUID> {
}

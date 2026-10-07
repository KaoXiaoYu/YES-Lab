package cn.openlims.platform.project.repository;

import cn.openlims.platform.project.model.ProjectTeamEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectTeamRepository extends JpaRepository<ProjectTeamEntity, UUID> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"leader", "advisor", "members"})
    @Query("select distinct p from ProjectTeamEntity p where p.status in :statuses")
    java.util.List<ProjectTeamEntity> findDeadlineProjects(@Param("statuses") java.util.Collection<cn.openlims.platform.project.model.ProjectStatus> statuses);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select source from ProjectTeamEntity source where source.id = :id")
    Optional<ProjectTeamEntity> findByIdForUpdate(@Param("id") UUID id);
}

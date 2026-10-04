package cn.yeslab.platform.achievement.repository;

import cn.yeslab.platform.achievement.model.CompetitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompetitionRepository extends JpaRepository<CompetitionEntity, UUID> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"captainProfile", "advisorProfile", "participants.linkedProfile"})
    @Query("select distinct c from CompetitionEntity c where c.lifecycle <> :finished")
    java.util.List<CompetitionEntity> findDeadlineCompetitions(@Param("finished") cn.yeslab.platform.achievement.model.CompetitionLifecycle finished);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select source from CompetitionEntity source where source.id = :id")
    Optional<CompetitionEntity> findByIdForUpdate(@Param("id") UUID id);
}

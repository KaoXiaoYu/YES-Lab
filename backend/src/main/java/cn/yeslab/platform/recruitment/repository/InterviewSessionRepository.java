package cn.yeslab.platform.recruitment.repository;

import cn.yeslab.platform.recruitment.model.InterviewSessionEntity;
import cn.yeslab.platform.recruitment.model.InterviewSessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InterviewSessionRepository extends JpaRepository<InterviewSessionEntity, UUID> {
    List<InterviewSessionEntity> findByStatusInOrderByStartAtAsc(Collection<InterviewSessionStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from InterviewSessionEntity session where session.id = :id")
    Optional<InterviewSessionEntity> findLockedById(@Param("id") UUID id);
}

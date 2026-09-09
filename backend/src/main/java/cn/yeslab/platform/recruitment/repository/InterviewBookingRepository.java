package cn.yeslab.platform.recruitment.repository;

import cn.yeslab.platform.recruitment.model.InterviewBookingEntity;
import cn.yeslab.platform.recruitment.model.InterviewBookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InterviewBookingRepository extends JpaRepository<InterviewBookingEntity, UUID> {
    Optional<InterviewBookingEntity> findByApplicationId(UUID applicationId);
    List<InterviewBookingEntity> findBySessionIdOrderByQueueNumberAsc(UUID sessionId);
    long countBySessionId(UUID sessionId);
    long countBySessionIdAndStatusIn(UUID sessionId, Collection<InterviewBookingStatus> statuses);
    Optional<InterviewBookingEntity> findFirstBySessionIdAndStatusInOrderByQueueNumberAsc(UUID sessionId, Collection<InterviewBookingStatus> statuses);
    Optional<InterviewBookingEntity> findByIdAndSessionId(UUID id, UUID sessionId);
}

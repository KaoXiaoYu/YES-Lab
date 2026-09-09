package cn.yeslab.platform.notification.repository;

import cn.yeslab.platform.notification.model.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {
    List<NotificationEntity> findTop50ByRecipientIdOrderByCreatedAtDesc(UUID recipientId);
    long countByRecipientIdAndReadAtIsNull(UUID recipientId);
    Optional<NotificationEntity> findByIdAndRecipientId(UUID id, UUID recipientId);
    Optional<NotificationEntity> findFirstByRecipientIdAndGroupKeyAndReadAtIsNullOrderByCreatedAtDesc(UUID recipientId, String groupKey);
    boolean existsByRecipientIdAndGroupKeyAndCreatedAtAfter(UUID recipientId, String groupKey, Instant createdAfter);
    List<NotificationEntity> findByRecipientIdAndReadAtIsNull(UUID recipientId);
}

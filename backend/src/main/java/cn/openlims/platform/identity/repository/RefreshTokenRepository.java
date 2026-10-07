package cn.openlims.platform.identity.repository;

import cn.openlims.platform.identity.model.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);
    long deleteByAccount_Id(UUID accountId);
    long deleteByExpiresAtBefore(Instant instant);
}

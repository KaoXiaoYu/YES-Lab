package cn.yeslab.platform.identity.repository;

import cn.yeslab.platform.identity.model.MemberProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface MemberProfileRepository extends JpaRepository<MemberProfileEntity, UUID> {
    Optional<MemberProfileEntity> findByAccountId(UUID accountId);
    Optional<MemberProfileEntity> findByMemberCodeIgnoreCase(String memberCode);
    boolean existsByMemberCodeIgnoreCase(String memberCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select profile from MemberProfileEntity profile where profile.id = :profileId")
    Optional<MemberProfileEntity> findByIdForUpdate(@Param("profileId") UUID profileId);
}

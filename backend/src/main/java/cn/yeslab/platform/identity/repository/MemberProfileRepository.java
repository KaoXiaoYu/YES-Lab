package cn.yeslab.platform.identity.repository;

import cn.yeslab.platform.identity.model.MemberProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MemberProfileRepository extends JpaRepository<MemberProfileEntity, UUID> {
    Optional<MemberProfileEntity> findByAccountId(UUID accountId);
    Optional<MemberProfileEntity> findByMemberCodeIgnoreCase(String memberCode);
    boolean existsByMemberCodeIgnoreCase(String memberCode);
}

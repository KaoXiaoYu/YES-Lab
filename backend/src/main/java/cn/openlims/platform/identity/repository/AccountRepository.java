package cn.openlims.platform.identity.repository;

import cn.openlims.platform.identity.model.AccountEntity;
import cn.openlims.platform.identity.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {
    Optional<AccountEntity> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);
    List<AccountEntity> findByEnabledTrue();
    List<AccountEntity> findByRoleInAndEnabledTrue(List<Role> roles);
}

package cn.yeslab.platform.identity.repository;

import cn.yeslab.platform.identity.model.AccountEntity;
import cn.yeslab.platform.identity.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {
    Optional<AccountEntity> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);
    List<AccountEntity> findByRoleInAndEnabledTrue(List<Role> roles);
}

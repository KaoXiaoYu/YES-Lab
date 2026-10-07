package cn.openlims.platform.notification.repository;

import cn.openlims.platform.identity.model.Role;
import cn.openlims.platform.notification.model.MelinaRoleVisibilityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MelinaRoleVisibilityRepository extends JpaRepository<MelinaRoleVisibilityEntity, Role> { }

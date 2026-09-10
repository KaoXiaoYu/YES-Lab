package cn.yeslab.platform.notification.repository;

import cn.yeslab.platform.identity.model.Role;
import cn.yeslab.platform.notification.model.MelinaRoleVisibilityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MelinaRoleVisibilityRepository extends JpaRepository<MelinaRoleVisibilityEntity, Role> { }

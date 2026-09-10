package cn.yeslab.platform.notification.repository;

import cn.yeslab.platform.notification.model.MelinaAccountVisibilityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MelinaAccountVisibilityRepository extends JpaRepository<MelinaAccountVisibilityEntity, UUID> { }

package cn.openlims.platform.notification.repository;

import cn.openlims.platform.notification.model.NotificationMascotPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface NotificationMascotPreferenceRepository extends JpaRepository<NotificationMascotPreferenceEntity, UUID> { }

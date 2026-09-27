package cn.yeslab.platform.notification.repository;

import cn.yeslab.platform.notification.model.NotificationMascotPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface NotificationMascotPreferenceRepository extends JpaRepository<NotificationMascotPreferenceEntity, UUID> { }

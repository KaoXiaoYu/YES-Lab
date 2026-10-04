package cn.openlims.platform.achievement.repository;

import cn.openlims.platform.achievement.model.NewsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NewsRepository extends JpaRepository<NewsEntity, UUID> {
}

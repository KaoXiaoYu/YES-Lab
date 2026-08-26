package cn.yeslab.platform.achievement.repository;

import cn.yeslab.platform.achievement.model.NewsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NewsRepository extends JpaRepository<NewsEntity, UUID> {
}

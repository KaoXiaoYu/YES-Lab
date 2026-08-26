package cn.yeslab.platform.achievement.repository;

import cn.yeslab.platform.achievement.model.CompetitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CompetitionRepository extends JpaRepository<CompetitionEntity, UUID> {
}

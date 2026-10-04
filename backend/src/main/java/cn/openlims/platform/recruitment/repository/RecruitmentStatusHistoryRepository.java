package cn.openlims.platform.recruitment.repository;

import cn.openlims.platform.recruitment.model.RecruitmentStatusHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecruitmentStatusHistoryRepository extends JpaRepository<RecruitmentStatusHistoryEntity, UUID> {
    List<RecruitmentStatusHistoryEntity> findByApplicationIdOrderByChangedAtAsc(UUID applicationId);
}

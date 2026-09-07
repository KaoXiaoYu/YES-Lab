package cn.yeslab.platform.recruitment.repository;

import cn.yeslab.platform.recruitment.model.RecruitmentPortfolioImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecruitmentPortfolioImageRepository extends JpaRepository<RecruitmentPortfolioImageEntity, UUID> {
    List<RecruitmentPortfolioImageEntity> findByApplicationIdOrderByDisplayOrderAsc(UUID applicationId);
    Optional<RecruitmentPortfolioImageEntity> findByIdAndApplicationId(UUID id, UUID applicationId);
    long countByApplicationId(UUID applicationId);
}

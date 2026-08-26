package cn.yeslab.platform.recruitment.repository;

import cn.yeslab.platform.recruitment.model.RecruitmentApplicationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RecruitmentApplicationRepository extends JpaRepository<RecruitmentApplicationEntity, UUID> {
    Optional<RecruitmentApplicationEntity> findByApplicantId(UUID accountId);
}

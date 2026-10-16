package cn.yeslab.platform.recruitment.form.repository;

import cn.yeslab.platform.recruitment.form.model.RecruitmentFormConfigEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface RecruitmentFormConfigRepository extends JpaRepository<RecruitmentFormConfigEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from RecruitmentFormConfigEntity c where c.id = 1")
    Optional<RecruitmentFormConfigEntity> lockGlobal();
}

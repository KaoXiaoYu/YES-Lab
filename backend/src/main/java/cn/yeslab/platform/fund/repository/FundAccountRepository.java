package cn.yeslab.platform.fund.repository;
import cn.yeslab.platform.fund.model.FundAccountEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
public interface FundAccountRepository extends JpaRepository<FundAccountEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from FundAccountEntity a where a.id = 'LAB'")
    Optional<FundAccountEntity> lockAccount();
}

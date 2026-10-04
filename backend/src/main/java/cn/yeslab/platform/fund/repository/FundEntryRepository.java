package cn.yeslab.platform.fund.repository;
import cn.yeslab.platform.fund.model.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface FundEntryRepository extends JpaRepository<FundEntryEntity, UUID> {
    Optional<FundEntryEntity> findByRequestKey(String key);
    Optional<FundEntryEntity> findByOriginalId(UUID id);
    @Query("select e from FundEntryEntity e where (:type is null or e.type = :type) and (:from is null or e.occurredOn >= :from) and (:to is null or e.occurredOn <= :to)")
    Page<FundEntryEntity> filter(@Param("type") FundEntryType type, @Param("from") LocalDate from, @Param("to") LocalDate to, Pageable page);
}

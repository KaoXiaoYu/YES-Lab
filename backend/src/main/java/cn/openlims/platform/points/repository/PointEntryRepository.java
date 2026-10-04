package cn.openlims.platform.points.repository;

import cn.openlims.platform.points.model.PointEntryEntity;
import cn.openlims.platform.points.model.PointSubcategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PointEntryRepository extends JpaRepository<PointEntryEntity, UUID> {

    List<PointEntryEntity> findTop200ByMember_IdOrderByGrant_OccurredOnDescCreatedAtDesc(UUID memberId);

    List<PointEntryEntity> findByMember_Id(UUID memberId);

    @Query("""
            select entry.grant.occurredOn as date, coalesce(sum(entry.points), 0) as points
            from PointEntryEntity entry
            where entry.member.id = :memberId
              and entry.grant.occurredOn >= :startDate
              and entry.grant.occurredOn < :endDate
            group by entry.grant.occurredOn
            order by entry.grant.occurredOn
            """)
    List<DailyPointTotal> sumDailyForMember(
            @Param("memberId") UUID memberId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            select entry.member.id as memberId, coalesce(sum(entry.points), 0) as points
            from PointEntryEntity entry
            where entry.grant.occurredOn >= :startDate
              and entry.grant.occurredOn < :endDate
            group by entry.member.id
            """)
    List<MemberPointTotal> sumByMemberForPeriod(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            select coalesce(sum(entry.points), 0)
            from PointEntryEntity entry
            where entry.member.id = :memberId
              and entry.grant.subcategory = :subcategory
              and entry.grant.occurredOn >= :startDate
              and entry.grant.occurredOn < :endDate
            """)
    long sumForMonth(
            @Param("memberId") UUID memberId,
            @Param("subcategory") PointSubcategory subcategory,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    interface DailyPointTotal {
        LocalDate getDate();
        Long getPoints();
    }

    interface MemberPointTotal {
        UUID getMemberId();
        Long getPoints();
    }
}

package cn.yeslab.platform.task.repository;

import cn.yeslab.platform.task.model.TaskEntity;
import cn.yeslab.platform.task.model.TaskStatus;
import cn.yeslab.platform.task.model.TaskType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<TaskEntity, UUID> {

    List<TaskEntity> findByTaskTypeOrderByCreatedAtDesc(TaskType taskType);

    /** 新手任务是唯一一条 ONBOARDING 大任务。 */
    Optional<TaskEntity> findFirstByTaskTypeOrderByCreatedAtAsc(TaskType taskType);

    List<TaskEntity> findByTaskTypeAndStatusOrderByCreatedAtDesc(TaskType taskType, TaskStatus status);

    List<TaskEntity> findAllByOrderByCreatedAtDesc();

    /**
     * 待结算的任务：已过截止日期或已被结束，且绑定了积分但尚未结算。
     *
     * <p>新手任务被显式排除：它们的 {@code points} 恒为 0，只作转正门槛、不参与积分结算。</p>
     */
    @Query("""
           select t.id from TaskEntity t
           where t.taskType <> :onboardingType
             and t.points > 0
             and t.pointsSettledAt is null
             and (t.status = :closedStatus or (t.endDate is not null and t.endDate < :today))
           """)
    List<UUID> findDueSettlementTaskIds(
            @Param("today") LocalDate today,
            @Param("onboardingType") TaskType onboardingType,
            @Param("closedStatus") TaskStatus closedStatus
    );

    /**
     * 悲观写锁读取任务行。
     *
     * <p>悬赏的「先到先得」与「先完成先得」都必须串行化：读占用数 → 写对象、读名次 → 写名次，
     * 若只有普通查询，并发下会超发名额或产生重复名次。锁粒度限定在单条任务，不影响其它任务。</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TaskEntity t where t.id = :taskId")
    Optional<TaskEntity> findByIdForUpdate(@Param("taskId") UUID taskId);

    /**
     * 结算认领：条件更新，只有把 {@code points_settled_at} 从 {@code NULL} 改成时间戳的那一次调用返回 1。
     *
     * <p>这是跨实例互斥的关键——多实例或调度重入时只有一个执行者能拿到 1，其余拿到 0 直接跳过，
     * 不需要分布式锁。{@code clearAutomatically} / {@code flushAutomatically} 用于让后续读取拿到
     * 认领后的状态，避免把刚写入的标记又当成未结算写回去。</p>
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update TaskEntity t set t.pointsSettledAt = :settledAt, t.updatedAt = :settledAt
           where t.id = :taskId and t.pointsSettledAt is null
           """)
    int claimSettlement(@Param("taskId") UUID taskId, @Param("settledAt") Instant settledAt);
}

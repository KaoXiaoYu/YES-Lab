package cn.openlims.platform.task.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * 任务积分到期结算的定时入口。
 *
 * <p>沿用仓库既有的调度分工（见 {@code recruitment/service/InterviewRetentionScheduler}）：
 * <b>调度器只负责触发与容错，事务放在 {@link TaskSettlementService}</b>。刻意不在这里标
 * {@code @Transactional}——整批放进一个事务会让单个任务的失败连累全部，并且长时间持有行锁；
 * 逐个任务调用事务方法后，失败的那个下个周期会自动重试（条件「未结算」保证幂等）。</p>
 *
 * <p>时区必须是实验室时区 {@code Asia/Shanghai}：到期判定用的是业务日期。
 * 既有的面试场次清理用 UTC，两者有意不同。</p>
 *
 * <p>可通过 {@code openlims.task.settlement.enabled=false} 关闭（测试环境就是这么做的：
 * 调度线程用独立事务并会提交，会污染依赖逐用例回滚的测试）。</p>
 */
@Component
@ConditionalOnProperty(name = "openlims.task.settlement.enabled", havingValue = "true", matchIfMissing = true)
public class TaskSettlementScheduler {

    private static final Logger log = LoggerFactory.getLogger(TaskSettlementScheduler.class);
    private static final ZoneId LAB_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final TaskSettlementService settlement;

    public TaskSettlementScheduler(TaskSettlementService settlement) {
        this.settlement = settlement;
    }

    @Scheduled(cron = "${openlims.task.settlement.cron:0 5 * * * *}", zone = "Asia/Shanghai")
    public void settleDueTasks() {
        LocalDate today = LocalDate.now(LAB_TIME_ZONE);
        List<UUID> due = settlement.findDue(today);
        if (due.isEmpty()) {
            return;
        }
        int settled = 0;
        int failed = 0;
        for (UUID taskId : due) {
            try {
                TaskSettlementService.TaskSettlementSummary summary = settlement.settle(taskId);
                if (summary.settled()) {
                    settled++;
                }
            } catch (RuntimeException error) {
                // 单个任务失败不影响其余任务；事务已回滚（含结算标记），下个周期会重试。
                failed++;
                log.warn("任务积分结算失败，将在下个调度周期重试：taskId={}", taskId, error);
            }
        }
        log.info("任务积分结算完成：待结算 {} 个，成功 {} 个，失败 {} 个", due.size(), settled, failed);
    }
}

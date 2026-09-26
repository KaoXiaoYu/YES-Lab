# YES Lab 任务到期结算与到期冻结 · 技术设计

> 本文是横切三类任务的改造设计：**所有任务统一「到期冻结 + 到期结算」**。
> 需求清单见 [`docs/task-settlement-requirements.md`](task-settlement-requirements.md)；悬赏任务本体见 [`docs/bounty-task-design.md`](bounty-task-design.md)。冲突时以需求清单为准。
> 本文引用的既有代码位置均已逐条核对。

## 1. 统一模型

**一个任务的有效截止时间，以及到期后的三种结果：**

|               | 新手任务 `ONBOARDING`             | 普通任务 `STANDARD`     | 悬赏任务 `BOUNTY`           |
| ------------- | --------------------------------- | ----------------------- | --------------------------- |
| 有效截止时间  | **对象级 `due_date`**（每人不同） | 任务级 `end_date`       | 任务级 `end_date`           |
| 到期时点      | 本人 `due_date` 的次日 00:00      | `end_date` 的次日 00:00 | `end_date` 的次日 00:00     |
| 也视为到期    | —（无 `CLOSED` 收口）             | 任务 `CLOSED`           | 任务 `CLOSED`               |
| 到期 → 成员侧 | 不能提交、不能改子任务            | 不能提交、不能改子任务  | 不能接取、提交、放弃        |
| 到期 → 管理侧 | 不能审核转正、不能驳回            | 不能审核通过、不能驳回  | 不能驳回                    |
| 到期 → 结算   | **不结算**（`points` 恒 0）       | 结算（只发 `APPROVED`） | 结算（只发 `APPROVED`）     |
| 补救动作      | 按人延长 `due_date`、打回技能测试 | 延长 `end_date`         | 延长 `end_date`、移除接取者 |

三条贯穿性结论：

1. **冻结与结算同时发生在到期时点**——不存在「已结算但还能改结论」的窗口，所以「结算后不能驳回」不需要额外守卫，它由「到期即不能驳回」覆盖。
2. **结算是终局**：`APPROVED` 在到期后成为不可撤销状态；`SUBMITTED`（到期未审核）永久停留、不计分。
3. **结算只认已通过的**：因此管理员必须在到期前完成审核。这是本次对已上线普通任务最大的行为变化。

## 2. 已核对的实现事实

1. **普通任务的截止日期目前只是显示层**：`TaskService.isMemberEditable`（`:920`）与 `requireStandardEditable`（`:936`）只判断 `status == PUBLISHED` 与对象非 `APPROVED`，**完全不看 `end_date`**。所以要新建统一的窗口判定，而不是改一两个条件。
2. **积分目前在审核通过时立即发放**：`TaskService.reviewStandard`（`:616` 起）在通过时调用 `PointService.grantForTask` 并回写 `point_grant_id` / `awarded_points`。本次要**把这段计分逻辑整体移到结算服务**。
3. **`grantForTask` 依赖认证上下文**：带 `@PreAuthorize("hasAuthority('POINTS_MANAGE')")`（`PointService.java:134-136`），且 operator 来自 `Authentication`。结算由**系统**触发、没有认证上下文 → 必须改为内部发放路径，并把 operator 定为任务创建者（`TaskEntity.getCreatedBy()`，`TaskEntity.java:119`）。
4. **三条跳过判定现成可复用**：`PointService.taskRecipientIneligibleReason(operator, member)`（`:183-188`）返回「教师 / 非正式成员 / 操作人本人」的原因或 `null`；`grantForTask` 就是「先判跳过、再发放」。结算沿用同一套，**不放宽任何积分规则**。
5. **既有来源编号必须保持**：普通任务的历史记录用 `TASK:{taskId}:{memberProfileId}`（`PointService.java:148`）。结算继续用同一格式，才能与历史已发放记录幂等兼容。
6. **新手任务的截止按人算**：`OnboardingTaskIssuerService:78` 为 `dueDate = LocalDate.now(LAB_TIME_ZONE).plusDays(durationDays)`；`OnboardingTaskService:191-192` 在时长变更时按「各自发放日 + 新时长」重算所有未通过对象。共享大任务本身不设任务级截止，因此**新手任务的冻结必须按对象 `due_date` 判定**。
7. **`due_date` 已有写入方法**：`TaskAssignmentEntity.assignDueDate(LocalDate)`（`:170`）。按人延长不需要新的领域方法，只需要新的服务编排与接口。
8. **新手任务审核走同一套审核端点**：`TaskService.review`（`:225`）按类型分派到 `reviewOnboarding`（`:238`）与 `reviewStandard`（`:616`）。到期守卫加在分派之前即可同时覆盖两类。
9. **定时任务基础设施已存在**：`YesLabApplication.java:8` 已有 `@EnableScheduling`；既有先例 `recruitment/service/InterviewRetentionScheduler.java:21` 是「`@Component` + `@Scheduled(cron = "${...}", zone = ...)`，**调度器不持事务**，事务在同名 Service（`InterviewRetentionService.purgeExpiredBefore`，`:26-27`）」。结算照抄这套分工。
10. **测试有独立的 `backend/src/test/resources/application.yml`**（H2 内存库 + `ddl-auto: create-drop` + Flyway 关闭）。调度必须在测试里关闭：调度线程用独立事务且会**提交**，而 `PointApiTests` 依赖逐用例回滚做绝对断言（`DEVLOG.md` 记录过两次同类隔离事故）。
11. **生产 `api` 是单实例**：`compose.yaml` 的 `api` 无 `deploy.replicas`。但仍按多实例安全设计。
12. **`V11` 已上线**：`tasks.points_settled_at` 必须走新增的 `V14`，不得改 `V11`—`V13`（`DEVLOG.md` 2026-09-20 的 checksum 事故）。

## 3. 统一窗口判定

> **已按本文实现**（第 2 批）：`backend/src/main/java/cn/yeslab/platform/task/model/TaskTiming.java`。
> 实现与下方草稿有一处收敛：没有单独保留 `isConclusiveAllowed`，管理侧守卫直接用 `isExpired`
> （`TaskService.requireNotExpired`），少一层间接；另外补了任务级的 `isTaskExpired` 供悬赏接取窗口使用。

新增一个纯函数式的判定工具（放在任务模块内，三类任务共用），把「能不能改」与「能不能下结论」分开：

```java
// task/model/TaskTiming.java
public final class TaskTiming {

    /** 有效截止时间：新手任务优先取对象 due_date（为空时退回任务 end_date），其余取任务 end_date。null 表示不限。 */
    public static LocalDate deadlineOf(TaskAssignmentEntity a) {
        if (a.isOnboarding() && a.getDueDate() != null) return a.getDueDate();
        return a.getTask().getEndDate();
    }

    /** 是否已到期：截止时间已过（当天仍算未到期），或任务被结束为 CLOSED。 */
    public static boolean isExpired(TaskAssignmentEntity a, LocalDate today) {
        if (a.getTask().getStatus() == TaskStatus.CLOSED) return true;
        LocalDate deadline = deadlineOf(a);
        return deadline != null && deadline.isBefore(today);
    }

    /** 成员侧是否可写：对象未通过 + 未到期 + 任务在发布期 + 未到开始日期。 */
    public static boolean isMemberEditable(TaskAssignmentEntity a, LocalDate today, boolean onboardingStageOk) {
        if (a.getStatus() == TaskAssignmentStatus.APPROVED) return false;
        if (isExpired(a, today)) return false;
        if (a.isOnboarding()) return onboardingStageOk;          // 仍在技能测试阶段
        TaskEntity t = a.getTask();
        if (t.getStatus() != TaskStatus.PUBLISHED) return false;
        return t.getStartDate() == null || !today.isBefore(t.getStartDate());
    }

    /** 任务级判定：还没有对象、或需要按任务判断时使用（悬赏接取窗口、结算扫描）。 */
    public static boolean isTaskExpired(TaskEntity task, LocalDate today) {
        if (task.getStatus() == TaskStatus.CLOSED) return true;
        return task.getEndDate() != null && task.getEndDate().isBefore(today);
    }
}
```

替换点（全部已落地）：

| 现有实现                                          | 改为                                                                                  |
| ------------------------------------------------- | ------------------------------------------------------------------------------------- |
| `TaskService.isMemberEditable`（`:920`）          | 调用 `TaskTiming.isMemberEditable`（新手任务的阶段判定由 `onboardingStageOpen` 传入） |
| `TaskService.requireStandardEditable`（`:936`）   | 在原有两个判断之后补 `TaskTiming.isExpired`，文案为「任务已截止，不能再提交」         |
| `TaskService.requireEditable`（`:142`，报名者侧） | 补 `TaskTiming.isExpired`，文案为「新手任务已截止，不能再提交」                       |
| `dueDateOf`（`:402`）                             | 改为委托 `TaskTiming.deadlineOf`，消除第二处截止日期来源                              |
| `reviewOnboarding` / `reviewStandard`             | 在「已通过」判断之后调用 `requireNotExpired`，到期返回 409                            |
| 悬赏的接取/放弃/驳回                              | 复用同一条判定（第 7 批施工）                                                         |

**为什么把守卫放在对象层而不是任务层**：新手任务的有效截止时间是对象级的，如果只写任务级判定，报名者个人逾期就无法冻结（或反之会冻结所有人）。放进对象层后三类任务共用一条代码路径。

## 4. 结算服务

### 4.1 调度器与事务边界

**照抄既有分工**：调度器只触发 + 容错，事务在 Service。下面是实现后的真实结构（第 3 批已落地）：

```java
@Component
@ConditionalOnProperty(name = "yeslab.task.settlement.enabled", havingValue = "true", matchIfMissing = true)
public class TaskSettlementScheduler {
    private final TaskSettlementService settlement;

    @Scheduled(cron = "${yeslab.task.settlement.cron:0 5 * * * *}", zone = "Asia/Shanghai")
    public void settleDueTasks() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        for (UUID taskId : settlement.findDue(today)) {
            try { settlement.settle(taskId); }
            catch (RuntimeException error) { log.warn("任务积分结算失败，将在下个调度周期重试：taskId={}", taskId, error); }
        }
    }
}
```

```java
@Service
public class TaskSettlementService {

    /** 待结算的任务 id（已到期或已结束、绑定了积分、尚未结算）。 */
    @Transactional(readOnly = true)
    public List<UUID> findDue(LocalDate today) { ... }

    /** 结算单个任务；幂等，可重复调用。也由 close 接口同步调用。 */
    @Transactional
    public TaskSettlementSummary settle(UUID taskId) { ... }
}
```

**与设计草稿的一处收敛**：循环与容错放在**调度器**里，`settleAllDue` 这个方法不再存在。
这样既避开了「同一个 bean 内直调导致 `@Transactional` 失效」的自调用陷阱，也不需要自注入代理——
一个任务一个事务的语义靠「调度器逐个调用事务方法」直接成立。

要点：

- **调度频率** `0 5 * * * *`（每小时第 5 分钟）足够：到期按 DATE 判定，晚几分钟无影响；cron 走 `yeslab.task.settlement.cron` 配置（环境变量 `YESLAB_TASK_SETTLEMENT_CRON`）。
- **时区必须显式 `Asia/Shanghai`**：到期判定用实验室时区；既有 interviews 清理用 UTC，这里**有意不同**，代码注释里已说明。
- **bean 用属性开关包住**，测试 `application.yml` 设 `yeslab.task.settlement.enabled: false`（事实 10）。
- **不要在调度方法上标 `@Transactional`**：一个任务失败会连累整批，且长事务持有大量行锁。

### 4.2 待结算任务查询

```java
@Query("""
       select t.id from TaskEntity t
       where t.taskType <> :onboarding          // 新手任务 points 恒 0，天然排除；显式写出以示意
         and t.points > 0
         and t.pointsSettledAt is null
         and (t.status = :closed or (t.endDate is not null and t.endDate < :today))
       """)
List<UUID> findDueTaskIds(LocalDate today, TaskType onboarding, TaskStatus closed);
```

枚举一律用**参数**传入，不写 JPQL 字面量，避免方言差异。

### 4.3 幂等与跨实例互斥

两层保护：

1. **任务级认领**——条件更新，返回受影响行数：

   ```sql
   UPDATE tasks SET points_settled_at = :now
   WHERE id = :taskId AND points_settled_at IS NULL
   ```

   只有返回 `1` 的实例继续结算；返回 `0` 说明已被处理过，直接跳过。这是**跨实例互斥**，不需要分布式锁。

   **JPA 陷阱**：`@Modifying` 批量更新不同步持久化上下文，认领后必须重新读取任务，或用 `@Modifying(clearAutomatically = true, flushAutomatically = true)`，否则后续可能把 `points_settled_at` 又写回 `NULL`。

2. **每人一笔的来源编号**——`TASK:{taskId}:{memberProfileId}`（普通任务，与历史一致）与 `BOUNTY:{taskId}:{memberProfileId}`（悬赏）在 `point_grants.source_reference` 上唯一；发放前先查，已存在则复用原批次结果，**不重复计分也不报错**。这让「延长后重新结算」与「认领回滚后重试」都安全。

### 4.4 结算流程

```text
settle(taskId) 事务开始
  1. if (tasks.claimSettlement(taskId, now) == 0) return 已结算（幂等跳过）
  2. TaskEntity task = tasks.findById(taskId)                    // 认领后重新读取（JPA 陷阱）
  3. rows = assignments.findByTaskIdOrderByCompletionRankAsc/ CreatedAtAsc(taskId)
  4. for (row : rows) {
       if (row.status != APPROVED) continue                      // 只发已通过的
       pointService.grantForTaskSettlement(taskId, row.memberProfileId, task.points,
                                           task.getCreatedBy(), 来源编号, ...)
       // 规则性跳过 → 回写 points_skipped_reason；异常 → 整个事务回滚
     }
  5. 给任务创建者发结算汇总消息（获得 N 人、跳过 M 人及原因分布、未完成 K 人）
事务提交
```

- **逐人发放而不是一笔批量**：与历史粒度一致、幂等粒度最细、界面能逐人显示结果；`PROJECT_TASK` 是 `SHARED_TOTAL`，单人发放时「事项总分 = 该成员得分」自然成立。
- **一个任务一个事务**：失败整体回滚（含结算标记），下次调度重试，已发放的部分靠来源编号跳过。
- **新手任务永不进入这里**：`points = 0` 被查询条件排除。

### 4.5 `PointService` 的改动

> **已按本节实现**（第 3 批）：`grantForTask` 已被 `grantForTaskSettlement` **取代并删除**（它原先只有
> `TaskService.reviewStandard` 一个调用点）；来源前缀由任务模块传入，`TASK` / `BOUNTY` 两种前缀共用同一段逻辑。

```java
// 1) 把 grant(Authentication, request) 的方法体抽成私有方法，两条路径共用
private PointModels.GrantView grantAs(AccountEntity operator, PointModels.GrantRequest request) { ... }

// 2) 公开方法签名与鉴权完全不变（现有调用方零影响）
@PreAuthorize("hasAuthority('POINTS_MANAGE')")
@Transactional
public PointModels.GrantView grant(Authentication authentication, PointModels.GrantRequest request) {
    return grantAs(authService.requireAccount(authentication), request);
}

// 3) 面向任务结算的内部发放：不带 @PreAuthorize，不暴露任何接口
@Transactional
public TaskGrantResult grantForTaskSettlement(
        UUID taskId, UUID memberProfileId, int points, AccountEntity operator,
        String sourcePrefix, String taskTitle, LocalDate occurredOn,
        String evidenceUrl, String description, String contribution) { ... }
```

`sourcePrefix` 由任务模块传入（普通任务 `TASK`、悬赏 `BOUNTY`），来源编号统一拼成
`{sourcePrefix}:{taskId}:{memberProfileId}`——**普通任务沿用历史的 `TASK:` 前缀**，因此与已发放的历史记录幂等兼容。

发放参数（两类任务一致，只有来源编号前缀与标题前缀不同）：

| 参数              | 取值                                                                                 |
| ----------------- | ------------------------------------------------------------------------------------ |
| `subcategory`     | `PROJECT_TASK`                                                                       |
| `occurredOn`      | 结算当天（`Asia/Shanghai`）                                                          |
| `itemTotalPoints` | 该成员分值（单人发放时必须等于成员得分）                                             |
| `allocations`     | 仅一条：对象成员 + 分值 + 贡献说明                                                   |
| `sourceReference` | 普通任务 `TASK:{taskId}:{memberProfileId}`；悬赏 `BOUNTY:{taskId}:{memberProfileId}` |
| `evidenceUrl`     | 站内绝对路径 `/tasks/{assignmentId}`                                                 |
| `operator`        | **任务创建者**                                                                       |

**一处行为变化**：审核表单里的可选「凭证链接」不再进入积分流水——结算发生在到期时，没有审核人上下文，
凭证统一用站内任务路径（`/tasks/{assignmentId}`）。请求字段保留以兼容前端，但服务端不再使用它。

## 5. 需要改动的既有代码清单

> 第 2 批与第 3 批已完成下表除「按人延长」与悬赏相关的全部条目（`TaskTiming`、两个 `require*Editable`、
> `reviewOnboarding` / `reviewStandard` 的到期守卫、`reviewStandard` 移除即时计分、结算服务与调度器、
> `PointService` 的 `grantAs` / `grantForTaskSettlement`、`close` 同步结算、生产与测试的配置开关）。

| 文件 / 方法                                       | 改动                                                                                                                                                                                                |
| ------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `task/model/TaskTiming.java`（新）                | 统一窗口与到期判定（第 3 节）                                                                                                                                                                       |
| `task/service/TaskService.java`                   | `isMemberEditable` / `requireStandardEditable` 改用 `TaskTiming`；`reviewStandard` **删除即时计分**，只改状态并写审核留痕；`review` 入口加到期守卫；`myTasks` / 详情视图补「是否到期 / 是否已结算」 |
| `task/service/OnboardingTaskService.java`         | 审核入口加到期守卫；新增**按人延长 `due_date`** 的服务方法（写审计留痕 + 发消息）；逾期对象总览补充标记                                                                                             |
| `task/service/TaskSettlementScheduler.java`（新） | 定时入口                                                                                                                                                                                            |
| `task/service/TaskSettlementService.java`（新）   | 认领、遍历、发放、汇总通知（第 4 节）                                                                                                                                                               |
| `task/repository/TaskRepository.java`             | `findDueTaskIds`、`claimSettlement`                                                                                                                                                                 |
| `points/service/PointService.java`                | `grantAs` 抽取、`grantForTaskSettlement`、`grantForTask` 去认证化（第 4.5 节）                                                                                                                      |
| `task/controller/AdminTaskController.java`        | 结束任务时同步触发结算；新增按人延长新手任务截止日期的接口（`TASK_MANAGE`）                                                                                                                         |
| `task/api/TaskModels.java`                        | 视图补 `expired`、`pointsSettled`、`pointsSettledAt`、`submittedAwaitingReview`、`deadline` 等字段                                                                                                  |
| `backend/src/test/resources/application.yml`      | 新增 `yeslab.task.settlement.enabled: false`                                                                                                                                                        |
| `backend/src/main/resources/application.yml`      | 新增 `yeslab.task.settlement-cron`（带默认值，可运维调整）                                                                                                                                          |

**不改动**：`TaskAssignmentEntity` 的状态机（`SUBMITTED → APPROVED/REJECTED` 的形状不变，只是「什么时候允许」变了）；积分模块的校验规则；新手任务的转正核心与建档案逻辑。

## 6. 新手任务的出口（按人延长）

新手任务纳入冻结后，**没有出口就会堵死招新**：报名者逾期 → 管理员不能审核 → 该人永远停在技能测试阶段。因此必须提供三条出口，且**至少第一条是本次新增**：

| 出口                          | 作用范围       | 说明                                                                                                |
| ----------------------------- | -------------- | --------------------------------------------------------------------------------------------------- |
| **按人延长 `due_date`**（新） | 单个报名者     | 管理员为该对象设置新的截止日期；写留痕（原日期 → 新日期、操作人、时间、理由），发站内消息说明新日期 |
| 调整大任务时长（既有）        | 所有未通过对象 | `OnboardingTaskService` 现有的重算逻辑；影响面在界面上明确提示                                      |
| 打回技能测试阶段（既有）      | 单个报名者     | 按当前时长重置 `due_date`，可重新提交与审核                                                         |

**留痕的范围**：`task_assignments` 上的 `due_date_extended_at` / `due_date_extended_by_account_id` /
`due_date_extension_reason` 记录**最近一次**延长。延长是低频管理动作，先落最近一次即可；完整的操作历史
等后续的「全局操作审计」（已在 `README` 的后续待办里）落地后再补流水表。这一点是有意取舍，不是遗漏。

接口：

| 方法 | 路径                                                                 | 说明                                                                                |
| ---- | -------------------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| PUT  | `/api/v1/admin/tasks/onboarding-assignments/{assignmentId}/due-date` | 按人延长；请求体 `{ "dueDate": "2026-10-15", "reason": "..." }`；要求 `TASK_MANAGE` |

返回 `DueDateExtensionView`（`previousDueDate` / `dueDate` / `extendedAt` / `extendedBy` / `reason`）。
校验：新日期必须**晚于今天**（否则延长没有意义）、晚于原日期；对象必须属于新手任务大任务且未通过。

界面：逾期对象行内显示「已逾期，无法审核」并就地给出「延长截止日期」与「打回技能测试阶段」两个动作，避免管理员走进死路。

## 7. 迁移 `V14__task_settlement.sql`

> **已按本文实现并完成真机演练**：脚本为 `backend/src/main/resources/db/migration/V14__task_settlement.sql`，
> 演练脚本为 `.codex-run/settlement-rehearsal/rehearse-v14.sh`（**15 项断言全部通过**，见 DEVLOG 第 1、4 批），
> 并以 `ddl-auto=validate` 对真实 MySQL 校验过实体映射。
> 与本文草稿的两处差异：① 回填改为**与「本次刚加上该列」严格绑定**（理由见要点）；
> ② 第 4 批追加了新手任务按人延长的三列审计字段与外键（见下）。

```sql
-- 1) 结算标记（缺什么补什么，幂等）
SET @has_settled := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tasks' AND COLUMN_NAME = 'points_settled_at');
SET @stmt := IF(@has_settled = 0,
    'ALTER TABLE tasks ADD COLUMN points_settled_at DATETIME(6) NULL',
    'DO 0');
PREPARE add_settled FROM @stmt; EXECUTE add_settled; DEALLOCATE PREPARE add_settled;

-- 2) 待结算扫描索引（同样先查 information_schema.STATISTICS 再创建）
--    CREATE INDEX idx_tasks_settlement ON tasks (task_type, points_settled_at, end_date)

-- 3) 【第 4 批追加】新手任务按人延长截止日期的留痕（三列 + 外键，各自先查 information_schema）
--    ALTER TABLE task_assignments ADD COLUMN due_date_extended_at DATETIME(6) NULL;
--    ALTER TABLE task_assignments ADD COLUMN due_date_extended_by_account_id BINARY(16) NULL;
--    ALTER TABLE task_assignments ADD COLUMN due_date_extension_reason VARCHAR(500) NULL;
--    ALTER TABLE task_assignments ADD CONSTRAINT fk_task_assignments_due_date_extender
--        FOREIGN KEY (due_date_extended_by_account_id) REFERENCES accounts (id);

-- 4) 【关键】历史普通任务视为已结清，且只在「本次刚加上该列」的那一次运行里执行
--    这些任务走的是旧的「审核通过即发」口径，积分早已发过；
--    若不回填，部署后调度会把它们当作待结算逐个处理：
--    虽然来源编号幂等不会重复计分，但会写结算标记、向创建者发一批汇总消息，
--    并且让这些历史任务此后无法驳回 —— 都不是期望的行为。
SET @stmt := IF(@has_settled = 0,
    'UPDATE tasks SET points_settled_at = NOW(6) WHERE task_type = ''STANDARD'' AND points_settled_at IS NULL',
    'DO 0');
PREPARE backfill_settled FROM @stmt; EXECUTE backfill_settled; DEALLOCATE PREPARE backfill_settled;
```

要点：

- **回填是本迁移的核心目的之一**，不是可选项；演练时必须核对「部署后调度不处理任何历史任务」。
- **回填与「刚加上该列」绑定，而不是用 `created_at < NOW(6)` 之类的条件**：后者在重复执行时 `NOW(6)` 会更晚，反而把迁移之后新建的任务也误标为已结算。绑定加列这个一次性动作后，重复执行天然只会走 `DO 0` 分支，语义精确且演练安全。
- **不动任何既有列与数据**；新手任务（`points = 0`）不回填；悬赏表结构变更全部放在 `V15__bounty_task.sql`。
- 与 `V15` 分开的理由：结算改造是全站横切、可以独立上线与验证；悬赏是新类型，晚一步上线也不影响结算能力。两次迁移分别演练，失败时能立刻分辨是哪一批。
- **本机演练还暴露了一件事**：手工按 `sort -V` 排序会得到 `V7_1_1 → V7_1_2 → V7_1 → V7` 的错误顺序（`V7` 之后才建 `discussion_posts`），必须按 Flyway 的版本序 `V7 < V7_1 < V7_1_1 < V7_1_2` 执行；演练脚本里已写死正确顺序。

## 8. 与既有文档的冲突条款

本次**推翻**了 `docs/task-module-design.md` 的以下口径，必须在文档与代码注释中同步，避免后来者按旧文档施工：

| 既有条款                                                            | 新口径                                                         |
| ------------------------------------------------------------------- | -------------------------------------------------------------- |
| 第 7 节「审核通过即自动发放积分」                                   | 积分改为**到期结算**；审核通过只改状态                         |
| 第 9 节「逾期只是显示层派生，不写库、不改状态、不构成结论」         | 逾期（到期）是**硬边界**：冻结成员提交与管理层结论，并触发结算 |
| 第 4.8 节「新手任务到期后不自动处理，只显示已逾期，管理员仍可审核」 | 新手任务到期后**不能审核与驳回**，需先按人延长或打回           |
| 第 4.8 节「`CLOSED` 后成员只读、管理员仍可审核」                    | 到期（含 `CLOSED`）后**管理员也不能审核或驳回**，只能延长      |

`docs/task-module-design.md` 顶部需加一段指向本文的说明；其余条款保持原样，由本文覆盖。

## 9. 测试计划

> **第 5 批已完成**：全量测试 **62 项通过**（改造前 51 项），本节列出的普通任务与新手任务用例均已落地；
> 悬赏相关条目（`ABANDONED` 状态、`BOUNTY:` 前缀、移除接取者）留到第 7 批一并覆盖。

### 9.1 到期冻结

- 普通任务 `end_date` 为昨天：成员提交 409、管理端审核通过 409、驳回 409 ✅ `TaskSettlementApiTests.expiredStandardTaskFreezesEveryoneUntilDeadlineIsExtended`；
- 任务 `CLOSED`：即使 `end_date` 在未来，也全部被拒；且**连修改（含延长截止日期）都被拒**（不可逆终局）✅ `TaskApiTests.closedTaskFreezesMemberSubmissionAndAdminReview`、`TaskSettlementApiTests.closedTaskCannotBeModifiedAnymore`；
- `end_date` 为空：永不冻结、也不会被结算 ✅ `TaskSettlementApiTests.taskWithoutDeadlineNeverExpires`；
- 到期后延长 `end_date` 到未来：同一对象立即恢复可提交、可审核 ✅ 同上第一条；
- 新手任务：本人 `due_date` 为昨天 → **本人被冻结、管理员不能审核**；按人延长后恢复可提交、可审核，并可成功转正 ✅ `OnboardingTaskApiTests.overdueApplicantIsFrozenUntilDueDateIsExtendedPerPerson`；
- 按人延长只影响被延长者，共享大任务与其他报名者的 `due_date` 不变 ✅ `OnboardingTaskApiTests.extendingOneApplicantDoesNotAffectOthers`；
- 到期后的清理动作（悬赏移除接取者）：第 7 批覆盖。

### 9.2 到期结算

- **普通任务：审核通过后不产生积分记录**（本次最重要的行为变化）✅ `TaskApiTests.approvalRecordsResultButPointsAreOnlyGrantedAtSettlement`；
- 未到期时结算不发分（并给出「任务尚未到期」原因）✅ `taskThatIsNotDueYetIsNotSettled`、`taskWithoutDeadlineNeverExpires`；
- 任务 `CLOSED` 时 `close` 接口同步结算，不必等调度 ✅ `closingTaskSettlesItImmediately`；
- 同一任务内混放已通过与未通过对象 → 只有 `APPROVED` 拿到积分，其余状态与数据不变 ✅ `dueTaskGrantsApprovedOnlyAndIsIdempotent`；
- **幂等**：连续结算两次，积分记录数不变、`points_settled_at` 不被刷新 ✅ 同上；
- `points = 0` 的任务不进入待结算队列、也不写结算标记（含所有新手任务）✅ `zeroPointTaskNeverEntersSettlementQueue`；
- **延长截止日期**：已结算任务延长到未来 → 标记重置；`grantedCount` 只计本次新发、`reusedCount` 计幂等复用；新完成者补到积分、已发过的人不重复 ✅ `extendingDeadlineAfterSettlementGrantsOnlyNewCompleters`；
- 三种跳过情形（教师、非正式成员、任务创建者本人）结算成功且原因落库 ✅ `TaskApiTests.ineligibleRecipientsAreSkippedWithReasonAndZeroPointTasksGrantNothing`；
- 凭证为站内任务路径、创建者收到结算汇总 ✅ `settlementUsesStationTaskPathAsEvidenceAndNotifiesCreator`；
- 来源编号普通任务仍为 `TASK:{taskId}:{memberProfileId}`（与历史兼容）✅ 同上；悬赏 `BOUNTY:` 前缀第 7 批覆盖；
- **认领互斥**（两个线程/两次调度同时结算只有一个执行）：H2 上只能证明「服务层没被绕过」，**真正的并发验证放在预生产 MySQL**（第 10 节第 6 步）；
- 历史任务不被结算：`V14` 回填后调度不返回它们 —— 由 `V14` 演练脚本在真实 MySQL 上核对（H2 跑不到 Flyway）；
- **定时任务路径**：以 `*/5 * * * * *` 实跑，审核不发分 → 截止日期改到昨天 → 一个调度周期后积分到账 → 再等两个周期不重复 ✅ `.codex-run/settlement-rehearsal/verify-scheduled-settlement.sh`。

### 9.3 已改造的既有测试

以下用例原本断言旧口径，**按新口径重写而不是删除**（`DEVLOG.md` 记录过「不得通过放宽业务校验掩盖失败」）：

- `TaskApiTests.closedTaskBlocksMemberSubmissionButStillAllowsReview` → 重命名为 `closedTaskFreezesMemberSubmissionAndAdminReview`，改为断言「结束任务后管理员也不能审核通过或驳回」；
- `TaskApiTests.approvalGrantsPointsAndIsIdempotentWhileRejectionSkipsThem` → 重命名为 `approvalRecordsResultButPointsAreOnlyGrantedAtSettlement`；
- `TaskApiTests.ineligibleRecipientsAreSkippedWithReasonAndZeroPointTasksGrantNothing`：三种跳过情形改到结算后核对；其中「不能给自己发放」按新口径改成**完成者即任务创建者**（结算没有审核人）；
- `scripts/smoke-task-module.sh`：断言「审核通过后总积分增加」的检查点改为「审核通过不写入积分 → 结束任务时结算 → 总积分增加」，并由 53 项增至 **60 项全部通过**；
- `PointApiTests` 的逐用例回滚隔离保持不变：结算测试同为 `@Transactional`，且调度在测试环境关闭（`yeslab.task.settlement.enabled: false`）；
- 新手任务相关用例确认测试数据的 `due_date` 在未来（默认时长 7 天天然满足），未被新的冻结守卫误伤。

**回归底线**：改造后全量测试 **62 项通过**，且**没有一条断言被放宽**（唯一一处放宽是被明确否决的：曾出现「计数断言受共享上下文影响」的失败，改用按成员显式指派解耦，而不是把 `== 1` 放宽成 `>= 1`）。

## 10. 上线演练与验收

1. 备份生产 MySQL（禁止 `docker compose down -v`，不得删除 `/srv/yeslab/data`）。
2. 预生产库按 `V1`—`V13` 建库 → 造历史数据：一条**已过期的 `STANDARD` 任务**（含已通过、已发过积分的对象）、一条进行中的 `STANDARD`、一条 `ONBOARDING`（含一个已逾期对象）。
3. 执行 `V14`：核对 `points_settled_at` 已加上、历史 `STANDARD` 任务已被回填、`ONBOARDING` 未被回填；重复执行确认幂等。
4. 以 `ddl-auto=validate` 启动后端，确认结构校验通过。
5. **启动后观察一个调度周期**：确认调度没有处理任何历史任务、创建者没有收到新的结算汇总消息、积分记录没有增加。
6. 普通任务实测：审核通过不发分 → 到期结算发分 → 再次结算不重复。
7. 普通任务实测：到期后提交/审核/驳回全部 409，延长后恢复。
8. 新手任务实测：逾期报名者不能提交、不能审核；按人延长后成功提交并转正。
9. 悬赏实测：结算后不能驳回；奖金顺延只在到期前发生。

## 11. 界面设计

> **第 6 批已落地并完成真机自检**：改动落在 `TasksView`、`TaskDetailView`、`AdminTasksView`、
> `AdminTaskProgressView`、`AdminOnboardingTaskView`、`authApi.js`、`portal.css`；
> 断言脚本 `.codex-run/ui-review/verify-settlement-ui.mjs`（**14 项全部通过**）+ 10 张截图
> （1440/375，`.codex-run/ui-review/settle-*.png`），全部页面 `scrollWidth == innerWidth`。

沿用 `design-system/yes-lab/MASTER.md` 与既有的三条 `ui-ux-pro-max` 规则（剩余数量用 `role="status"` 完整句子播报、不可逆动作二次确认、异步提交期间禁用按钮）+ `Disabled States`（到期/满员的禁用态必须明显区别于可用态）。本次新增的界面要点：

- **统一显示「截止日期 + 剩余天数」**，到期后替换为「已截止」；成员端提交/放弃/接取按钮进入 `disabled` 且写明原因，不用静默禁用。
- **可写性由后端裁定**：`MyTaskView` / `MyTaskDetailView` 新增 `expired` / `editable` / `pointsSettled`，
  `TaskView` / `TaskSummaryView` 新增 `pointsSettledAt` / `expired`。前端**不重复实现窗口规则**，
  避免「界面能点、接口返回 409」的错位。
- **管理端新增两组状态**：「待审核 N 人（到期后将无法审核）」与「待结算 / 已结算（时间）」；已结算任务明确写出「不能再审核或驳回」。
- **到期对象的补救入口就地提供**：新手任务逾期行给出「延长截止日期」与「打回技能测试阶段」；普通任务与悬赏给出「延长截止日期」；延长时说明会重置结算、新完成的成员在再次到期后补发积分。
- **普通任务的文案必须改**：不能再出现「逾期不影响提交」这类旧说明（第 8 节）。
- **成员端要讲清「已提交但已截止」的后果**：`SUBMITTED` + 已截止时明确写出「本次不会获得积分，如确需补救请联系管理员延长截止日期」，否则成员会一直等审核。
- **审核表单去掉已失效的「积分凭证链接」输入**：结算不再使用审核人填写的链接（第 4.5 节），保留一个不生效的输入框属于界面缺陷。
- 窄屏下状态与日期不裁字；按钮保持 44px 与可见焦点；全部按钮沿用既有语义色令牌。

## 12. 风险与取舍

| 风险                                                            | 处理                                                                                                     |
| --------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| **改变已上线的普通任务行为，成员与管理员都可能误解**            | 需求清单第 5 节逐条列出行为变化；界面明确「到期前必须审核完」；上线验收覆盖全部变化点                    |
| **历史任务被调度重新处理**                                      | `V14` 回填历史 `STANDARD` 任务为已结算 + 来源编号保持兼容；演练第 5 步专门核对「调度不处理历史任务」     |
| **到期即冻结导致待审核对象永远拿不到积分**                      | 这是「只发已通过」的直接推论；管理端在到期前提示待审核数量，到期后明确显示「已过期未审核」，不自动改状态 |
| **新手任务冻结可能堵死招新**                                    | 新增「按人延长」出口；界面在逾期对象上就地给出延长与打回两个动作；三个出口都写审计                       |
| **结算重复执行（多实例 / 调度重入）**                           | 任务级条件更新认领 + 每人来源编号唯一；`compose.yaml` 当前单实例，但按多实例安全设计                     |
| **JPA 批量更新与持久化上下文不一致**                            | 认领后重新读取任务，或用 `clearAutomatically`，避免把 `points_settled_at` 写回 `NULL`                    |
| **调度线程事务提交污染依赖回滚的测试**                          | `yeslab.task.settlement.enabled` 在测试 `application.yml` 关闭；测试直接调用结算 Service                 |
| **`@Scheduled` 时区漏写会算错一天**                             | 显式 `zone = "Asia/Shanghai"`，与到期判定对齐；与既有 interviews 清理的 UTC 有意不同并写明               |
| **`settleAllDue` 与 `settle` 自调用导致 `@Transactional` 失效** | 通过代理调用或拆独立 bean；用「认领只有一次」的并发断言覆盖                                              |
| **既有 50 项测试大面积失败**                                    | 按第 9.3 节逐项改造而非删除，且不放宽任何断言；新手任务测试需确认 `due_date` 在未来                      |
| **两份迁移的顺序耦合**                                          | `V14`（结算，全站可独立上线）先于 `V15`（悬赏）；结算逻辑不依赖任何悬赏字段                              |

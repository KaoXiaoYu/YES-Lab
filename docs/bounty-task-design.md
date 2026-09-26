# YES Lab 悬赏任务设计

> 本文是悬赏任务**本体**的实现前技术设计。
> **到期冻结与到期结算的通用规则不在本文**——它们对三类任务统一，见 [`docs/task-settlement-design.md`](task-settlement-design.md)；需求清单见 [`docs/bounty-task-requirements.md`](bounty-task-requirements.md)。冲突时以需求清单为准。
> 文中引用的既有代码位置均已逐条核对。

## 1. 需求与设计结论

| 需求                 | 设计结论                                                                                             |
| -------------------- | ---------------------------------------------------------------------------------------------------- |
| 奖金是线下奖励       | 纯文本字段 `tasks.prize_description`（≤500 字）登记**统一说明**，配 `tasks.prize_slots = m` 表示份数 |
| 多份奖金怎么算       | **最先完成的 m 人**获得；奖金持有者 = 已完成对象中名次最靠前的 `min(m, 已完成人数)` 位               |
| 驳回后怎么办         | **自动顺延**给名次最靠前的未获奖完成者；暂时无人可接则份额空置，等下一个完成者自动获得               |
| 人数要求             | `tasks.headcount_limit` 为空 = **不限接取人数**；非空 = 最多 n 人接取、先到先得                      |
| 可以绑积分           | 复用 `tasks.points`：> 0 表示绑定，发布时锁定                                                        |
| 积分什么时候发       | **不在完成时发**；由全站统一的**到期结算**处理（只发已完成的）                                       |
| 到期后               | 不能接取、提交、放弃、驳回；可延长截止日期、可移除接取者（通用口径见结算设计文档）                   |
| 提交即完成           | 提交完成说明即 `PENDING → APPROVED`，没有管理员审核环节；`SUBMITTED` 状态悬赏不使用                  |
| 可放弃，但只能接一次 | 本人放弃或管理员移除都记 `ABANDONED` 并归还接取名额；同一人对同一悬赏**只能接取一次**                |
| 复用既有任务结构     | 沿用富文本正文、可选子任务（0–50）、大任务 + 对象 + 子任务提交三张表与既有成员端提交页面             |

### 1.1 三类任务的分工

|              | 新手任务 `ONBOARDING` | 普通任务 `STANDARD`             | **悬赏任务 `BOUNTY`**                     |
| ------------ | --------------------- | ------------------------------- | ----------------------------------------- |
| 对象来源     | 面试通过自动分配      | 发布时按等级条件快照 + 手工指定 | **成员自主接取**                          |
| 人数         | 不限                  | 不限                            | **接取上限可不限或限 n 人**               |
| 完成认定     | 管理员审核通过        | 管理员审核通过                  | **提交即完成（无审核环节）**              |
| 奖励         | 无（转正门槛）        | 到期结算积分                    | **奖金 m 份（先完成先得）+ 到期结算积分** |
| 截止时间来源 | 对象级 `due_date`     | 任务级 `end_date`               | 任务级 `end_date`                         |
| 到期前可驳回 | 是                    | 是                              | 是（触发奖金顺延）                        |
| 成员入口     | 我的报名              | 我的任务                        | **悬赏榜 + 我的任务**                     |

## 2. 已核对的实现事实

1. **`TaskType` 只有两个值**：`task/model/TaskType.java` 为 `ONBOARDING` / `STANDARD`；`V11__task_module.sql:8` 的 `tasks.task_type` 是 `ENUM ('ONBOARDING','STANDARD')`。新增类型必须走迁移。
2. **两处 ENUM 需要扩值**：`V11:51` 的 `task_assignments.source` 为 `ENUM ('CRITERIA','MANUAL','ONBOARDING')`，`V11:52` 的 `status` 为 `ENUM ('APPROVED','PENDING','REJECTED','SUBMITTED')`。悬赏需要 `CLAIM` 与 `ABANDONED`。
3. **MySQL 的 ENUM 按序号存储**：新取值**只能追加到末尾**。插到中间或调整顺序会让存量行按新序号解释成别的值——这是本次迁移最容易出事故的地方（第 4 节）。
4. **`V11` 已上线**：`DEVLOG.md` 2026-09-20 记录过因直接改 `V11` 导致 `Migration checksum mismatch`、新镜像启动失败。因此本次一律新增 `V15`（结算字段在 `V14`），不动 `V11`—`V14`。
5. **成员端提交接口对任务类型不敏感**：`TaskService.submitMyTask`（`:824`）只校验 `requireStandardEditable`（`:936`），不看 `task_type`。悬赏复用同一端点，窗口判定统一交给结算改造里的 `TaskTiming`（结算设计第 3 节）。
6. **`myTasks` 不筛类型**：`TaskService.myTasks`（`:788`）返回该成员的全部非草稿对象。因此**接取后的悬赏会自动出现在「我的任务」里**，无需新列表接口，只需在视图模型上补悬赏字段。
7. **对象状态只有两种终态**：`TaskAssignmentEntity.isTerminal()`（`:195`）返回 `APPROVED || REJECTED`。新增 `ABANDONED` 后必须同步为三种，否则放弃过的对象会被当成「可编辑」。
8. **接取名额只能靠服务层串行化**：`task_assignments` 的唯一约束是 `(task_id, member_profile_id)` 与 `(task_id, recruitment_application_id)`（`V11:69-70`），能防「同一人重复接取」，**防不了超发**；MySQL 无法用 CHECK 表达「计数不超过 N」。并发控制必须落在服务层（第 5 节）。
9. **本地 H2 与生产 MySQL 存在差异**：测试环境关闭 Flyway 且用 H2 `create-drop`，唯一约束与 `CHECK` 只在生产 MySQL 生效（`DEVLOG.md` 2026-09-17 已记录）。服务层校验不能只依赖数据库约束。
10. **积分的发放由结算统一处理**：普通任务原来在审核通过时计分（`TaskService.reviewStandard` 调用 `PointService.grantForTask`），本次改造后所有积分的实际发放都收敛到 `TaskSettlementService`（结算设计第 4 节）。悬赏**不新增任何计分调用点**。

## 3. 语义定义（悬赏本体）

- **接取（claim）**：成员主动把一条悬赏纳入自己的任务列表。接取是**一次性**的：同一成员对同一条悬赏只能接取一次。
- **接取上限（headcount）**：`headcount_limit` 为空 = 不限人数；非空 = 最多 n 人接取，先到先得、接满即止。它只控制参与规模，**与奖金、积分都无关**。
- **完成名次（completion_rank）**：完成时锁定的「第几个完成」，从 1 开始，一经分配不再重算，是历史留痕。
- **奖金份数（prize_slots = m）**：奖金有 m 份、内容相同。**奖金持有者 = 当前已完成对象中名次最靠前的 `min(m, 已完成人数)` 位**——这是奖金部分的核心不变量。
- **顺延**：某位奖金持有者被驳回后，不变量重新成立：空出的份额自动由名次最靠前的未获奖完成者接手；若当时没有这样的人，份额空着，等下一个完成者自动获得。
- **到期与结算**：悬赏的有效截止时间是任务级 `end_date`（`CLOSED` 也算到期）；到期后成员侧只读、管理员不能驳回，并按全站口径结算积分（只发 `APPROVED`）。详见结算设计文档。
- **明确不是**：悬赏不是「组队悬赏」（不要求 N 人共同完成）、不是「先接取先获奖」（获奖看完成名次）、积分也不是「完成即到账」（到期结算）。

## 4. 数据模型：`V15__bounty_task.sql`

> **已按本节实现并完成真机演练**（第 7 批）：脚本 `backend/src/main/resources/db/migration/V15__bounty_task.sql`，
> 演练脚本 `.codex-run/settlement-rehearsal/rehearse-v15.sh`（**19 项断言全部通过**），
> 并以 `ddl-auto=validate` 对由 `V1`—`V15` 建出的真实 MySQL 结构校验通过。

改动全部是**追加**，不删除、不重命名、不动存量数据。结算字段（`tasks.points_settled_at`）由 `V14` 提供，本脚本不重复添加。

| 变更                                                                   | 说明                                                                         |
| ---------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| `tasks.task_type` 追加 `BOUNTY`                                        | `ENUM ('ONBOARDING','STANDARD','BOUNTY')`，追加在末尾                        |
| `tasks.headcount_limit` 新增 `INTEGER NULL`                            | 接取人数上限 1—1000；`NULL` = 不限                                           |
| `tasks.prize_slots` 新增 `INTEGER NULL`                                | 奖金份数 m（1—100）；`NULL` = 不设奖金                                       |
| `tasks.prize_description` 新增 `VARCHAR(500) NULL`                     | 奖金说明（纯文本）                                                           |
| `task_assignments.source` 追加 `CLAIM`                                 | `ENUM ('CRITERIA','MANUAL','ONBOARDING','CLAIM')`，追加在末尾                |
| `task_assignments.status` 追加 `ABANDONED`                             | `ENUM ('APPROVED','PENDING','REJECTED','SUBMITTED','ABANDONED')`，追加在末尾 |
| `task_assignments.completion_rank` 新增 `INTEGER NULL`                 | 完成名次，从 1 开始；未完成的为 `NULL`                                       |
| `task_assignments.prize_awarded` 新增 `BOOLEAN NOT NULL DEFAULT FALSE` | 是否持有奖金份额；因驳回顺延而可变（第 6 节）                                |

写法要点：

- **加列前查 `information_schema`**，用 `PREPARE` / `EXECUTE` 执行，保证脚本可重复执行——沿用 `V13__subtask_submission.sql:8-16` 的既有模式。
- **`MODIFY COLUMN` 写出完整的新 ENUM 定义**且新值放在最后：MySQL 追加 ENUM 成员属于 `ALGORITHM=INPLACE`，不需要搬数据，但改写定义时必须保持既有取值的相对顺序。
- **新增的 `NOT NULL` 列一律带 `DEFAULT`**：`DEVLOG.md` 2026-09-20 记录过 H2 上「非空列无默认值 + 表已有数据」导致加列失败的问题。
- **与 `V14` 分开**：结算改造是全站横切、先上线可独立验证；悬赏是新类型，晚一步不影响结算能力；失败时能立刻分辨是哪一批。

## 5. 接取与名额并发控制

**问题**：先到先得意味着「读占用数 → 写对象」必须原子。两个成员同时抢最后一个名额时，若只有普通查询会双双读到「还剩 1 个」并各自插入，造成超发。唯一约束防不了这种情况。

**方案**：把「计数 + 插入」放进同一事务，并先对**任务行**加悲观写锁：

```text
claim(taskId, account) 事务开始
  1. TaskEntity task = tasks.findByIdForUpdate(taskId)      // SELECT ... FOR UPDATE，锁住这一条悬赏
  2. 校验：task.isBounty() && status == PUBLISHED
  3. 校验时间窗口：TaskTiming.isMemberEditable（任务未到期 + 已到开始日期）
  4. 校验资格：复用 task_audience_rules 命中本人（条件为空 = 全体成员可接）
  5. 校验重复：findByTaskIdAndMemberProfile_Id(taskId, profileId) 存在即拒（含 ABANDONED / REJECTED 行）
  6. if (headcountLimit != null) {
        long occupied = countByTaskIdAndStatusIn(taskId, [PENDING, APPROVED]);
        if (occupied >= headcountLimit) 抛 409「接取名额已满」
     }
  7. assignments.save(new TaskAssignmentEntity(task, profile, null, CLAIM))   // 状态 PENDING
  8. 站内消息「已接取」；若设了上限且刚好招满，另发一条给创建者
事务提交（锁释放）
```

设计理由：

- **锁的粒度是单条任务**：并发只发生在同一条悬赏的抢名额者之间，不影响其它任务与接口。
- **锁顺序统一**：接取、完成、放弃、移除、驳回都先 `findByIdForUpdate` 再改数据，避免死锁。
- **不依赖数据库约束**：服务层即使在没有唯一约束的 H2 上也靠「先查重复」+ 行锁保证不超发；MySQL 的唯一约束只作兜底。
- **不限人数时仍然加锁**：不为两种配置写两条路径。
- **H2 只能验证「服务层没被绕过」**：H2 的 MVCC 与 InnoDB 行锁行为不同，**真正的超发验证必须在预生产 MySQL 上做**（第 13 节第 2 步）。

## 6. 完成、名次与奖金分配

### 6.1 完成流程

```text
submitCompletion(taskId, profile, note) 事务开始
  1. TaskEntity task = tasks.findByIdForUpdate(taskId)              // 与接取共用同一把锁
  2. TaskTiming 判定未到期（成员侧可写）
  3. 找到本人对象，要求 status == PENDING（APPROVED / ABANDONED / REJECTED 都拒绝）
  4. int rank = countByTaskIdAndCompletionRankIsNotNull(taskId) + 1   // 历史第几个完成，单调不复用
  5. assignment.completeWithRank(rank, note)                          // → APPROVED，写入 completion_rank / submitted_at
  6. recomputePrizeHolders(task)                                      // 见 6.2
  7. 通知本人（名次、是否获奖、积分「待结算」）；若奖金份数首次全部产生，通知创建者
事务提交
```

**注意：完成时不发积分**，积分统一由到期结算处理（结算设计第 4 节），因此这里不出现任何积分调用。

### 6.2 奖金分配：一条不变量 + 一次重算

**不变量**：

> 设 `V` = 该任务下 `status = APPROVED` 的对象，按 `completion_rank` 升序排列。
> 奖金持有者集合 = `V` 的前 `min(m, |V|)` 位（`m = prize_slots`；未设奖金时集合恒为空）。

`prize_awarded` 是这个不变量在库里的**投影列**，在每次「完成」或「驳回」后重算一次：

```text
recomputePrizeHolders(task) {
  if (!task.hasPrize()) return;
  V = assignments.findByTaskIdAndCompletionRankIsNotNullOrderByCompletionRankAsc(taskId)
                 .filter(status == APPROVED)                 // 按名次升序
  holders = V.subList(0, min(m, V.size()))
  for (row : V) {
    boolean should = holders.contains(row)
    if (should && !row.prizeAwarded()) { row.markPrize(true);  通知「奖金顺延获得」 }   // 仅在顺延时发生
    if (!should && row.prizeAwarded()) { row.markPrize(false) }                      // 只可能是刚被驳回的人
  }
}
```

- **完成时**：新完成者的名次最大，进入 `V` 的末尾。只有「已完成人数 ≤ m」或「有人被驳回后腾出份额」时它才会获奖。
- **驳回时**：被驳回者离开 `V`，后面的名次整体前移，于是**名次最靠前的未获奖完成者自动补位**——这就是「顺延」，不需要额外规则。
- **无人可补位时**：份额空置（`V.size() < m`），等下一个完成者完成时按不变量自动获得，不需要「预留份额」的中间状态。
- **只在锁内重算**：必须在持有任务行锁的事务里执行，否则并发的「完成」与「驳回」可能交错，出现两个人同时补位。
- **被降级的人只会是刚被驳回者**：其余人的相对名次没变，`V` 的截断位置也不会前移。这条要写成断言覆盖的测试用例。
- **名次不重算**：被驳回者的 `completion_rank` 保留作为历史留痕；不变量只看 `APPROVED`，因此不影响后续分配。
- **到期后不再重算**：驳回只在到期前可用（结算设计第 3 节），所以到期后奖金持有者冻结。

### 6.3 边界情形

| 情形                                  | 结果                                                                |
| ------------------------------------- | ------------------------------------------------------------------- |
| m = 2，只有 1 人完成                  | 该人获奖，第 2 份空置；第 2 人完成时自动获得                        |
| m = 2，3 人完成                       | 名次 1、2 获奖，第 3 人无奖金（积分仍看结算资格）                   |
| m = 2，名次 1 被驳回，名次 3 已完成   | 名次 3 顺延获奖并收到通知；名次 1 的 `prize_awarded` 归位为 `false` |
| m = 2，名次 1 被驳回，只有名次 2 完成 | 名次 2 保持获奖，第 2 份空置，等下一个完成者                        |
| 未设奖金（`prize_slots` 为空）        | 不执行重算，`prize_awarded` 恒为 `false`                            |
| 到期后驳回获奖者                      | **被拒绝（409）**，奖金持有者冻结                                   |
| 到期后仍有未完成接取者                | 什么都不做，只显示「已逾期未完成」，由结算判定其拿不到积分          |

### 6.4 线下奖金发放/领取台账（已确认并实施）

**目标**：逐获奖者记录线下履约进度，不登记金额、不替代线下付款；状态为 `PENDING（待发放） → ISSUED（已发放，待确认） → RECEIVED（已领取）`。获奖记录被驳回且尚未发放时转为 `REVOKED（资格撤销）`，保留时间与操作人。

**数据模型**：新增 `bounty_prize_fulfillments` 表，一条悬赏对象最多一条履约记录（`task_assignment_id` 唯一外键）。记录 `status`、`issued_at` / `issued_by_account_id`、`received_at` / `received_by_profile_id`、`revoked_at` / `revoked_by_account_id` / `revoked_reason` 及创建更新时间。它与 `task_assignments.prize_awarded` 分工：后者表示当前奖金资格；新表表示履约和历史留痕。`V16` 将历史奖金持有者回填为待发放。

**状态同步与安全不变量**：

1. `recomputePrizeHolders` 在对象首次成为奖金持有者时幂等创建 `PENDING` 记录；仅当对象从奖金集合移除时，才把仍为 `PENDING` 的履约记录置为 `REVOKED`。历史记录不删除、不重用。
2. 管理员登记发放仅允许当前 `APPROVED && prize_awarded && fulfillment=PENDING` 的对象；记录当前时间和管理员账号。成员只可对本人 `ISSUED` 记录确认领取，记录当前时间与成员档案。
3. **物理奖励已发出无法由系统收回**：若记录已为 `ISSUED` 或 `RECEIVED`，事后驳回必须返回 409，不改变完成状态与奖金资格。仍为 `PENDING` 时可以驳回，履约记录转 `REVOKED` 并照常顺延。
4. 发放/领取是履约台账动作，允许在悬赏到期或结束后完成；不改变完成名次、奖金份额资格与积分结算结果。
5. 每个状态只允许单向转换，不提供“撤回发放/领取”入口；误登记需人工审计处理，是否需要带理由的纠错流程须另行确认。

6. 接口在管理发放、本人确认和奖金资格撤销时都先锁定任务行，避免完成、领奖资格变更与发放交错；所有记录没有反向纠错入口，误登记走人工审计。

## 7. 状态机

### 7.1 悬赏对象

```text
（无对象）
  │ 成员接取：占用接取名额（事务内校验窗口 / 资格 / 重复 / 上限）
  ▼
PENDING 进行中 ──截止前提交完成说明（提交即完成，写入名次、触发奖金分配）──► APPROVED 已完成
  │                                                                            │
  │ 本人放弃 / 管理员移除                                                        │ 管理员事后驳回（必填意见，触发奖金顺延）
  ▼                                                                            ▼
ABANDONED 已放弃（归还名额，本人不可再接）                        REJECTED 已驳回（归还名额，本人不可再接）
```

- `SUBMITTED`（待确认）在悬赏上**永不出现**，它继续服务普通任务与新手任务。
- 只有 `APPROVED` 参与奖金不变量，也只有 `APPROVED` 在结算时获得积分。
- **到期后上述所有箭头都被冻结**（不能提交、不能放弃、不能驳回），只剩管理员「移除接取者」这一条清理动作。
- 终态之间没有回退路径：驳回不能撤销、放弃不能撤回，这是「只能接一次」的直接推论。

### 7.2 悬赏任务主体

```text
DRAFT 草稿 ──发布（校验奖励、奖金说明与份数、日期与人数）──► PUBLISHED 已发布 ──结束──► CLOSED 已结束
   │                                                          │                          │
   └ 可删除、不上榜、不能接取                                    └ 可接取、可提交            └ 成员只读；结束即结算
```

## 8. 时间限制

悬赏的时间判定**不单独实现**，统一使用结算改造引入的 `TaskTiming`（结算设计第 3 节）：

| 动作                       | 允许条件                                                                             |
| -------------------------- | ------------------------------------------------------------------------------------ |
| 在悬赏榜看到它             | 任务 `PUBLISHED`                                                                     |
| 接取                       | `TaskTiming.isMemberEditable`：未到期、已到开始日期、任务在发布期                    |
| 提交完成说明（提交即完成） | 同上                                                                                 |
| 提交子任务内容             | 同上                                                                                 |
| 放弃                       | 对象 `PENDING` 且未到期                                                              |
| 管理端驳回                 | 对象 `APPROVED` 且**未到期**（到期后 409）                                           |
| 管理端移除                 | 对象 `PENDING`；**到期后仍可执行**（清理动作，不产生结论）                           |
| 管理端调整                 | 接取上限与奖金份数只增不减；截止日期只能延长（已结算则重置结算标记，由结算服务处理） |

`end_date` 为空表示永不到期，此时只有管理员结束任务能触发冻结与结算。

## 9. 后端接口

### 9.1 成员端 `/api/v1/bounties`（新增 `BountyController`）

| 方法 | 路径                                | 说明                                                                                                                                                             |
| ---- | ----------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| GET  | `/api/v1/bounties`                  | 悬赏榜：可接取列表 + 我已接取/已完成；每项含奖金说明、`prizeSlots`、`prizeIssued`、`headcountLimit`、`claimed`、截止日期与剩余天数、我的状态与名次、积分结算状态 |
| GET  | `/api/v1/bounties/{taskId}`         | 悬赏详情：正文、子任务清单（只带 `hasContent`）、奖金说明与份数、接取情况、积分与结算状态、我能否接取及原因                                                      |
| POST | `/api/v1/bounties/{taskId}/claim`   | 接取；成功后返回 `assignmentId`；满员 409、重复 409、到期或未开始 409、资格不符 403                                                                              |
| POST | `/api/v1/bounties/{taskId}/abandon` | 放弃本人 `PENDING` 的接取；到期或已完成时 409                                                                                                                    |

**提交与子任务复用既有接口**，不新增重复端点：

| 方法 | 路径                                                           | 悬赏下的行为                                                          |
| ---- | -------------------------------------------------------------- | --------------------------------------------------------------------- |
| POST | `/api/v1/tasks/{assignmentId}/submission`                      | 走 BOUNTY 分支：未到期则 `PENDING → APPROVED`（提交即完成，不发积分） |
| POST | `/api/v1/tasks/{assignmentId}/subtasks/{subtaskId}/submission` | 与其他类型一致，不强制全勾，同样受到期约束                            |
| GET  | `/api/v1/tasks`、`/tasks/{assignmentId}`、子任务详情           | 复用；视图模型补悬赏与结算字段                                        |

### 9.2 管理端 `/api/v1/admin/bounties`（新增 `AdminBountyController`，`TASK_MANAGE`）

| 方法   | 路径                                                                            | 说明                                                                                              |
| ------ | ------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| GET    | `/api/v1/admin/bounties?status=&keyword=`                                       | 悬赏列表与汇总（接取/上限、已完成、获奖/份数、放弃、驳回、待结算/已结算、积分合计）               |
| POST   | `/api/v1/admin/bounties`                                                        | 创建草稿：标题、正文、奖金说明与份数、绑定积分、接取上限、起止日期、子任务、接取条件              |
| GET    | `/api/v1/admin/bounties/{taskId}`                                               | 读取（含子任务正文，供编辑）                                                                      |
| PUT    | `/api/v1/admin/bounties/{taskId}`                                               | 修改：草稿可改全部；已发布仅可改标题、正文、奖金说明、子任务、**上调**人数/份数、**延长**截止日期 |
| POST   | `/api/v1/admin/bounties/{taskId}/publish`                                       | 发布（校验奖励、说明与份数配套、日期与人数；**不展开对象**）                                      |
| POST   | `/api/v1/admin/bounties/{taskId}/close`                                         | 结束（成员侧只读）**并同步触发一次结算**                                                          |
| DELETE | `/api/v1/admin/bounties/{taskId}`                                               | 删除草稿                                                                                          |
| GET    | `/api/v1/admin/bounties/{taskId}/claims`                                        | 接取名单与汇总：接取者、接取时间、完成名次、是否持有奖金、状态、完成说明、结算状态与积分结果      |
| POST   | `/api/v1/admin/bounties/{taskId}/claims/{assignmentId}/revoke`                  | 事后驳回：`APPROVED → REJECTED`，必填意见，归还接取名额，触发奖金顺延；**到期后 409**             |
| DELETE | `/api/v1/admin/bounties/{taskId}/claims/{assignmentId}`                         | 移除接取者：`PENDING → ABANDONED`，归还接取名额                                                   |
| POST   | `/api/v1/admin/bounties/{taskId}/claims/{assignmentId}/prize-fulfillment/issue` | `TASK_MANAGE` 登记当前获奖对象已线下发放；写入管理员与时间，截止/结束后仍可登记                   |

**没有结算端点**：结算只由定时任务与 `close` 触发。**与普通任务接口的隔离**：`/api/v1/admin/tasks` 的列表、汇总、条件预览一律不返回也不接受 `BOUNTY` 类型；类型校验在服务层收口。

成员端领取确认接口：`POST /api/v1/tasks/{assignmentId}/bounty-prize/confirm-received`；只允许本人确认 `ISSUED` 状态的奖金。所有履约端点均使用事务和单向状态校验，重复或越权操作返回明确 409/404。

### 9.3 视图模型新增字段

| 模型                              | 新增字段                                                                                                                                                                                                                               |
| --------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `BountyBoardItemView`（新）       | `taskId`、`title`、`summary`、`prizeDescription`、`prizeSlots`、`prizeIssued`、`headcountLimit`、`claimed`、`startDate`、`endDate`、`myStatus`、`myRank`、`prizeAwarded`、`points`、`pointsSettled`、`claimable`、`claimBlockedReason` |
| `BountyDetailView`（新）          | 上述字段 + `contentHtml`、`subtasks`（含 `hasContent`）、`windowOpen`、`windowClosedReason`                                                                                                                                            |
| `MyTaskView` / `MyTaskDetailView` | 新增 `taskType`、`prizeDescription`、`prizeSlots`、`prizeAwarded`、`completionRank`，以及结算改造引入的 `expired` / `pointsSettled` 等字段                                                                                             |
| `BountyClaimRowView`（新）        | 姓名、编号、年级、状态、接取时间、完成名次、是否持有奖金、提交时间、完成说明、积分结果与跳过原因、驳回留痕                                                                                                                             |
| `BountyClaimsView`（新）          | 汇总（接取/上限、已完成、获奖/份数、放弃、驳回、待结算/已结算、积分合计）+ 逐人明细                                                                                                                                                    |
| `CreateBountyRequest`（新）       | `title`、`contentHtml`、`prizeDescription`、`prizeSlots`、`points`、`headcountLimit`、`startDate`、`endDate`、`subtasks`、`audienceRules`                                                                                              |

**不复用 `CreateTaskRequest`**：它的字段全部服务于普通任务（积分必填、条件与手工指定成员），把悬赏塞进去会让必填校验变成一堆条件分支。悬赏走独立的请求与响应记录，服务层共用实体与仓储。

### 9.4 校验规则

1. `title` 与清洗后的 `contentHtml` 必填（沿用「无可见文字且无 `<img>` 则拒绝」口径）。
2. **至少一种奖励**：`prizeSlots` 与 `points` 不能同时为空 / 为 0。
3. `prizeSlots` 非空时为 1—100 且 `prizeDescription` 必填；为空时 `prizeDescription` 必须为空。
4. `points` 为 0—100000；请求里不传视为 0。
5. `headcountLimit` 为空或 1—1000；设了上限时要求 `prizeSlots ≤ headcountLimit`。
6. `endDate` 不得早于 `startDate`；两者都允许为空。
7. 子任务 0—50 条，标题不得为空、同任务内不得重复。
8. 已发布任务：`headcountLimit` 与 `prizeSlots` 只能上调，且不得低于当前占用数 / 已产生份数；`endDate` 只能延长。
9. 已发布任务不接受接取条件的修改。

## 10. 权限

| 能力                                                     | 权限要求                                                      |
| -------------------------------------------------------- | ------------------------------------------------------------- |
| 创建 / 修改 / 发布 / 结束 / 删除悬赏、看名单、驳回、移除 | `TASK_MANAGE`（教师与核心学生自动拥有）                       |
| 浏览悬赏榜、接取、放弃、提交完成说明                     | 已登录且拥有成员档案（`TEACHER` / `CORE_STUDENT` / `MEMBER`） |
| 游客                                                     | 不可见、不可接取（没有成员档案就无法归属对象）                |
| 到期结算                                                 | 系统内部动作，无对外接口、无新增权限                          |

接取资格复用 `task_audience_rules`（角色 / 成员状态 / 年级 / 能力标签），语义与普通任务一致：同维度取「或」、跨维度取「且」；条件为空表示全体成员可接。教师默认允许接取（能获奖，但结算时积分跳过）。

## 11. 站内消息

复用 `NotificationService.send(recipient, type, title, summary, targetPath)` 与现有字符串类型（通知类型不是枚举，无需迁移）：

| 事件                     | 类型             | 收件人     | 目标地址                              |
| ------------------------ | ---------------- | ---------- | ------------------------------------- |
| 接取成功                 | `TASK_ASSIGNED`  | 接取者     | `/tasks/{assignmentId}`               |
| 接取名额招满             | `TASK_UPDATED`   | 任务创建者 | `/admin/bounties/{taskId}/claims`     |
| 完成（含名次、是否获奖） | `TASK_APPROVED`  | 完成者     | `/tasks/{assignmentId}`               |
| 奖金顺延获得             | `TASK_UPDATED`   | 顺延者     | `/tasks/{assignmentId}`               |
| 奖金份数全部产生         | `TASK_UPDATED`   | 任务创建者 | `/admin/bounties/{taskId}/claims`     |
| 管理员事后驳回           | `TASK_REJECTED`  | 被驳回者   | `/bounties/{taskId}`                  |
| 结算完成（汇总）         | `TASK_UPDATED`   | 任务创建者 | 由结算服务统一发送（结算设计第 4 节） |
| 积分到账                 | `POINTS_GRANTED` | 获得积分者 | `/profile`（积分模块既有行为）        |

- **完成时不再发积分消息**：完成消息只说明名次、是否获奖与「积分将在任务到期后统一发放」。
- 到期未完成的人**不发催办消息**。

## 12. 测试计划

> **已全部落地**：后端 `BountyApiTests` 10 项，全量 **72 项通过**；前端悬赏榜/详情/管理端表单与名单复核
> 已完成真机自检（`.codex-run/ui-review/verify-bounty-ui.mjs`，**21 项断言全部通过**）。
> 路由与设计稿略有收敛：创建/编辑沿用「同页内联表单」的既有做法（与普通任务管理页一致），
> 因此没有单独的 `/admin/bounties/new` 与 `/edit` 路由。

### 12.1 新增 `BountyApiTests`（集成测试，`@Transactional` 与既有测试隔离方式一致）

**接取名额**

- 上限为 1 时两个线程同时接取，**只有一个成功**，另一个 409，对象数恰好为 1；
- 上限为 2 时第三个人 409；上限为空时 5 个人都能接取成功；
- 放弃 `PENDING` 后名额被他人接取成功；管理员移除后同样释放。

**只能接一次**

- 同一人第二次接取 409；放弃后、被移除后、被驳回后再接均 409。

**时间限制**

- `start_date` 在明天时接取 409；`end_date` 在昨天时接取 409、提交 409、放弃 409；
- 到期后**驳回也 409**（结算设计第 9.1 节的通用用例在这里再覆盖一次悬赏分支）；
- 到期后对象状态与 `prize_awarded` 完全不变；管理员延长 `end_date` 后同一对象恢复可提交；
- 任务 `CLOSED` 后接取/提交/放弃全部 409，但管理员仍可移除接取者与延长截止日期。

**完成名次与奖金**

- 三人依次完成，`completion_rank` 为 1、2、3，唯一且连续；
- m = 2 时第 1、2 名 `prize_awarded = true`、第 3 名 `false`；完成人数不足 m 时全部获奖；
- 驳回第 1 名后第 3 名顺延获奖并收到通知，第 1 名归位为 `false` 且名次仍为 1；
- 驳回第 2 名（此时只有 2 人完成）后无人可补位，第 3 人完成后自动获得该份额；
- m 为空时不执行重算；只有刚被驳回者会被降级。

**积分（与结算服务的契约）**

- **完成时不发积分**：对象转 `APPROVED` 后 `point_grants` 数量不变；
- 到期结算后只有 `APPROVED` 拿到积分，`PENDING` / `ABANDONED` / `REJECTED` 都没有且状态不变；
- 奖金与积分独立：试用成员完成 → `prize_awarded = true` 且积分被跳过并记原因；
- 悬赏的结算用例与普通任务共用结算服务的测试（结算设计第 9.2 节），本类只验证悬赏专属字段（名次、奖金、来源编号前缀 `BOUNTY:`）。

**校验、隔离与权限**

- 奖励两种都没有时被拒；只填份数不填说明、或只填说明不填份数被拒；
- 设了接取上限时 `prizeSlots > headcountLimit` 被拒；
- 已发布任务上调人数/份数成功，下调到低于占用数 / 已产生份数被拒；延长截止日期成功；
- 普通任务列表 / 汇总 / 条件预览不返回悬赏，悬赏列表不返回普通任务；
- 管理端接口对普通成员 403；访问他人悬赏对象 404；游客访问悬赏榜 403；
- 富文本中的脚本与事件属性被清洗，`<img>` 与链接保留。

### 12.2 冒烟脚本

扩展 `scripts/smoke-task-module.sh`（或新增 `scripts/smoke-bounty.sh`），用真实 HTTP 覆盖：创建「接取上限 2、奖金 1 份、绑定积分」的悬赏 → 发布 → 两人接取、第三人被拒 → 依次完成 → 核对名次与获奖、确认此时**没有**积分流水 → 结束任务触发结算 → 核对只有已完成者拿到积分 → 驳回第 1 名 → 核对奖金顺延且积分不回收 → 再次驳回被拒（到期后）。

## 13. 迁移演练与上线验收

`V15` 是 MySQL 专用语法（`ENUM`、`MODIFY COLUMN`、`information_schema` 动态 SQL），测试环境关闭 Flyway 且用 H2，**自动化测试覆盖不到**。必须人工演练：

1. 备份生产 MySQL（沿用现有 `backup.sh`；禁止 `docker compose down -v`，不得删除 `/srv/yeslab/data` 下任何数据）。
2. 在预生产库按 `V1`—`V14` 建库 → **造存量数据**：一条 `STANDARD` 任务（含 `SUBMITTED` 对象）、一条 `ONBOARDING` 任务与对象。
3. 执行 `V15`，核对三处 ENUM 定义已含新取值、**存量行的 `task_type` 与 `status` 读回值完全不变**、五处新列已加上（`prize_awarded` 为 `NOT NULL DEFAULT 0`）。
4. **重复执行 `V15`** 确认幂等；再以 `ddl-auto=validate` 启动后端确认结构校验通过。
5. 创建「接取上限 1 人、奖金 1 份」的悬赏，**在 MySQL 8.4 上用两个并发请求接取**，确认只有一人成功、`task_assignments` 恰好一行。
6. 造一条奖金 2 份的悬赏：三人依次完成 → 驳回第 1 名 → 确认奖金顺延给第 3 名。
7. 把 `end_date` 改为昨天：确认接取/提交/放弃/驳回全部 409 且对象状态不变；改回未来后确认恢复可用。

8. 执行 `V16` 后核对每条历史 `prize_awarded=TRUE` 的悬赏对象恰有一条 `PENDING` 台账，非获奖对象没有回填记录；重复执行 `V16` 不增加重复记录。
9. 用 `ddl-auto=validate` 启动后端；验证发放与本人领取都正确留痕、任务到期/结束后仍允许补录、发放后驳回返回 409 且奖金/完成状态不变，并进行管理/成员双角色浏览器验收。

结算相关验收见 `docs/task-settlement-requirements.md` 第 7 节与 `docs/task-settlement-design.md` 第 10 节。`V16` 同样是 MySQL 专用迁移，H2 测试不能替代其备份、预生产演练和数据核对。

## 14. 前端设计

### 14.1 新增与改动的文件

| 文件                                            | 说明                                                                                                         |
| ----------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| `src/services/authApi.js`                       | 按仓库既有约定把悬赏接口并入该文件，不新建 `bountyApi.js`                                                    |
| `src/views/BountyBoardView.vue`                 | 悬赏榜：卡片列表 + 状态筛选（可接取 / 我已接取 / 接取已满 / 已截止 / 已结束）                                |
| `src/views/BountyDetailView.vue`                | 悬赏详情：正文、子任务清单、奖金说明与份数、接取情况、积分与结算状态、接取 / 放弃、截止时间                  |
| `src/views/AdminBountiesView.vue`               | 悬赏列表与汇总 + 创建 / 编辑表单（接取上限「不限 / 限制 n 人」、奖金份数与说明、绑定积分、截止日期）         |
| `src/views/AdminBountyClaimsView.vue`           | 接取名单与事后复核：名额与份数余额、结算状态、逐人明细（名次、是否获奖、积分结果）、驳回、移除、延长截止日期 |
| `src/views/TasksView.vue`、`TaskDetailView.vue` | 增加悬赏分支：悬赏标识、奖金说明、名次与获奖结果、积分与到期状态                                             |
| `src/components/PortalShell.vue`                | 成员顶栏与后台侧栏新增「悬赏」/「悬赏管理」入口，图标用既有 Lucide SVG（`Gift` / `Trophy`）                  |
| `src/router/index.js`                           | 新增路由（全部懒加载）                                                                                       |
| `src/portal.css`                                | 悬赏卡片、名额与份数进度、结算状态与窄屏适配样式                                                             |

路由：

```text
/bounties                        悬赏榜        roles: TEACHER, CORE_STUDENT, MEMBER
/bounties/:taskId                悬赏详情      roles: TEACHER, CORE_STUDENT, MEMBER
/admin/bounties                  悬赏管理      roles: TEACHER, CORE_STUDENT
/admin/bounties/new              创建悬赏      roles: TEACHER, CORE_STUDENT
/admin/bounties/:taskId/edit     编辑悬赏      roles: TEACHER, CORE_STUDENT
/admin/bounties/:taskId/claims   接取名单与复核 roles: TEACHER, CORE_STUDENT
```

已接取的悬赏不新增页面，直接进入既有的 `/tasks` 与 `/tasks/:assignmentId`。

### 14.2 UI 规则取用（`ui-ux-pro-max`）

先读 `design-system/yes-lab/MASTER.md`（Swiss Modernism 2.0、编辑风留白、语义色令牌），本次检索与采纳结论：

| 查询                                                        | 命中                                                          | 采纳结论                                                                                                                               |
| ----------------------------------------------------------- | ------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| `"limited slots remaining claim" --domain ux`（**0 命中**） | 无                                                            | 按技能契约改写查询重试，不编造结果                                                                                                     |
| `"remaining count scarcity" --domain ux`                    | Contextual Live Badge Updates（Accessibility, High）          | 接取名额与奖金份数变化用**单一** `role="status" aria-atomic="true"` 播报完整句子（「还剩 2 个接取名额」「奖金还剩 1 份」），不播裸数字 |
| `"irreversible action confirmation" --domain ux`            | Confirmation Dialogs（High）、Confirmation Messages（Medium） | 接取不可逆（只能接一次）→ 二次确认弹窗；成功后明确提示结果，不静默成功                                                                 |
| `"loading feedback async submit" --domain ux`               | Loading Buttons、Submit Feedback、Loading States（均 High）   | 接取 / 放弃 / 提交期间按钮 `disabled` + loading，防重复点击；失败就地给出原因                                                          |
| `"deadline expiry countdown" --domain ux`（**0 命中**）     | 无                                                            | 同上，改写查询重试                                                                                                                     |
| `"expired unavailable disabled state" --domain ux`          | Disabled States（Interaction, Medium）                        | 已截止 / 已满员的按钮要明显区别于可用状态（降透明度 + `cursor: not-allowed`），不能只在文案上区分                                      |

其余沿用既有约定：对比度 ≥ 4.5:1、触控目标 ≥ 44px、键盘可见焦点、`prefers-reduced-motion`、375/768/1024/1440 断点、图标只用 SVG。到期与结算状态的通用界面规则见结算需求清单 H 组。

### 14.3 交互要点

- 悬赏卡片把两个数字分开呈现并各自标注含义：**接取**（`不限` 或 `已接 3 / 上限 5`）与**奖金**（`已产生 1 / 共 3 份`），避免被误读成同一个名额。
- 截止时间显示为日期 + 剩余天数；**已截止**时卡片与详情页进入只读态，按钮禁用并写明「已截止，如需继续请联系管理员延长截止日期」。
- 接取按钮文案区分状态：可接取「立即接取」、已接取「进入任务」、接取已满「接取名额已满」、已截止「已截止」。
- 接取确认弹窗写明：**「接取后名额即被占用，且你只能接取这条悬赏一次；放弃后无法再次接取。」**
- 奖金说明旁标注线下发放；成员任务详情显示逐人发放与领取状态，避免误以为线上支付。
- 完成后显示「已完成 · 第 k 名」「已获奖 / 未获奖」，并按通用规则显示积分的待结算 / 已结算状态。
- 管理端创建表单：接取上限用「不限 / 限制 N 人」二选一（不限时隐藏数字输入），奖金份数与说明成对校验，积分填 0 时明确提示「不绑定积分」。
- 管理端名单页顶部显示接取名额、奖金份数与结算状态；**到期后驳回入口禁用**并写明「任务已截止，不能再驳回」。
- 窄屏下悬赏卡片单列堆叠，名额、份数与日期不裁字；全部操作按钮保持 44px 与可见焦点。

## 15. 明确不实现

- 逐人奖金金额登记、每份奖金内容不同（分名次奖励）、线上支付 / 报销；
- 成团 / 组队悬赏、多人共享一条悬赏对象；
- 抽签、优先级队列、名额超发、候补队列、预留份额中间态；
- 自动分配接取者、满员自动结束、奖金发完自动结束、到期自动作废；
- 对未完成者的自动催办、截止前定时提醒；
- 放弃后重新接取、接取冷却期、限制接取次数上限（除「一次」之外）；
- 「撤销驳回」与自动反向积分；
- 顺延之外的重新排名（`completion_rank` 一经分配不再重算）；
- 悬赏专属的结算实现（结算由全站统一服务处理，无对外结算接口）；
- 悬赏多版本 / 灰度 / 模板 / 周期悬赏；
- 附件上传、评论 / 讨论区、悬赏与项目或竞赛关联。

## 16. 实施顺序

悬赏本体分五批（**结算横切改造单独一批，见结算需求清单第 7 节；`V14` 必须先于 `V15`**）：

1. **`V15` 迁移**（不含 Java 代码，先演练）；
2. **领域层**：枚举、实体字段与流转方法、仓储查询（含 `findByIdForUpdate` 与完成名次计数）；
3. **服务与接口**：`BountyService` + `BountyController` / `AdminBountyController` + `TaskService` 的 BOUNTY 分支与视图模型；
4. **后端测试**：`BountyApiTests` + 回归既有测试与冒烟脚本；
5. **前端**：接口封装、悬赏榜、悬赏详情、管理端列表 / 表单 / 名单复核、导航与路由、我的任务悬赏分支。

每批完成后执行：`npm run check`、Java 21 下 `./mvnw test` 全量回归、`git diff --check`、更新 `DEVLOG.md`。

## 17. 风险与取舍

| 风险                               | 处理                                                                                                             |
| ---------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| ENUM 追加顺序写错导致存量数据错值  | 新值只追加到末尾；预生产演练必须显式核对旧行读回值（第 13 节第 3 步）                                            |
| 接取超发                           | 任务行悲观写锁 + 事务内计数 + 服务层重复校验；唯一约束只作兜底；上线前必须在预生产 MySQL 实测并发（H2 不能替代） |
| 完成名次重复或奖金重复持有         | 名次与 `prize_awarded` 都在同一把任务行锁内计算；`completion_rank` 只增不复用；重算写成幂等的不变量投影          |
| 到期冻结后管理员误以为还能驳回     | 到期后驳回返回 409，界面禁用入口并写明原因；奖金持有者在到期时冻结                                               |
| 与 `V14` 的顺序耦合                | `V14`（结算，全站可独立上线）先于 `V15`（悬赏）；悬赏代码不依赖结算之外的任何新字段，结算代码完全不依赖悬赏字段  |
| `TaskService` 已 1272 行，继续膨胀 | 接取、名次、奖金这条与普通任务完全不同的链路放进新的 `BountyService`；只有提交与视图复用 `TaskService` 分支      |
| 误记线下发放不可系统撤销           | 管理端登记前二次确认；发放/领取记录不提供反向入口，误登记须人工审计                                              |

## 18. 线下奖金发放/领取台账实施记录

用户于 2026-09-26 确认加入逐人履约台账；以下流程已成为需求清单 C14—C17、D9、E4、F7、G8、H4 的正式口径。

### 端到端流程

1. 当前奖金持有者生成一条 `PENDING` 履约记录；每位获奖者独立跟踪。
2. 管理员线下实际发出奖金后，在悬赏名单页登记“已发放”，保存操作人和时间；允许任务到期/结束后补录。
3. 成员在自己的任务页确认“已领取”，保存本人档案和时间；仅允许本人对已发放记录确认。
4. 尚未发放的奖金资格被驳回时，旧履约记录转 `REVOKED`、保留历史，奖金照常顺延；已登记发放或已领取的对象驳回返回 409，防止系统状态与无法收回的线下奖励冲突。
5. 履约状态不改变奖金资格、完成名次或积分结算。管理员误登记后的纠错暂不提供反向按钮，避免将“线下已交付”误记为未发放。

### 实施与验证口径

- 新增 `V16__bounty_prize_fulfillment.sql`，创建 `bounty_prize_fulfillments`，以 `task_assignment_id` 唯一关联一个悬赏对象；保存 `PENDING / ISSUED / RECEIVED / REVOKED` 与各状态的时间、操作人和撤销理由。不得改动 `V11`—`V15`。
- `BountyService.recomputePrizeHolders` 在资格产生/撤销时以任务行锁同步履约状态；标记发放与驳回共用同一锁，领取动作以对象记录锁避免并发重复确认。
- 管理端新增“登记已发放”操作与逐人状态；成员端获奖对象显示履约进度并提供本人确认入口。通知管理端发放后请成员确认，成员确认后通知创建者。
- API 见第 9.2 节。误登记后的审计纠错不提供系统反向按钮；发放后通知成员确认，领取后通知任务创建者。
- 自动化测试覆盖单向状态机、发放前驳回并留痕、发放/领取后拒绝驳回、越权与重复请求、到期后仍可登记、积分/名次不变；V16 上线前仍须预生产 MySQL 幂等演练与双角色浏览器验收。

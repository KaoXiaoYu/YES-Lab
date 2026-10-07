# YES Lab 任务模块设计

> 2026-10-02 普通任务按人补发改造见第 18 节，2026-10-02 已批准并实施。

> 本文是任务模块的实现前设计，结合现有后端分层、权限模型、积分规则、招新状态机、富文本安全边界和前端路由约定编写。文中所有字段、接口与文件路径均对应仓库当前代码，未引入新的框架或依赖。

> ⚠️ **部分条款已被后续的「任务到期结算与截止规则」改造推翻**，阅读时请以
> [`docs/task-settlement-design.md`](task-settlement-design.md) 与
> [`docs/task-settlement-requirements.md`](task-settlement-requirements.md) 为准：
>
> | 本文条款                                                            | 新口径                                                                       |
> | ------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
> | 第 7 节「审核通过即自动发放积分」                                   | 积分改为**到期结算**，审核通过只改状态                                       |
> | 第 9 节「逾期只是显示层派生，不构成任何自动结论」                   | 到期是**硬边界**：冻结成员提交与管理层结论，并触发结算                       |
> | 第 4.8 节「新手任务到期后不自动处理，只显示已逾期，管理员仍可审核」 | 新手任务到期后**不能审核与驳回**，需先按人延长 `due_date` 或打回技能测试阶段 |
> | 第 4.8 节「`CLOSED` 后成员只读、管理员仍可审核」                    | 到期（含 `CLOSED`）后**管理员也不能审核或驳回**，只能延长                    |
> | 第 4.8 节及结算文档「截止后不能审核新手任务」                       | 新手任务截止后仍可审核已提交对象；驳回后本人有 24 小时补交                   |
>
> 其余条款保持有效。悬赏任务（第三类任务）见 [`docs/bounty-task-design.md`](bounty-task-design.md)。

## 1. 需求与已确认边界

| 需求                       | 设计结论                                                                                              |
| -------------------------- | ----------------------------------------------------------------------------------------------------- |
| 面向不同等级的用户发放任务 | 复用现有四个字段组合筛选：角色、成员状态、年级、能力标签；同时支持直接指定具体成员                    |
| 任务文本内容可以很多       | 任务正文使用 `LONGTEXT` 富文本，前端 Tiptap 编辑，后端 OWASP 白名单清洗                               |
| 任务可能有多个子任务       | 单层子任务列表，每个子任务由对象单独勾选完成                                                          |
| 子任务不需要截止日期       | 子任务只有标题与顺序；**仅任务主体**设置起止日期                                                      |
| 能够查看完成情况           | 管理员查看任务级汇总 + 子任务完成率 + 逐人明细                                                        |
| 新手任务从面试通过后发放   | 面试通过（即流转到技能测试阶段）时，把该报名者分配到大任务                                            |
| 新手任务是大任务           | **全实验室唯一一条新手任务大任务**，所有技能测试阶段报名者共享；管理员在大任务里添加子任务            |
| 管理员设定时长且可改       | 大任务包含时长（天），每个对象的截止日期 = **本人分配当天 + 时长**；改时长后按各自发放日重算          |
| 新手任务内容可改           | 大任务内容与子任务由管理员直接维护，**保存后即时对所有在途对象生效**（无模板与同步开关）              |
| 完成全部子任务才能转正     | 报名者必须勾选**全部**子任务才能提交；管理员审核通过即转正                                            |
| 通过技能测试就直接转为成员 | 新手任务审核通过 = 直接转为正式成员；**试用期阶段从流程中删除**                                       |
| 多余的角色设计去掉         | **成员状态停用 `CANDIDATE` / `PAUSED` / `EXITED`**，只保留「试用 / 正式」可选；枚举与存量数据保留读取 |
| 豁免保留                   | 保留「豁免并转正」，需填写理由并写入状态历史                                                          |
| 新手任务不发积分           | 只作转正门槛，积分固定为 0                                                                            |
| 普通任务发布时绑定积分     | 积分在**发布时锁定**，发布后不可修改                                                                  |
| 审核通过即发放积分         | 普通任务由管理员人工确认通过时，在同一事务内自动发放积分；驳回不发分                                  |
| 不加入自动判断模块         | 是否完成由对象提交、管理员人工判断；不做自动判定、不做定时提醒、不做到期自动处理                      |

## 2. 关键结构事实

设计前已核实的四条代码事实，它们直接决定了架构：

1. **技能测试阶段的人还没有成员档案。** `MemberProfileEntity` 只在 `RecruitmentService.convertToMember`（第 438 行）创建。技能测试阶段的申请人是 `VISITOR` 账号 + `RecruitmentApplicationEntity`，**没有 `member_profile_id`**。
   → 新手任务必须挂在**招新报名记录**上，不能挂在成员档案上。

   注意：这里的 `VISITOR` 只是账号角色，与「刚注册的游客」不是一回事——技能测试阶段是面试通过后的笔试阶段，账号角色尚未变更而已。

2. **现有招新状态机不允许从技能测试直接转正。** `ALLOWED_TRANSITIONS`（第 645 行）为 `SKILL_TEST → {PROBATION, REJECTED}`、`PROBATION → {FORMAL_MEMBER, REJECTED}`。
   → 实现「通过技能测试直接转正」必须扩展状态机。

3. **转正需要管理员补两项必填数据。** `ConvertMemberRequest` 要求 `memberCode`（学号/内部编号，全局唯一）与 `skillTags`（至少一个）。姓名、专业、班级、年级、联系方式从报名记录继承。
   → 新手任务的**审核表单必须同时收集这两项**，否则无法建档案。

4. **`PROBATION` 被两处数据库 ENUM 列和全部历史状态记录引用。** `V1__baseline_schema.sql` 第 215 行 `recruitment_applications.stage`、第 252—253 行 `recruitment_status_history.from_stage/to_stage` 都是含 `PROBATION` 的 MySQL ENUM；历史状态行也存有该值。该表名为**单数** `recruitment_status_history`（实体 `@Table(name = "recruitment_status_history")`），且 `application_id` 与 `operator_account_id` **均无外键约束**，`operator_username` 为冗余存储（渲染不依赖关联 `accounts`）——这三点决定了回退脚本的写法。
   → **不能直接删除枚举值**，详见第 6 节。

## 3. 两类任务

任务模块承载两类目标完全不同的任务，共用富文本、子任务、完成情况与审核这套能力：

|                | 新手任务 `ONBOARDING`                              | 普通任务 `STANDARD`                   |
| -------------- | -------------------------------------------------- | ------------------------------------- |
| 发放对象       | 招新报名记录（`recruitment_applications`）         | 成员档案（`member_profiles`）         |
| 发放时机       | 面试通过（流转到技能测试阶段）时在大任务上建立对象 | 管理员按等级条件批量发放和/或指定个人 |
| 任务主体       | **全实验室唯一一条**，所有技能测试报名者共享       | 每次创建一条独立任务                  |
| 时长           | 每人的截止日期 = 本人分配当天 + 大任务时长（天）   | 管理员直接填写任务级起止日期          |
| 积分           | 固定 0，不发放                                     | 发布时绑定并锁定，审核通过后发放      |
| 审核通过的结果 | **直接转为正式成员**                               | 记录完成并自动发放积分                |
| 等级条件       | 不适用                                             | 角色 / 成员状态 / 年级 / 能力标签     |
| 查看入口       | 报名者：我的报名页；管理员：招新管理 + 任务管理    | 成员：我的任务；管理员：任务管理      |

**为什么把新手任务并进任务模块而不是单独做一套：** 它需要富文本说明、子任务清单、逐项勾选、管理员审核和完成情况查看——与普通任务完全同构。差别只在「对象是谁」「怎么产生」「通过后发生什么」，因此建模为同一模块的两种类型，而不是两套重复代码。

### 3.1 等级维度与现有字段

「等级」不新增字段，复用现有四个维度组合筛选：

| 等级维度                 | 现有存储                 | 可选值                                                                     |
| ------------------------ | ------------------------ | -------------------------------------------------------------------------- |
| `ROLE` 角色              | `accounts.role`          | `TEACHER`、`CORE_STUDENT`、`MEMBER`                                        |
| `MEMBER_STATUS` 成员状态 | `member_profiles.status` | `TRIAL`、`OFFICIAL`（`CANDIDATE` / `PAUSED` / `EXITED` 已停用，见 6.5 节） |
| `GRADE` 年级             | `member_profiles.grade`  | 自由文本，按精确值匹配（如 `24级`、`大二`）                                |
| `SKILL_TAG` 能力标签     | `member_skill_tags.tag`  | 自由文本，按精确值匹配                                                     |

**约束：`VISITOR` 无法作为任务对象。** 游客账号没有成员档案，既没有年级也没有能力标签，因此 `ROLE` 维度只提供 `TEACHER`、`CORE_STUDENT`、`MEMBER` 三个取值。新手任务是游客唯一会收到的任务类型。

## 4. 数据模型

新增 5 张表（`tasks`、`task_subtasks`、`task_audience_rules`、`task_assignments`、`task_subtask_progress`），由 Flyway `V11__task_module.sql` 创建，使用与 `V8__points_ledger.sql` 相同的 MySQL 8.4 语法约定（`BINARY(16)` 主键、`ENGINE=InnoDB`、`utf8mb4_0900_ai_ci`）。试用期批量回退单独放在 `V12__retire_probation_stage.sql`（见 6.3 节），把建表与数据修正分成两支迁移，便于分别演练与回滚判断。测试环境沿用 H2 `create-drop`，实体映射需同时兼容两种数据库。

### 4.1 `tasks` 任务主体

| 字段                        | 类型                  | 说明                                                                      |
| --------------------------- | --------------------- | ------------------------------------------------------------------------- |
| `id`                        | BINARY(16) PK         |                                                                           |
| `task_type`                 | ENUM NOT NULL         | `ONBOARDING` / `STANDARD`                                                 |
| `title`                     | VARCHAR(160) NOT NULL | 任务标题                                                                  |
| `content_html`              | LONGTEXT NOT NULL     | 富文本正文，落库前白名单清洗                                              |
| `start_date`                | DATE NULL             | 开始日期                                                                  |
| `end_date`                  | DATE NULL             | 截止日期                                                                  |
| `points`                    | INT NOT NULL          | 每个通过对象自动发放的积分；`ONBOARDING` 固定 0，`STANDARD` 范围 0—100000 |
| `status`                    | ENUM NOT NULL         | `DRAFT` / `PUBLISHED` / `CLOSED`；新手任务大任务固定 `PUBLISHED`          |
| `duration_days`             | INT NULL              | 新手任务大任务的时长（天，1—365）；普通任务为空                           |
| `created_by_account_id`     | BINARY(16) NOT NULL   | 创建账号，不可更新                                                        |
| `created_at` / `updated_at` | DATETIME(6) NOT NULL  |                                                                           |
| `published_at`              | DATETIME(6) NULL      | 发布时间                                                                  |

积分是**任务级统一分值**，对每个通过审核的对象各发一份；子任务不单独计分，不支持按对象设置不同分值。新手任务的 `points` 恒为 0。

### 4.2 `task_subtasks` 子任务定义

| 字段            | 类型                  | 说明                         |
| --------------- | --------------------- | ---------------------------- |
| `id`            | BINARY(16) PK         |                              |
| `task_id`       | BINARY(16) NOT NULL   | 外键 → `tasks`，级联删除     |
| `title`         | VARCHAR(200) NOT NULL | 子任务标题                   |
| `content_html`  | LONGTEXT NULL         | 子任务说明（富文本，可为空） |
| `display_order` | INT NOT NULL          | 展示顺序                     |

子任务定义属于任务本身，对该任务的全部对象共享。**子任务不单独指派负责人、不设置截止日期、不单独计分、也不单独提交与单独审核。**

`content_html` 与任务正文走同一条 OWASP 白名单（`TaskContentSanitizer`），编辑器上限 5000 字符；允许为空——纯清单项（只有标题）是合法形态，为空时不渲染空白正文块。

### 4.3 `task_audience_rules` 等级发放条件

仅用于普通任务。

| 字段         | 类型                  | 说明                                                     |
| ------------ | --------------------- | -------------------------------------------------------- |
| `id`         | BINARY(16) PK         |                                                          |
| `task_id`    | BINARY(16) NOT NULL   | 外键 → `tasks`，级联删除                                 |
| `dimension`  | ENUM NOT NULL         | `ROLE` / `MEMBER_STATUS` / `GRADE` / `SKILL_TAG`         |
| `rule_value` | VARCHAR(120) NOT NULL | 该维度的一个取值（列名不用 `value`，因为它是 H2 保留字） |

条件语义：**同一维度内多个取值取「或」，不同维度之间取「且」**。

### 4.4 `task_assignments` 发放对象与完成情况

| 字段                         | 类型                 | 说明                                              |
| ---------------------------- | -------------------- | ------------------------------------------------- |
| `id`                         | BINARY(16) PK        |                                                   |
| `task_id`                    | BINARY(16) NOT NULL  | 外键 → `tasks`                                    |
| `member_profile_id`          | BINARY(16) NULL      | 外键 → `member_profiles`，普通任务使用            |
| `recruitment_application_id` | BINARY(16) NULL      | 外键 → `recruitment_applications`，新手任务使用   |
| `source`                     | ENUM NOT NULL        | `CRITERIA` / `MANUAL` / `ONBOARDING`              |
| `status`                     | ENUM NOT NULL        | `PENDING` / `SUBMITTED` / `APPROVED` / `REJECTED` |
| `due_date`                   | DATE NULL            | 新手任务：本人截止日期 = 分配当天 + 大任务时长    |
| `completion_note`            | LONGTEXT NULL        | 对象提交的完成说明                                |
| `submitted_at`               | DATETIME(6) NULL     |                                                   |
| `reviewed_by_account_id`     | BINARY(16) NULL      | 人工审核账号                                      |
| `reviewed_at`                | DATETIME(6) NULL     |                                                   |
| `review_comment`             | VARCHAR(1000) NULL   | 审核意见                                          |
| `exemption_reason`           | VARCHAR(500) NULL    | 豁免并转正的理由                                  |
| `converted_profile_id`       | BINARY(16) NULL      | 新手任务通过后生成的成员档案，用于追溯转正结果    |
| `point_grant_id`             | BINARY(16) NULL      | 外键 → `point_grants`，本次自动发放的积分批次     |
| `awarded_points`             | INT NULL             | 实际计入积分；未计分时为 `NULL`                   |
| `points_skipped_reason`      | VARCHAR(200) NULL    | 未自动计分的原因                                  |
| `created_at` / `updated_at`  | DATETIME(6) NOT NULL |                                                   |

双目标设计：`member_profile_id` 与 `recruitment_application_id` **恰好一个非空**，用数据库 `CHECK` 约束保证。唯一约束 `(task_id, member_profile_id)` 与 `(task_id, recruitment_application_id)` 分别去重；MySQL 唯一索引允许多个 `NULL`，两套约束可共存。

### 4.5 `task_subtask_progress` 子任务勾选

| 字段            | 类型                | 说明                                                     |
| --------------- | ------------------- | -------------------------------------------------------- |
| `id`            | BINARY(16) PK       |                                                          |
| `assignment_id` | BINARY(16) NOT NULL | 外键 → `task_assignments`，级联删除                      |
| `subtask_id`    | BINARY(16) NOT NULL | 外键 → `task_subtasks`，级联删除                         |
| `completed`     | BOOLEAN NOT NULL    | 是否**已提交内容**（列名保留 completed，避免动历史数据） |
| `content_html`  | LONGTEXT NULL       | 成员为该子任务提交的富文本内容（`V13` 新增）             |
| `completed_at`  | DATETIME(6) NULL    | 提交时间                                                 |

唯一约束 `(assignment_id, subtask_id)`，同时支撑「我的进度 x/y」与「某个子任务的全员提交率」。

**子任务不是勾选完成，而是提交一段富文本内容**：`completed` 表示「已提交」，`content_html` 是提交的内容；大任务被通过前可以修改并重新提交（同一行覆盖）。

### 4.6 新手任务大任务（单例约定）

**不存在「模板」表**：新手任务本身就是 `tasks` 里唯一一条 `task_type = ONBOARDING` 的大任务，
于是它与普通任务共用完全相同的表、实体与服务能力（标题、富文本正文、子任务、时长）。

- 由 `OnboardingTaskService.requireOrCreate` 保证单例：不存在时用**代码内置默认内容**（标题、说明、5 项子任务、7 天）落库，内置内容只维护这一份；
- 大任务创建即 `PUBLISHED`，`points = 0`，不允许删除，也不参与普通任务的列表与筛选；
- 管理员在「任务管理 → 新手任务」直接编辑它：标题、说明、`duration_days`、子任务清单（逐条添加 / 删除 / 排序，最多 50 项，标题去重）。

### 4.7 对象侧：截止日期、进度与即时生效

- **每个报名者在大任务上有一条对象**（`task_assignments`，`recruitment_application_id` 非空，`source = ONBOARDING`），唯一约束 `(task_id, recruitment_application_id)` 保证同一报名记录不会重复分配；
- 对象的 `due_date = 分配当天 + 大任务当时的 duration_days`，因此**同一大任务下每个人的截止日期不同**，按各自的发放日计算；
- 进度记录在 `task_subtask_progress(assignment_id, subtask_id)`，天然按人隔离；
- 管理员保存大任务后即时生效：
  - **同步按子任务 `id` 匹配**（不再按标题）：提交里带 `id` 的更新标题与正文并保留该子任务的勾选记录；`id` 为空的视为新增；未出现在提交里的 `id` 视为删除。这样**改标题不会丢勾选、也不会误删正文**；
  - **新增子任务** → 所有 `SUBMITTED`（已提交待确认）的对象退回 `PENDING` 并发送站内消息，需勾选新项后重新提交；`APPROVED` 对象不动；
  - **删除子任务** → 先删除对应的勾选记录再删除子任务（外键顺序）；
  - **修改时长** → 尚未通过的对象按「本人分配当天 + 新时长」重算 `due_date` 并发消息；
  - 标题与正文对所有对象即时可见（同一条任务记录）。

### 4.8 状态机

普通任务：

```text
DRAFT 草稿 ──发布（锁定积分）──> PUBLISHED 已发布 ──结束──> CLOSED 已结束
   │                                 │                        │
   └ 可删除、成员不可见                └ 条件、对象、积分锁定     └ 成员不可再勾选/提交，管理员仍可审核
```

新手任务：大任务创建即 `PUBLISHED`；每个报名者一条对象，状态流转与普通任务相同，但不设 `CLOSED` 收口。

对象状态（人工，无自动判断）：

```text
PENDING 待完成 ──对象提交完成说明──> SUBMITTED 待确认
                                        ├──管理员通过（普通任务：发放积分）──> APPROVED
                                        │  （新手任务：直接转正）
                                        └──管理员驳回（必填意见，不发分、不转正）──> REJECTED
                                                                                    └──修改后重新提交──> SUBMITTED
```

- 普通任务：对象必须在任务 `PUBLISHED` 期间才能勾选子任务和提交说明；`CLOSED` 后对象端只读。
- `SUBMITTED` 允许对象再次修改说明并重新提交（覆盖并更新 `submitted_at`）。
- 驳回必须填写审核意见；通过时审核意见可选。
- **只有从「待确认」进入「已通过」的这一次操作会触发计分或转正**；重复调用审核接口是幂等的。
- 新手任务：对象必须**为全部子任务提交内容**才能提交（后端拒绝并提示 x / y）；管理员审核通过前也会再校验一次，豁免路径除外。
- **新手任务到期后不自动处理**：超过 `due_date` 只在界面标记「已逾期」，不自动打回、不自动拒绝、不发提醒；由管理员手动调整时长或打回。

### 4.9 快照与同步语义

普通任务发布时按条件实时查询命中成员，一次性写入 `task_assignments`，**之后不再随成员等级变化自动增删**：成员等级变化不应重写历史发放范围，完成情况、审核留痕与积分发放必须对应稳定的对象集合。发布后才满足条件的新成员由管理员执行「补充发放」，按条件求差集后追加。

新手任务不做快照：**所有对象共享同一条大任务**，管理员修改大任务内容与子任务时对全部在途对象即时生效（见 4.7 节）。这与普通任务的「发布即快照」形成分工——普通任务的发放范围必须稳定可审计，新手任务的门槛内容则可以随时收敛。

### 4.10 子任务正文、独立页面与懒加载

子任务是**独立可打开的任务条目**：大任务页只给入口，正文与勾选在子任务页完成。

- **数据**：`task_subtasks.content_html`（可为空）。
- **页面层级**：
  - 任务列表 = 若干**大任务卡片**（标题、简介摘要、进度条 `x / y`、截止日期、状态）；
  - 大任务页 = 富文本介绍 + 完成进度 + **子任务入口列表**（标题、是否已完成、是否有正文）+ 完成说明提交 / 审核结果；
  - 子任务页 = 该子任务的富文本正文 + 完成勾选 + 返回大任务（并显示大任务标题、总进度与截止日期）。
- **懒加载约定**：正文只在详情接口返回，列表与进度接口只返回 `hasContent` 布尔值。这样「我的任务」列表、`{taskId}/progress`、以及管理端大任务读取都不会把 50 份正文一起吐出来。
- **接口**（均为详情接口，越权一律 404）：

  | 方法 | 路径                                                          | 说明                                                                                      |
  | ---- | ------------------------------------------------------------- | ----------------------------------------------------------------------------------------- |
  | GET  | `/api/v1/tasks/{assignmentId}/subtasks/{subtaskId}`           | 成员读取某个子任务：标题、正文、完成状态、大任务上下文（标题 / 进度 / 截止 / 是否可编辑） |
  | GET  | `/api/v1/recruitment/me/onboarding-task/subtasks/{subtaskId}` | 报名者读取本人的某个子任务，沿用 `RECRUITMENT_SELF_VIEW`                                  |
  | GET  | `/api/v1/admin/tasks/{taskId}/subtasks/{subtaskId}`           | 管理端编辑前读取子任务正文（`TASK_MANAGE`）                                               |

- **写入形状**：任务保存请求里的 `subtasks` 由 `List<String>` 改为 `List<SubtaskInput>`，每项 `{ id?, title, contentHtml? }`——`id` 为空表示新增，缺失的 `id` 表示删除。
- **不能让懒加载变成丢数据**：管理端保存时，对「已存在、有正文、但这次没展开」的子任务，前端**先按需拉取正文再提交**，失败则中止保存并报错；否则 `contentHtml` 会以空值提交，把管理员上次写的说明写掉。这条已在真机点击验收中实测（改标题但不展开 → 保存后正文仍在）。
- **提交**：`POST .../subtasks/{subtaskId}/submission`（成员端与报名者端各一），内容必填、走白名单清洗、上限 5000 字符；报名者侧在大任务「已提交待确认」时改动内容会退回「待完成」（既有规则不变）。
- **不单独审核**：子任务没有 `SUBMITTED / APPROVED / REJECTED`，只有「已勾选 / 未勾选」。审核始终在大任务层做一次，避免出现两层结论互相矛盾。
- **子任务数量下限两类不同**：普通任务**允许 0 项**（纯提交型任务：只要成员交一份完成说明），此时成员端不渲染子任务标题、入口列表与进度条，改为一句「本任务没有子任务，直接填写完成说明提交即可。」；新手任务**强制至少 1 项**（`@NotEmpty` + 服务层校验），因为转正门槛 `hasCompletedAllSubtasks()` 要求 `total > 0`，0 项会让报名者永远无法提交、也就永远无法转正。

## 5. 新手任务

### 5.1 大任务与内置兜底

管理员在「任务管理 → 新手任务」维护**唯一那条大任务**的标题、富文本说明、子任务清单与时长（天，默认 7）。
大任务不存在时（例如功能刚上线、还没有人进入技能测试阶段），`OnboardingTaskService.requireOrCreate` 用**代码内置的默认内容**自动初始化后落库，保证不会因为没配置而卡住招新流程；内置默认内容只维护一份。

### 5.2 面试通过后自动分配

管理员把报名推进到技能测试阶段时（`RecruitmentService.changeStage` 中 `target == SKILL_TEST` 分支），在同一事务内：

1. 读取或初始化那条共享大任务；
2. 若该报名记录在大任务上**已有对象**则直接返回（幂等，`(task_id, recruitment_application_id)` 唯一约束兜底）；
3. 在该大任务上创建对象：`source = ONBOARDING`，状态 `PENDING`，`due_date = 分配当天 + duration_days`；
4. 向报名者发送梅琳娜消息「新手任务已发放」，说明截止日期。

面试通过即流转到技能测试阶段（含候补观察改判通过、场次结束后补录通过），全部走同一分支，因此三种入口都能自动分配；「批量补发」用同一个幂等入口遍历技能测试阶段且在大任务上还没有对象的记录。

### 5.3 报名者侧

报名者账号角色仍是 `VISITOR`，访问「我的报名」页。新手任务在该页以卡片展示：大任务标题、富文本说明、**自己的进度（已完成 x / y 项 + 进度条）**、`due_date` 与剩余天数、子任务勾选、完成说明提交、驳回意见、当前状态。接口放在招新路径下，复用 `RECRUITMENT_SELF_VIEW` / `RECRUITMENT_SELF_EDIT`，与现有报名页鉴权一致。

新手任务下的子任务同样支持正文与独立页面（见 4.10 节）：报名者在大任务卡片里点子任务进入子任务页看正文、勾选完成，返回后继续。

**提交前置条件**：`hasCompletedAllSubtasks()` 为真。未勾完时前端禁用提交按钮并提示还差几项，后端同样拒绝（`请先完成全部子任务（已完成 x / y 项）`）；在「待确认」状态下改动勾选内容会退回「待完成」，避免用旧提交通过新内容。

### 5.4 修改大任务与即时生效

**不再有「同步在途任务」开关**——大任务只有一条，改动天然对所有对象生效，规则见 4.7 节：新增子任务把已提交的对象退回「待完成」并发消息、删除子任务连同勾选记录一起删除、改时长按各自发放日重算截止日期。

### 5.5 审核通过即转正

管理员在招新管理或任务管理中审核新手任务。**通过**时，审核表单在审核意见之外还要求填写 `memberCode`（学号/内部编号）与 `skillTags`（至少一个能力标签）——这是 `MemberProfileEntity` 的必填字段，无法从报名记录继承。

通过操作前先校验「子任务已全部勾选」（填了豁免理由的路径除外），随后在同一事务内：

1. 复用现有建档案逻辑创建 `MemberProfileEntity`（`MemberStatus.OFFICIAL`），姓名、专业、班级、年级、联系方式从报名记录继承；
2. 账号角色 `VISITOR → MEMBER`；
3. 报名记录标记 `markConverted(profileId)`；
4. 招新阶段写入 `FORMAL_MEMBER`，历史备注「新手任务审核通过，通过技能测试直接转为正式成员」；
5. 对象状态置 `APPROVED`，写入 `converted_profile_id`；
6. 发送梅琳娜消息「新手任务已通过，你已成为正式成员」。

实现上把 `convertToMember` 的建档案与状态流转核心抽成共用私有方法，**所有转正路径复用同一段代码**，不复制粘贴。

### 5.6 转正入口与守卫

转正入口统一为「转为正式成员」，可从招新管理或新手任务审核发起。守卫规则：

- 该报名记录**存在**新手任务 → 必须已通过，否则拒绝并提示「请先完成并通过新手任务」，同时提供两个后续动作：
  - **打回技能测试阶段**（推荐，见 5.7）；
  - **豁免并转正**：需填写理由，写入 `exemption_reason` 与状态历史，保留豁免能力。
- 该报名记录**不存在**新手任务 → 这种情况只会出现在本功能上线前的历史记录上，处理方式见第 6 节。

### 5.7 打回技能测试阶段

`PROBATION → SKILL_TEST` 作为**通用管理动作**保留，任何仍停留在试用期阶段的记录都可以打回（正常情况下不会再产生这类记录，因为 `V12` 脚本已经批量回退）。

打回时：

- 若该记录在大任务上已有未通过的对象 → 保留勾选进度，按当前时长重置截止日期（`due_date = 打回当天 + duration_days`）；
- 若还没有对象 → 在大任务上补建一条对象；
- 写入状态历史，记录操作人与打回原因。

## 6. 阶段与状态精简

按确认结论做两项精简：

1. **试用期阶段从招新流程中删除**：通过技能测试（新手任务）直接转为正式成员；
2. **成员状态停用 `CANDIDATE` / `PAUSED` / `EXITED`**：这三个值零业务引用，只保留给存量数据读取。

两项采用同一套处理原则：**行为上删除，枚举上保留仅供读取**，存量数据不迁移、行为零变化。

### 6.1 为什么不能直接删枚举值

这些值都被数据库 ENUM 列与存量数据引用，直接删除会破坏现有数据：

- **`RecruitmentStage.PROBATION`**
  - `V1__baseline_schema.sql` 第 215 行：`recruitment_applications.stage` 的 MySQL ENUM 取值之一；
  - `V1__baseline_schema.sql` 第 252—253 行：`recruitment_status_history.from_stage` / `to_stage` 的 ENUM 取值之一；
  - 历史状态记录中**已经存有**该值。
- **`MemberStatus.CANDIDATE` / `PAUSED` / `EXITED`**
  - `V1__baseline_schema.sql` 第 28 行：`member_profiles.status` 是含全部 5 个取值的 MySQL ENUM；
  - 生产库中可能已有成员处于这些状态。

删除 Java 枚举值会让含该值的行**反序列化失败**，而修改 MySQL ENUM 需要先搬移数据、且没有安全的默认目标值。

### 6.2 试用期的处理方式

1. `RecruitmentStage.PROBATION` 枚举值**保留**，仅为历史记录反序列化兼容，并在代码注释与文档中标注为「已停用，仅兼容历史」。
2. `ALLOWED_TRANSITIONS` 调整：

   | 原                                      | 新                                       |
   | --------------------------------------- | ---------------------------------------- |
   | `SKILL_TEST → {PROBATION, REJECTED}`    | `SKILL_TEST → {FORMAL_MEMBER, REJECTED}` |
   | `PROBATION → {FORMAL_MEMBER, REJECTED}` | `PROBATION → {SKILL_TEST, REJECTED}`     |

   即：**没有任何路径能再进入试用期**，试用期只能被离开；`PROBATION → SKILL_TEST` 保留为回退通道。

3. `convertToMember` 的前置条件由 `stage == PROBATION` 改为「技能测试阶段 + 转正守卫（5.6）」。
4. 前端移除试用期：`RecruitmentView.vue` 的阶段数组与标签、`AdminRecruitmentView.vue` 的 `nextStages` 与试用期转换卡片、`AuthView.vue` 的「试用期与正式成员」文案。
5. 仍停留在试用期的历史记录：由 **Flyway 数据迁移脚本批量回退到技能测试阶段**，不逐条手动处理，也不删除任何账号或报名记录，详见 6.3 节。

### 6.3 试用期批量回退脚本

新增 `backend/src/main/resources/db/migration/V12__retire_probation_stage.sql`。**只做确定性的阶段数据修正，不改账号、不删报名、不动成员档案**：

```sql
-- 1) 记录待回退的报名记录
CREATE TEMPORARY TABLE tmp_probation_rollback (
    application_id BINARY(16) NOT NULL PRIMARY KEY
) ENGINE=InnoDB;

INSERT INTO tmp_probation_rollback (application_id)
SELECT id FROM recruitment_applications WHERE stage = 'PROBATION';

-- 2) 先写状态历史，保留可审计的回退依据
INSERT INTO recruitment_status_history
    (id, application_id, from_stage, to_stage, operator_account_id, operator_username, note, changed_at)
SELECT UUID_TO_BIN(UUID()), application_id, 'PROBATION', 'SKILL_TEST',
       UUID_TO_BIN('00000000-0000-0000-0000-000000000000'), 'system',
       '试用期阶段已取消，系统批量回退至技能测试阶段', NOW(6)
FROM tmp_probation_rollback;

-- 3) 执行阶段回退
UPDATE recruitment_applications SET stage = 'SKILL_TEST' WHERE stage = 'PROBATION';

DROP TEMPORARY TABLE tmp_probation_rollback;
```

脚本要点与依据：

- **操作人用系统保留值**：`operator_account_id` 取全零 UUID、`operator_username` 取 `system`。该表的 `operator_account_id` **没有外键约束**，且 `operator_username` 是冗余存储的显示字段，因此无需也无须把这次批量操作挂到某位管理员名下——归属于个人反而不符合事实。
- **不改 ENUM 定义**：脚本只更新行值，`PROBATION` 继续作为合法枚举值存在，历史记录仍可正常反序列化。
- **保留 `changed_at` 与备注**：让回退在招新详情的状态历史里可见，管理员能看到「什么时候、因为什么」被退回技能测试阶段。
- **幂等**：`WHERE stage = 'PROBATION'` 只匹配当前仍在试用期的记录；Flyway 只会执行一次，脚本在预生产重复演练也安全。

**脚本不做新手任务补发**，理由是：新手任务正文、子任务清单与时长属于业务配置，若写进 SQL 就会与 Java 中的内置默认内容形成两份富文本，必然漂移。因此：

- 阶段回退由 `V12` 脚本完成；
- **新手任务补发由应用层完成**：新增管理端幂等操作「批量补发新手任务」，覆盖两类记录——刚被脚本回退到技能测试阶段的记录，以及功能上线前就已处于技能测试阶段、从未获得新手任务的记录；
- 该操作放在「任务管理 → 新手任务」页，是**上线检查清单中的必做一步**（见 6.6 节）。

若希望脚本连新手任务一起生成，需要接受在 SQL 里维护一份富文本默认内容；当前设计不采用。

### 6.4 需要同步改动的文件

| 文件                                                                                       | 改动                                                                     |
| ------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------ |
| `recruitment/model/RecruitmentStage.java`                                                  | `PROBATION` 标注为已停用、仅兼容历史                                     |
| `recruitment/service/RecruitmentService.java`                                              | 转移表、转正前置条件、转正守卫、新手任务发放、共用转正核心方法           |
| `task/service/OnboardingTaskService.java`、`task/service/OnboardingTaskIssuerService.java` | 共享大任务的读取/保存/兜底、即时生效规则、按人分配对象与截止日期         |
| `src/views/AdminOnboardingTaskView.vue`、`src/components/TaskSubtaskEditor.vue`            | 大任务与子任务的维护界面；子任务编辑器与普通任务共用                     |
| `src/views/RecruitmentView.vue`                                                            | 阶段数组与标签去掉试用期                                                 |
| `src/views/AdminRecruitmentView.vue`                                                       | 标签、`nextStages`、试用期转换卡片改为技能测试转正                       |
| `src/views/AuthView.vue`                                                                   | 流程文案                                                                 |
| `backend/src/test/java/cn/yeslab/platform/identity/IdentityRecruitmentApiTests.java`       | 第 125 行改为从技能测试转正，并补充新手任务相关用例                      |
| `backend/docs/access-control.md`                                                           | 招新状态机图、权限矩阵、转正与豁免规则、试用期回退说明、成员状态取值范围 |
| `backend/docs/module-boundaries.md`、`README.md`                                           | 招新流程说明与上线后补发步骤                                             |

### 6.5 成员状态精简

**停用 `CANDIDATE`（候选）、`PAUSED`（暂停）、`EXITED`（退出）三个成员状态。**

核实结论：这三个值**零业务引用**——后端没有任何逻辑判断它们，只出现在：

- `identity/model/MemberStatus.java` 的枚举声明；
- `V1__baseline_schema.sql` 第 28 行 `member_profiles.status` 的 ENUM 定义；
- `src/components/MemberProfileDisplay.vue` 与 `src/views/AdminMembersView.vue` 的 `statusLabels` 标签表。

处理方式：

1. **枚举值保留**，标注为「已停用，仅兼容历史读取」；`V1` 的 ENUM 定义不动，存量数据不迁移，行为零变化。
2. **不再作为可选值**：
   - `AdminMembersView.vue` 把 `statusLabels` 拆成「全量标签（只读展示）」与「可选状态（下拉选项）」两份，下拉只提供 `TRIAL` 与 `OFFICIAL`；
   - `MemberProfileDisplay.vue` 保留全量标签，保证历史成员的详情页仍能正确显示「暂停 / 退出」。
3. **后端加校验**：`MemberProfileService.updateManagedMember` 与创建学生管理员的入口**拒绝**把状态设为这三个值，避免绕过界面直接调接口写入。这是本次新增的校验点，需要对应测试。
4. `MemberStatus` 的 `TRIAL` 与 `OFFICIAL` 保持不变；`AdminHomepageView.vue` 第 87 行按 `['OFFICIAL','TRIAL']` 过滤首页成员的逻辑不受影响。

停用后成员状态只剩两个可选值：**试用**与**正式**。这同时让 7.4 节「非正式成员不能计分」的判定口径更清晰。

### 6.6 上线检查清单（新增）

`V12` 属于**无法被自动化测试覆盖**的迁移：测试环境关闭 Flyway 且使用 H2，而这些迁移是 MySQL 专用语法（`ENUM`、`BINARY(16)`、`UUID_TO_BIN`）。因此必须按以下顺序人工验收：

1. 备份生产 MySQL（沿用现有 `backup.sh`，禁止 `down -v`）；
2. **先在预生产库演练** `V11` + `V12`，核对回退前后的记录数与 `recruitment_status_history` 新增行数是否一致；
3. 生产部署后确认 `SELECT COUNT(*) FROM recruitment_applications WHERE stage = 'PROBATION'` 为 0；
4. 检查若干条记录的招新详情，确认状态历史出现了「系统批量回退至技能测试阶段」；
5. 管理员执行一次「批量补发新手任务」，确认这些记录都获得了新手任务与截止日期；
6. 抽查一条记录完成新手任务并通过审核，确认能正常转为正式成员；
7. 确认成员管理页的状态下拉只剩「试用 / 正式」，且历史处于「候选 / 暂停 / 退出」的成员详情页仍能正常显示。

### 6.7 保留不动

- **`MemberStatus.TRIAL`（成员档案的「试用」状态）保留**。它与招新阶段无关，管理员仍可在成员管理中把成员设为试用；普通任务发给试用成员时「不能计分」的处理（7.4 节）继续适用。
- **`MemberStatus.OFFICIAL`** 保留。
- **`TEACHER` 与 `CORE_STUDENT` 两个角色保留**：权限集合相同，但对应不同人群（指导教师 / 核心学生），前端标签与展示也不同。
- **`QUIZ_MANAGE` / `QUIZ_PARTICIPATE` / `QUESTION_WRITE` / `TAG_MANAGE` / `SYSTEM_ADMIN` 五个权限保留**：它们目前没有 `hasAuthority` 引用，但 `AGENTS.md` 与 `DEVLOG.md` 明确记载「测验、写题及其管理业务仍只保留权限/字段，不实现业务功能」，属于已声明的预留，删除等于放弃该约定。

## 7. 普通任务：发布时绑定积分

### 7.1 发布即锁定

积分值在创建时可填、发布时必须已确定，**发布后不可修改**——对应「发布时就绑定积分」。这保证同一任务的所有对象口径一致，也避免对象中途看到分值变化。已发布任务仍可修改标题、正文、起止日期与子任务。

### 7.2 发放参数

审核通过时由后端生成，前端不传任何积分参数：

| 参数              | 取值                                                                             |
| ----------------- | -------------------------------------------------------------------------------- |
| `subcategory`     | `PROJECT_TASK`（项目任务）                                                       |
| `title`           | `任务：{任务标题}`，超过 160 字符截断                                            |
| `occurredOn`      | 审核当天（`Asia/Shanghai`），满足 `@PastOrPresent`                               |
| `itemTotalPoints` | 任务分值。`PROJECT_TASK` 为 `SHARED_TOTAL`，单人发放时事项总分必须等于该成员得分 |
| `allocations`     | 仅一条：该对象成员 + 任务分值 + 贡献说明                                         |
| `sourceReference` | `TASK:{taskId}:{memberProfileId}`，全局唯一，用于幂等                            |
| `evidenceUrl`     | 管理员审核时可选填的外部凭证链接；未填时使用站内绝对路径 `/tasks/{assignmentId}` |
| `description`     | 审核意见与任务起止日期                                                           |
| `contribution`    | `完成「{任务标题}」并通过人工确认`，超过 500 字符截断                            |
| `operator`        | 当前执行审核的管理员账号                                                         |

`PROJECT_TASK` 的 `monthlyCap` 为 `null`，不存在月度封顶折算差额；即使后续为该子类设置上限，积分模块也会把「应得 / 实际计入」分别写库，任务对象只记录实际计入值。

### 7.3 幂等

`point_grants.source_reference` 有唯一约束。发放前先查询 `TASK:{taskId}:{memberProfileId}`：

- 不存在 → 发放，并把 `point_grant_id`、`awarded_points` 回写对象记录；
- 已存在 → 视为已发放，复用原批次结果，**不重复计分、不返回错误**。

管理员重复点击、网络重试或并发提交都只会产生一条积分记录。

### 7.4 不能计分的三种情况

积分模块 `PointService.validateRecipient` 的三条既有硬约束，任务模块**不修改也不绕过**：

| 情况                                   | 积分模块的既有规则           |
| -------------------------------------- | ---------------------------- |
| 对象是教师                             | 指导教师不参与成员积分统计   |
| 对象不是正式成员（试用、暂停、候选等） | 只能给正式成员发放积分       |
| 执行审核的管理员就是该对象本人         | 积分管理员不能给自己发放积分 |

任务可以发给试用成员，但积分只能发给正式成员，因此第二种情况是真实冲突。

处理方式：**审核照常成功**，任务完成状态正常流转，积分记为「未发放」并把原因写入 `points_skipped_reason`，在完成情况页与成员端明确显示。既不阻塞任务流程，也不放宽积分规则。

**发放前可见性**：条件预览名单对每位命中成员标注计分状态与原因。若任务积分大于 0 而名单中无人可计分，发布前给出明确警告。

### 7.5 更正与撤销

**审核通过为终态，任务模块不提供「撤销审核」**。更正误审由管理员在现有「积分管理」中用反向流水撤销，任务对象保留原发放引用，保持单一撤销路径。

### 7.6 实现方式

在 `PointService` 新增面向任务来源的方法，把来源编号约定、单人分配形状与幂等检查封装在积分模块内部：

```java
@PreAuthorize("hasAuthority('POINTS_MANAGE')")
@Transactional
public TaskGrantResult grantForTask(UUID taskId, UUID memberProfileId, int points,
                                    String taskTitle, LocalDate occurredOn,
                                    String evidenceUrl, String description, String contribution)
```

- 任务模块只传任务、成员、分值与凭证链接，不直接拼装 `PointModels.GrantRequest`；
- 方法自身标注 `@PreAuthorize`：`PointService` 内部自调用不经过 Spring 代理，安全注解必须落在被任务模块调用的这一个方法上；
- 返回「是否实际发放」「实际计入分值」「未发放原因」，供任务模块回写；
- 审核接口与积分发放处于同一事务，积分发放失败时任务状态一并回滚。

## 8. 普通任务发放流程

```text
管理员创建任务（草稿）
   ├─ 填写标题、富文本正文、起止日期、每个通过对象可得的积分
   ├─ 添加若干子任务并排序
   ├─ 选择等级条件（角色 / 成员状态 / 年级 / 能力标签，可多选）
   └─ 可选：直接指定若干成员
        ↓
   预览命中名单（人数 + 姓名/编号/年级/状态/标签 + 是否可计分及原因）
        ↓
   发布 → 锁定积分与对象 + 展开 task_assignments（条件命中 ∪ 手工指定，去重）
        + 向每位对象发送梅琳娜站内消息
        ↓
   成员在「我的任务」勾选子任务、提交完成说明
        ↓
   管理员在「任务完成情况」逐人人工确认
        ├─ 通过 → 同一事务内自动发放任务积分
        └─ 驳回 → 不发分，必填意见，成员可修改后重新提交
```

校验规则：

1. 必须至少有一个等级条件，或至少指定一名成员，否则拒绝发布。
2. `endDate` 不得早于 `startDate`。
3. 子任务标题不得为空，同一任务内标题不得重复，最多 50 条。
4. 富文本清洗后若既无可见文字也无 `<img>`，拒绝保存（沿用 `DiscussionService.cleanContent` 口径）。
5. 条件命中为零且未手工指定成员时，发布返回明确错误。
6. 已发布任务的条件、对象与积分值不可修改。
7. 修改已发布任务的子任务时：新增子任务是安全的；删除子任务会级联删除对应勾选进度，前端必须二次确认并提示影响人数。
8. `points` 必须在 0—100000 之间；大于 0 而命中名单中无人可计分时，发布前警告。

## 9. 完成情况查看

2026-10-05 展示修订对应需求第 10 节：所有任务提交进度复用 `TaskSubmissionProgress.vue`，比例来自现有提交数/总数，未完成灰色、完成绿色，使用 progressbar 语义与数量说明；无子任务按总任务提交状态显示 0%/100%。补发区用原生 details/summary 默认收起且保留内部状态。普通任务审核列表复制接口数组后用 computed 排序，待确认与子任务交齐优先，不改变后端状态和业务流程。

管理员在任务完成情况页看到三个层次：

- **任务汇总**：应完成人数、已通过、待确认、进行中（有勾选但未提交）、未开始、已驳回、已计分人数与已发放积分合计。
- **子任务完成率**：每个子任务的「完成人数 / 应完成人数」与百分比。
- **逐人明细**：姓名、成员编号、年级、成员状态、角色、子任务完成 x/y、完成说明、提交时间、审核账号、审核时间、审核意见、积分结果（+N 分 / 未计分 + 原因），并提供「通过 / 驳回」操作。

新手任务的完成情况在「任务管理 → 新手任务」查看：报名者姓名、**子任务完成进度条（x / y）**、完成说明、各自的截止日期与剩余天数、当前状态、审核结果、是否豁免以及转正后的成员档案链接。

**两类任务共用同一套对象与子任务模型**：普通任务按成员档案发放、按任务快照对象；新手任务在全实验室唯一的大任务上按报名记录建立对象。差别只在对象是谁、对象怎么产生、通过后发生什么。

**逾期只是显示层派生**：对象的截止日期（新手任务取 `due_date`，普通任务取任务的 `end_date`）早于今天且对象未通过时标记「已逾期」，不写库、不改状态、不发消息，也不构成任何自动结论。

## 10. 后端接口

### 10.1 成员端 `/api/v1/tasks`（仅普通任务）

| 方法  | 路径                                                | 说明                                                                            |
| ----- | --------------------------------------------------- | ------------------------------------------------------------------------------- |
| GET   | `/api/v1/tasks`                                     | 我收到的任务列表                                                                |
| GET   | `/api/v1/tasks/{assignmentId}`                      | 大任务详情：正文、子任务清单（含 `hasContent`，**不含子任务正文**）、进度与截止 |
| GET   | `/api/v1/tasks/{assignmentId}/subtasks/{subtaskId}` | 子任务详情：该子任务的富文本正文、完成状态与大任务上下文                        |
| PATCH | `/api/v1/tasks/{assignmentId}/subtasks/{subtaskId}` | 勾选或取消一个子任务                                                            |
| POST  | `/api/v1/tasks/{assignmentId}/submission`           | 提交或更新完成说明，状态转 `SUBMITTED`                                          |

只返回对象与当前账号成员档案一致的任务；他人对象一律返回 404，避免用 403 探测他人任务是否存在。

### 10.2 报名者端 `/api/v1/recruitment/me/onboarding-task`

| 方法  | 路径                                                          | 说明                                                                                      |
| ----- | ------------------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| GET   | `/api/v1/recruitment/me/onboarding-task`                      | 我的新手任务：大任务正文、**本人进度 x/y 与完成标记**、状态、截止日期与剩余天数、审核意见 |
| GET   | `/api/v1/recruitment/me/onboarding-task/subtasks/{subtaskId}` | 某个子任务的正文与完成状态（报名者本人）                                                  |
| PATCH | `/api/v1/recruitment/me/onboarding-task/subtasks/{subtaskId}` | 勾选或取消一个子任务                                                                      |
| POST  | `/api/v1/recruitment/me/onboarding-task/submission`           | 提交或更新完成说明；**未勾完全部子任务时拒绝**                                            |

### 10.3 管理端 `/api/v1/admin/tasks`（`TASK_MANAGE`）

| 方法      | 路径                                                             | 说明                                                                                                     |
| --------- | ---------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| GET       | `/api/v1/admin/tasks?type=&status=&keyword=`                     | 任务列表，附完成与计分汇总，可按类型筛选                                                                 |
| POST      | `/api/v1/admin/tasks`                                            | 创建普通任务草稿                                                                                         |
| GET       | `/api/v1/admin/tasks/{taskId}`                                   | 任务详情                                                                                                 |
| PUT       | `/api/v1/admin/tasks/{taskId}`                                   | 修改任务；已发布任务不可改条件、对象与积分值                                                             |
| POST      | `/api/v1/admin/tasks/{taskId}/audience-preview`                  | 按条件预览命中成员与计分状态                                                                             |
| POST      | `/api/v1/admin/tasks/{taskId}/publish`                           | 发布并锁定积分与对象，幂等                                                                               |
| POST      | `/api/v1/admin/tasks/{taskId}/close`                             | 结束任务                                                                                                 |
| POST      | `/api/v1/admin/tasks/{taskId}/assignments`                       | 补充发放                                                                                                 |
| DELETE    | `/api/v1/admin/tasks/{taskId}/assignments/{assignmentId}`        | 移除对象；已有提交或已计分时拒绝                                                                         |
| GET       | `/api/v1/admin/tasks/{taskId}/progress`                          | 完成情况汇总 + 子任务完成率 + 逐人明细（子任务只带 `hasContent`，不带正文）                              |
| GET       | `/api/v1/admin/tasks/{taskId}/subtasks/{subtaskId}`              | 编辑前读取某个子任务的标题与富文本正文                                                                   |
| PUT       | `/api/v1/admin/tasks/{taskId}/assignments/{assignmentId}/review` | 人工确认；普通任务通过时计分，新手任务通过时转正                                                         |
| DELETE    | `/api/v1/admin/tasks/{taskId}`                                   | 删除任务，仅允许普通任务草稿                                                                             |
| GET / PUT | `/api/v1/admin/tasks/onboarding`                                 | 读取与保存**新手任务大任务**（标题、说明、时长、子任务）；保存返回 `reopenedCount` 与 `rescheduledCount` |
| GET       | `/api/v1/admin/tasks/onboarding-overview`                        | 大任务内容 + 技能测试阶段完成情况（含每人进度、截止日期、审核留痕）                                      |
| POST      | `/api/v1/admin/tasks/onboarding-tasks/backfill`                  | **批量补发**：为技能测试阶段且在大任务上还没有对象的记录补建，返回补发与跳过条数；幂等                   |

### 10.4 招募流程接口调整

| 方法  | 路径                                                  | 说明                                                                      |
| ----- | ----------------------------------------------------- | ------------------------------------------------------------------------- |
| PATCH | `/api/v1/admin/recruitment/applications/{id}/stage`   | 转移表调整，支持 `SKILL_TEST → FORMAL_MEMBER` 与 `PROBATION → SKILL_TEST` |
| POST  | `/api/v1/admin/recruitment/applications/{id}/convert` | 前置条件改为技能测试阶段 + 转正守卫；新增可选 `exemptionReason`           |

### 10.5 审核请求示例

普通任务通过：

```json
{ "decision": "APPROVED", "comment": "验收通过，代码已合并", "evidenceUrl": "https://example.com/pull/128" }
```

新手任务通过（额外必填转正数据）：

```json
{
  "decision": "APPROVED",
  "comment": "新手任务全部完成，同意转正",
  "memberCode": "S-2026-018",
  "skillTags": ["视觉", "Python"]
}
```

豁免并转正：

```json
{ "exemptionReason": "该同学在功能上线前已完成同等训练，经指导老师确认免修" }
```

### 10.6 新手任务大任务保存请求示例

```json
{
  "title": "新手入门任务",
  "contentHtml": "<p>请在截止日期前完成下列基础训练…</p>",
  "durationDays": 7,
  "subtasks": [
    { "id": "…", "title": "配置开发环境", "contentHtml": "<p>安装 Git 与 JDK 21…</p>" },
    { "title": "完成 ROS2 教程", "contentHtml": null },
    { "id": "…", "title": "跑通第一个仿真" }
  ]
}
```

规则：带 `id` 表示更新该子任务（保留其勾选记录），不带 `id` 表示新增，未出现的 `id` 表示删除，`contentHtml` 为空表示该子任务只有标题。

保存响应（含即时生效的影响面）：

```json
{
  "task": { "taskId": "…", "title": "新手入门任务", "durationDays": 7, "subtasks": ["…"] },
  "reopenedCount": 2,
  "rescheduledCount": 5
}
```

## 11. 权限

在 `Permission` 枚举新增 `TASK_MANAGE`，加入 `Role.adminPermissions()`，教师与核心学生自动获得。

| 能力                                               | 权限要求                                                            |
| -------------------------------------------------- | ------------------------------------------------------------------- |
| 创建、修改、删除、发布、结束普通任务               | `TASK_MANAGE`                                                       |
| 维护新手任务大任务（内容、时长、子任务）与批量补发 | `TASK_MANAGE`                                                       |
| 查看全部任务与完成情况、审核通过/驳回              | `TASK_MANAGE`（计分沿用 `POINTS_MANAGE`；转正沿用 `MEMBER_MANAGE`） |
| 打回技能测试阶段、豁免并转正                       | `RECRUITMENT_MANAGE` + `MEMBER_MANAGE`（沿用现有转正接口的权限）    |
| 查看「我的任务」、勾选子任务、提交完成说明         | 已登录且拥有成员档案（教师、核心学生、普通成员）                    |
| 查看与提交本人的新手任务                           | `RECRUITMENT_SELF_VIEW` / `RECRUITMENT_SELF_EDIT`（游客）           |

同步更新 `backend/docs/access-control.md`（权限矩阵、招新状态机图、转正守卫与豁免规则）、`backend/docs/module-boundaries.md`、`README.md`，并在 `RolePermissionTests` 增加 `TASK_MANAGE` 断言。

## 12. 前端设计

### 12.1 新增文件

| 文件                                      | 说明                                                                                                                |
| ----------------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| `src/services/taskApi.js`                 | 复用 `authApi.js` 的请求封装与错误处理模式                                                                          |
| `src/components/TaskRichTextEditor.vue`   | Tiptap 富文本编辑器，对齐 `DiscussionRichTextEditor.vue`                                                            |
| `src/components/TaskAudienceSelector.vue` | 等级条件多选 + 指定成员 + 实时命中预览（含计分状态）                                                                |
| `src/components/TaskProgressTable.vue`    | 完成情况汇总、子任务完成率、逐人明细与审核操作                                                                      |
| `src/components/OnboardingTaskPanel.vue`  | 新手任务展示与提交面板，报名页与招新管理共用                                                                        |
| `src/views/TasksView.vue`                 | 「我的任务」列表（普通任务）                                                                                        |
| `src/views/TaskDetailView.vue`            | 我的任务详情                                                                                                        |
| `src/views/AdminTasksView.vue`            | 后台任务列表、完成汇总、新手任务入口                                                                                |
| `src/views/AdminTaskFormView.vue`         | 创建 / 编辑普通任务                                                                                                 |
| `src/views/AdminTaskProgressView.vue`     | 任务完成情况与人工审核                                                                                              |
| `src/components/TaskSubtaskEditor.vue`    | 子任务逐条添加 / 删除 / 排序 + **展开式富文本正文编辑**（一次只展开一条，展开时按需取正文），新手任务与普通任务共用 |
| `src/views/TaskSubtaskView.vue`           | 成员端子任务页：正文 + 完成勾选 + 返回大任务                                                                        |
| `src/views/OnboardingSubtaskView.vue`     | 报名者端子任务页（新手任务），沿用报名自身权限                                                                      |
| `src/views/AdminOnboardingTaskView.vue`   | 新手任务大任务维护（标题、说明、时长、子任务）+ 技能测试阶段完成情况与审核                                          |

新手任务通过 `OnboardingTaskPanel.vue` 嵌入现有 `src/views/RecruitmentView.vue`（我的报名）与 `src/views/AdminRecruitmentView.vue`（招新管理），不新增报名侧页面。

### 12.2 路由

```text
/tasks                              我的任务          roles: TEACHER, CORE_STUDENT, MEMBER
/tasks/:assignmentId                大任务详情        roles: TEACHER, CORE_STUDENT, MEMBER
/tasks/:assignmentId/subtasks/:subtaskId  子任务页     roles: TEACHER, CORE_STUDENT, MEMBER
/admin/tasks                        任务管理          roles: TEACHER, CORE_STUDENT
/admin/tasks/new                    创建任务          roles: TEACHER, CORE_STUDENT
/admin/tasks/:taskId/edit           编辑任务          roles: TEACHER, CORE_STUDENT
/admin/tasks/:taskId/progress       完成情况与审核     roles: TEACHER, CORE_STUDENT
/application/subtasks/:subtaskId    新手任务子任务页  roles: VISITOR
/admin/tasks/onboarding            新手任务          roles: TEACHER, CORE_STUDENT
```

全部使用 `() => import(...)` 保持按路由拆分。

### 12.3 导航入口

在 `src/components/PortalShell.vue` 中新增：

- 成员系统顶栏导航与后台侧栏「成员系统快捷入口」：新增「任务」，图标 `ListChecks`；
- 后台侧栏「后台管理」分组与顶栏「后台管理」下拉：新增「任务管理」，图标 `ClipboardCheck`。

### 12.4 交互要点（沿用 UI/UX Pro Max 既有规范）

- 新手任务页显著说明这是**全实验室共享的大任务**，并用提示条写清保存后的即时影响：新增子任务会把已提交的人退回「待完成」、删除子任务会清掉对应勾选、修改时长会按各自发放日重算截止日期。
- 子任务编辑器逐条添加 / 删除 / 上移 / 下移，按钮均有 `aria-label`、44px 触控目标与可见焦点；窄屏下每行自动换行而不是裁掉标签。
- 报名者侧用进度条 + 「我的进度 x / y 项子任务」呈现本人完成度；未完成全部子任务时提交按钮禁用并写明还差几项。
- 任务列表页的大任务卡片直接显示「子任务 x / y · 截止日期 · 状态」，不用点进去才知道进度。
- 子任务入口列表整行可点，右侧标出「已完成 / 未完成」与「有说明」；子任务页顶部提供「返回大任务」。
- 子任务正文为空时不渲染空白正文块，只显示标题与勾选。
- 管理端子任务编辑采用「列表 + 单条展开」：一次只挂载一个富文本编辑器，避免 50 条子任务同时初始化编辑器导致卡顿。
- 积分值输入旁说明「每个通过审核的对象各得此分值，0 表示不计分；发布后不可修改」。
- 发布前必须先看到命中名单、人数与计分状态；未满足发放条件时按钮禁用并说明原因。
- 审核按钮明确区分「确认通过（并发放 N 积分）」与「驳回」。
- **新手任务审核弹窗明确提示「通过后将直接转为正式成员」**，并要求填写学号/内部编号与能力标签；编号重复时就地报错。
- 转正被守卫拦下时，界面同时给出「打回技能测试阶段」与「豁免并转正」两个动作，并解释各自影响。
- 报名者侧显示截止日期与剩余天数；逾期用语义色并在文本中写明「已逾期」，同时说明可联系管理员延长。
- 子任务勾选提供保存中状态与失败回滚提示；提交后显示「等待管理员确认」。
- 未计分对象显示原因文本，不只依赖颜色；完成情况表窄屏改纵向卡片；触控目标不小于 44px；保留键盘焦点、`aria-live` 与减少动态效果适配。

### 12.5 普通任务管理维护（2026-09-27）

- 管理列表与编辑区分离；列表支持标题搜索、状态数量、待办优先/截止日期/名称排序。编辑区依次为任务设置、内容与子任务、发放对象；成员通过搜索与复选指定。
- 草稿保存时先清空旧发放规则并 flush，再写入新规则，避免 Hibernate 插入早于删除造成 V11 唯一约束冲突；整个操作保持同一事务。已有子任务按 ID 同步，不再整体重建。
- `TaskView.memberProfileIds` 返回手工指定成员编号，供草稿编辑回填；每次保存后更新服务端返回的子任务 ID 与表单快照。
- 发布前预览名单，未保存或名单为空时禁止发布；保存错误保留输入并聚焦错误摘要。已结束任务只提供查看进度。未改变发布、审核、结算规则，不涉及迁移。
- 页面规范见 `design-system/yes-lab/pages/task-management.md`。

## 13. 站内消息

复用 `NotificationService.send(recipient, type, title, summary, targetPath)` 与现有「梅琳娜」口径：

| 事件                                          | 类型             | 目标地址                       |
| --------------------------------------------- | ---------------- | ------------------------------ |
| 新手任务自动发放                              | `TASK_ASSIGNED`  | `/application`                 |
| 普通任务发布 / 补充发放                       | `TASK_ASSIGNED`  | `/tasks/{assignmentId}`        |
| 新手任务通过并转正                            | `TASK_APPROVED`  | `/profile`                     |
| 普通任务确认通过                              | `TASK_APPROVED`  | `/tasks/{assignmentId}`        |
| 确认驳回                                      | `TASK_REJECTED`  | 对应任务页                     |
| 新手任务新增子任务被退回待完成 / 截止日期重算 | `TASK_UPDATED`   | `/application`                 |
| 积分到账                                      | `POINTS_GRANTED` | `/profile`（积分模块既有行为） |

普通任务通过且成功计分时会产生两条消息（任务结果 / 积分流水），职责不同，保留积分模块既有行为不做抑制。

**不实现截止前自动提醒与到期自动处理**——属于定时自动判定，本轮明确不做。

## 14. 明确不实现的部分

- 自动判断完成或自动勾选子任务；
- 截止前定时提醒、到期自动打回、逾期自动处理、自动催办；
- 子任务嵌套、子任务独立指派负责人、子任务截止日期、子任务单独计分；
- 子任务单独提交与单独审核（子任务只有「已勾选 / 未勾选」两种状态）；
- 按对象设置不同积分值；
- 新手任务发放积分（已确认为转正门槛，积分固定 0）；
- 「撤销审核」及自动反向积分（更正走现有积分管理的反向流水）；
- 新手任务大任务多版本管理与按批次灰度；
- 停用 `MemberStatus.TRIAL`（成员档案的「试用」状态与招新阶段无关，本次保留）；
- 任务附件上传、任务评论 / 讨论区、任务与项目团队或竞赛记录的关联；
- 任务模板、周期任务、批量导入。

## 15. 实施顺序与验证计划

建议分七批提交，每批独立可验证：

1. **数据与领域层**：`V11` 迁移 + 实体枚举 + 仓库 + `Permission.TASK_MANAGE`。
2. **删除试用期阶段与精简成员状态**：转移表调整、转正前置条件与守卫、共用转正核心方法抽取、打回动作、成员状态可选值收窄与后端校验、前端阶段与文案调整、现有招新与成员管理测试改造。
3. **试用期批量回退脚本**：`V12` 迁移，并在预生产 MySQL 演练（见 6.6 节检查清单）。这一批不含 Java 代码，独立提交便于单独演练与回退判断。
4. **新手任务**：共享大任务（含内置兜底与时长）、子任务即时生效（新增 / 删除 / 时长重算）、面试通过自动分配、报名者端进度与提交、批量补发、全勾后审核通过即转正、豁免。
5. **子任务正文与独立页面**

- 子任务可保存富文本正文，脚本与事件属性被清洗；正文为空时保存成功且接口返回 `hasContent=false`；
- 按 `id` 同步：改标题后原勾选记录与正文都保留；新增子任务把已提交对象退回 `PENDING`；删除子任务时勾选记录一并删除；
- 子任务详情接口只允许本人对象（成员端按成员档案、报名者端按报名记录），他人访问返回 404；
- 列表与进度接口不返回子任务正文，只返回 `hasContent`；
- 子任务页勾选后，大任务进度与「是否可提交」同步变化。

**普通任务与积分**：`TaskService`（条件解析、发布快照与积分锁定、补充发放、人工审核、计分、汇总）+ `PointService.grantForTask` + `TaskModels` + 管理端 Controller。6. **后端测试**：新增 `TaskApiTests` 与 `OnboardingTaskApiTests`，回归全部现有测试（当前 36 项）。7. **前端**：`taskApi.js` + 5 个组件 + 6 个页面 + 路由 + `PortalShell` 导航 + 报名页与招新管理嵌入。8. **子任务正文与独立页面**：`task_subtasks.content_html`（改 `V11`，未上线）、子任务按 `id` 同步、子任务详情接口、大任务卡片进度、成员端与报名者端子任务页、管理端展开式正文编辑。

必须覆盖的测试用例：

**删除试用期与转正**

- `SKILL_TEST → PROBATION` 被拒绝，`PROBATION → FORMAL_MEMBER` 被拒绝；
- `SKILL_TEST → FORMAL_MEMBER` 在新手任务已通过时成功；
- 技能测试阶段转正时缺少 `memberCode` 或 `skillTags` 被拒绝，编号重复被拒绝且不产生档案；
- `PROBATION → SKILL_TEST` 打回成功并写入历史，打回后重新计时；
- 历史 `PROBATION` 记录仍能被读取（枚举兼容性回归）；
- 批量补发接口幂等：连续调用两次，第二次补发条数为 0，且不产生重复新手任务。

**成员状态精简**

- 通过成员管理接口把状态设为 `CANDIDATE` / `PAUSED` / `EXITED` 一律被拒绝；
- 创建学生管理员时同样被拒绝；
- 把状态设为 `TRIAL` 或 `OFFICIAL` 正常成功；
- 存量处于 `CANDIDATE` / `PAUSED` / `EXITED` 的成员仍能被正常读取并返回原状态（枚举兼容性回归）；
- 公开成员目录仍只展示 `OFFICIAL`。

**新手任务**

- 两位报名者进入技能测试阶段后拿到**同一个 `taskId`**、各自的 `assignmentId`，进度互不影响；
- 每人的截止日期 = 本人分配当天 + 大任务时长；
- 未勾完全部子任务时提交被拒（400），管理端绕过提交直接通过也被拒；
- 勾完全部子任务后提交成功，管理员审核通过即转正并写入成员档案；
- 管理员新增子任务 → 已提交对象退回 `PENDING`，勾选新项后可重新提交并转正；
- 管理员删除子任务 → 对应勾选记录一并删除；修改时长 → 未通过对象的截止日期按各自发放日重算；
- 大任务不存在时用内置默认内容初始化后分配；
- 批量补发幂等：连续调用两次第二次为 0 条；
- 新手任务未通过时转正被拒；豁免并转正成功且理由落库；
- 报名者只能操作自己的新手任务，无法读写他人记录。

**普通任务与积分**

- 普通成员调用管理端接口返回 403；
- 同维度「或」、跨维度「且」的条件组合命中人数正确；
- 发布后每人恰好一条对象，重复发布不重复创建；
- 发布后修改积分值被拒绝；
- 发布后成员等级变化不影响已发放对象，补充发放只追加差集；
- 审核通过后自动生成一条 `PROJECT_TASK` 积分发放，成员总积分增加，`point_grant_id` 与 `awarded_points` 正确回写；
- 重复审核不重复计分，`source_reference` 仍只有一条；
- 三种不可计分情形下审核成功、积分跳过、原因落库；
- `points = 0` 审核通过时不产生任何积分记录；
- 驳回未填意见被拒绝且不产生积分记录；
- 成员访问他人对象返回 404；
- 任务结束后成员不能提交，管理员仍可审核；
- `endDate` 早于 `startDate`、空条件且无指定成员、积分越界时拒绝；
- 富文本中的脚本与事件属性被清洗，`<img>` 与链接保留。

每批完成后执行：

- `npm run check`（ESLint + Prettier + Vite 生产构建）；
- Java 21 下 `./mvnw test` 全量回归；
- `git diff --check`；
- 更新根目录 `DEVLOG.md`；
- 上线时同步发布 API 与 Web 镜像，生产 MySQL 依次自动执行 `V11`、`V12`，部署前按既有流程确认数据库备份，并按 6.6 节检查清单验收批量回退结果；部署后必须执行一次「批量补发新手任务」。

## 16. 招新资料自填与截止规则（2026-09-26 历史实施规则，已由第 17 节修订）

> 以下记录的是 V17 配套实施的上一版资料补全时点规则；当前行为与验收口径以第 17 节为准。

### 报名者自行填写转正资料

- 在报名记录上保存报名者本人填写的 `memberCode`（学号/内部编号）与 `skillTags`；仅该报名账号在 `SKILL_TEST` 阶段可更新自己的字段。
- 新增报名者自助读写接口，服务端以 JWT 账号绑定的 application id 定位记录，不接收客户端指定的 application id；拒绝读取或修改他人记录。
- 保留现有必填规则：编号非空且唯一、能力标签至少一项。提交和转正时校验编号与现有正式成员编号冲突；转正时再校验一次，避免候选人之间的竞争写入。
- 管理端申请详情返回这两项供核对，但审核/豁免转正请求不接受这两项字段；创建正式成员档案时从报名记录复制。正式成员形成后，管理员仍通过既有成员管理能力修改档案。
- 数据列与实体字段通过新增 `V17` 迁移添加；不得改写已应用的 `V11`—`V16`。预生产 MySQL 演练和部署后数据核对仍为上线前强制人工验收。

### 验收重点

- 报名者自助页面使用明确可见的标签、就地校验与保存中/成功/失败状态；管理员审核页仅显示只读值。
- 用例覆盖本人/他人权限、技能测试阶段外拒绝、编号冲突、能力标签缺失、审核与豁免转正取值，以及正式成员管理仍可维护资料。

## 17. 转正后首次进入成员系统补全资料（2026-09-26 已批准）

本节修订第 16 节中“转正前必填并从报名记录创建完整成员资料”的要求；第 16 节关于管理员只读、已填资料沿用和管理端事后维护仍然有效。

### 规则

- 新手任务审核通过和管理员豁免转正均不再要求申请人提前填全 `memberCode` 与 `skillTags`，资料缺项不能成为审核拒绝理由。
- 转正时将报名记录中已填字段拷贝到 `member_profiles`，缺失字段以 `NULL` 编号及空标签集合保存。管理员审核表继续只读。
- 正式成员第一次进入受权限保护的成员页面时，客户端先读取本人档案；编号为空或能力标签为空，则强制进入唯一的资料补全页面，保留原目标 URL，补全并保存成功后返回。登出、登录恢复及必要的重新登录不受拦截。非 `MEMBER` 角色及资料已经完整的旧成员不受影响。
- 补全接口由 JWT 定位本人档案；要求编号匹配成员编号格式且全局唯一，能力标签 1—12 项。只更新这两个字段，不覆盖管理员维护的其他成员资料；完成后标准自助资料编辑规则不变。
- 技能测试阶段既有的已保存资料可沿用。管理员可在成员管理中处理缺漏或重复值；完成后本人补齐剩余字段。

### 技术设计

- 新增 `V18__member_profile_code_nullable.sql`：幂等将 `member_profiles.member_code` 改为 nullable，保留唯一索引（MySQL 唯一索引允许多个 `NULL`）；不得修改已应用的 `V1` 或 `V17`。
- `MemberProfileEntity` 的编号映射可空；新增 `qualificationComplete` 视图字段与本人补全 API。`RecruitmentService.convertApplicantToMember` 不再对缺项报冲突，转正时安全复制已有可用资料。
- Vue 路由守卫对 `MEMBER` 角色在每个会话首次访问受保护路由时检查本人档案，并缓存会话级结果；不完整则转到 `/complete-profile?redirect=原地址`。补全页显示清楚原因、可见标签、字段错误与保存状态，提交成功后解除拦截并返回原地址；错误提供重试与登出出口。
- 现有报名自填接口不再作为转正前必经步骤；已提交资料仍保留并用于转正预填。

### 自动化与人工验收

- 测试覆盖新手任务通过及豁免转正可空字段、已填字段保留、本人补全成功、格式/重复编号/缺少标签拒绝，以及非本人无法补全。
- 前端验收覆盖成员首次登录强制跳转、刷新/深链仍被拦截、保存成功回到原页面、已完整成员与教师/核心成员不受影响。
- 上线前备份并在预生产 MySQL 演练 V18；部署后核对列仍有唯一索引且可多个 NULL，再验证转正空档案与填写解锁。H2 不替代 MySQL DDL 演练。

## 18. 普通任务按人补发设计（2026-10-02 已批准并实施）

对应需求清单第 9 节；补发入口以本节为准。用户已批准按教师/核心学生/普通成员角色筛选的方案。

### 18.1 现状与改造边界

- `src/views/AdminTaskProgressView.vue` 当前只把能力标签转换成 `SKILL_TAG` 规则，直接提交补发，缺少成员名单与本次人数。
- 后端 `AudienceRequest` 已支持 `ROLE`、`GRADE` 等规则及最多 100 个 `memberProfileIds`；`GET /api/v1/admin/tasks/member-options` 已提供 `profileId/name/memberCode/grade/memberStatus/role`，进度接口已有任务对象名单。
- 沿用既有 Vue 3、Lucide SVG、明暗主题和 `design-system/yes-lab/pages/task-management.md`；不增加依赖或数据库字段，不修改既有迁移。

### 18.2 页面与操作草图

```text
选择补发成员
按身份和年级筛选，再勾选本次要补发的人。

成员身份  [ ] 教师  [ ] 核心学生  [ ] 普通成员
成员年级  [ ] 2024级  [ ] 2025级  [ ] 年级未填
搜索成员  [输入姓名或学号/内部编号____________]

匹配 12 人 · 可补发 9 人 · 已发放 3 人
[选中当前筛选结果]  [仅看已选]  [清空选择]

[ ] 张同学    20250001  普通成员  2025级  可补发
[ ] 李同学    20250002  核心学生  2025级  可补发
[-] 王同学    20250003  普通成员  2025级  已发放

已选 8 人（含不在当前结果中的 2 人） [查看已选名单]
[补发给 8 人]
```

草图姓名和年级仅为示例；实际年级选项读取现有档案，不硬编码学年。桌面保持筛选、名单、操作上下排列；375px 单列，成员资料自然换行，复选框与姓名保持一体。列表分页呈现，每页 20 人；「选中当前筛选结果」指所有匹配且未发放的成员，按钮旁显示总人数，不只选择当前页；超过 100 人时提示缩小筛选范围，不截断。

采用原生复选框和具名按钮；角色/年级集合允许换行；控件触控区域至少 44px、键盘焦点可见、正文对比度至少 4.5:1。筛选不使用位移动画，遵守 `prefers-reduced-motion`；已发放状态通过文本说明，不仅使用颜色。

### 18.3 前端状态与提交语义

1. 读取任务进度和 `listTaskMemberOptions()`，按 `memberProfileId` 建立已发放集合；候选只来自有成员档案的非游客账号。
2. 独立维护角色选择、年级选择、关键词、已选 ID 集合。年级用真实原值匹配，空值独立处理；搜索对姓名/编号做包含匹配。选择不随过滤变化清空，页面结果变化回到第一页。
3. 已发放成员不可选。每次进度刷新将新出现的已发放 ID 从选择中移除并提示；默认不勾选任何人。全部已选名单可在同一区域展开，避免额外确认弹窗。
4. 提交 `rules: []` 与本次明确勾选的 `memberProfileIds`。角色/年级仅用于选人，不把规则同时提交，避免服务端把管理员手动取消的成员再次纳入并集。
5. 提交期间冻结补发表单，避免请求结果覆盖后续选择。失败保留输入、聚焦补发区域错误；成功立即显示新增/跳过计数、清空已完成选择，再刷新进度。刷新失败显示「补发成功，名单刷新失败，可重试刷新」，不自动重复补发。

### 18.4 后端与兼容

- 保留 `POST /api/v1/admin/tasks/{taskId}/assignments` 和 `AudienceRequest`，兼容原条件补发调用；Controller 添加 `@Valid`，使既有 100 人上限真正生效。新界面仅发送成员 ID。
- 仍由 `TASK_MANAGE` 授权；仍只允许普通任务且状态为 `PUBLISHED`，其他任务类型/结束任务拒绝。截止日期和积分不在本次改动中重定义。
- 在事务开始复用 `TaskRepository.findByIdForUpdate` 锁定任务后读取对象，按请求去重、校验档案存在且非游客，再求差集。非法 ID 整次拒绝并回滚，不只在前端校验。锁定后的名单用于计算本次新增和已存在跳过数。
- 明确指定 ID 创建的对象记录 `MANUAL`；仅条件命中的新增对象仍用 `CRITERIA`。兼容请求同时包含条件和 ID 时，显式指定优先标记 `MANUAL`。不改历史来源或任务原始规则。
- 将补发通知限定为本次新建对象，避免当前 `notifyAssignees(task)` 对全体已有对象再次发送通知；初次发布仍通知全部发放对象。
- 为准确反馈并保留现有返回结构，在补发响应的 `TaskView` 增加可选 `supplementResult`（`createdCount`、`skippedExistingCount`）；其他接口返回空值。旧前端仍可读取 `result.id/status` 等原字段，新界面读取计数。
- 复用现有对象唯一约束与悲观锁；无数据库迁移。并发幂等需在 MySQL/InnoDB 复核，H2 结果不能替代。

本次采用已批准的角色筛选，不引入具体权限项字段。

### 18.5 实施与验收计划

- 后端验证明确指定、重复/部分重复、空名单、超过 100 人、无效 ID/游客、非管理者/其他任务类型/任务状态拒绝；验证已提交/已通过对象进度及积分不变、来源正确、通知仅新增对象各一次。并发同一成员补发应最终只有一个对象与一条通知。
- 前端验证角色与年级组合、年级空值、姓名/编号搜索、分页、批选/取消、隐藏选择提示、仅看已选、已发放禁选、上限、失败保留和成功后刷新失败；预览名单与请求 ID 集合一致。
- 代码完成后运行适用的后端测试与前端 `npm run check`；本地真实前后端与 Chrome CDP 截取 1440/1024/768/375 × 亮/暗主题到 `.codex-run/ui-review/`，人工核对成员列表、长姓名/编号、键盘焦点、对比度、触控尺寸、减少动态与横向溢出。
- 上线前在预生产 MySQL 用专用演练任务和成员验证并发去重、事务回滚、通知差集；无新增迁移。收尾关停本次启动的后端/Vite/Chrome，若需临时 MySQL 仅用 `scripts/temp-mysql.sh` 并停止。
- 实施结果和实际验收记录见根目录 DEVLOG.md 的 2026-10-02 施工条目。

规则来源：`ui-ux-pro-max` 查询 `filter selection feedback --domain ux`，采用匹配结果中的 Chip Collection Reflow（完整标签、集合换行）；查询 `form checkbox v-model --stack vue`，采用原生控件和双向绑定。其余交互按现有任务管理规范与 skill 的可访问性、表单反馈规则设计。页面级设计规范同步记录按人补发规则。

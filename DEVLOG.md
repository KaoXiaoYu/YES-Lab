# YES Lab 开发日志

> 本日志已于 2026-08-25 合并重复的 UI、Logo、启动与验证记录，仅保留关键决策、当前能力和后续待办。
> 2026-09-20 起本文件只保留最近 20 条记录及当日新增记录；超出时把最早一条移入 [`docs/devlog-archive.md`](docs/devlog-archive.md)（已归档 90 条，含完整索引）。

## 当前状态

- 前端：Vue 3 + Vite，包含公开展示、公开成员主页、登录注册、游客报名、成员个人主页、成员/招新管理、项目团队空间和竞赛成果管理。
- 后端：Java 21 + Spring Boot 4.1.1、Spring Security、JWT、JPA；本地使用 H2，生产配置使用 MySQL 8.4 + Flyway。
- 角色：教师与核心学生均为系统管理员；普通成员维护个人主页；游客只维护本人报名并查看进度。
- 尚未实现：测验、写题、积分申请/终审页面、真实公开排行榜写入、比赛记录删除/导入和项目即时聊天；积分账本及管理员发放接口已经实现。

## 使用说明补充

- 项目在公开首页的主图和资料从“项目团队 → 项目空间 → 编辑项目资料”维护；需同时开启“允许公开展示”。
- 比赛首页展示顺序和新闻引用由系统管理员在“成果管理”页面维护；实验室简介、首屏、栏目文案、奖项、赞助伙伴、外部入口及首页成员/项目展示选择由“主页编辑”页面维护。

## 后续待办

- 正式服务器首次上线前完成域名解析、HTTPS 签发、MySQL/Flyway 空库与已有库演练、备份恢复演练；生产环境关闭演示账号初始化并配置独立 JWT 密钥和正式 CORS 域名。
- 后续增加密码重置、登录限流与告警、JWT 双密钥平滑轮换、全局操作审计、独立的内部联系方式查看权限。
- 后续实现头像文件上传，以及测验和积分模块；项目即时聊天如需加入，应先补充消息、文件、已读与内容治理规则。
- 使用真实成员、项目、仓库和社交平台资料替换占位内容。

> 以上为 2026-08-25 记录，部分条目已实现；最新进展见下方记录。

> 以下为最近记录，按时间先后排列。

## 2026-09-17：GitHub Actions #36 格式失败修复

### 完成内容

- 读取 Actions 运行 `35189690751`（第 36 次）第三次尝试的日志，确认 `npm run check` 在 Prettier 检查阶段因 `src/views/AdminRecruitmentView.vue` 格式不一致而退出；Node.js 20 deprecated 信息只是运行时警告。
- 使用项目既有 Prettier 配置格式化该 Vue 文件，仅调整代码排版，不改变招新业务逻辑。

### 验证结果

- `npm run check` 通过：ESLint、Prettier 和 Vite 生产构建均成功。
- 构建仅保留既有 Three.js 主包超过 500kB 的分块大小提示；该提示不影响退出码，也不是 Actions #36 的失败原因。
- `git diff --check` 通过；本轮未修改后端、数据库或生产环境。

## 2026-09-17：面试待补录状态手动切换

### 完成内容

- 将“待补录面试结果”从自动推导状态改为报名记录上的持久化手动状态；管理员可在招新详情中将尚无结论的“面试”记录设为“待补录面试结果”，并可在提交结论前恢复为“面试”。
- 新增管理员切换接口和 V10 Flyway 迁移；每次设为待补录或恢复面试都会写入招新状态历史，保留操作人和操作说明。
- 对仍处于等待、已叫号或面试中的活动预约禁止切换，并在按钮附近说明应先完成或释放对应预约；前后端同时校验，避免绕过界面直接切换。
- 报名者进入待补录状态后不能继续预约面试，个人招新页会明确显示“面试结果待补录”；恢复为面试后可重新预约。
- 原有详细面试结果补录保持不变，继续支持参与面试官姓名、评分、评价、建议标签、结论和面试官意见。
- UI/UX Pro Max 用于复核状态切换确认、禁用按钮说明、成功反馈和可访问关联；README、模块边界及权限说明同步更新。

### 验证结果

- `npm run check` 通过：ESLint、Prettier 和 Vite 生产构建均成功；仅保留既有 Three.js 主包超过 500kB 的分块大小提示。
- 使用 Java 21 运行后端全量测试，共 36 项通过，0 失败、0 错误、0 跳过；集成测试覆盖手动设为待补录、恢复面试、待补录时禁止预约、活动预约阻止切换，以及切换后补录详细结论。
- `git diff --check` 通过；本轮未连接生产数据库，也未执行部署。

### 待办

- 上线时需同时发布 API 与 Web 镜像，并在生产 MySQL 执行 V10 Flyway 迁移；部署前按既有流程确认数据库备份。

## 2026-09-17：修复待补录手动切换无响应

### 完成内容

- 修复“设为待补录面试结果”在存在等待、已叫号或面试中预约时被直接禁用、点击没有反馈的问题。
- 手动切换现在会直接把残留的活动预约同步标记为已完成，并立即进入“待补录面试结果”，无需先返回面试场次完成或释放预约；审计历史会注明本次同步收口。
- 撤销待补录、恢复为“面试”时，会释放这条尚未录入结论的已完成预约，使报名者可以重新选择面试场次。
- 更新确认文案与补录说明，移除不再适用的禁用提示；UI/UX Pro Max 用于复核点击反馈、确认信息和恢复路径。
- README、模块边界与权限说明同步更新为新的直接切换规则。

### 验证结果

- `npm run check` 通过：ESLint、Prettier 和 Vite 生产构建均成功；仅保留既有 Three.js 主包超过 500kB 的分块大小提示。
- 使用 Java 21 运行后端全量测试，共 36 项通过，0 失败、0 错误、0 跳过；集成测试覆盖活动预约直接切换、预约同步完成、报名者停止预约、撤销后释放预约，以及后续补录完整面试结论。
- `git diff --check` 通过；GitHub Actions 已确认上一版镜像构建成功，但生产服务器仍需按部署流程拉取本次修复后的新镜像。

### 待办

- 提交并推送本次修复后等待 GitHub Actions 生成新镜像；生产服务器更新到对应提交 SHA 后再进行实际页面验收。

## 2026-09-17：任务模块设计

### 完成内容

- 新增 `docs/task-module-design.md`，结合现有代码给出「面向不同等级用户发放任务」的完整实现前设计，覆盖数据模型、发放条件、完成情况与人工确认、接口、权限、前端页面与迁移。
- 确认「等级」复用现有四个字段并组合筛选：角色（`accounts.role`）、成员状态（`member_profiles.status`）、年级（`member_profiles.grade`）、能力标签（`member_skill_tags.tag`）；同维度取「或」、跨维度取「且」。未新增等级字段，也未引入自动分档。
- 发放支持两种粒度：按等级条件批量展开，以及在条件之外直接指定具体成员；发布前提供命中名单预览，发布时一次性快照为每人一条任务对象，等级后续变化不重写历史，改用「补充发放」追加差集。
- 任务主体使用 `LONGTEXT` 富文本正文（Tiptap 编辑 + 后端 OWASP 白名单清洗，规则沿用讨论板策略），任务级设置起止日期，子任务为单层列表且每个子任务由成员单独勾选。
- 完成情况采用人工流程：成员勾选子任务并提交完成说明后进入待确认，管理员人工通过或驳回（驳回必填意见），驳回后可重新提交；不写自动判定。管理员侧提供任务汇总、子任务完成率与逐人明细三层视图。
- 新增 `TASK_MANAGE` 权限（教师与核心学生），成员端沿用项目/成果模块做法按成员档案归属校验；任务发布与审核结果复用现有「梅琳娜」站内消息。
- 明确排除项：自动判断、截止前定时提醒、子任务嵌套与独立指派、附件、任务与项目/竞赛关联、任务模板与周期任务。
- 记录待确认细节：游客能否作为任务对象（游客无成员档案，当前按不支持处理）、子任务是否需要独立截止日期。
- 本条目中积分相关的结论已被同日的「任务模块积分自动发放设计」取代，以新条目为准。

### 验证结果

- 本次仅新增设计文档与开发日志，未改动后端、前端、数据库迁移或生产配置，因此未运行构建与测试；未连接生产环境，也未执行部署。
- 设计中引用的字段、枚举、权限、清洗策略、消息接口与前端路由均已在当前仓库代码中逐项核对。

### 待办

- 等待确认第 12 节的两个细节后按第 13 节顺序实施：数据与领域层 → 服务与接口层 → 后端测试 → 前端页面与导航。
- 实施阶段每批完成后执行 `npm run check`、Java 21 全量后端测试与 `git diff --check`。

## 2026-09-17：任务模块积分自动发放设计

### 完成内容

- 按用户确认调整任务模块设计：管理员审核确认任务完成即**自动发放对应积分**。更新 `docs/task-module-design.md`，新增第 6 节「积分自动发放」，并同步数据模型、状态机、接口、消息与测试计划。
- 任务主体新增 `points` 字段（每个通过对象各得该分值，`0` 表示不计分，范围 0—100000）；积分按任务统一设置，子任务不单独计分，不支持按对象设置不同分值。修改分值只影响之后的通过操作，不追溯已发放积分。
- 逐行核实现有积分模块约束并按其设计集成：`PROJECT_TASK` 为 `SHARED_TOTAL`，单人发放时事项总分必须等于该成员得分；`evidenceUrl` 必填，未填时使用站内绝对路径 `/tasks/{assignmentId}`；`sourceReference` 使用 `TASK:{taskId}:{memberProfileId}` 保证幂等，重复审核不重复计分；`occurredOn` 取审核当天以满足 `@PastOrPresent`；`PROJECT_TASK` 月度上限为空，不存在封顶折算差额。
- 识别并处理三处真实冲突：积分模块 `validateRecipient` 禁止给教师、非正式成员发放积分，也禁止积分管理员给自己发分，而任务可以发给试用成员。确定处理方式为**审核照常成功、积分跳过并记录原因**，不清空也不阻塞任务流程，不放宽积分模块既有规则；条件预览名单提前标注每位命中成员的计分状态与原因。
- 确定审核通过为终态，任务模块不提供「撤销审核」，更正误审走现有积分管理的反向流水，保持单一撤销路径。
- 实现方式确定为在 `PointService` 新增面向任务来源的 `grantForTask(...)`，把来源编号约定、单人分配形状与幂等检查封装在积分模块内部；该方法需自带 `@PreAuthorize`（内部自调用不经过 Spring 代理）。任务审核与积分发放处于同一事务，避免「已通过但未计分」的不一致。
- 明确 `points = 0` 时不产生任何积分记录；站内消息保留积分模块既有的 `POINTS_GRANTED` 行为，任务模块另发 `TASK_APPROVED` 说明任务结果，未计分时在摘要中写明原因。
- 待确认细节由三项收敛为两项：游客能否作为任务对象、子任务是否需要独立截止日期。

### 验证结果

- 本次仅更新设计文档与开发日志，未改动后端、前端、数据库迁移或生产配置，因此未运行构建与测试；未连接生产环境，也未执行部署。
- 设计所依赖的积分规则（`validateAllocations`、`validateRecipient`、`creditedPoints`、`validateEvidenceUrl`、`sourceReference` 唯一约束、`PROJECT_TASK` 的 `SHARED_TOTAL` 与空月度上限）均已逐条比对 `PointService` 与 `PointSubcategory` 源码确认。

### 待办

- 等待确认第 12 节的剩余两个细节后开始实施。
- 实施阶段重点验证：审核通过自动生成 `PROJECT_TASK` 积分记录且成员总积分同步增加、重复审核不重复计分、三种不可计分情形下审核成功且原因落库、`points = 0` 无积分记录。

## 2026-09-17：新手任务与转正、普通任务积分锁定设计

### 完成内容

- 按用户确认把任务模块拆成两类任务并重写 `docs/task-module-design.md`：`ONBOARDING` 新手任务面向招新报名记录，`STANDARD` 普通任务面向成员档案；两者共用富文本、子任务、完成情况与人工审核能力。
- **核实到三条决定架构的代码事实**：`MemberProfileEntity` 只在 `convertToMember` 创建且要求 `stage == PROBATION`，技能测试与试用期申请人仍是 `VISITOR` 账号且无成员档案；`ALLOWED_TRANSITIONS` 不允许技能测试直接转正；`ConvertMemberRequest` 要求管理员补填全局唯一的 `memberCode` 与至少一个 `skillTags`。据此确定新手任务必须挂在报名记录上、转正审核表单必须同时收集这两项数据。
- 确定新手任务完成并审核通过后**直接转为正式成员、跳过试用期**：新增 `SKILL_TEST → FORMAL_MEMBER` 流转与转正守卫，抽取 `convertToMember` 的建档案与流转核心供两条路径复用；`PROBATION` 阶段与既有接口保留兼容，但转正一律受新手任务守卫约束。
- 新手任务**不发积分**（`points` 恒为 0），只作转正门槛，从而绕开了此前「试用成员不能发放积分」与「必须先转正才能计分」的冲突。
- 新手任务内容由**管理员维护模板**，报名者进入技能测试阶段时自动复制发放为专属任务与子任务；模板不存在时用代码内置默认内容自动初始化，避免卡住招新流程或在测试环境要求先建模板。模板修改不影响已发放的新手任务。
- 报名者是游客，无法访问成员端任务接口，因此新手任务的读写接口放在 `/api/v1/recruitment/me/onboarding-task`，沿用 `RECRUITMENT_SELF_VIEW` / `RECRUITMENT_SELF_EDIT`，并在「我的报名」与「招新管理」页嵌入同一面板组件。
- 历史豁免：功能上线前已进入技能测试/试用期的记录没有新手任务，设计为放行并在状态历史写入说明，避免这些报名无法收口；新记录因自动发放与内置兜底必然带有新手任务，门槛在新流程中始终生效。
- 普通任务积分改为**发布时绑定并锁定**，发布后不可修改标题以外的口径值，对应「发布时就绑定积分」；数据模型新增 `task_type`，`task_assignments` 改为 `member_profile_id` 与 `recruitment_application_id` 恰好一个非空并加 `CHECK` 约束，新增 `converted_profile_id` 追溯转正结果。
- 表数量由 5 张增至 7 张（新增新手任务模板及其子任务表），测试计划拆为新手任务/转正与普通任务/积分两组，并明确要求回归现有 36 项测试。
- 更新需同步的文档清单：`access-control.md` 招新状态机图与权限矩阵、`module-boundaries.md`、`README.md`。

### 验证结果

- 本次仅更新设计文档与开发日志，未改动后端、前端、数据库迁移或生产配置，因此未运行构建与测试；未连接生产环境，也未执行部署。
- 设计所依赖的招新规则（`ALLOWED_TRANSITIONS` 转移表、`convertToMember` 前置条件与建档案字段、`ConvertMemberRequest` 必填项、`changeStage` 的技能测试分支）与积分规则均已逐条比对源码确认。

### 待办

- 等待确认第 14 节三个细节后按第 15 节五批顺序实施。
- 实施时需重点回归现有 36 项测试，尤其是技能测试与转正相关用例，确认新增守卫未破坏既有招新流程。

## 2026-09-17：删除试用期阶段与新手任务时长设计

### 完成内容

- 按用户确认继续调整 `docs/task-module-design.md`：**试用期阶段从招新流程中删除**，通过技能测试（新手任务）直接转为正式成员；新手任务改为**面试通过后**发放；模板新增**时长（天）**；模板保存时可选择同步在途新手任务；保留豁免机制并新增「打回技能测试阶段」。
- 核实并记录「删除试用期」的完整影响面：`V1__baseline_schema.sql` 第 215 行 `recruitment_applications.stage` 与第 252—253 行 `recruitment_status_histories.from_stage/to_stage` 都是含 `PROBATION` 的 MySQL ENUM，且历史状态行已存有该值，因此**不能直接删除枚举值**，否则历史记录无法反序列化、修改 ENUM 列会报错或丢数据。
- 据确定处理方式为「行为上删除、枚举上保留」：`RecruitmentStage.PROBATION` 保留并标注为已停用仅兼容历史；转移表改为 `SKILL_TEST → {FORMAL_MEMBER, REJECTED}` 与 `PROBATION → {SKILL_TEST, REJECTED}`，即没有任何路径能再进入试用期，试用期只能被离开；`convertToMember` 前置条件由 `stage == PROBATION` 改为技能测试阶段加转正守卫。
- 仍停留在试用期的历史记录**不做静默数据回退**，由管理员在招新管理页用「打回技能测试阶段」逐条处理，保证操作人可审计并决定是否重新计时；招新管理页会显示醒目提示。
- 新手任务改为模板含 `duration_days`（默认 7 天），发放时 `start_date = 发放当天`、`end_date = 发放当天 + duration_days`；面试通过即流转到技能测试阶段，候补观察改判通过与会场补录通过走同一分支，三个入口都能自动发放。
- 模板保存新增 `syncPending` 开关：关闭时只影响之后新发放的任务；打开时同步所有 `PENDING` 新手任务的标题、正文与截止日期，子任务按标题匹配重建并保留同名项的勾选状态，`end_date` 按同步当天加时长重算以给足完整新时长；已提交待确认与已终态的对象不动，避免把已提交内容突然判为不达标。
- 转录正守卫为「存在新手任务则必须已通过」，被拦下时同时提供「打回技能测试阶段」与「豁免并转正」两个动作；豁免需填理由并写入 `task_assignments.exemption_reason` 与状态历史。
- 明确新手任务到期后不自动处理：只显示「已逾期」，不自动打回、不自动拒绝、不发提醒，由管理员手动延长或打回，符合「不加入自动判断模块」。
- 记录 `MemberStatus.TRIAL`（成员档案的试用状态）与招新阶段无关，本次保留不动；若要一并停用需单独确认，因其影响成员管理、项目成员选项与积分统计。
- 列出删除试用期需同步改动的 8 个文件，并把实施批次由五批调整为六批，新增「删除试用期阶段」独立批次，测试计划补充删除试用期、打回、模板同步与历史枚举兼容性用例。

### 验证结果

- 本次仅更新设计文档与开发日志，未改动后端、前端、数据库迁移或生产配置，因此未运行构建与测试；未连接生产环境，也未执行部署。
- 已通过全仓库检索确认 `PROBATION` 的全部引用位置：`RecruitmentStage` 枚举、两行转移表、`convertToMember` 前置条件、`IdentityRecruitmentApiTests` 第 125 行，以及 `RecruitmentView.vue`、`AdminRecruitmentView.vue`、`AuthView.vue` 三个前端文件。

### 待办

- 等待确认第 6.4 节（是否一并停用成员档案的试用状态）与其他细节后，按第 15 节六批顺序实施。
- 实施时需改造 `IdentityRecruitmentApiTests` 中依赖试用期转正的用例，并全量回归现有 36 项测试。

## 2026-09-17：试用期批量回退脚本设计

### 完成内容

- 纠正上一版对用户意图的理解：历史试用期记录**不逐条手动打回，也不删除任何账号或报名记录**，而是新增 Flyway 数据迁移脚本批量转回技能测试阶段。设计文档第 6 节改为「行为上删除、枚举上保留、数据用脚本批量回退」。
- 新增 `V12__retire_probation_stage.sql` 设计：用临时表记录待回退记录，先写入 `recruitment_status_history` 再执行 `UPDATE recruitment_applications SET stage = 'SKILL_TEST' WHERE stage = 'PROBATION'`，全程只修正阶段字段。
- 核实并据此确定脚本写法：状态历史表名为**单数** `recruitment_status_history`（上一版文档误写为复数，已全部更正）；该表的 `application_id` 与 `operator_account_id` **均无外键约束**，`operator_username` 为冗余存储且渲染不依赖关联 `accounts`。
- 回退历史统一记为系统操作：`operator_account_id` 用全零保留 UUID、`operator_username` 用 `system`，并在备注写明「试用期阶段已取消，系统批量回退至技能测试阶段」。把批量操作挂到某位管理员名下不符合事实，因此不采用「取第一个教师账号」的做法。
- 明确脚本**不做新手任务补发**：新手任务正文、子任务清单与时长属于业务配置，写进 SQL 会与 Java 内置默认模板形成两份富文本内容并必然漂移。改为新增管理端幂等操作「批量补发新手任务」，覆盖刚回退的记录与功能上线前已在技能测试阶段的记录，并列为上线后必做步骤。
- 新增第 6.5 节上线检查清单：备份生产库、先在预生产演练 `V11`+`V12` 并核对记录数与历史新增行数、部署后确认试用期记录数为 0、抽查状态历史、执行批量补发、抽查一条新手任务转正。
- 记录迁移脚本的验证缺口：测试环境关闭 Flyway 且使用 H2，而迁移是 MySQL 专用语法（`ENUM`、`BINARY(16)`、`UUID_TO_BIN`），因此 `V11`/`V12` **无法被自动化测试覆盖**，必须在预生产 MySQL 人工演练。
- 管理端接口表新增 `POST /api/v1/admin/recruitment/onboarding-tasks/backfill`；权限表把「批量补发」并入 `TASK_MANAGE`；实施批次由六批调整为七批，把 `V12` 脚本单列为第三批便于独立演练；测试计划补充批量补发幂等用例。

### 验证结果

- 本次仅更新设计文档与开发日志，未改动后端、前端、数据库迁移或生产配置，因此未运行构建与测试；未连接生产环境，也未执行部署。
- 已通过源码与建表语句核对 `recruitment_status_history` 的表名、列定义与外键情况（`grep` 确认文档中已无复数表名残留），并确认 `V1__baseline_schema.sql` 第 215、252—253 行与 `accounts` 表结构。

### 待办

- 等待确认第 6.6 节（是否一并停用成员档案的试用状态）后按第 15 节七批顺序实施。
- `V11`/`V12` 无自动化测试覆盖，必须在有 MySQL 的环境先行演练；本机无 Docker daemon 与 Compose 插件，此项需在预生产或服务器完成。

## 2026-09-17：成员状态精简设计

### 完成内容

- 按用户确认精简多余的状态设计：**停用 `MemberStatus.CANDIDATE` / `PAUSED` / `EXITED` 三个成员状态**。设计文档第 6 节由「删除试用期阶段」改为「阶段与状态精简」，两项共用同一处理原则——行为上删除、枚举上保留仅供读取、存量数据不迁移。
- 核实停用依据：这三个状态**零业务引用**，后端没有任何逻辑判断它们，只出现在 `MemberStatus` 枚举声明、`V1__baseline_schema.sql` 第 28 行 `member_profiles.status` 的 ENUM 定义，以及 `MemberProfileDisplay.vue` 与 `AdminMembersView.vue` 的 `statusLabels` 标签表。
- 确定停用方式：枚举值与数据库 ENUM 保留并标注「已停用，仅兼容历史读取」；`AdminMembersView.vue` 把 `statusLabels` 拆成「全量标签（只读展示）」与「可选状态（下拉选项）」两份，下拉只提供 `TRIAL` 与 `OFFICIAL`；`MemberProfileDisplay.vue` 保留全量标签，保证历史成员详情页仍能显示「暂停 / 退出」。
- 新增后端校验点：`MemberProfileService.updateManagedMember` 与创建学生管理员入口**拒绝**把状态设为这三个值，避免绕过界面直接调接口写入。这是本次新增的校验，已列入测试计划。
- 记录保留不动的范围：`MemberStatus.TRIAL`（成员档案的试用状态，与招新阶段无关）、`MemberStatus.OFFICIAL`、`TEACHER` 与 `CORE_STUDENT` 两个角色（权限相同但人群与标签不同），以及 `QUIZ_MANAGE` / `QUIZ_PARTICIPATE` / `QUESTION_WRITE` / `TAG_MANAGE` / `SYSTEM_ADMIN` 五个占位权限——后者无 `hasAuthority` 引用，但 `AGENTS.md` 与 `DEVLOG.md` 明确记载「测验、写题及其管理业务仍只保留权限/字段」，属已声明的预留，删除等于放弃该约定，故未删。
- 补齐改写时丢失的「等级维度与现有字段」映射表（第 3.1 节），并把 `MEMBER_STATUS` 维度的可用值同步为 `TRIAL` / `OFFICIAL`，注明 `VISITOR` 无成员档案不可作为对象。
- 更新需同步改动的文件清单、上线检查清单（新增第 7 项：确认状态下拉只剩「试用 / 正式」且历史成员详情仍可显示）、实施批次名称，并新增「成员状态精简」测试用例组。

### 验证结果

- 本次仅更新设计文档与开发日志，未改动后端、前端、数据库迁移或生产配置，因此未运行构建与测试；未连接生产环境，也未执行部署。
- 已逐项检索确认各状态的引用位置与数量（`CANDIDATE`、`EXITED` 各 1 处业务外引用，`PAUSED` 仅标签表，`TRIAL` 有 3 处后端判断），并确认 `member_profiles.status` 为含全部 5 个取值的 MySQL ENUM，故不能直接删除枚举值。
- 已修正文档内因新增小节导致的交叉引用编号（6.5 → 6.6），`git diff --check` 通过。

### 待办

- 按第 15 节七批顺序实施；第 2 批已扩展为「删除试用期阶段与精简成员状态」。
- 实施时需回归现有 36 项测试，重点是成员管理相关用例与 `RolePermissionTests`。

## 2026-09-17：任务模块需求清单

### 完成内容

- 新增 `docs/task-module-requirements.md`，把此前多轮确认的设计收敛为可逐条审核的需求清单，共 6 组 46 条编号需求，覆盖招新流程改造、新手任务、普通任务、成员状态精简、权限、数据库与文档，并附界面入口、明确不做项、施工批次与上线强制验收步骤。
- 澄清一处沟通误会：此前描述的是**当前已部署代码**的实际招新流程（仍含试用期），并非设计结论；设计结论为删除试用期、技能测试通过后直接转为正式成员。代码尚未做任何改动。
- 待用户审核后开始施工；默认按「保留五个占位权限」与「存量停用状态仍按原文字显示」两项施工，若需调整由用户说明。

### 验证结果

- 本次仅新增需求清单与开发日志，未改动后端、前端、数据库迁移或生产配置，因此未运行构建与测试；未连接生产环境，也未执行部署。
- 清单中的 46 条需求均逐条对应 `docs/task-module-design.md` 的既定设计，未新增未经确认的范围。

### 待办

- 等待用户审核需求清单；通过后按第 6 节批次从第 1 批（`V11` 迁移 + 实体 + `TASK_MANAGE` 权限）开始施工。

## 2026-09-17：更新 AGENTS.md 协作约定

### 完成内容

- 按当前需求重写根目录 `AGENTS.md`，由原来的 4 条纯流程约定扩展为「协作流程 + 项目口径」结构。
- 修正原第 4 条的过时记载：原文把「积分及其管理业务」与测验并列为「只保留权限/字段、不实现业务功能」，但积分账本与管理员发放接口实际已实现（V8 迁移与积分模块已上线）；现改为只保留**测验、写题**三项权限，并补充讨论板、站内消息、积分账本与管理员积分发放为已实现能力。
- 新增「招新流程口径」：明确流程为报名 → 初筛 → 面试 → 技能测试 → 正式成员、没有试用期、技能测试通过新手任务审核后直接转正，并标注该口径为**目标口径**、施工前代码仍为旧版，避免把设计结论误读为现状。
- 新增「阶段与状态变更原则」：把本次设计形成的做法固化为约定——删除阶段或状态时采用「行为上删除、枚举上保留仅供读取」，不改数据库 ENUM、不迁移存量数据；确需迁移时使用独立 Flyway 脚本并写入可审计的状态历史、以系统身份标注操作人。
- 新增「数据库迁移约定」：明确迁移只写 MySQL 语法且无法被自动化测试覆盖，必须列出人工验收步骤，不得以构建或测试通过代替实机验证；并保留禁止 `docker compose down -v` 与删除生产业务数据的红线。
- 新增协作流程第 4 条：涉及新模块或流程改造时先确认需求清单与技术设计文档，两者冲突以书面文档为准，并指向 `docs/task-module-requirements.md` 与 `docs/task-module-design.md`。

### 验证结果

- 本次仅修改 `AGENTS.md` 与开发日志，未改动后端、前端、数据库迁移或生产配置，因此未运行构建与测试；未连接生产环境，也未执行部署。
- 已核对 `AGENTS.md` 中「已实现能力」与 `DEVLOG.md` 的当前状态记载及实际代码模块一致；`git diff --check` 通过。

### 待办

- 等待用户审核 `docs/task-module-requirements.md`；通过后按第 6 节批次从第 1 批开始施工，并在第 2 批完成后把「招新流程口径」的目标口径说明移除。

## 2026-09-17：任务模块施工第 1 批（数据与领域层）

### 完成内容

- 新增 Flyway `V11__task_module.sql`，只新建 7 张表，不修改或删除任何现有表与数据：`tasks`、`task_subtasks`、`task_audience_rules`、`task_assignments`、`task_subtask_progress`、`onboarding_task_template`、`onboarding_task_template_subtasks`。
- `task_assignments` 用 `CHECK ((member_profile_id IS NULL) <> (recruitment_application_id IS NULL))` 保证双目标恰好一个非空，并对 `(task_id, member_profile_id)` 与 `(task_id, recruitment_application_id)` 分别加唯一约束；`task_assignments` 建立指向 `accounts`、`member_profiles`、`recruitment_applications`、`point_grants` 的外键。
- 新增 `task` 模块 12 个领域类：5 个枚举（`TaskType`、`TaskStatus`、`TaskAssignmentStatus`、`TaskAssignmentSource`、`TaskAudienceDimension`）与 7 个实体，实体内部只封装状态流转方法（提交、通过、驳回、记录计分、记录转正、记录豁免、勾选子任务），不含任何业务编排。
- 新增 7 个 Spring Data 仓库接口，含后续批次需要的派生查询（按成员读取本人任务、按报名记录判断是否已有在途新手任务、按任务统计状态等）。
- 在 `Permission` 枚举新增 `TASK_MANAGE` 并加入 `Role.adminPermissions()`，教师与核心学生自动获得；`RolePermissionTests` 补充 `TASK_MANAGE` 的正反向断言（教师/核心学生拥有，普通成员与游客都没有）。
- 两处实现细节与设计文档对齐：`task_audience_rules` 的取值列命名为 `rule_value`（`value` 是 H2 保留字，否则测试建表会失败）；`task_assignments.completion_note` 由 TEXT 改为 LONGTEXT。已同步更新 `docs/task-module-design.md` 的字段表。

### 验证结果

- 使用 Java 21 执行 `./mvnw compile` 通过。
- 使用 Java 21 执行后端全量测试：**36 项通过，0 失败、0 错误、0 跳过**，新增实体与仓库在 H2 `create-drop` 下建表与装配正常，未影响既有模块。
- 未执行 `V11` 的 MySQL 实机迁移（测试环境关闭 Flyway 且为 H2），该步骤列入上线前人工验收，见 `docs/task-module-requirements.md` 第 6 节。

### 待办

- 进入第 2 批：删除试用期阶段与精简成员状态（转移表调整、转正守卫、打回与豁免、成员状态可选值收窄与后端校验、前端阶段与文案调整）。

## 2026-09-17：任务模块施工第 2 批（删除试用期阶段与精简成员状态）

### 完成内容

- **删除试用期阶段**：`ALLOWED_TRANSITIONS` 改为 `SKILL_TEST → {FORMAL_MEMBER, REJECTED}` 与 `PROBATION → {SKILL_TEST, REJECTED}`，即没有任何路径可以进入试用期，试用期只能被离开（打回）；`RecruitmentStage.PROBATION` 保留并标注为已停用、仅兼容历史数据反序列化。
- **转正前置条件改为技能测试阶段**：`convertToMember` 不再要求 `stage == PROBATION`，改为要求 `SKILL_TEST`，并新增转正守卫与可选的 `exemptionReason`。
- **抽出共用转正核心** `convertApplicantToMember(...)`，供管理员直接转正与后续新手任务审核通过两条路径复用，建档案与状态流转只有一份实现。
- **新增 `OnboardingTaskGate` 接口**解耦招新与任务模块：接口定义在招新模块，实现在任务模块（`TaskOnboardingGate`），避免两个包相互依赖。守卫规则为「存在新手任务则必须已通过；不存在则按历史记录放行并在状态历史写明原因」；豁免时关闭在途新手任务并记录豁免理由与生成的成员档案。
- **精简成员状态**：`MemberStatus.CANDIDATE` / `PAUSED` / `EXITED` 标注为已停用；`MemberProfileService` 新增 `validateSelectableStatus`，成员管理与创建学生管理员两个入口都拒绝写入这三个值（错误信息「成员状态只支持「试用」或「正式」」）；枚举值与数据库 ENUM 不动，存量数据不迁移。
- **前端调整**：报名页阶段数组去掉试用期并保留历史标签；`AuthView` 流程文案改为「技能测试与正式成员」；`AdminRecruitmentView` 的 `nextStages` 去掉技能测试→试用期，转正卡片移到技能测试阶段并新增可选豁免理由输入，另为历史试用期记录新增「打回技能测试阶段」卡片；`MemberProfileDisplay` 保留全量状态标签用于只读展示；`AdminMembersView` 新增 `selectableStatuses`（只含试用与正式），并把历史停用状态渲染为禁用的「已停用，请改选」选项，避免静默改值。
- **测试**：改造原有转正用例（去掉试用期流转，状态历史断言 6 → 5）；新增两项集成测试——试用期停用与历史记录打回（含直接改写容器内实体模拟遗留数据、验证试用期不能直接转正），以及停用成员状态被拒绝、试用与正式仍可设置。
- **文档**：同步 `backend/docs/access-control.md`（权限矩阵新增 `TASK_MANAGE`、招新状态机与转正守卫、成员状态取值范围）、`backend/docs/module-boundaries.md`（招新流程、转正口径、新手任务接口入口）与 `README.md`（招新流程步骤与能力清单）。

### 验证结果

- 使用 Java 21 执行后端全量测试：**38 项通过，0 失败、0 错误、0 跳过**（原 36 项 + 新增 2 项）。
- `npm run check` 通过：ESLint、Prettier 与 Vite 生产构建均成功，仅保留既有 Three.js 主包超过 500 kB 的分块提示。
- `git diff --check` 通过。首轮失败原因记录：新增测试的注册密码超过 6—18 位限制，以及未先记录面试通过导致技能测试推进被拒，均已修正。
- `V12` 数据回退脚本属于下一批，本批未改动存量数据。

### 待办

- 进入第 3 批：新增 `V12__retire_probation_stage.sql`，批量把试用期记录回退到技能测试阶段并写入状态历史，不删除任何账号或报名记录。

## 2026-09-17：任务模块施工第 3 批（试用期批量回退脚本）

### 完成内容

- 新增 `backend/src/main/resources/db/migration/V12__retire_probation_stage.sql`：用临时表记录待回退记录，先以系统身份写入 `recruitment_status_history`，再执行 `UPDATE recruitment_applications SET stage = 'SKILL_TEST' WHERE stage = 'PROBATION'`。全程只修正阶段字段，不删除任何账号、报名记录或成员档案，也不改动任何 ENUM 定义。
- 回退历史的操作人使用系统保留值（全零 UUID 与 `system`）：该表 `operator_account_id` 无外键约束、`operator_username` 为冗余展示字段，因此无需把批量操作归属到某位管理员名下。
- 脚本刻意不生成新手任务：新手任务正文与子任务清单属于业务配置，复制进 SQL 会与代码内置默认模板形成两份富文本内容；补发改由管理端幂等操作完成，并写入状态历史备注提示。

### 验证结果

- **本机存在可用的 MySQL，因此本次突破了此前「迁移无法验证」的限制**：用独立 datadir 与端口在 `/tmp` 启动了一个临时 MySQL 实例（9.5.0），完整执行 `V1`—`V11` 全部历史迁移，**14 个迁移文件全部成功**。
- 构造测试数据（2 条试用期、1 条技能测试、1 条面试）后执行 `V12`：试用期记录数由 2 变为 0，两条记录全部转为 `SKILL_TEST`，其余阶段记录未被改动；`recruitment_status_history` 新增 2 行 `PROBATION → SKILL_TEST` 的 `system` 记录。重复执行 `V12` 后历史行数不变，确认幂等。
- 验证 `V11` 的 `CHECK` 约束：`member_profile_id` 与 `recruitment_application_id` 同时为空或同时非空都被 MySQL 拒绝（错误 3819），恰好一个非空时插入成功。
- **用真实 MySQL 完成 Hibernate 结构校验**：以 `ddl-auto=validate`、`flyway.enabled=false` 连接该实例启动 Spring Boot，应用正常启动（`Started YesLabApplication`，JDBC URL 为 `jdbc:mysql://127.0.0.1:3399/yeslab_probe`，驱动 MySQL Connector/J），说明 `V1`—`V12` 建出的表结构与全部实体映射一致，包含新增的 7 张任务表。
- 验证后已关闭临时实例并删除 `/tmp/yeslab-mysql`，未触碰本机原有的两个 MySQL 实例，也未改动生产环境。
- 局限性说明：本机 MySQL 为 9.5.0，而生产目标是 8.4，因此 `V11`/`V12` 的正式演练仍应在上线检查清单中的预生产 8.4 库执行；本次验证覆盖了语法、约束语义、回退结果、幂等性与实体结构一致性。
- 本批不含 Java 代码，未运行后端测试；`git diff --check` 通过。

### 待办

- 进入第 4 批：新手任务（模板与内置兜底、面试通过自动发放、报名者端接口、模板同步在途、批量补发、审核通过即转正）。

## 2026-09-17：任务模块施工第 4 批（新手任务）

### 完成内容

- 新增 `OnboardingTaskIssuer` 接口（招新模块定义）与 `OnboardingTaskIssuerService`（任务模块实现），在 `RecruitmentService.changeStage` 中当目标阶段为技能测试时触发发放，因此**手动推进、场次内提交通过、候补改判通过与场次结束后补录通过四个入口都会自动发放**；发放幂等，已有待完成/待确认/已通过的新手任务时不重复发放。
- 新增 `OnboardingTemplateService`：模板读取、保存与在途同步。模板不存在时用**代码内置默认内容**（标题、富文本说明、5 项子任务、7 天时长）自动落库，内置内容只维护这一份。保存模板时支持 `syncPending` 开关，打开后同步所有「已发布且未提交」的新手任务：覆盖标题与正文、子任务按标题匹配重建并保留同名项勾选、截止日期按同步当天加时长重算、仅作用于待完成对象，并逐个发送站内消息。
- 子任务同步先在 flush 前显式删除将被移除子任务的勾选记录，再调整子任务结构，避免子任务与勾选记录之间的外键阻止删除；`TaskEntity.syncSubtasks` 负责按标题保留、删除、新增与重排。
- 新增 `TaskService`：报名者端读取本人新手任务、勾选子任务、提交完成说明；管理端新手任务总览、批量补发与人工审核。审核通过时先在事务内落「已通过」使转正守卫放行，再调用招新模块的共用转正核心完成建档案与阶段流转，因此**建档案与转正逻辑仍然只有一份实现**；驳回必填意见且可重新提交；豁免理由会关闭在途任务并写入任务记录与状态历史。
- 新增 `TaskModels`（含第 5 批普通任务的请求与视图定义）、报名者端 `OnboardingTaskController`（挂在 `/api/v1/recruitment/me/onboarding-task`，沿用报名自身权限）与管理端 `AdminTaskController`（模板读写、总览、批量补发、人工审核）。
- **修复施工中发现的 Spring 循环依赖**：`TaskService` 依赖 `RecruitmentService` 完成转正，而 `RecruitmentService` 又依赖 `OnboardingTaskIssuer`；最初把发放逻辑放在 `TaskService` 中导致 `recruitmentService ↔ taskService` 无法创建。改为把发放抽成不依赖招新服务的独立 bean `OnboardingTaskIssuerService`，切断回边，未使用 `@Lazy` 绕过。
- 新增集成测试 `OnboardingTaskApiTests` 4 项：面试通过自动发放与审核转正全流程（含未提交不可通过、普通成员越权 403、转正后成员资料与权限、已通过不可再改）、驳回必填意见并可重新提交、转正守卫拦截与豁免放行、模板同步（保留同名勾选、新增项、时长改为 10 天、截止日期重算）与批量补发幂等。
- 改造既有 `visitorCanApplyAndAdminCanConvertTheAccountToMember`：技能测试阶段现在会自动发放新手任务，直接调用转正接口会被守卫拦下，因此改为走「完成新手任务 → 审核通过」的真实路径，并断言状态历史为 5 条。
- 测试对共享 H2 上下文做了隔离：模板是全局单例，`OnboardingTaskApiTests` 在每个用例前重置为已知模板（不开启同步），并把同步条数断言改为至少 1，避免依赖用例执行顺序。

### 验证结果

- 使用 Java 21 执行后端全量测试：**42 项通过，0 失败、0 错误、0 跳过**（第 2 批后 38 项 + 新增 4 项）。
- `npm run check` 通过：ESLint、Prettier 与 Vite 生产构建均成功，仅保留既有 Three.js 分块提示。
- `git diff --check` 通过。
- 首轮全量运行出现两项失败，均为真实问题而非偶发：既有转正用例未适配新手任务守卫；模板同步条数断言受共享上下文影响。已分别修正，未通过放宽业务校验来掩盖。

### 待办

- 进入第 5 批：普通任务与积分（等级条件解析与预览、发布快照与积分锁定、成员端任务、补充发放、审核计分、完成情况汇总）。

## 2026-09-17：任务模块施工第 5 批（普通任务与积分）

### 完成内容

- 在 `PointService` 新增 `grantForTask(...)`：把任务来源的积分发放封装在积分模块内部，任务模块只传任务、成员、分值与凭证链接。来源编号固定为 `TASK:{taskId}:{memberProfileId}`，发放前先查是否已存在，重复审核直接复用原批次，**不重复计分也不报错**。教师、非正式成员、审核人本人三种情况按「跳过并返回原因」处理，而不是抛错，且**不放宽积分模块任何既有规则**。
- 判定的优先级调整为「教师 → 非正式成员 → 审核人本人」，与设计文档列举顺序一致；预览与实发共用同一套判定。
- `TaskService` 新增普通任务全部逻辑：创建草稿、修改、发布、结束、删除草稿、列表与汇总、完成情况、按条件预览、补充发放、移除对象、人工审核计分，以及成员端的我的任务、详情、勾选子任务与提交完成说明。
- 等级条件复用现有字段（角色、成员状态、年级、能力标签），**同维度取「或」、跨维度取「且」**；已停用的成员状态不能作为条件。`VISITOR` 因没有成员档案不会命中。
- 发布时快照对象并锁定积分：发布后条件、对象与积分值不可修改，只能改标题、正文、起止日期与子任务；已发布任务的子任务可以增删，删除时先清理对应勾选记录再调整结构，避开外键。
- 人工审核统一为一个端点：按任务类型分派，普通任务通过时计分并回写 `point_grant_id` 与 `awarded_points`，新手任务通过时转正；两者共用统一的 `ReviewResultView`，驳回都必须填写意见且不发分。
- **修复施工中发现的真实缺陷**：发布任务时用 `assignments.saveAll(...)` 写入对象，任务聚合内的集合不会同步，导致发布响应里的对象数为 0。改为在 `TaskEntity` 上提供 `addAssignment` / `removeAssignment`，新增与移除统一走聚合集合（集合本身是 cascade=ALL + orphanRemoval），使事务内视图与数据库一致。
- 新增 `TaskController`（成员端 `/api/v1/tasks`）并扩展 `AdminTaskController`（普通任务全部管理端接口 + 统一审核端点）。
- 新增 `backend/docs/module-boundaries.md` 的「任务模块」章节，覆盖两类任务、全部接口、等级条件语义与计分跳过规则。

### 验证结果

- 使用 Java 21 执行后端全量测试：**47 项通过，0 失败、0 错误、0 跳过**（第 4 批后 42 项 + 新增 `TaskApiTests` 5 项）。
- 新增测试覆盖：同维度「或」与跨维度「且」（含负例）、教师对象在预览中标记不可计分、停用成员状态不能作为条件、普通成员调用管理端 403、发布快照与对象数、发布后积分锁定与规则不变、已发布任务不能重复发布或删除、成员端勾选与提交、未提交不能通过、他人对象 404、审核通过自动计分且重复审核不重复计分、积分总值实际增加、完成情况汇总与子任务完成率、驳回必填意见且不计分、驳回后可重新提交、教师与非正式成员与审核人本人三种跳过情形、积分 0 不产生积分记录、任务结束后成员只读但管理员仍可审核、富文本脚本与事件属性被清洗。
- **发现并修复测试隔离问题**：`TaskApiTests` 最初未加 `@Transactional`，其提交的积分改动污染了既有的 `PointApiTests`（该类依赖逐用例回滚做绝对断言），导致两项既有断言失败。按仓库既有模式为 `TaskApiTests` 加上 `@Transactional` 后恢复隔离，**未修改或放宽任何既有测试**。
- `npm run check` 通过：ESLint、Prettier 与 Vite 生产构建均成功，仅保留既有 Three.js 分块提示；`git diff --check` 通过。

### 待办

- 进入第 7 批：前端接口封装、组件、页面、路由、导航，以及在报名页与招新管理中嵌入新手任务面板。

## 2026-09-17：任务模块施工第 7 批（前端）

### 完成内容

- `src/services/authApi.js` 新增任务模块接口封装（新手任务读写、模板读写、总览与批量补发、普通任务增删改查与发布/结束、条件预览、补充发放与移除对象、完成情况、统一审核、成员端我的任务与提交）。沿用仓库既有约定把接口集中在该文件，未新建 `taskApi.js`，避免为复用请求封装而改动既有私有函数。
- 新增 `OnboardingTaskPanel.vue`：新手任务的展示与提交面板，报名者可直接勾选子任务、填写完成说明、查看剩余天数与驳回意见；`editable` 关闭时作为纯展示组件复用。
- 新增成员端 `TasksView.vue`（我的任务，支持按状态筛选，显示子任务进度、积分与逾期）与 `TaskDetailView.vue`（任务详情、勾选子任务、提交完成说明、查看审核意见与计分结果）。
- 新增管理端 `AdminTasksView.vue`（任务列表与汇总、创建与编辑、等级条件多选、直接指定成员、命中名单预览含计分状态、发布/结束/删除）、`AdminTaskProgressView.vue`（任务汇总、子任务完成率、逐人明细、人工审核、补充发放与移除对象）与 `AdminOnboardingTemplateView.vue`（模板编辑含时长与同步开关、技能测试阶段完成情况、审核转正与豁免、批量补发）。
- **复用既有富文本编辑器** `DiscussionRichTextEditor.vue`（它已支持 `label` 与 `maxLength`），未新建重复的编辑器组件。
- 新增后端辅助接口 `GET /api/v1/admin/tasks/member-options`，为「直接指定成员」提供成员列表，避免前端跨模块调用项目模块的成员选项接口；`OnboardingRowView` 补充 `taskId` 供审核调用。
- 路由新增 `/tasks`、`/tasks/:assignmentId`、`/admin/tasks`、`/admin/tasks/onboarding`、`/admin/tasks/:taskId/progress`，全部按路由懒加载。
- `PortalShell.vue` 在成员系统顶栏与后台侧栏快捷入口新增「我的任务」，在后台侧栏与「后台管理」下拉新增「任务管理」。
- `RecruitmentView.vue` 在技能测试阶段嵌入新手任务面板；`AdminRecruitmentView.vue` 的转正卡片补充说明，并在那里链接到任务管理中的新手任务审核页，避免两个入口重复实现转换表单。
- `src/portal.css` 新增任务模块样式：任务卡片网格、完成情况汇总、子任务完成率、逐人明细、条件与预览区、详情面板与审核表单；延续既有语义色令牌、可见焦点、44px 触控目标、减少动态效果与窄屏单列适配。

### 验证结果

- `npm run check` 通过：ESLint、Prettier 与 Vite 生产构建均成功；构建输出中新增 `TasksView`、`TaskDetailView`、`AdminTasksView`、`AdminTaskProgressView`、`AdminOnboardingTemplateView` 与 `OnboardingTaskPanel` 等按路由拆分的产物，仅保留既有 Three.js 主包超过 500 kB 的分块提示。
- 使用 Java 21 执行后端全量测试：**47 项通过，0 失败、0 错误、0 跳过**。
- `git diff --check` 通过。
- 修复过程中出现的问题：`AdminOnboardingTemplateView` 引用未使用的图标导致 ESLint 报错；六个新前端文件未符合 Prettier 格式。均已修正后重新通过检查。
- 受限于当前环境没有可连接的浏览器实例，**未执行前端点击与截图视觉验收**；本次仅完成构建、静态检查与接口契约核对，不将构建通过当作浏览器验收通过。

### 待办与上线说明

- 上线前需在预生产 MySQL 演练 `V11`、`V12`，按需求清单第 6 节检查清单逐项验收，并在部署后执行一次「批量补发新手任务」。
- 建议在可用的浏览器环境中补做一次真实点击验收：报名者完成新手任务、管理员审核转正、普通任务发布与计分、驳回与重新提交。
- 若后续要减少重复，可将 `DiscussionService`、`MemberProfileService` 与任务模块各自持有的 OWASP 白名单策略抽成公共常量；本次为降低对既有模块的改动面而未合并。

## 2026-09-17：本地启动与端到端冒烟验证

### 完成内容

- 新增 `scripts/smoke-task-module.sh`：任务模块的端到端冒烟脚本（真实 HTTP，35 项断言），覆盖新手任务转正与普通任务计分两条完整链路，可重复执行。
- 脚本覆盖的检查点：教师与游客登录、报名表提交（含 5 道技术题）、初筛与面试通过、推进技能测试后**自动发放新手任务**（标题、子任务数量、起始日与「发放当天 + 模板时长」的截止日）、子任务勾选、未提交时审核被守卫拦截、提交完成说明、管理端总览、审核通过后**回写成员档案 ID**、重复审核被拒、转正后成员编号与角色、普通任务创建与发布、**发布后积分锁定为原值**、已发布任务仍可改内容、按成员编号定位任务对象、成员端可见与提交、驳回未填意见被拒、审核通过自动计分、**成员总积分实际增加对应分值**、积分流水来源为 `TASK:{taskId}`、结束任务、普通成员访问管理端 403、停用成员状态 PAUSED 被拒。

### 验证结果

- **后端**：以隔离的内存 H2 与 `target/smoke-*` 上传目录启动，健康检查 `UP`；冒烟脚本 **35 项检查全部通过（0 失败）**。
- **前端**：Vite dev server 在 `127.0.0.1:5173` 启动，首页返回 200，同源代理 `/actuator/health` 与 `/api/v1/auth/login` 均返回 200，确认前后端联调链路可用。
- 验证完成后已停止前后端进程并释放 8080/5173 端口；冒烟使用独立内存数据库，**本机 `backend/data/yeslab.mv.db` 的修改时间仍为 9/16，未被写入**。
- 脚本调试过程中修正了 4 处**脚本自身的断言写法问题**（用 `.` 匹配 UUID、`len()` 路径拼接错误、成员编号跨次运行重复导致预期冲突），均为测试脚本缺陷，不是产品缺陷；修正后全部通过。
- `npm run check` 与 `git diff --check` 通过；脚本通过 `bash -n` 语法检查。

### 待办

- 冒烟脚本仅覆盖接口链路；**浏览器点击与截图视觉验收仍未执行**（当前环境无可用浏览器实例），建议在本地按上方步骤启动后手工过一遍关键页面。
- 本地开发使用 H2（`ddl-auto: update`）不会执行 Flyway，因此 `V11` 中的 `CHECK` 约束与唯一约束只在生产 MySQL 生效；本地开发依赖服务层去重与校验。如需本地与生产完全一致，可为实体补充 `@UniqueConstraint` 后重新验证。

## 2026-09-17：VS Code 一键启动验证

### 完成内容

- 逐字执行 `.vscode/tasks.json` 中定义的启动命令，验证「YES Lab: 一键启动本地开发」可用。说明：无法在 VS Code 界面内点击运行任务，因此改为按任务定义的原命令、原工作目录执行，并额外校验任务配置本身。
- 静态检查：`tasks.json` 为合法 JSON，包含 3 个任务；「启动后端」工作目录为 `backend`，「启动前端」为仓库根；「一键启动」以 `parallel` 顺序依赖两者。
- 依赖检查：`/usr/libexec/java_home -v 21` 正常解析到本机 Microsoft OpenJDK 21；`npm` 与 `node` 位于 nvm 的 `v22.22.2` 目录下，在非交互 shell（`zsh -c` / `bash -c`）中同样可解析，因此任务不依赖交互式 shell 的初始化脚本。
- 后端按任务原命令启动（`task_java_home=$(/usr/libexec/java_home -v 21) && export JAVA_HOME=... && ./mvnw spring-boot:run`），10.9 秒完成启动，`Started YesLabApplication`，Tomcat 监听 8080。
- 前端按任务原命令启动（`VITE_API_BASE_URL=http://127.0.0.1:8080 npm run dev -- --host 127.0.0.1`），Vite v8.3.0 在 671 毫秒内就绪并监听 `127.0.0.1:5173`。

### 验证结果

- 后端健康检查 `UP`；前端首页返回 200，入口 HTML 标题正常，`/src/main.js` 存在。
- **跨域 + Cookie 会话链路全部通过**（这是设置 `VITE_API_BASE_URL` 后最容易出问题的环节）：
  - CORS 预检（`OPTIONS`，带 `Authorization` 与 `Content-Type` 请求头，Origin 为 `http://127.0.0.1:5173`）返回 200，响应包含 `Access-Control-Allow-Origin: http://127.0.0.1:5173`、`Access-Control-Allow-Credentials: true` 与允许的方法列表。
  - 跨域登录返回 200，下发 `yeslab_refresh_token`，属性为 `Path=/api/v1/auth; HttpOnly; SameSite=Lax`；由于前后端同属 `127.0.0.1` 站点（端口不同不影响 SameSite 判定），Lax Cookie 会被正常携带。
  - 携带该 Cookie 跨域调用 `/api/v1/auth/refresh` 返回 200，正确恢复账号（范桌轩大王 / MEMBER）并完成 Cookie 轮换，说明页面刷新后的 `restoreSession` 在跨域模式下可用。
- Vite dev server 按需编译新增文件全部返回 200 且无错误标记：`OnboardingTaskPanel.vue`、`TasksView.vue`、`TaskDetailView.vue`、`AdminTasksView.vue`、`AdminTaskProgressView.vue`、`AdminOnboardingTemplateView.vue`、`router/index.js`、`services/authApi.js`。
- 验证后已停止前后端进程，8080 与 5173 均已释放；后端使用内存 H2 与 `target/vs-*` 上传目录，**本机 `backend/data/yeslab.mv.db` 修改时间仍为 9/16，未被写入**；临时目录与日志已清理。

### 发现的一项配置不一致（未擅自修改）

`.vscode/tasks.json` 的前端任务设置了 `VITE_API_BASE_URL=http://127.0.0.1:8080`，使前端**跨域直连**后端，绕过了 `vite.config.js` 中的同源 `/api` 代理。而 2026-08-26 的部署记录明确写着「前后端改为同源 `/api`：本地由 Vite 代理，生产由 Caddy 代理，减少跨域 Cookie 差异」。两种模式经实测都可用，但当前任务配置的跨域模式**与生产环境的同源行为不一致**，本地无法复现生产下的相对路径与 Cookie 作用域表现。

建议把该任务命令改为不设置该变量（`npm run dev -- --host 127.0.0.1`），让本地与生产一致；是否需要调整待确认后再改。

### 待办

- 浏览器点击与截图视觉验收仍未执行（当前环境无可用浏览器实例）。用一键启动后建议手工过一遍：报名页新手任务面板、`/tasks`、`/admin/tasks`、`/admin/tasks/onboarding`、任务完成情况页。
- 一键启动为并行启动，后端约需 11 秒；在日志出现 `Started YesLabApplication` 之前打开页面会看到连接失败提示，属预期现象。

### 补充：tasks.json 前端任务改为同源代理

- 按确认把 `.vscode/tasks.json` 中「YES Lab: 启动前端」的命令由 `VITE_API_BASE_URL=http://127.0.0.1:8080 npm run dev -- --host 127.0.0.1` 改为 `npm run dev -- --host 127.0.0.1`，去掉跨域直连，改为经 `vite.config.js` 的同源 `/api` 代理访问后端，与生产环境由 Caddy 代理的同源行为保持一致。只改这一处，其余任务定义未动。
- 验证结果：以改后的原命令重新启动前后端，后端健康检查与前端首页均正常；经 5173 同源路径 `/actuator/health` 返回 200；同源登录 200、同源刷新会话 200 并正确恢复账号（范桌轩大王 / MEMBER）；未带令牌经 5173 调用 `/api/v1/recruitment/me/questions` 返回 401，说明请求确实走相对路径经 Vite 代理转发。前端模块中 `apiBaseUrl` 解析为空字符串，确认 `VITE_API_BASE_URL` 已不再注入。
- Cookie 仍为 `Path=/api/v1/auth; HttpOnly; SameSite=Lax`；同源模式下不再依赖跨域 Cookie 行为，本地与生产的会话表现一致。
- 验证后已停止前后端进程并释放 8080/5173；后端仍使用内存 H2 与 `target/vs2-*` 目录，本机 `backend/data/yeslab.mv.db` 未被写入；临时产物已清理。
- 还原方式：如需回到跨域直连，把该行改回 `VITE_API_BASE_URL=http://127.0.0.1:8080 npm run dev -- --host 127.0.0.1` 即可。

## 2026-09-20：本地登录报 502 的根因定位与修复

### 现象

本地打开页面登录时提示「请求失败（502）」。

### 根因

502 来自 Vite 代理：前端经同源 `/api` 代理转发到 `127.0.0.1:8080`，而后端进程并未在运行，代理返回 502。进一步定位到后端**无法完成启动**，因果链如下：

1. 本地 H2 持久化库 `backend/data/yeslab.mv.db` 的 `member_profiles` 表**缺少 `showcase_configured` 列**。
2. `application.yml` 本地使用 `ddl-auto: update`，Hibernate 尝试执行 `alter table member_profiles add column showcase_configured boolean not null`；由于该表已有 3 行数据且 DDL 未带 DEFAULT，H2 拒绝（`NULL not allowed for column "SHOWCASE_CONFIGURED"`）。
3. **Hibernate 只把这条 DDL 失败记为 WARNING 并继续**，因此应用看起来「启动成功」（日志出现 `Tomcat started on port 8080` 与 `Started YesLabApplication`）。
4. 紧接着启动流程中第一次查询 `member_profiles` 就抛 `Column "MPE1_0.SHOWCASE_CONFIGURED" not found`，应用优雅关闭，Maven 以 `BUILD FAILURE` 退出。

这是 `DEVLOG.md` 于 2026-09-06 记录、当时明确搁置的遗留问题（「本地持久化 H2 的历史 `showcase_configured` 缺列问题仍待单独处理」），与本次任务模块改动无关。

为什么只有这一列缺失：全部迁移中带 `NOT NULL` 的新增列只有 `member_profiles.showcase_configured`（V4）、`recruitment_applications.interview_result_pending`（V10）与 `discussion_posts.announcement/pinned`（V7_1_1）。后三者当时对应表为空，H2 可以为空表添加 NOT NULL 列；而 `member_profiles` 有数据，因此只有它失败。逐列核对确认本地库中这四项中仅 `showcase_configured` 缺失。

### 处理方式

选择**修复本地库**而非重建：先确认本地库里的成员资料包含使用者自行修改的内容（S-001 姓名已从播种值「范桌轩大王」改为「烤小鱼大王」），重建会丢失这些本地数据。

- 修改前已备份到 `/tmp/yeslab-before.mv.db`。
- 执行并验证：`ALTER TABLE member_profiles ADD COLUMN IF NOT EXISTS showcase_configured BOOLEAN DEFAULT FALSE NOT NULL;`，3 位既有成员正确回填为 `FALSE`，列属性为 `BOOLEAN / NOT NULL / DEFAULT FALSE`。
- 已在数据库副本上先验证该语句，再应用到真实库；修改时确认 8080 无进程占用。

### 验证结果

- 按 `.vscode/tasks.json` 的后端原命令、使用真实的本地库配置启动：**启动成功且无任何 schema 报错**（`SHOWCASE_CONFIGURED` 相关错误数为 0），健康检查 `UP`，`Started YesLabApplication in 12.2s`。
- 复现使用者的操作路径——经已运行的前端 `127.0.0.1:5173` 同源代理登录：返回 **200**，正确显示「汤洪大王 / TEACHER」，502 消失。
- 在真实本地库上抽查任务模块只读接口，全部 200：`/api/v1/admin/tasks`、`/admin/tasks/onboarding-template`、`/admin/tasks/onboarding-overview`、`/admin/tasks/member-options`，以及回归项 `/admin/members`、`/points/rules`。
- 数据核对：新手任务模板返回内置默认内容（时长 7 天、5 项子任务）；本地库 3 位成员资料完整（汤洪大王 T-001 TEACHER、范桌轩大王 S-CORE-001 CORE_STUDENT、烤小鱼大王 S-001 MEMBER），使用者自行修改的姓名未受影响。
- 验证结束后已停止本次启动的后端进程以释放 8080，便于使用者用自己的 VS Code 任务启动（其前端进程仍在 5173 运行）；临时文件已清理，修复前备份保留在 `/tmp/yeslab-before.mv.db`。

### 待办与建议

- 建议后续在实体的 NOT NULL 布尔字段上补 `columnDefinition`（例如 `boolean default false not null`），使 Hibernate 生成的 DDL 自带默认值，从而让 `ddl-auto: update` 在旧库上也能自愈；生产用 `validate`，不受影响。本次未擅自修改实体，待确认后再做。
- 更彻底的做法是让本地开发也走迁移（需要为非 MySQL 方言准备一套 H2 迁移），或在启动流程中加入结构自检，避免 Hibernate 静默吞掉 DDL 失败后以误导性的运行时错误退出。

## 2026-09-20：DEVLOG 拆分归档

- 为降低每次任务前的必读上下文，`DEVLOG.md` 只保留「当前状态 / 使用说明补充 / 后续待办」和最近 20 条记录，更早的 90 条记录（2026-08-24 ~ 2026-09-17）整体移入 `docs/devlog-archive.md`。
- 归档文件保留原始正文与日期标题，并在顶部提供按时间排列的完整索引，可用关键词直接检索定位历史细节。
- 正文一律未改写，只调整存放位置；新增的仅是说明行、归档索引和本条记录。
- 待办：「后续待办」小节仍是 2026-08-25 的记录，其中头像上传等条目已实现，需在下一次任务中重新梳理。

## 2026-09-20：网站构建接入 UI/UX Pro Max skill

### 完成内容

- 按要求确立约定「后续网站构建使用 ui-ux-pro-max skill」，并把可执行口径写入 `AGENTS.md` 新增的「网站与界面构建约定」一节。
- 技能原先只装在 Codex 目录 `~/.codex/skills/ui-ux-pro-max`，不在 DSH 扫描的根目录（`.dsh/skills`、`.agents/skills`、`~/.dsh/skills`、`~/.agents/skills`）内，因此会话内 `skill ui-ux-pro-max` 曾报 unknown。
- 已复制到用户级 `~/.agents/skills/ui-ux-pro-max`（3.5 MB / 70 文件，排除 `__pycache__`）；`~/.codex` 原目录保持原样，技能本体未做任何修改。
- 约定中同时写明脚本直达路径 `python3 ~/.agents/skills/ui-ux-pro-max/scripts/search.py`：技能正文示例使用 `${CLAUDE_PLUGIN_ROOT}` 变量，该变量在 DSH 下不解析。
- 明确设计系统取用顺序：先读 `design-system/yes-lab/MASTER.md`，再看 `pages/<page>.md` 覆盖；未经用户授权不得用 `--force` 覆盖既有存档。

### 验证

- `python3 ~/.agents/skills/ui-ux-pro-max/scripts/search.py --help` 正常输出，`--domain`、`--stack`、`--design-system`、`--persist` 参数齐全。
- 安装后无需重启，本会话技能目录即时刷新出 `ui-ux-pro-max`，`skill ui-ux-pro-max` 成功加载正文，基目录解析为 `/Users/kaoxiaoyu/.agents/skills/ui-ux-pro-max`。
- 仓库内未新增技能文件，`design-system/yes-lab/` 既有存档保持不变。

### 待办与建议

- 技能为用户级安装，队友与其他机器需各自安装一次；若要随仓库分发，可改装到项目 `.agents/skills/`（约 3.5 MB）。
- 后续界面类任务应记录实际使用的查询词（`--design-system` / `--domain` / `--stack`）与采纳结论，便于回溯设计依据。

## 2026-09-20：全站 UI 设计评审（首次真机截图）

### 完成内容

- 按「网站与界面构建约定」做首次全站 UI 评审：先读 `design-system/yes-lab/MASTER.md` 与页面级覆盖，再开三路只读子审计（设计令牌与视觉一致性、可访问性、响应式与交互动效），每路都按技能契约检索规则并给出 `文件:行号` 证据。
- 首次打通真机截图：本地起后端（8080，H2）与前端（5173），用无头 Chrome 153 经 CDP 截取首页桌面三段、移动端、暗色主题等 10 张图，存放于 `.codex-run/ui-review/`（已 gitignore，不进版本库）。
- 结论：视觉识别度与桌面完成度高于同类实验室站点；欠账集中在移动端交互、对比度与设计系统一致性三处。

### 关键发现

- **对比度存在系统性失败**：`--color-text-subtle:#94A3B8`（`theme.css:5`）与 `--admin-subtle:#98A2B3`（`admin.css:19`）在浅底仅 2.4–2.6:1，约 58 处正文级使用；边框与输入边界 1.2–2.5:1，低于 WCAG 1.4.11 要求的 3:1。
- **移动端交互近乎缺失**：`portal.css` 9892 行中仅 2 处 `:active`，hover 规则全部隔离在 `@media (hover:hover) and (pointer:fine)` 内；积分日历触控目标 14×14px（`portal.css:9008-9010`）；≤760px 时顶栏退化为管理员 13 项横向滚动（`portal.css:5872-5888`）。
- **设计系统已漂移**：`MASTER.md` 中 5 个语义色与 `--shadow-xl` 从未定义；实际正文字体为 `Noto Sans SC`、标题常用 `Noto Serif SC`，`Crimson Text` 仅在 `style.css:4` 声明一次即被覆盖；四份样式共存 171 个跨文件重复选择器、33 处 `!important`、约 257 处规则体内硬编码颜色。
- **暗色模式覆盖约 0.6% 选择器**：`admin.css` 对 `var(--color-*)` 引用为 0；暗色下 `--color-accent-fill` 仍为亮色 `#a16207` 未重调。
- **首屏体量偏重**：`public/models/go2.glb` 6.7 MB 挂载即加载；`yes-lab-logo.png` 620 KB 被当装饰水印重复 3 次（`PublicHomeView.vue:646-648`）；构建产物单文件 CSS 305 KB；`melina-mail.svg` 598 KB。
- **肉眼可见的观感问题**：首屏 3 个透明度 0.055/0.035 的位图 Logo 水印（`refinement.css:164-186`）在机器人模型附近呈糊状杂点；项目区仅 1 张卡、动态仅 1 条，使三栏网格右侧大片留白，`Portfolio Grid` 形态被内容量拖累。

### 验证

- 截图确认真机渲染正常：桌面/移动/暗色三态均能出图，无横向滚动；`localStorage.yeslab-theme` + `data-theme` 暗色切换生效。
- 三路子审计均为只读，未修改任何产品代码；本次评审新增文件仅 `.codex-run/ui-review/`（gitignore 内）。
- 评审结束后已停止本次启动的前后端与无头 Chrome，8080/5173 端口释放；`backend/data/yeslab.mv.db` 未被写入。

### 待办与建议

- 高收益先修三项：① 两个浅灰 token 提到 4.5:1；② 补 `.profile-editor-content:focus-visible`（`portal.css:1304`，全站唯一无兜底焦点的元素）；③ 移动端补全局 `:active` 反馈并把积分日历命中区扩到 ≥44px。
- 第二批：移动顶栏抽屉化、`go2.glb` 压缩到 1.5 MB 内（可先用现成 465 KB 的 `skydio-x2.glb`）、水印改用 SVG 或去掉、按代码实际值回写 `MASTER.md`。
- 需用户先划清「视觉完成度 vs 重构成本」的边界，再决定是否合并四份样式的重复选择器与清理死样式。
- 另有一处文档矛盾待确认：`AGENTS.md` 记「任务模块**尚未施工**」，但 DEVLOG 2026-09-17 已记录第 1~7 批施工与冒烟验证，需核对后修订其一。（已在 2026-09-20「AGENTS.md 事实修订」中处理）

## 2026-09-20：AGENTS.md 事实修订

### 完成内容

- 核对代码后修订 `AGENTS.md` 两处过时表述：任务模块已施工（`V11__task_module.sql`、`V12__retire_probation_stage.sql`、后端 28 个类、4 个前端视图、后端 47 项测试通过），不再是「尚未施工」；剩余仅是上线动作（预生产 MySQL 演练、部署后批量补发新手任务、真实浏览器点击验收）。
- 「招新流程口径」由「施工完成后生效」改为**已生效**，并写明 `RecruitmentView` 的实际阶段序列 `SIGNUP → SCREENING → INTERVIEW → SKILL_TEST → FORMAL_MEMBER`。
- 「网站与界面构建约定」补一条：界面改动必须做真机截图自检，不得以构建通过代替视觉验收，并写明本机可用的无头 Chrome 与 CDP 截图路径（`.codex-run/ui-review/`，Chrome 需 `--no-sandbox`）。

### 验证

- 逐项对照代码核对：`RecruitmentView.vue:34` 阶段数组不含 `PROBATION`；`RecruitmentStage`/`MemberStatus` 的停用项与 `AGENTS.md` 描述一致；`ls db/migration` 至 `V12`。`npx prettier --check AGENTS.md` 通过。

## 2026-09-20：界面美化与可用性修复（第一批）

### 完成内容

- 按「网站与界面构建约定」加载 ui-ux-pro-max 后做界面精修，先读 `design-system/yes-lab/MASTER.md`，检索 3 次并据此定调：`"editorial academic research visual refinement" --domain style`（命中 `editorial-grid-magazine`：非对称网格、编辑字体、印刷感分隔）、`"touch target size tap feedback" --domain ux`（命中 Touch Target Size / Tap Delay）、`"empty state sparse list placeholder" --domain ux`（命中 Empty States / Placeholder Content）。结论是保留瑞士编辑风的留白，只收干净四处：装饰杂点、灰字偏浅、内容稀疏、触屏无反馈。
- **对比度**：`--color-text-subtle` `#94a3b8 → #5b6b80`（2.45 → 5.2:1）；`--color-text-secondary` `#64748b → #475569`（4.55 → 7.24:1，同时与 MASTER 的 `--color-muted-foreground` 对齐）；新增 `--color-border-strong`（亮 `#74849a`、暗 `#5b708f`）专供输入控件边界，达到 3:1（WCAG 1.4.11）。`admin.css` 的 `--admin-subtle #98a2b3 → #5b6b80`、`--admin-border-strong #d0d5dd → #74849a`（暗 `#3a4657 → #5b708f`）同步。
- **首屏净化**：删除 3 个 620 KB 位图水印（`PublicHomeView.vue` 的 `.hero-brand-mark`），并清掉 `refinement.css` 中对应的 3 处规则与漂移动画，保留轨道圆环。首屏不再有糊状杂点，同时减少约 1.9 MB 位图解码。
- **稀疏内容版式**：项目数少于 3 时给网格加 `project-grid--solo` / `--pair`（单条居中放大到 8 栏、图 16:9；两条并排各 6 栏），避免三栏网格右侧留下大片空白。
- **触屏反馈**：全局补 `:active` 按压反馈（`button` / `a` / `summary` / `[role=button]`，130ms；reduced-motion 由既有全局规则归零）；积分日历在 `pointer: coarse` 下放大为 18px 格 + 6px 间距（中心间距 24px，满足 WCAG 2.5.8 的间距例外）。
- **移动端顶栏**：≤760px 由横向滚动改为换行，并沿用「后台管理」下拉；同时修掉一处逻辑倒置——原先移动端强制显示 6 个后台直链、隐藏下拉，管理员在 375px 要横向划约 1200px 才能找到入口。
- **两处只有真机截图才能暴露的问题**：① `theme.css` 在 ≤760px 用 `.theme-toggle span { display: none }`，连图标容器（同为 `span`）一起隐藏，移动端只剩一个空白方块按钮，已改为 `> span:last-child`（两处）；② `.update-list article:hover` 用 padding 位移做强调导致整行内容跳动，改为 `box-shadow: inset 3px 0 0 var(--color-accent)`。
- 顺带补上评审发现的唯一无兜底焦点：`.profile-editor-content:focus-visible` 用 inset 描边（与讨论区编辑器一致）。

### 验证

- 真机截图：无头 Chrome 153 经 CDP 截 1440/375 × 亮/暗，改前 10 张、改后 13 张，存 `.codex-run/ui-review/`（gitignore）。确认：首屏杂点消失、单条项目卡居中放大、移动端顶栏两枚胶囊无横向滚动、主题切换图标恢复、登录页金色 CTA 与输入边界清晰、暗色底纹正常。
- `npm run check` 通过（ESLint `--max-warnings=0`、Prettier、Vite 构建）；改后首页总高 6053 → 6250px（单条项目卡放大所致），375px 下无横向滚动。
- 改动只落在 CSS 与两处模板：`theme.css`、`style.css`、`portal.css`、`refinement.css`、`admin.css`、`PublicHomeView.vue`、`AGENTS.md`。未动后端、路由与数据。

### 待办与建议

- 第二批候选：`go2.glb` 6.7 MB 压缩与按需加载、`melina-mail.svg` 598 KB、构建后单文件 CSS 305 KB 拆分、≤1400px 顶栏账号名直接隐藏（1366px 笔记本看不到账号）、admin 抽屉缺 Esc 与焦点管理、模态焦点陷阱。
- 仍缺真实登录态截图：本地无可用演示账号，后台与成员页目前只有代码审计与令牌推断，没有视觉确认。（已在 2026-09-20「登录态界面复核与顶栏挤压修复」中补齐）
- `--color-border`（装饰分隔线）仍为 1.42:1，这是有意的编辑风留白选择，未按 UI 组件 3:1 处理；若后续要做严格的 WCAG 1.4.11 审计，需先明确「哪些边界算组件边界」。

## 2026-09-20：登录态界面复核与顶栏挤压修复

### 完成内容

- 用 README 的本地演示账号（`teacher` / `YesLab-Teacher-2026!`，见 `README.md:126`）打通登录态截图：在浏览器页面上下文调用 `POST /api/v1/auth/login`（`rememberMe: true`）写入刷新 Cookie，之后逐页导航由应用自身 `restoreSession` 恢复会话，共截 12 张——后台成员/任务/积分/主页编辑/招新管理（含亮暗与移动端）＋成员端任务、积分榜、个人主页、讨论板。
- 复核结论：后台与成员端与公开端共用同一套令牌，输入框边界、区块 eyebrow、侧栏对比度、暗色分支均正常；任务页在无数据时给出规范空态（「暂无任务」+ 说明 + 操作指引），不是白屏。
- 修掉登录态截图才暴露的真问题：**成员系统顶栏在 1024–1440px 文字重叠**。实测 `.portal-topbar nav` 所在的 `1fr` 列宽不足而 nav 是 `overflow: visible`：1440px 内容溢出约 103px、1080px 约 121px，导致「后台管理」压在右侧账号区上。
  - 将导航压缩规则由 `max-width: 1400px` 提前到 `max-width: 1680px`（`padding-inline` 收到 11px、`gap` 收到 5px、隐藏与 Logo 重复的「MEMBER SYSTEM」副标）。
  - 给 `.portal-topbar nav` 增加 `flex-wrap: wrap` 作为兜底：宁可换行也不压字。1440/1366 仍为单行，≤1200 时「后台管理」换到第二行。
  - 复核测量：1440/1366/1200/1080/900 五个宽度下 nav 与账号区均无重叠（修复前 1440 重叠约 83px）。

### 验证

- CDP 实测各宽度边界盒；修复前后对比见 `.codex-run/ui-review/zoom-topbar-1440.png` 与 `zoom-topbar-1024.png`（2x 裁剪放大）。
- 登录态截图 12 张、顶栏放大 3 张，全部存 `.codex-run/ui-review/`（gitignore）。
- `npm run check` 通过（ESLint `--max-warnings=0`、Prettier、Vite 构建）。
- 评审用前后端与无头 Chrome 已停止，8080/5173/9222 全部释放；未改动后端、路由与数据。

### 待办与建议

- 上一批列出的第二批候选仍然有效（`go2.glb` 压缩、单文件 CSS 拆分、admin 抽屉 Esc 与焦点管理、模态焦点陷阱）。
- 顶栏在 ≤1200px 会换行成两行，可接受但非理想；若要维持单行，需要精简导航项或把「后台管理」改为纯图标，取舍需用户确认。
- 登录态只覆盖 `teacher`（系统管理员）视角；`core`、`member` 与游客三类尚未截图复核。

## 2026-09-20：修掉未渲染的按钮（`.portal-secondary` 从未定义）

### 问题

- 用户反馈「任务管理部分有些按钮没经过渲染」。CDP 逐按钮实测计算样式后定位：这些按钮拿到的是浏览器默认外观（`background-color: rgb(239, 239, 239)`、`border: 2px outset`）。
- 根因：**`.portal-secondary` 在全部 CSS 中从未定义**，只实现了 `.portal-primary`。受影响的不止任务模块——4 个视图共 7 处使用该类的按钮/链接都是原生外观。
- 具体位置：`AdminTasksView`「预览命中名单」「收起」「新手任务与模板」、`AdminOnboardingTemplateView`「批量补发新手任务」、`AdminTaskProgressView` 与 `RecruitmentView` 的同类次要按钮。

### 完成内容

- `portal.css` 补 `.portal-secondary` 基础样式（`min-height: 48px`、8px 圆角、`--color-border-strong` 边框、卡片底、主色文字）与 `:disabled` 态；hover 放进既有的 `@media (hover: hover) and (pointer: fine)` 块，与 `.portal-primary:hover` 并列，遵循仓库既有约定。
- `admin.css` 补后台作用域版本（40px 高、`--admin-border-strong` 边框、`--admin-card` 底、与 `.portal-primary` 相同的尺寸与过渡），并加对应 hover；暗色通过 `--admin-*` 变量自动适配。
- 顺带修两处同类渲染缺陷：`.portal-state.success` 同样从未定义（3 处操作成功提示渲染成中性灰块），现补为成功色（`--color-success` 系列）；行内错误提示此前继承整页状态块的 `min-height: 180px; padding: 40px`，新增 `.portal-state.inline` 紧凑变体并用于 `OnboardingTaskPanel.vue:140`、`TaskDetailView.vue:143`。

### 验证

- **逐按钮实测计算样式**（判定条件：`background-color === rgb(239,239,239)` 或 `border-style` 为 `outset`/`inset`）：任务列表含展开的创建表单 22 个交互元素、新手任务与模板 19 个、我的任务 3 个——修复前分别有 2 / 1 / 0 个为原生外观，修复后**三页均为 0**。
- 截图对照：`audit-tasks-list.png`（「新手任务与模板」是裸链接、表单内按钮为原生灰底）→ `fixed-tasks-list.png`（同一位置已是规范次要按钮）；`fixed-tasks-onboarding.png` 底部「保存模板 / 批量补发新手任务」两枚按钮尺寸与层级一致。
- 全仓静态扫描：555 个模板类名中 37 个在 CSS 中无定义；其中 Tailwind 工具类与组件 `<style scoped>` 属正常，真正的缺失只有 `.portal-secondary`（已修），其余 5 个（`create-core-student`、`waitlist-decision-card`、`proof-grid`、`about-title`、`sponsor-list`）是挂在已渲染元素上的多余类名，截图确认不影响版式。
- `npm run check` 通过（ESLint、Prettier、Vite 构建）。

### 待办与建议

- 那 5 个多余类名可安全删除，但需逐处确认没有被 `:class` 动态使用，建议随下一次样式清理一并处理。
- 类名「定义了但没用」与「用了但没定义」目前只能靠人工发现；本次用的两个扫描脚本（类名比对、按钮计算样式实测）留在 `.codex-run/ui-review/`，可作为界面回归检查复用。

## 2026-09-20：新手任务改为「共享大任务 + 可添加子任务」

### 背景

用户指出任务模块的模型不对：新手任务应当是**一个大任务**，子任务可以添加，一个大任务可以有多个子任务，所有技能测试（测验）阶段的报名者共享同一个新手任务；并且只有完成所有子任务才能转正，报名者要能看到自己的进度。原实现是「全局模板 → 面试通过时复制成每人一条 `ONBOARDING` 任务」，与这个口径不符。

经逐项确认后按以下结论改造：共享大任务；子任务保持单层、不单独指派 / 不设截止日期 / 不单独计分；两类任务统一为「大任务 + 子任务」模型；时长按每人各自进入技能测试的日期计算；`V11` 尚未上线，直接改 `V11` 而不新增迁移脚本；新增子任务时把「已提交待确认」的对象退回「待完成」并通知。

### 完成内容

- **数据模型（改 `V11__task_module.sql`）**：删掉 `onboarding_task_template` 与 `onboarding_task_template_subtasks` 两张模板表，表数量 7 → 5；`tasks` 去掉 `template_synced_at`、新增 `duration_days`；`task_assignments` 新增 `due_date`。新手任务就是 `tasks` 里唯一一条 `ONBOARDING` 大任务，与普通任务共用同一套表、实体与子任务能力。
- **后端领域层**：删除两个模板实体与对应仓库；`TaskEntity` 新增 `updateOnboardingDetails`；`TaskAssignmentEntity` 新增 `dueDate`、`issuedOn()`、`hasCompletedAllSubtasks()`、`reopenForNewSubtasks()`、`reopenForEdit()`。新增 `TaskContentSanitizer` 承载富文本白名单与子任务规范化，新手任务与普通任务共用。
- **后端服务层**：`OnboardingTemplateService` 由新的 `OnboardingTaskService` 取代（单例读取 / 兜底初始化 / 保存），内置默认内容（标题、说明、5 项子任务、7 天）只维护一份；保存时即时生效——新增子任务把 `SUBMITTED` 对象退回 `PENDING` 并发站内消息、删除子任务先删勾选记录、改时长按各人发放日重算 `due_date` 并发消息；返回 `reopenedCount` 与 `rescheduledCount`。`OnboardingTaskIssuerService` 改为在共享大任务上按报名记录建对象（唯一约束兜底幂等），`due_date = 分发当天 + 时长`。
- **转正门槛**：报名者必须勾选**全部**子任务才能提交（前端禁用 + 后端 400，提示已完成 x / y）；管理员审核通过前再校验一次，豁免路径除外；`SUBMITTED` 状态下改动勾选会退回 `PENDING`，避免用旧提交通过新内容。
- **接口**：删除 `GET|PUT /admin/tasks/onboarding-template`；新增 `GET|PUT /admin/tasks/onboarding`（保存返回影响面）、保留 `GET /admin/tasks/onboarding-overview` 与 `POST /admin/tasks/onboarding-tasks/backfill`（改为在大任务上补建对象）。
- **前端**：新增共用组件 `TaskSubtaskEditor.vue`（逐条添加 / 删除 / 上移 / 下移，带序号、计数与 `aria-label`），新手任务与普通任务共用；`AdminOnboardingTemplateView.vue` 重命名为 `AdminOnboardingTaskView.vue` 并去掉模板与同步开关，改为大任务编辑 + 完成情况审核；`OnboardingTaskPanel.vue` 增加进度条与「还差 N 项才能提交」的禁用提示；`AdminTasksView.vue` 的「每行一项」文本框换成同一编辑器。
- **文档**：`docs/task-module-requirements.md`（B 组 15 条重写为共享大任务口径）、`docs/task-module-design.md`（数据模型 4.1/4.4/4.6/4.7、第 5 节、接口表、前端与测试计划）、`backend/docs/module-boundaries.md`、`backend/docs/access-control.md`、`README.md`、`AGENTS.md` 同步。
- **UI 规则取用**：加载 `ui-ux-pro-max` 后读 `design-system/yes-lab/MASTER.md`，检索 `"editable list add remove item reorder" --domain ux`（命中 Chip Collection Reflow：集合必须换行而不是裁掉标签）、`"progress indicator task completion checklist" --domain ux`（命中 Progress Indicators 与 Focus States：多步进度要有进度条、每个控件都要可见焦点）。据此实现子任务行换行布局、进度条 + 「x / y」文本、行内按钮 44px 与键盘焦点环。

### 验证结果

- **后端**：Java 21 全量测试 **47 项通过，0 失败、0 错误、0 跳过**。`OnboardingTaskApiTests` 4 项按新模型重写：两人共享同一 `taskId` 而进度互不影响、每人截止日期 = 各自发放日 + 时长、未勾完不能提交与不能通过、勾完提交后审核转正、驳回必填意见并可重提、新增子任务把已提交对象退回待完成并可重新提交转正、删除子任务清理勾选记录、时长变更按各自发放日重算、批量补发幂等、转正守卫与豁免。
- **冒烟**：更新 `scripts/smoke-task-module.sh` 后以隔离内存 H2 运行真实 HTTP 链路，**46 项检查全部通过（0 失败）**，含「未完成全部子任务时拒绝提交」「管理员新增子任务 → 已提交对象退回待完成 → 勾选新项 → 重新提交」。
- **前端**：`npm run check` 通过（ESLint `--max-warnings=0`、Prettier、Vite 生产构建），`git diff --check` 通过。
- **真机截图自检**（无头 Chrome 153 + CDP，脚本与截图在 `.codex-run/ui-review/`，已 gitignore）：管理端新手任务页 1440 亮 / 暗、375 亮；报名者「我的报名」新手任务卡片 1440 / 375；提交区 1440 / 375；普通任务创建表单 1440。实测：提交区提示「还需要完成 4 项子任务才能提交」且按钮 `disabled`，进度条 `role=progressbar` 且 `aria-valuenow=2 / aria-valuemax=6`；子任务编辑器 18 个行内按钮全部带 `aria-label`、行高 44px、「添加子任务」在触屏 / 窄屏下 44px（桌面沿用后台既有的 40px 次要按钮尺寸）；键盘 Tab 聚焦时出现 2px 可见焦点环；375px 下 `scrollWidth == innerWidth`，无横向滚动，子任务行自动换行不裁字。
- 验证用前后端与无头 Chrome 已停止，8080 / 5173 / 9222 释放；后端全程使用隔离内存 H2 与 `target/smoke-*` 目录，**未写入 `backend/data/yeslab.mv.db`**。

### 待办与说明

- **有意保留的一处差异**：普通任务仍允许随时提交完成说明，是否达标由管理员人工判断（不强制勾完全部子任务）；新手任务因为是转正门槛才强制全勾。若要两者完全一致，可再改为普通任务也强制全勾。
- `V11` 已改动但**仍未上线**，需按原计划在预生产 MySQL 演练 `V11` + `V12`，部署后执行一次「批量补发新手任务」，并补做真实浏览器点击验收（报名者勾完全部子任务 → 管理员审核转正 → 普通任务发布与计分 → 驳回重提）。
- 本地 H2 开发库（`backend/data/yeslab.mv.db`）若曾用旧模型创建过 `onboarding_task_template` 表或每人一条 `ONBOARDING` 任务，`ddl-auto=update` 不会自动清理，理论上会让单例读取命中旧任务；如遇到，可手工执行 `DROP TABLE onboarding_task_template_subtasks; DROP TABLE onboarding_task_template;` 并删除旧 `task_type='ONBOARDING'` 的 `tasks` 行（本次未擅自改动本地库）。

## 2026-09-20：子任务改为「独立页面 + 富文本正文」设计（待审，未施工）

### 背景

用户澄清子任务的形态：任务列表里是若干**大任务**，打开大任务看到介绍、完成进度与**各个子任务的入口**；每个子任务也是独立的任务，子任务的内容同样用富文本编辑。

已确认的三点：子任务**不单独提交、不单独审核**（审核仍在大任务层做一次）；本次只加「正文 + 独立页面 + 独立完成状态」，不放开嵌套 / 独立指派 / 独立截止 / 单独计分；先出文档审核，通过后再施工。

### 完成内容（仅文档）

- `docs/task-module-requirements.md`：B12 改为「标题 + 可选富文本正文，单层、不单独指派/截止/计分/审核」，新增 B16（子任务页面与深链）、B17（正文可为空、按白名单清洗）；C9 补充正文，新增 C9.1/C9.2；界面入口表补「大任务详情」与「子任务页面」；明确不做里新增「子任务单独提交与单独审核」；施工批次新增第 8 批。
- `docs/task-module-design.md`：`task_subtasks` 新增 `content_html`（可空、上限 5000 字符、同一条白名单）；新增 **4.10 子任务正文、独立页面与懒加载**（页面层级、懒加载约定、三个子任务详情接口、`SubtaskInput{id,title,contentHtml}` 写入形状、勾选与不单独审核）；4.7 的子任务同步由「按标题匹配」改为**按 `id` 匹配**（改标题不再丢勾选与正文）；接口表、保存请求示例、前端文件与路由（`/tasks/{assignmentId}/subtasks/{subtaskId}`、`/application/subtasks/{subtaskId}`）、交互要点、测试计划同步。
- 工作量评估：数据模型只加一列（`V11` 未上线，直接改）；后端改视图模型、保存校验与同步逻辑、三个详情接口；前端新增两个子任务页、子任务编辑器改「列表 + 单条展开式富文本」、大任务卡片加进度；测试补子任务正文清洗、按 id 同步、越权 404、列表不返回正文。

### 验证结果

- 本轮**只改文档，未改任何代码**：`npm run check` 通过（ESLint、Prettier、Vite 构建），`git diff --check` 通过。
- 未运行后端测试（后端未改动）；未连接生产环境，也未执行部署。

### 待办

- 等用户审核上述两份文档；通过后按第 8 批施工，施工完成再补后端测试、`npm run check` 与真机截图。

## 2026-09-20：任务模块第 8 批（子任务独立页面与富文本正文）

### 背景

用户确认子任务形态：任务列表是若干**大任务**，打开大任务看到介绍、完成进度与**各个子任务的入口**；每个子任务也是独立任务，子任务内容用富文本编辑。经确认采用方案 A：子任务**不单独提交、不单独审核**，只加「正文 + 独立页面 + 独立完成状态」，审核仍在大任务层做一次；先出文档审核，本批为审核通过后的施工。

### 完成内容

- **数据模型**：`V11` 的 `task_subtasks` 新增 `content_html LONGTEXT`（可空，允许只有标题的纯清单项），仍直接改 `V11`（未上线）。
- **领域层**：`TaskSubtaskEntity` 增加正文、`hasContent()`、`rename()`、`updateContent()`；`TaskEntity` 的子任务写入改为 `SubtaskDraft(id, title, contentHtml)` 记录，`replaceSubtasks` 用于草稿重建、`syncSubtasks` 改为**按 id 同步**并返回新增标题——**改标题不再丢勾选记录，也不会误删正文**（原来按标题匹配，加正文后必然出事）。`TaskContentSanitizer` 增加 `cleanSubtaskContent`（可为空、上限 5000 字符、同一条 OWASP 白名单）。
- **服务层**：新增三个子任务详情读取（成员端 `GET /api/v1/tasks/{assignmentId}/subtasks/{subtaskId}`、报名者端 `GET /api/v1/recruitment/me/onboarding-task/subtasks/{subtaskId}`、管理端 `GET /api/v1/admin/tasks/{taskId}/subtasks/{subtaskId}`），返回正文 + 本人完成状态 + 大任务上下文，越权一律 404；**列表与进度接口不返回正文，只返回 `hasContent`**；保存时校验提交的 id 属于当前任务；`SubtaskView` 增加 `hasContent`。内置默认新手任务子任务同时补上了引导性说明。
- **前端**：`TaskSubtaskEditor` 升级为「标题 + 展开式富文本说明」（展开时才按需拉正文、一次只挂载一个编辑器、带「有说明」标记）；成员端 `TaskDetailView` 与报名者端 `OnboardingTaskPanel` 的子任务列表改为**入口列表**（标题 + 完成状态 + 有说明 + 箭头）；新增成员端子任务页 `TaskSubtaskView.vue`（`/tasks/:assignmentId/subtasks/:subtaskId`）与报名者端子任务页 `OnboardingSubtaskView.vue`（`/application/subtasks/:subtaskId`），页面含大任务进度、截止日期、"标记为已完成"与返回入口；`TasksView` 大任务卡片补进度条。
- **修掉一个会丢数据的隐患**：管理端保存时，对「已存在、有正文、但这次没展开」的子任务，先按需拉取正文再提交，失败则中止保存；否则会把管理员上次写的说明写空。已在真机点击中实测（改标题但不展开 → 保存后正文仍在）。
- **修掉一个窄屏布局缺陷**：子任务里嵌富文本编辑器后，工具栏在 ≤760px 的 `nowrap` 把整页撑到 801px（真机截图实测 `innerWidth` 从 375 变 801）。给编辑器容器加 `min-width: 0 / max-width: 100%` 并让工具栏换行后恢复 375px 无横向滚动。
- **文档**：`docs/task-module-requirements.md`（B12/B16/B17、C9.1/C9.2、界面入口、第 8 批）、`docs/task-module-design.md`（4.2、4.7、新增 4.10、接口表、前端与路由、交互要点、测试计划、懒加载不丢数据的约定）。
- **UI 规则取用**：沿用 `design-system/yes-lab/MASTER.md` 与上一批的检索结论（Chip Collection Reflow 要求集合换行不裁字、Progress Indicators 要求多步进度可量化、Focus States 要求每个控件可见焦点），本批新增的子任务入口整行可点、状态用文字表达不只靠颜色、按钮保持 44px 与可见焦点。

### 验证结果

- **后端**：Java 21 全量测试 **50 项通过，0 失败、0 错误、0 跳过**（原 47 项 + 新增 3 项）。新增用例覆盖：子任务正文保存与清洗、`hasContent`、列表不返回正文、子任务详情越权 404、按 id 同步改标题保留勾选与正文、删除子任务清理勾选、提交别的任务的子任务 id 被拒（400）。
- **冒烟**：真实 HTTP 端到端 **53 项检查全部通过（0 失败）**，新增「大任务列表只带 hasContent 不带正文」「报名者/成员子任务页能读到富文本说明」「子任务页返回本人进度上下文」「在子任务页勾选完成」「按 id 新增子任务后已勾选进度保留」。
- **前端**：`npm run check` 通过（ESLint `--max-warnings=0`、Prettier、Vite 构建），`git diff --check` 通过。
- **真机截图自检**（无头 Chrome 153 + CDP，`.codex-run/ui-review/`）：成员端任务列表、大任务入口页（1440/375）、子任务页（1440/375）、报名者端子任务页（1440/375）、管理端子任务说明展开（1440 亮/暗、375）。实测：各页无横向滚动（375px 下 `scrollWidth == innerWidth`）、大任务入口列表显示「未完成 · 有说明」与进度条、子任务页正文与进度上下文正确、管理端展开后编辑器可用且窄屏工具栏换行。
- 验证用前后端与无头 Chrome 已停止，端口释放；后端使用隔离内存 H2 与 `target/smoke-*` 目录，**未写入 `backend/data/yeslab.mv.db`**。

### 待办

- `V11`/`V12` 仍未上线：需预生产 MySQL 演练、部署后执行一次「批量补发新手任务」，并补做整体点击验收。
- 普通任务的提交口径仍与新手任务不同（普通任务不强制勾完全部子任务，由管理员人工判断），如需完全统一可再改。

## 2026-09-20：新手任务页与子任务编辑器三处界面修复

### 背景

用户真机截图指出三处问题：① 新手任务卡片里的 eyebrow「ONBOARDING BIG TASK」与标题后缀「（大任务）」多余；② 「保存新手任务」与「批量补发新手任务」两个按钮没对齐；③ 普通任务创建表单里的子任务区块只占半列、内部大片留白，空间利用率太低。

### 完成内容

- **① 去掉多余标识**：`AdminOnboardingTaskView.vue` 的卡片头部删掉 eyebrow 行，标题由「新手任务（大任务）」改为「新手任务」（说明文字保留，因为它解释了共享大任务与转正口径）。
- **② 按钮错位的真因**：不是高度差（实测两个按钮都是 40px），而是 `.admin-form-card .portal-primary { margin-top: 20px }` 这条既有规则让主按钮在操作行里多出 20px 上边距（实测 `top` 差 20px、操作行高 60px）。在 `portal.css` 增加 `.task-form-actions .portal-primary { margin-top: 0 }`，把这条全局规则在操作行内收口。修复后两按钮 `top` 完全相同。
- **③ 子任务区块改成整行 + 收紧留白**：`AdminTasksView.vue` 给子任务编辑器加 `class="full"`，让它跨满表单栅格（原来只占右半列 516px）；同时把编辑器内部从「提示 2 行 + 空态说明 1 行 + 底部按钮行」压成「提示 1 行 + 底部一行（按钮 + 计数/空态文案）」，删掉独立的空态段落。实测编辑器高度 236px → 118px，提示由 60px 变 20px（1 行），编辑器宽度 516px → 1048px（占满整行）。
- 顺带把两处子任务提示文案压成一句，避免窄列里换行成三行。

### 验证结果

- 真机复验（无头 Chrome + CDP，`.codex-run/ui-review/fix3-*.png`，只读操作，未写任何接口）：
  - 新手任务页：`eyebrow = null`、标题「新手任务」、两个按钮同为 40px 且 `top` 一致（`topsAligned: true`）；
  - 普通任务表单：`spansFullRow: true`（编辑器宽度 1048 = 栅格宽度）、`hintHeight: 20`、独立空态已移除、编辑器高度 118px；
  - 375px 下两页均 `scrollWidth == innerWidth`（无横向滚动），按钮仍对齐。
- `npm run check` 通过（ESLint `--max-warnings=0`、Prettier、Vite 构建）。
- 本轮只改前端模板与 CSS，未改后端、接口与数据模型，因此未重跑后端测试。

### 待办与说明

- 复现问题时我起后端发现 8080 已被占用（用户自己的本地后端），我的测量脚本因此打到了用户的本地库，并执行过一次 `PUT /api/v1/admin/tasks/onboarding`（把新手任务大任务写成 2 项子任务：配置开发环境、阅读新人手册）。已告知用户并经确认后**恢复为内置默认 5 项**：改前先只读核对，改后回读确认 5 项标题与说明齐全；当时技能测试阶段对象数为 0，因此 `reopenedCount=0`、`rescheduledCount=0`，没有退回任何人、没有重算截止日期、也没有发站内消息。今后验证统一改用备用端口 + 独立内存库，只读用户实例、不再写入。

## 2026-09-20：表单顺序调整（大任务描述上移）与富文本字段补可见标题

### 背景

用户真机截图指出：子任务区块正下方紧跟着富文本编辑器，两个带边框的块「挨在一块」；下方那个其实是**大任务描述**，应该排在其他内容上面。

### 完成内容

- **调整两个管理页的字段顺序**，让「描述」紧跟标题、子任务靠后：
  - 普通任务表单：任务标题 → **大任务描述（富文本）** → 开始/截止日期 → 积分 → 子任务 → 发放条件；
  - 新手任务页：任务标题 → **新手任务说明（富文本）** → 时长 → 子任务 → 保存/补发。
- **补上富文本字段的可见标题**：`DiscussionRichTextEditor` 的 `label` 属性只用于 `aria-label`，不会渲染可见文字（这一点之前被忽略了），所以「这块是大任务描述」只能靠猜。现在用 `.task-editor-field > .task-editor-label` 渲染可见标题（11px / 700，与 `.admin-form-grid label` 同一套字号，`gap: 8px`），普通任务显示「大任务描述」、新手任务显示「新手任务说明」。
- 本轮只改前端模板与 CSS，未动后端、接口与数据模型。

### 验证结果

- **在自己的隔离环境验收**（后端 `8081` + 内存库 `yeslabui4` + 前端 `5174`，刻意不碰用户正在运行的 8080/5173）：
  - 普通任务表单字段顺序实测：`任务标题 → 富文本字段(大任务描述) → 开始日期 → 截止日期 → 积分 → 子任务区块`；
  - 新手任务页：`任务标题 → 富文本字段(新手任务说明) → 时长`，其后才是子任务区块；
  - 可见标题已渲染：`labelVisible: true`、`labelFont: 11px/700`；1440px 与 375px 均 `scrollWidth == innerWidth`。
- 截图：`.codex-run/ui-review/fix4-*.png`、`fix5-*.png`。
- `npm run check` 通过（ESLint `--max-warnings=0`、Prettier、Vite 构建）；本轮未改后端，未重跑后端测试。

### 待办与说明

- 已验证本地库的新手任务大任务恢复为内置默认 5 项；本轮起我的验收一律走备用端口 8081/5174 + 内存库，不再读写用户实例。

## 2026-09-20：任务模块全流程「浏览器点击」验收（补做）

### 背景

用户追问「是否模仿了用户/管理员进行全流程操作验证」。此前的验证只到 API 层（MockMvc 集成测试 + 真实 HTTP 冒烟），浏览器里只做过渲染截图与零散交互；因此本轮把整条链路真的在页面上点了一遍。

### 做法

- 新增可重复执行的脚本 `.codex-run/ui-review/clickthrough-task-module.mjs`（gitignore 内，仅本地验收用）：无头 Chrome + CDP，**真实鼠标事件点击、真实键盘输入**，每次点击前校验目标元素是否被模态/浮层遮挡并按真实用户做法先关闭模态，点击后回验「点中的元素文字」是否与预期一致，页面里所有 `window.confirm` 自动确认。
- 环境：隔离内存库 + 标准端口（后端 8080、前端 5173 同源代理），用户已关闭自己的实例。
- 覆盖 8 组、**64 项断言**，全部通过（exit 0），截图 13 张（按用户要求只保留亮色）：`click-01..13-*.png`。

### 链路与结果

| 阶段                 | 操作（全部页面点击）                                                                                                                                                                  |
| -------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| ① 游客报名           | 注册 → 填姓名/专业/班级/年级/邮箱/兴趣方向/3 道技术题 → 提交报名表（按钮变「修改报名表」、流程记录出现报名）                                                                          |
| ② 管理员推进         | 选中报名者 → 进入初筛 → 通过并进入面试 → 设为待补录 → 补录面试信息并确认录取 → 阶段到技能测试 → 新手任务页出现该报名者（待完成 0 / 5）                                                |
| ③ 报名者完成新手任务 | 我的报名显示「我的进度 0 / 5」、未完成时提交按钮 `disabled=true` 且提示还差 5 项 → 逐个进入子任务页（读富文本说明并勾选）5 次 → 回到报名页显示 5 / 5 → 填完成说明提交（进入等待确认） |
| ④ 审核转正           | 新手任务页审核 → 填编号与标签 → 提交 → 提示已转为正式成员、该行退出技能测试列表 → **本人重新登录后成员主页显示学号与「正式成员」**                                                    |
| ⑤ 普通任务发布       | 创建任务（标题、富文本描述、起止日期、积分、子任务标题 + 子任务富文本说明、条件=普通成员）→ 保存草稿 → 发布                                                                           |
| ⑥ 成员完成           | 我的任务出现卡片 → 打开大任务 → 点进子任务页读说明 → 勾选完成 → 返回提交完成说明 → 等待确认                                                                                           |
| ⑦ 审核计分           | 完成情况页 → 人工审核 → 通过 → 状态已通过且「已计 20 分」                                                                                                                             |
| ⑧ 驳回重提           | 新建第二个任务 → 发布 → 成员提交 → 管理员选「驳回（不发分）」并填意见 → 成员端看到驳回意见 → 修改说明重新提交成功                                                                     |

### 顺带捞出的一个真实产品问题（未改，待确认）

报名表提交时，如果**兴趣方向一个都没选**，前端不会拦（HTML5 `required` 覆盖不到复选框组，邮箱虽有 `required` 但兴趣方向没有），后端返回的是：

```json
{ "message": "请检查提交内容", "fields": { "interestDirections": "请至少选择一个兴趣方向" } }
```

而界面只显示一句「请检查提交内容」，**不告诉用户是哪个字段错了**。建议二选一（都很小）：提交前在 `RecruitmentView` 里补齐必填校验并就地提示；或把后端 `fields` 明细渲染到表单上。

### 验证结果

- 点击验收：**64 项断言全部通过**（脚本退出码 0），截图 13 张（亮色）。
- 过程中修掉的都是**脚本自身问题**，不是产品缺陷：浏览器残留会话导致 `/register` 被重定向；点击索引在「可见元素」与「全部元素」列表间错位；注册成功后的 `SubmissionFeedbackModal` 盖住提交按钮；新增子任务本就会自动展开说明编辑器、再点一下反而收起；新增子任务后富文本编辑器异步挂载需要等待；切换账号后忘记重新登录管理员导致找不到按钮。
- `npm run check`、后端 50 项测试、冒烟 53 项仍为通过（本轮未改产品代码）。

### 待办

- 报名表「兴趣方向 / 邮箱」的字段级错误提示是否要修，等用户确认。
- `V11`/`V12` 预生产 MySQL 演练与部署后批量补发新手任务仍未执行。

## 2026-09-20：普通任务「零子任务」的空壳界面收干净

### 背景

用户问「现在大任务中可以没有子任务吗」。核对后确认：普通任务**可以**（`CreateTaskRequest.subtasks` 只有上限 `@Size(max=50)`，成员提交也不校验子任务），新手任务**不可以**（`@NotEmpty` + 服务层 400，且这是有意为之——转正门槛 `hasCompletedAllSubtasks()` 要求 `total > 0`，允许 0 项会让报名者永远提交不了、永远转不了正）。但普通任务允许 0 项时界面留了空壳。用户选择方案 A：普通任务允许 0 项，把空壳收干净。

### 完成内容

- `TaskDetailView.vue`：子任务标题与入口列表加 `v-if="totalCount"`，0 项时改为一句「本任务没有子任务，直接填写完成说明提交即可。」；顶部说明文案由「逐项勾选完成的子任务…」改为不预设一定有子任务。
- `TasksView.vue`：任务卡片上的进度条加 `v-if="task.totalSubtasks"`，不再渲染 0 / 0 的空进度条（详情页的进度条此前已有条件渲染）。
- 提示文案：普通任务表单写明「子任务可以不设——不设时成员直接提交完成说明」；新手任务表单写明「至少需要一项：成员必须勾完全部子任务才能提交与转正」。
- `docs/task-module-design.md` 4.10 节补充两类任务子任务数量下限不同的原因。
- 管理端完成情况页此前已有「该任务没有子任务。」空态，无需改动。

### 验证结果

- 真机复验（隔离环境：后端 8081 + 内存库 `yeslabzero`，前端 5174，无头 Chrome；**13 项断言全部通过**，退出码 0）：创建一个 0 子任务的普通任务并发布后——成员端卡片不再出现 0 / 0 进度条、大任务页不再出现「子任务（0 / 0 已完成）」与空列表、改为空态说明、成员可正常提交、管理端完成情况页显示「该任务没有子任务。」；新手任务页显示「至少需要一项」、子任务全部删空后保存按钮变禁用、空态给出添加提示。
- 截图：`.codex-run/ui-review/zero-subtask-*.png`（亮色）。
- `npm run check` 与 `git diff --check` 通过；本轮只改前端模板与文档，未改后端接口与数据模型，未重跑后端测试。

### 待办

- 报名表「兴趣方向 / 邮箱」的字段级错误提示是否要修，仍等用户确认。
- `V11`/`V12` 预生产 MySQL 演练与部署后批量补发新手任务仍未执行。

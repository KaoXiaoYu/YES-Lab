# OpenLIMS 最新前端接口差异（2026-10-07）

> 以下差异以同步前的 5459b31 为基线；用户已批准同步，完成状态见文末。

## 对比范围与结论

- 用户要求先对比接口差异；本轮仅核对源码并记录结论，未修改业务代码、数据库或部署配置。
- YES Lab 当前基线：`5459b31`；此前前端移植来源为 `0fb3fe9`。
- 上游固定版本：`YESlab-UAVtech/OpenLIMS@a2df1b67468c664ec0cef3d254f992edee6415d6`。此前移植来源与上游 `220c83a` 的 Git tree 相同，可作为上游更新的比较基线。
- 核对前端请求封装、全部控制器映射、相关 DTO/服务、鉴权配置及上游新增测试：需要新增 **2 个 GET 接口**，没有移除现有接口，没有新增写接口，没有现有请求/响应字段的破坏性变更。
- `fetchPublicMemberDirectory()` 是新增前端封装，但调用的 `GET /api/v1/public/member-profiles` 已在 YES Lab 存在，无需补接口。成员“今日”页复用现有接口。

## 1. 后台工作总览

| 项目      | 差异                                                                                                                     |
| --------- | ------------------------------------------------------------------------------------------------------------------------ |
| 接口      | 新增 `GET /api/v1/admin/overview`；当前 YES Lab 未实现                                                                   |
| 参数      | 无查询参数、无请求体                                                                                                     |
| 响应      | 现有 `ApiResponse` 包装；`data` 新增 `items`、`totalPending`、`generatedAt`                                              |
| 每个 item | `key`、`label`、`hint`、`count`、`href`                                                                                  |
| 权限      | JWT 经 `Authorization: Bearer …`；沿用 `/api/v1/admin/**` 的 `TEACHER` / `CORE_STUDENT` 角色限制；普通成员 403，匿名 401 |
| 数据访问  | 查询现有招新、任务分配、悬赏奖金履约、成果实体；不写入业务数据                                                           |

上游七项统计：

| key                   | 计数口径                          | 跳转                      |
| --------------------- | --------------------------------- | ------------------------- |
| RECRUITMENT_SIGNUP    | 招新阶段 SIGNUP                   | `/admin/recruitment`      |
| RECRUITMENT_SCREENING | 招新阶段 SCREENING                | `/admin/recruitment`      |
| RECRUITMENT_INTERVIEW | 招新阶段 INTERVIEW                | `/admin/recruitment`      |
| ONBOARDING_REVIEW     | ONBOARDING 任务分配状态 SUBMITTED | `/admin/tasks/onboarding` |
| TASK_REVIEW           | STANDARD 任务分配状态 SUBMITTED   | `/admin/tasks`            |
| BOUNTY_PRIZE          | 奖金履约状态 PENDING              | `/admin/bounties`         |
| ACHIEVEMENT_REVIEW    | 成果审核状态 PENDING              | `/admin/achievements`     |

`totalPending` 为以上七项之和，不是去重人数。上游仅要求管理员角色，没有额外新增的细粒度权限。

**移植适配点：** 上游任务待审核计数只检查分配状态和任务类型，未排除 `CLOSED`。YES Lab 当前 `TaskService.requireTaskNotClosed()` 明确禁止审核已手动结束的任务；结算也不会把全部 SUBMITTED 改成其他状态。因此同步时应按现有可审核规则排除已关闭任务，避免总览出现无法处理的待办。本次没有修改该规则。实际代码允许截止日后审核尚未关闭的任务，不能只按到期日期过滤。

来源：[总览服务](https://github.com/YESlab-UAVtech/OpenLIMS/blob/a2df1b67468c664ec0cef3d254f992edee6415d6/backend/src/main/java/cn/openlims/platform/administration/AdminOverviewService.java)。

## 2. 讨论分页与搜索

| 项目     | 差异                                                                                                |
| -------- | --------------------------------------------------------------------------------------------------- |
| 接口     | 新增 `GET /api/v1/discussions/page`；当前只有旧的全量列表接口                                       |
| 参数     | `sort=NEWEST`、`page=0`、`size=20`、可选 `q`                                                        |
| 参数边界 | page 负数归零；size 限制在 1—50；页码从 0 开始                                                      |
| 排序值   | 沿用 NEWEST、OLDEST、ID_ASC、ID_DESC、MOST_LIKED、MOST_REPLIED                                      |
| 响应     | `data.items`、`pinned`、`totalCount`、`page`、`size`、`hasMore`；items/pinned 中仍使用现有 PostView |
| 权限     | 可匿名读取，登录后保留 likedByMe/canEdit/canDelete/canPin 等个人状态；现有写入权限不变              |
| 搜索     | 标题、去 HTML 标签的正文、内容编号；空格分词，所有词都必须匹配，不区分大小写                        |

无搜索条件时 `pinned` 返回全部置顶帖；有搜索条件时返回空数组。`items` 和 `totalCount` 仍包括匹配的置顶帖，不能把两数组长度直接相加作为总数。旧 `GET /api/v1/discussions?sort=…` 保留全量数组响应，旧调用方继续兼容。

上游实现先调用全量列表再在内存搜索/分页，尚不是数据库分页。超大 page 的 `page * size` 存在 int 溢出边界，同步时应加边界处理；无需为此改现有数据结构。

来源：[分页服务](https://github.com/YESlab-UAVtech/OpenLIMS/blob/a2df1b67468c664ec0cef3d254f992edee6415d6/backend/src/main/java/cn/openlims/platform/discussion/service/DiscussionService.java)。

## 3. 讨论草稿与数据库

此前询问中“讨论草稿需要新后端接口”的描述需纠正：草稿由前端 `useDraft` 使用浏览器 localStorage 保存，按账号隔离，超过 14 天不恢复，显式退出时清除该账号草稿，提交成功后清除对应草稿。它不支持跨设备同步，不新增草稿表或保存接口。

两项新接口复用已有实体，**无需新增 Flyway 迁移，禁止修改既有迁移**。分页调用现有列表逻辑，可能沿用既有内容编号补齐行为，因此并非声明整个讨论服务完全只读。

来源：[本地草稿实现](https://github.com/YESlab-UAVtech/OpenLIMS/blob/a2df1b67468c664ec0cef3d254f992edee6415d6/src/composables/useDraft.js)。

## 最小同步范围与后续验证

用户已授权“同步前端及必要接口，保留 YES Lab 数据与任务改动”；本轮依最新指示先完成对比，尚未施工。

必要后端业务文件共 5 个：新增 `AdminOverviewController` / `AdminOverviewService`，增量修改 `DiscussionModels` / `DiscussionController` / `DiscussionService`，统一采用 `cn.yeslab`。无需改 SecurityConfig、实体、任务审核/结算/履约逻辑。另添加针对性的接口测试。

不移植上游 DemoContentSeeder、演示数据启动配置、通用首页默认文案或宣传视频工程。保留 YES Lab 名称、Logo、现有 CMS 数据、任务进度与审核排序；草稿存储键采用 yeslab 前缀。

施工后验证：匿名/成员/管理员权限；分页边界、搜索与置顶、旧列表兼容；总览七项与总数、已关闭任务排除、奖金履约计数；原任务和讨论回归。涉及前端的施工另做规定的各断点明暗截图。本轮仅源码对比，未运行构建、接口测试或生产验证。

## 同步完成与验证结果

- 已按用户确认同步 a2df1b6 最新页面和两个必要接口；后端仅修改以上 5 个业务文件，新增 5 个接口测试。总览排除 CLOSED，分页采用 long 偏移防溢出；旧接口、任务规则、品牌、数据与迁移保持。
- Java 21 全量 120 项测试及打包、npm run check 通过。新增 motion，已有 source-map-js 升级到 1.2.2，安装审计 0 漏洞。
- 隔离 H2 + Chrome CDP：四预设首页/登录 48 组截图，8 组核心对比度最低 5.09:1；总览/分页搜索/草稿恢复与退出清理、后台持久外壳/抽屉焦点及未保存保护、品牌/权限和全部既有任务进度回归通过。截图和报告在 `.codex-run/ui-review/openlims-latest-*`；不宣称全页面全像素无障碍审计。
- 全部临时实例已关停，专用存储/profile 和最终内存实例创建的空默认目录已清理；未连接生产、未启动 MySQL。生产部署与既有 MySQL 实机验收另行执行。

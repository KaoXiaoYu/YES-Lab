> 当前完整模块操作入口与流程见 [使用指南](../../docs/user-guide.md)。本文件保留 API 边界，新增能力按底部补充说明读取；历史接口/字段的兼容保留不表示新增入口。

# 模块边界

## 公开展示端

公开、只读 API 位于 `/api/v1/public`，继续提供首页、项目、成员、排行榜和动态数据，不要求登录。

- `GET /api/v1/public/member-profiles`：读取已正式公开的成员目录。
- `GET /api/v1/public/member-profiles/{id}`：读取成员公开主页；不返回内部编号和内部联系方式。
- `GET /api/v1/public/project-teams`：读取已开启公开展示且未归档的项目团队。
- `GET /api/v1/public/project-teams/{id}`：读取单个公开项目；不返回项目管理员和内部权限信息。
- `GET /api/v1/public/project-teams/{id}/cover`：读取公开项目已上传的主图；无上传时由前端使用默认图。
- `GET /api/v1/public/competitions`：读取管理员选入首页的已认证比赛，按管理员排序值排列。
- `GET /api/v1/public/competitions/countdown`：读取全部未结束比赛中下一个省赛/国赛场次，仅返回比赛名、赛道、阶段和日期。
- `GET /api/v1/public/competitions/{id}`：读取任意已认证比赛的图文详情。
- `GET /api/v1/public/competitions/{id}/certificate`：内联读取已认证比赛的证书；未认证记录返回 404。
- `GET /api/v1/public/competitions/{competitionId}/images/{imageId}`：读取已认证比赛的公开图片。
- `GET /api/v1/public/news`：读取公开的外部新闻引用，按发布日期倒序。

`GET /api/v1/public/home` 同时返回可维护的 `homepageContent`，公开前端据此渲染首屏、3D 模型轮播、带跳转地址的研究方向、栏目文案、概览、关于我们特色卡片、备用比赛成果、赞助伙伴和外部入口，并应用管理员选择的指导老师、核心成员与项目顺序。

前端会将一次完整的公开响应保存为仅含公开字段的浏览器快照。刷新时先同步使用该快照，再向数据库接口更新；当前浏览器从未成功取得数据且 API 不可用时才使用源码内置兜底。快照不是另一套业务数据源，也不会把数据库内容写回 Git 仓库。

## 公开主页内容管理

具有 `CONTENT_MANAGE` 权限的系统管理员可访问：

- `GET /api/v1/admin/homepage`：读取当前主页配置、最后保存时间和操作账号。
- `PUT /api/v1/admin/homepage`：整体校验并保存一版主页配置。
- `POST /api/v1/admin/homepage/models`：上传经过格式和大小校验的 GLB 2.0 模型，返回公开资源地址。
- `GET /api/v1/public/homepage/models/{id}`：公开读取已上传的 GLB 模型，使用不可变缓存地址。

主页配置采用单一持久化版本，覆盖实验室品牌文案、研究方向名称与导航地址、各展示栏目文案、概览条、关于我们特色卡片、备用比赛成果、备用动态、赞助伙伴、外部入口、3D 模型轮播和首页内容选择。3D 轮播最多 8 项，每项对应一个 GLB，至少启用一项；模型地址只允许项目内置 `/models/*.glb` 或后台上传产生的公开 UUID 地址。研究方向导航地址仅允许页内锚点、站内绝对路径或 HTTP(S) URL。成员资料、项目详情、比赛记录与新闻引用继续由各自模块维护；主页配置只保存其展示对象 ID 和顺序，不复制业务数据。

## 身份认证

- `POST /api/v1/auth/register`：注册游客账号。
- `POST /api/v1/auth/login`：账号密码登录并签发 JWT。
- `PUT /api/v1/auth/password`：登录账号验证当前密码后修改密码，并撤销该账号全部刷新会话。
- `GET /api/v1/auth/me`：读取当前账号、角色与权限。

JWT 使用 HS256 签名，API 保持无状态；生产环境必须替换 `OPENLIMS_JWT_SECRET`。访问令牌有效期为 15 分钟，刷新令牌只以摘要保存并在每次刷新时轮换；退出、本人改密和管理员重置都会撤销相应刷新会话。

## 游客招新

- `GET /api/v1/recruitment/me`：读取自己的报名表、当前阶段和变更历史。
- `PUT /api/v1/recruitment/me`：在报名阶段创建或修改自己的报名表。

流程固定为：报名 → 初筛 → 面试 → 技能测试 → 正式成员。试用期阶段已停用（枚举保留仅兼容历史数据），技能测试阶段完成新手任务并经管理员审核通过后直接转为正式成员；任意非终态可进入“未通过”。技能测试阶段只保留 `linkedQuizId`，不接测验业务。

新手任务通过 `GET|PATCH|POST /api/v1/recruitment/me/onboarding-task` 由报名者本人查看与提交，沿用 `RECRUITMENT_SELF_VIEW` 与 `RECRUITMENT_SELF_EDIT`；管理端审核走任务模块的审核接口。

## 成员个人主页

- `GET /api/v1/member/profile`：读取自己的规范成员资料和成长数据占位。
- `PUT /api/v1/member/profile`：本人编辑内部联系方式、主页标语和富文本内容。
- `GET /api/v1/member/profile/showcase`：读取本人可展示的公开项目、已认证奖项及当前选择顺序。
- `PUT /api/v1/member/profile/showcase`：保存本人项目和奖项的展示选择与顺序。
- `PUT /api/v1/member/profile/avatar`、`DELETE /api/v1/member/profile/avatar`：本人上传、替换或移除头像。

姓名、编号、专业、班级、年级、成员状态和能力标签仍由管理员维护。富文本在后端通过 OWASP HTML Sanitizer 白名单清洗。

## 成员积分

- `GET /api/v1/points/rules`：登录账号读取积分分类、分配方式和月度上限。
- `GET /api/v1/member/points`：成员读取本人总积分、分类汇总、子类汇总和最近 200 笔流水。
- `POST /api/v1/admin/points/grants`：具有 `POINTS_MANAGE` 权限的教师或核心学生发放一批积分。
- `GET /api/v1/admin/points/grants`：积分管理员读取最近 200 次发放与撤销记录。
- `POST /api/v1/admin/points/grants/{grantId}/reversal`：为一批错误发放创建等额反向流水；原记录不会修改或删除。

积分子类包括 `COMPETITION_AWARD`、`PROJECT_TASK`、`LAB_ACTIVITY`、`MEDIA_CONTENT`、`MEDIA_OPERATION` 和 `MEDIA_REACH`。竞赛积分采用 `PER_MEMBER`，每名成员的分值必须等于奖项分值；其他类型采用 `SHARED_TOTAL`，全部成员的应得分之和必须等于事项总分。实验室贡献每人每月最多 100 分，运营执行和传播效果分别每人每月最多 200 分，按 `occurredOn` 所在历史月份校验；后一笔超过上限时保留应得分并只计入剩余额度，已经达到上限后拒绝继续发放。竞赛、项目和内容制作暂不设月度上限。

发放请求必须包含事项名称、子类、成果日期、事项总分、请求键、适用的库内来源和一至一百条成员分配；每条成员分配都要填写个人贡献说明，事项本身可再填写整体说明。只允许给正式核心学生或普通成员加正分；指导教师不参评，管理员不能给自己发分。后端生成 `sourceReference`，请求键/摘要防止重试导致重复发放，成员总积分随流水在同一事务更新，发放与撤销都会产生梅琳娜站内消息。

```json
{
  "title": "完成 SLAM 部署任务",
  "subcategory": "PROJECT_TASK",
  "occurredOn": "2026-09-15",
  "itemTotalPoints": 50,
  "sourceReference": "PROJECT:8f12:SLAM-DEPLOYMENT",
  "evidenceUrl": "https://example.com/evidence/slam",
  "description": "任务已经按登记条件验收",
  "allocations": [
    {
      "memberProfileId": "00000000-0000-0000-0000-000000000001",
      "points": 30,
      "contribution": "完成部署配置与数据采集"
    },
    {
      "memberProfileId": "00000000-0000-0000-0000-000000000002",
      "points": 20,
      "contribution": "完成复现测试与验收记录"
    }
  ]
}
```

参考制度中的具体事项分值没有硬编码进接口。管理员按最终确认的制度填写事项总分，接口负责权限、分配合计、月度封顶、库内来源、幂等和撤销留痕。公开排行榜读取真实账本汇总，只返回总/月/年榜的有界预览。

## 招新管理

教师和核心学生均拥有系统管理员权限，可访问：

- `GET /api/v1/admin/recruitment/applications`
- `GET /api/v1/admin/recruitment/interviewers`
- `PATCH /api/v1/admin/recruitment/applications/{id}/stage`
- `PUT /api/v1/admin/recruitment/applications/{id}/interview`
- `PATCH /api/v1/admin/recruitment/applications/{id}/interview-decision`
- `PATCH /api/v1/admin/recruitment/applications/{id}/interview-result-pending`
- `PUT /api/v1/admin/recruitment/applications/{id}/password`
- `POST /api/v1/admin/recruitment/applications/{id}/convert`

招新密码重置只适用于尚未转为正式成员的报名账号，重置结果固定为默认密码 `OpenLIMS521`。转正会保留原报名与面试历史，将游客账号角色改为普通成员，并创建规范成员资料；转正仅对技能测试阶段的记录可用，并受新手任务守卫约束，可由管理员填写豁免理由后豁免并转正。

### 面试预约与叫号

- `GET /api/v1/recruitment/interviews`：面试阶段报名者读取可预约场次或自己的预约、面试号与当前叫号；未预约时不返回地点、发布者或面试官信息。
- `POST /api/v1/recruitment/interviews/sessions/{id}/book`、`DELETE /api/v1/recruitment/interviews/booking`：预约一个场次或在开始前取消；取消后原号码不复用。
- `/api/v1/admin/recruitment/interview-sessions`：教师和核心学生发布未来十四天内的线下场次并指定多人面试官；发布者必须参加。管理员场次响应同时汇总已预约的大一、大二人数，兼容“大一/大二”和入学年份写法。
- 本场任一面试官可以修改/取消、一次叫一人、开始面试、将未到场者以新号码移至队尾、提交“通过 / 候补观察 / 未通过”结论或提前结束。通过与候补观察必须填写简评；候补观察会完成当前预约但继续停留在面试阶段，管理员之后可最终改判为通过或未通过。对于仍在面试阶段且尚无结论的遗漏记录，管理员可直接手动切换为“待补录面试结果”；系统会把仍在等待、已叫号或面试中的预约同步标记为已完成，避免继续占用叫号队列。提交结果前恢复为“面试”时会释放未录入结论的已完成预约，使报名者可以重新预约。**系统管理员可撤销最终“面试未通过”结论，将记录回退到“待补录面试结果”；初筛未通过等其他拒绝记录不能通过此入口恢复。撤销会保留评分和详细评价、清除旧最终结论、写入跨阶段历史，并通知报名者。**待补录期间候选端停止提供预约入口。补录必须填写参与面试官和详细评价，可填写 0—100 分评分、建议标签与最终说明，通过时还必须填写最终说明。全部状态切换、结论和详细信息继续写入原招新记录并保留审计。

取消或提前结束会释放未完成预约并由梅琳娜通知报名者重新预约；对应场次保留 24 小时后由每小时运行的清理任务永久删除。面试通过的报名者继续保留在招新管理中，以便推进技能测试与正式成员转换；完成正式成员转换后退出待处理列表。面试阶段没有可用场次时，系统至多每 24 小时向每位教师和核心学生发送一次发布提醒。

## 站内消息

- `GET /api/v1/notifications`：读取最近 50 条消息及未读数。
- `GET /api/v1/notifications/visibility`：读取当前账号是否展示梅琳娜、消息铃铛和弹窗。
- `PATCH /api/v1/notifications/{id}/read`、`PATCH /api/v1/notifications/read-all`：标记单条或全部已读。
- `GET|PUT /api/v1/admin/notifications/melina-visibility`：仅指导老师可按角色设置默认展示范围，并用指定账号例外覆盖角色设置。

站内消息只由后端机器人“梅琳娜”在业务事件中创建，不提供成员发送接口。前端仅在当前账号允许展示时轮询消息；单条显示摘要，多条新消息折叠为总数提示。隐藏展示不删除已收到的消息。相同讨论内容的点赞在阅读前合并计数；讨论公告发布时向全部启用账号各生成一条 `DISCUSSION_ANNOUNCEMENT` 消息。

## 讨论板

`GET /api/v1/discussions?sort=...` 允许匿名访问，所有未注册访客和注册用户都能浏览讨论、回复、公开姓名及公开头像；排序支持 `NEWEST`、`OLDEST`、`ID_ASC`、`ID_DESC`、`MOST_LIKED` 和 `MOST_REPLIED`。帖子与回复共用 `discussion_content_numbers` 的全局递增编号，删除内容不回收编号。`GET /api/v1/discussions/authors/{profileId}` 返回已公开正式成员的发帖与回复，用于成员公开主页；匿名用户同样可读取。

只有教师、核心学生和正式成员可以发布、编辑和删除自己的富文本讨论与一级回复，并给他人讨论或回复点赞；注册游客调用写接口返回 403。发帖请求可选择 `announcement=true`，公告创建后标题和正文不可再修改，并由梅琳娜广播站内消息。教师和核心学生可以通过 `PATCH /api/v1/discussions/{postId}/pin` 置顶或取消置顶，也可以删除违规内容；普通成员没有置顶权限，任何人都不能给自己的内容点赞。作者头像和姓名使用公开成员 `profileId` 深链接到个人主页，回复和点赞会向内容作者生成站内消息。

讨论内容支持标题、列表、引用、链接、网络图片、行内代码和代码块。前端 Tiptap 只负责编辑体验，后端 OWASP HTML Sanitizer 白名单是最终安全边界；只允许 HTTP(S) 链接和图片地址，移除脚本、事件属性及未授权标签。过长的帖子、回复和公开主页讨论动态只在展示层折叠，可由用户展开/收起，服务端内容不截断。已有纯文本内容无需数据迁移，仍可直接显示。

## 成员管理

教师和核心学生可访问：

- `GET /api/v1/admin/members`
- `GET /api/v1/admin/members/{id}`
- `PUT /api/v1/admin/members/{id}`
- `POST /api/v1/admin/members/core-students`
- `PUT /api/v1/admin/members/{id}/password`
- `PUT /api/v1/admin/members/{id}/avatar`、`DELETE /api/v1/admin/members/{id}/avatar`

管理员维护姓名、编号、角色、状态、专业、班级、年级、内部联系方式和能力标签，可直接创建具有完整系统权限的核心学生账号，也可协助维护成员头像并把密码重置为默认密码 `OpenLIMS521`。教师角色会自动清空不适用的专业、班级和年级；标语和主页正文继续由成员本人维护。

## 项目团队管理

- `GET /api/v1/projects`：系统管理员读取全部项目；普通成员读取自己参与的项目。
- `GET /api/v1/projects/member-options`：读取可加入项目的试用/正式成员。
- `POST /api/v1/projects`：仅系统管理员创建项目并指定负责人、可选指导老师、成员和项目管理员。
- `GET /api/v1/projects/{id}`：读取有权访问的项目团队空间。
- `PUT /api/v1/projects/{id}`：负责人、项目管理员或系统管理员维护项目资料、指导老师与公开开关。
- `PUT /api/v1/projects/{id}/cover`：负责人、项目管理员或系统管理员上传或替换项目主图。
- `GET /api/v1/projects/{id}/cover`：项目参与者或系统管理员通过 JWT 读取内部项目主图。
- `PUT /api/v1/projects/{id}/team`：负责人或系统管理员维护团队名称、成员和项目管理员；只有系统管理员能更换负责人。

项目主图支持 JPG、PNG、WebP，最大 8MB，并校验文件签名；未上传时统一显示 OpenLIMS 默认图。指导老师只能关联教师账号，且独立于负责人、成员和项目管理员，不因关联而自动获得团队角色。当前团队空间不包含即时消息、文件聊天或已读状态。

## 任务模块

> 2026-09-21 起本模块还有两项与「积分何时发放」和「第三类任务」相关的口径，见
> `docs/task-settlement-design.md`（到期结算与到期冻结）与 `docs/bounty-task-design.md`（悬赏任务）：
> 积分不再在审核通过时发放，而是任务到期后由 `TaskSettlementService` 统一结算，只发给到期时已通过的对象；
> 悬赏任务是成员自主接取、有接取名额上限、先完成先得奖金（线下）的第三类任务，走 `/api/v1/bounties` 与
> `/api/v1/admin/bounties`，不参与按等级条件发放。
> 奖金履约通过 `V16` 台账独立记录管理员线下发放与获奖成员本人确认领取，截止/结束后仍可补录；该台账不参与名次与积分结算。

任务模块承载三类目标不同的任务（新手任务、普通任务、悬赏任务），**共用同一套「大任务 + 子任务 + 每人对象」模型**：`tasks` 是大任务，`task_subtasks` 是它下面可增删的子任务，`task_assignments` 记录发给谁与完成情况，`task_subtask_progress` 记录每个人对每个子任务的勾选。富文本正文、完成情况与人工审核能力同样共用。所有任务正文在后端通过 OWASP HTML Sanitizer 白名单清洗（允许标题、列表、引用、链接、图片、行内代码与代码块），前端 Tiptap 只是编辑体验，后端清洗是最终安全边界。

### 新手任务（`ONBOARDING`）

新手任务是**全实验室唯一的一条大任务**（`tasks` 中唯一一条 `ONBOARDING`），**所有处于技能测试阶段的报名者共享它**：管理员在大任务里维护标题、富文本说明、子任务清单与时长（天），每个报名者在大任务上有一条自己的对象，进度按人记录。新手任务**不发放积分**，只作转正门槛；**必须勾选全部子任务**才能提交，管理员审核通过即直接转正。

- 大任务不存在时（功能刚上线、还没人进入技能测试阶段）用代码内置默认内容（标题、说明、5 项子任务、7 天）自动初始化落库，不阻塞招新流程；创建即生效、不可删除。
- 每人的截止日期 = **本人被分配到大任务当天 + 当时的时长**，因此同一大任务下各人截止日期不同；对象侧存 `task_assignments.due_date`。
- 管理员保存大任务后**即时对在途对象生效**：新增子任务会把「已提交待确认」的对象退回「待完成」并发站内消息；删除子任务连同对应勾选记录一起删除；修改时长会按各自发放日重算尚未通过对象的截止日期。
- `GET /api/v1/recruitment/me/onboarding-task`：报名者读取共享大任务内容、**本人进度（已完成 x / y 项）**、剩余天数与审核意见。
- `PATCH /api/v1/recruitment/me/onboarding-task/subtasks/{subtaskId}`、`POST /api/v1/recruitment/me/onboarding-task/submission`：勾选子任务或提交完成说明，沿用 `RECRUITMENT_SELF_VIEW` 与 `RECRUITMENT_SELF_EDIT`；未勾完全部子任务时提交被拒，待确认状态下改动勾选会退回「待完成」。
- `GET|PUT /api/v1/admin/tasks/onboarding`：读取与保存新手任务大任务（标题、富文本说明、时长天数、子任务清单）；保存结果返回 `reopenedCount`（被退回待完成的人数）与 `rescheduledCount`（截止日期被重算的人数）。
- `GET /api/v1/admin/tasks/onboarding-overview`：大任务内容 + 技能测试阶段完成情况与缺失提示。
- `POST /api/v1/admin/tasks/onboarding-tasks/backfill`：为技能测试阶段且在大任务上还没有对象的记录批量补建，幂等。

### 普通任务（`STANDARD`）

面向**成员档案**，由管理员按等级条件或指定个人发放。

- `GET /api/v1/admin/tasks`、`{taskId}`、`{taskId}/progress`：任务列表与汇总、详情、完成情况（任务汇总 + 子任务完成率 + 逐人明细含积分结果）。
- `POST /api/v1/admin/tasks`、`PUT /api/v1/admin/tasks/{taskId}`：创建草稿与修改。**发布后条件、对象与积分值锁定**，只能修改标题、正文、起止日期与子任务。
- `POST /api/v1/admin/tasks/audience-preview`：按条件预览命中成员，并标注每人是否可计分及原因。
- `POST /api/v1/admin/tasks/{taskId}/publish`：发布并快照对象，`{taskId}/close` 结束，`DELETE {taskId}` 仅删除草稿。
- `POST /api/v1/admin/tasks/{taskId}/assignments`：补充发放，按条件补差集；`DELETE .../assignments/{assignmentId}` 移除未提交且未计分的对象。
- `GET /api/v1/tasks`、`{assignmentId}`、`PATCH {assignmentId}/subtasks/{subtaskId}`、`POST {assignmentId}/submission`：成员读取本人任务、勾选子任务与提交完成说明，只返回与当前成员档案一致的对象。
- `PUT /api/v1/admin/tasks/{taskId}/assignments/{assignmentId}/review`：人工审核入口。普通任务确认通过只记完成，积分由到期定时结算；驳回必填意见且不发分；新手任务确认通过时直接转为正式成员（通过前再次校验子任务已全部勾选，填了豁免理由的路径除外）。

### 等级条件与计分

发放条件复用现有字段，不新增等级维度：`ROLE`（教师/核心学生/普通成员）、`MEMBER_STATUS`（仅试用与正式）、`GRADE`、`SKILL_TAG`。**同一维度内多个取值取「或」，不同维度之间取「且」**。`VISITOR` 没有成员档案，不能作为任务对象；新手任务是游客唯一会收到的任务类型，它按报名记录而非成员档案建立对象。

两类任务的提交口径有一处**有意保留的差异**：新手任务必须勾完全部子任务才能提交（转正门槛必须可判定）；普通任务允许随时提交完成说明，是否达标由管理员人工判断，避免成员因某一项无法完成而被卡住、管理员也无从审核。

积分复用 `PROJECT_TASK` 子类，`sourceReference` 为 `TASK:{taskId}:{memberProfileId}`，唯一约束保证重复审核不重复计分。凭证默认使用站内路径 `/tasks/{assignmentId}`，审核时可填外部链接。以下三种情况**审核照常成功但跳过计分并记录原因**，与积分模块既有规则一致且不放宽：对象是教师、对象不是正式成员、审核人就是对象本人。管理端列表与详情中的「已逾期」只是按截止日期的显示层派生，不写库、不改状态、也不触发任何自动处理。

## 竞赛成果与新闻管理

- `GET /api/v1/competitions`：系统管理员读取全部记录；普通成员读取已认证记录及自己担任队长或被关联的记录。
- `GET /api/v1/competitions/countdown`：登录成员读取自己作为队长、关联队员或指导老师参与的最近未结束场次；管理员也不会因权限看到与本人无关的倒计时。
- `POST /api/v1/competitions`：试用/正式成员提交比赛；提交账号自动成为队长。
- `PUT /api/v1/competitions/{id}`：队长修改未认证记录，系统管理员可修改全部记录。
- `PUT /api/v1/competitions/{id}/certificate`：队长或管理员替换证书；已结束比赛重新进入待审核。
- `PUT /api/v1/competitions/{id}/images`：队长或管理员替换比赛图集，最多 8 张。
- `DELETE /api/v1/competitions/{competitionId}/images/{imageId}`：队长或管理员单独删除一张比赛图集图片，并同步清理存储文件。
- `GET /api/v1/competitions/{id}/certificate`：只有队长和系统管理员可读取私有证书。
- `PATCH /api/v1/admin/achievements/competitions/{id}/review`：系统管理员通过或驳回已结束比赛。
- `PATCH /api/v1/admin/achievements/competitions/{id}/display`：系统管理员维护首页开关和手动排序。
- `/api/v1/admin/achievements/news`：系统管理员创建、读取和修改外部新闻引用。

完赛记录允许待成绩/已结束未获奖，只有获奖成果要求奖项与 PDF/JPG/PNG 证书；文件类型优先按内容签名识别。扩展名为 `.jpg/.jpeg`、内容实际为 BMP 的扫描件会在保存时解码并转成真正 JPEG，存量同类文件也会在首次读取时使用临时文件原子替换完成修复；不可解码的伪装内容会拒绝。新参赛记录填写单一比赛日期和私有报名截图，导师与旧省赛/国赛日期兼容保留；日程已统一在首页/个人操作台按北京时间展示。队员与指导老师可关联成员系统账号，也可只保留展示姓名；关联项目仅允许队长选择自己参与的项目。认证通过后公开详情返回带版本参数的证书地址与稳定图片 ID，前端将图片证书作为主图、PDF 证书作为内嵌文档展示；认证前证书和图集均不可公开访问。公开证书、图集、头像和项目封面响应允许一年不可变缓存，资源更新时通过新 ID 或版本参数换址。

## 暂不实现

- 测验与写题业务接口。
- 成员积分申请/终审工作流、附件上传、具体事项规则后台配置与公开排行榜写入。
- 新闻正文抓取或复制；当前只保存外部标题、来源、链接、摘要和发布日期。
- 竞赛记录删除、批量导入和通用操作审计。
- 登录失败限流、异常登录告警与完整通用审计系统。

## 基金、日程、悬赏与积分联动补充（2026-10-04）

- 基金：`/api/v1/public/fund/summary` 公开汇总，`/api/v1/fund` 与 `/entries` 成员汇总/明细；`POST /api/v1/fund/initialize`、`/entries`、`/entries/{id}/reverse` 按 FUND_MANAGE 校验期初/收支/撤销；单账户人民币、请求幂等、反向流水及非负余额。
- 日程：`GET /api/v1/public/deadlines` 支持类型/分页/首页 14 天窗口；`GET /api/v1/me/deadlines` 按本人关联范围读取；首页全量分页与个人历史保留。
- 悬赏：`/api/v1/bounties` 榜/详情/接取/放弃；`POST /api/v1/tasks/{assignmentId}/bounty-prize/confirm-received` 本人奖金领取，`/api/v1/admin/bounties` 管理、claims 名单和线下奖金发放，任务提交复用 assignments。名次/奖金与到期积分结算相互独立。
- 所有任务到期后冻结提交和审核，普通任务不再“审核即计分”；新手按人延期唯一解锁，普通任务角色/年级/个人补发沿用后台 assignments 接口。完整规则仍在任务/结算/悬赏需求设计中。
- 积分管理 sources 接口读取符合条件的库内来源和关联正式学生，后端自动编号、请求键/摘要幂等；取消新增凭证要求，旧字段兼容。首页真实总/月/年榜公开最多 6 人，完整榜认证分页。
- 比赛两主状态未开始/完赛，完赛有待成绩/已结束获奖或未获奖；private registration 端点校验归属，公开接口不泄露报名图，获奖证书审核规则保留。

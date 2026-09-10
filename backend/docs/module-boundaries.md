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

`GET /api/v1/public/home` 同时返回可维护的 `homepageContent`，公开前端据此渲染首屏、带跳转地址的研究方向、栏目文案、概览、关于我们特色卡片、备用比赛成果、赞助伙伴和外部入口，并应用管理员选择的指导老师、核心成员与项目顺序。

前端会将一次完整的公开响应保存为仅含公开字段的浏览器快照。刷新时先同步使用该快照，再向数据库接口更新；当前浏览器从未成功取得数据且 API 不可用时才使用源码内置兜底。快照不是另一套业务数据源，也不会把数据库内容写回 Git 仓库。

## 公开主页内容管理

具有 `CONTENT_MANAGE` 权限的系统管理员可访问：

- `GET /api/v1/admin/homepage`：读取当前主页配置、最后保存时间和操作账号。
- `PUT /api/v1/admin/homepage`：整体校验并保存一版主页配置。

主页配置采用单一持久化版本，覆盖实验室品牌文案、研究方向名称与导航地址、各展示栏目文案、概览条、关于我们特色卡片、备用比赛成果、备用动态、赞助伙伴、外部入口和首页内容选择。研究方向导航地址仅允许页内锚点、站内绝对路径或 HTTP(S) URL。成员资料、项目详情、比赛记录与新闻引用继续由各自模块维护；主页配置只保存其展示对象 ID 和顺序，不复制业务数据。

## 身份认证

- `POST /api/v1/auth/register`：注册游客账号。
- `POST /api/v1/auth/login`：账号密码登录并签发 JWT。
- `GET /api/v1/auth/me`：读取当前账号、角色与权限。

JWT 使用 HS256 签名，API 保持无状态；生产环境必须替换 `YESLAB_JWT_SECRET`。当前只签发短期访问令牌，不实现刷新令牌和主动吊销。

## 游客招新

- `GET /api/v1/recruitment/me`：读取自己的报名表、当前阶段和变更历史。
- `PUT /api/v1/recruitment/me`：在报名阶段创建或修改自己的报名表。

流程固定为：报名 → 初筛 → 面试 → 技能测试 → 试用期 → 正式成员；任意非终态可进入“未通过”。技能测试阶段只保留 `linkedQuizId`，不接测验业务。

## 成员个人主页

- `GET /api/v1/member/profile`：读取自己的规范成员资料和成长数据占位。
- `PUT /api/v1/member/profile`：本人编辑内部联系方式、主页标语和富文本内容。
- `GET /api/v1/member/profile/showcase`：读取本人可展示的公开项目、已认证奖项及当前选择顺序。
- `PUT /api/v1/member/profile/showcase`：保存本人项目和奖项的展示选择与顺序。
- `PUT /api/v1/member/profile/avatar`、`DELETE /api/v1/member/profile/avatar`：本人上传、替换或移除头像。

姓名、编号、专业、班级、年级、成员状态和能力标签仍由管理员维护。富文本在后端通过 OWASP HTML Sanitizer 白名单清洗。

## 招新管理

教师和核心学生均拥有系统管理员权限，可访问：

- `GET /api/v1/admin/recruitment/applications`
- `GET /api/v1/admin/recruitment/interviewers`
- `PATCH /api/v1/admin/recruitment/applications/{id}/stage`
- `PUT /api/v1/admin/recruitment/applications/{id}/interview`
- `POST /api/v1/admin/recruitment/applications/{id}/convert`

一键转成员会保留原报名与面试历史，将游客账号角色改为普通成员，并创建规范成员资料。

### 面试预约与叫号

- `GET /api/v1/recruitment/interviews`：面试阶段报名者读取可预约场次或自己的预约、面试号与当前叫号；未预约时不返回地点、发布者或面试官信息。
- `POST /api/v1/recruitment/interviews/sessions/{id}/book`、`DELETE /api/v1/recruitment/interviews/booking`：预约一个场次或在开始前取消；取消后原号码不复用。
- `/api/v1/admin/recruitment/interview-sessions`：教师和核心学生发布未来十四天内的线下场次并指定多人面试官；发布者必须参加。
- 本场任一面试官可以修改/取消、一次叫一人、开始面试、将未到场者以新号码移至队尾、提交统一结论或提前结束。通过必须填写简评，结论继续写入原招新记录以兼容既有接口。

取消或提前结束会释放未完成预约并由梅琳娜通知报名者重新预约。面试阶段没有可用场次时，系统至多每 24 小时向每位教师和核心学生发送一次发布提醒。

## 站内消息

- `GET /api/v1/notifications`：读取最近 50 条消息及未读数。
- `PATCH /api/v1/notifications/{id}/read`、`PATCH /api/v1/notifications/read-all`：标记单条或全部已读。

站内消息只由后端机器人“梅琳娜”在业务事件中创建，不提供成员发送接口。前端登录后轮询消息；单条显示摘要，多条新消息折叠为总数提示。相同讨论内容的点赞在阅读前合并计数；讨论公告发布时向全部启用账号各生成一条 `DISCUSSION_ANNOUNCEMENT` 消息。

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
- `PUT /api/v1/admin/members/{id}/avatar`、`DELETE /api/v1/admin/members/{id}/avatar`

管理员维护姓名、编号、角色、状态、专业、班级、年级、内部联系方式和能力标签，可直接创建具有完整系统权限的核心学生账号，也可协助维护成员头像。教师角色会自动清空不适用的专业、班级和年级；标语和主页正文继续由成员本人维护。

## 项目团队管理

- `GET /api/v1/projects`：系统管理员读取全部项目；普通成员读取自己参与的项目。
- `GET /api/v1/projects/member-options`：读取可加入项目的试用/正式成员。
- `POST /api/v1/projects`：仅系统管理员创建项目并指定负责人、可选指导老师、成员和项目管理员。
- `GET /api/v1/projects/{id}`：读取有权访问的项目团队空间。
- `PUT /api/v1/projects/{id}`：负责人、项目管理员或系统管理员维护项目资料、指导老师与公开开关。
- `PUT /api/v1/projects/{id}/cover`：负责人、项目管理员或系统管理员上传或替换项目主图。
- `GET /api/v1/projects/{id}/cover`：项目参与者或系统管理员通过 JWT 读取内部项目主图。
- `PUT /api/v1/projects/{id}/team`：负责人或系统管理员维护团队名称、成员和项目管理员；只有系统管理员能更换负责人。

项目主图支持 JPG、PNG、WebP，最大 8MB，并校验文件签名；未上传时统一显示 YES Lab 默认图。指导老师只能关联教师账号，且独立于负责人、成员和项目管理员，不因关联而自动获得团队角色。当前团队空间不包含即时消息、文件聊天或已读状态。

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

已结束比赛必须填写获奖结果和比赛日期并上传 PDF/JPG/PNG 证书；文件类型优先按内容签名识别。扩展名为 `.jpg/.jpeg`、内容实际为 BMP 的扫描件会在保存时解码并转成真正 JPEG，存量同类文件也会在首次读取时使用临时文件原子替换完成修复；不可解码的伪装内容会拒绝。未结束比赛必须填写省赛、国赛时间和指导老师，右下角倒计时统一以上海日期边界选择下一场省赛或国赛并计算剩余天数。队员与指导老师可关联成员系统账号，也可只保留展示姓名；关联项目仅允许队长选择自己参与的项目。认证通过后公开详情返回带版本参数的证书地址与稳定图片 ID，前端将图片证书作为主图、PDF 证书作为内嵌文档展示；认证前证书和图集均不可公开访问。公开证书、图集、头像和项目封面响应允许一年不可变缓存，资源更新时通过新 ID 或版本参数换址。

## 暂不实现

- 测验与写题业务接口。
- 积分计算、积分变更与排行榜写入。
- 新闻正文抓取或复制；当前只保存外部标题、来源、链接、摘要和发布日期。
- 竞赛记录删除、批量导入和通用操作审计。
- JWT 刷新、吊销、密码重置与完整通用审计系统。

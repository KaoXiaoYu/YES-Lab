#!/usr/bin/env bash
# OpenLIMS 任务模块端到端冒烟测试（真实 HTTP，独立内存库）
set +u
BASE=http://127.0.0.1:8080
PASS=0
FAIL=0
TEACHER=""; VISITOR=""; EMAIL=""; PW=""; QIDS=""; APP=""; APPID=""
OB=""; OBSTATUS=""; OBTITLE=""; OBEND=""; OBTASK=""; OBASSIGN=""; OBSUB=""; OBN=""
OV=""; REV=""; NEWTOKEN=""; PROFILE=""; MEMBER=""; BEFORE=0; CREATED=""; TASKID=""
AFTERPTS=""; PROG=""; ASSIGN=""; MYTASKS=""; REV2=""; AFTER=0; LEDGER=""; MID=""

j() {
  python3 -c '
import sys, json
try:
    d = json.load(sys.stdin)
    print(eval("d" + sys.argv[1]))
except Exception:
    print("")
' "$1" 2>/dev/null
}

check() { # check <描述> <期望> <实际>
  if [ "$2" = "$3" ]; then PASS=$((PASS+1)); echo "  ✓ $1"; else FAIL=$((FAIL+1)); echo "  ✗ $1 —— 期望 [$2] 实际 [$3]"; fi
}
check_nonempty() {
  if [ -n "$2" ]; then PASS=$((PASS+1)); echo "  ✓ $1"; else FAIL=$((FAIL+1)); echo "  ✗ $1 —— 期望非空，实际为空"; fi
}
check_contains() {
  case "$3" in *"$2"*) PASS=$((PASS+1)); echo "  ✓ $1";; *) FAIL=$((FAIL+1)); echo "  ✗ $1 —— 期望包含 [$2] 实际 [$3]";; esac
}
status_of() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

echo "== 1. 登录与报名 =="
TEACHER=$(curl -s -X POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"teacher","password":"OpenLIMS-Teacher-2026!"}' | j "['data']['accessToken']")
check_nonempty "教师登录拿到访问令牌" "$TEACHER"

TS=$(date +%s)
EMAIL="smoke-$TS@example.com"
MCODE="S-SMOKE-$TS"
PW="Smoke2026"
VISITOR=$(curl -s -X POST "$BASE/api/v1/auth/register" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$EMAIL\",\"password\":\"$PW\"}" | j "['data']['accessToken']")
check_nonempty "游客注册拿到访问令牌" "$VISITOR"

QBODY=$(curl -s "$BASE/api/v1/recruitment/me/questions" -H "Authorization: Bearer $VISITOR")
BODY=$(echo "$QBODY" | python3 -c '
import sys, json
qs = json.load(sys.stdin)["data"]
ids = [q["id"] for q in qs]
print(json.dumps({
  "name": "冒烟测试同学", "major": "计算机科学", "className": "计科 2501", "grade": "2025",
  "email": sys.argv[1], "interestDirections": ["机器人"], "existingSkills": ["Python"],
  "intendedTags": ["机器人控制"], "mediaLinks": [],
  "technicalQuestionIds": ids,
  "technicalAnswers": [{"questionId": i, "answer": "测试回答"} for i in ids],
}, ensure_ascii=False))
' "$EMAIL")
APP=$(curl -s -X PUT "$BASE/api/v1/recruitment/me" -H "Authorization: Bearer $VISITOR" \
  -H 'Content-Type: application/json' -d "$BODY")
APPID=$(echo "$APP" | j "['data']['id']")
check_nonempty "提交报名表并拿到记录 ID" "$APPID"

echo "== 2. 推进到技能测试（应自动发放新手任务） =="
st=$(status_of -X PATCH "$BASE/api/v1/admin/recruitment/applications/$APPID/stage" -H "Authorization: Bearer $TEACHER" \
  -H 'Content-Type: application/json' -d '{"stage":"SCREENING","note":"冒烟","linkedQuizId":null}')
check "初筛推进" "200" "$st"
st=$(status_of -X PUT "$BASE/api/v1/admin/recruitment/applications/$APPID/interview" -H "Authorization: Bearer $TEACHER" \
  -H 'Content-Type: application/json' -d '{"interviewerUsername":"core","score":88,"evaluation":"基础良好","suggestedTags":["机器人控制"],"passed":true}')
check "记录面试通过" "200" "$st"
st=$(status_of -X PATCH "$BASE/api/v1/admin/recruitment/applications/$APPID/stage" -H "Authorization: Bearer $TEACHER" \
  -H 'Content-Type: application/json' -d '{"stage":"SKILL_TEST","note":"进入技能测试","linkedQuizId":null}')
check "推进技能测试" "200" "$st"

echo "== 3. 新手任务自动发放与提交 =="
OB=$(curl -s "$BASE/api/v1/recruitment/me/onboarding-task" -H "Authorization: Bearer $VISITOR")
OBSTATUS=$(echo "$OB" | j "['data']['status']")
OBTITLE=$(echo "$OB" | j "['data']['title']")
OBEND=$(echo "$OB" | j "['data']['endDate']")
OBTASK=$(echo "$OB" | j "['data']['taskId']")
OBASSIGN=$(echo "$OB" | j "['data']['assignmentId']")
OBN=$(echo "$OB" | j "['data']['subtasks'].__len__()")
check "面试通过后自动发放，状态为待完成" "PENDING" "$OBSTATUS"
check "使用内置默认大任务标题" "新手入门任务" "$OBTITLE"
check "子任务数量（至少 5）" "ok" "$(python3 -c "print('ok' if $OBN >= 5 else 'too-few')")"
TODAY=$(date +%Y-%m-%d)
EXPECT_END=$(python3 -c "import datetime;print((datetime.date.today()+datetime.timedelta(days=7)).isoformat())")
check "截止日期为本人发放当天 + 大任务时长(7天)" "$EXPECT_END" "$OBEND"
check "发放日期为当天" "$TODAY" "$(echo "$OB" | j "['data']['startDate']")"

# 大任务是所有技能测试阶段报名者共享的同一条，管理员直接在其中维护子任务。
ONBOARD_ADMIN=$(curl -s "$BASE/api/v1/admin/tasks/onboarding" -H "Authorization: Bearer $TEACHER")
check "管理端读取共享大任务" "$OBTASK" "$(echo "$ONBOARD_ADMIN" | j "['data']['taskId']")"

OBFIRST=$(echo "$OB" | j "['data']['subtasks'][0]['id']")
st=$(status_of -X POST "$BASE/api/v1/recruitment/me/onboarding-task/subtasks/$OBFIRST/submission" -H "Authorization: Bearer $VISITOR" \
  -H 'Content-Type: application/json' -d '{"contentHtml":"   "}')
check "空内容提交子任务被拒绝" "400" "$st"

st=$(status_of -X POST "$BASE/api/v1/recruitment/me/onboarding-task/submission" -H "Authorization: Bearer $VISITOR" \
  -H 'Content-Type: application/json' -d '{"completionNote":"只完成了一部分"}')
check "未提交全部子任务时拒绝提交" "400" "$st"

for idx in $(seq 0 $((OBN - 1))); do
  OBSUB=$(echo "$OB" | j "['data']['subtasks'][$idx]['id']")
  st=$(status_of -X POST "$BASE/api/v1/recruitment/me/onboarding-task/subtasks/$OBSUB/submission" -H "Authorization: Bearer $VISITOR" \
    -H 'Content-Type: application/json' -d "{\"contentHtml\":\"<p>冒烟提交：第 $((idx + 1)) 项已完成。</p>\"}")
  check "提交第 $((idx + 1)) 个子任务的内容" "200" "$st"
done

st=$(status_of -X PUT "$BASE/api/v1/admin/tasks/$OBTASK/assignments/$OBASSIGN/review" -H "Authorization: Bearer $TEACHER" \
  -H 'Content-Type: application/json' -d "{\"decision\":\"APPROVED\",\"memberCode\":\"$MCODE\",\"skillTags\":[\"机器人控制\"]}")
check "未提交完成说明时拒绝通过（守卫）" "409" "$st"

st=$(status_of -X POST "$BASE/api/v1/recruitment/me/onboarding-task/submission" -H "Authorization: Bearer $VISITOR" \
  -H 'Content-Type: application/json' -d '{"completionNote":"环境已配好，示例已跑通"}')
check "全部子任务完成后提交" "200" "$st"

# 管理员新增子任务：已提交待确认的对象退回待完成，勾选新项后可重新提交。
# 按 id 提交（保留已提交进度），并补上第一项的子任务说明与一项新子任务。
CUR=$(curl -s "$BASE/api/v1/admin/tasks/onboarding" -H "Authorization: Bearer $TEACHER")
SUB0=$(echo "$CUR" | j "['data']['subtasks'][0]['id']")
SUB1=$(echo "$CUR" | j "['data']['subtasks'][1]['id']")
SUB2=$(echo "$CUR" | j "['data']['subtasks'][2]['id']")
SUB3=$(echo "$CUR" | j "['data']['subtasks'][3]['id']")
SUB4=$(echo "$CUR" | j "['data']['subtasks'][4]['id']")
st=$(status_of -X PUT "$BASE/api/v1/admin/tasks/onboarding" -H "Authorization: Bearer $TEACHER" \
  -H 'Content-Type: application/json' \
  -d "{\"title\":\"新手入门任务\",\"contentHtml\":\"<p>请完成下列基础训练。</p>\",\"durationDays\":7,\"subtasks\":[{\"id\":\"$SUB0\",\"title\":\"配置开发环境（Git、Python 或 Java、代码编辑器）\",\"contentHtml\":\"<p>安装 Git 与 JDK 21。</p>\"},{\"id\":\"$SUB1\",\"title\":\"阅读实验室新人手册与安全规范\"},{\"id\":\"$SUB2\",\"title\":\"认识实验室常用的无人机与机器狗设备，了解基本安全操作\"},{\"id\":\"$SUB3\",\"title\":\"跑通一个示例程序或仿真环境\"},{\"id\":\"$SUB4\",\"title\":\"在讨论板发布一条自我介绍\"},{\"title\":\"冒烟新增：阅读一条安全通报\"}]}")
check "管理员在大任务上新增子任务（保留已提交进度）" "200" "$st"
OB2=$(curl -s "$BASE/api/v1/recruitment/me/onboarding-task" -H "Authorization: Bearer $VISITOR")
check "已提交对象被退回待完成" "PENDING" "$(echo "$OB2" | j "['data']['status']")"
check "新增子任务后总数" "6" "$(echo "$OB2" | j "['data']['subtasks'].__len__()")"
check "新增子任务后已提交进度保留" "5" "$(echo "$OB2" | j "['data']['submittedSubtasks']")"
check "大任务列表只带 hasContent 不带正文" "True" "$(python3 -c "
import json,sys
d=json.loads(sys.stdin.read())
print('contentHtml' not in d['data']['subtasks'][0])
" <<< "$OB2")"
ONB_SUBS=$(curl -s "$BASE/api/v1/admin/tasks/$OBTASK/assignments/$OBASSIGN/subtasks" -H "Authorization: Bearer $TEACHER")
check_contains "管理端能逐条看到某人子任务的提交内容" "冒烟提交" "$ONB_SUBS"
SUB_DETAIL=$(curl -s "$BASE/api/v1/recruitment/me/onboarding-task/subtasks/$SUB0" -H "Authorization: Bearer $VISITOR")
check_contains "报名者子任务页能读到富文本说明" "安装 Git 与 JDK 21" "$SUB_DETAIL"
check "子任务页返回本人进度上下文" "5" "$(echo "$SUB_DETAIL" | j "['data']['submittedSubtasks']")"
NEWSUB=$(echo "$OB2" | j "['data']['subtasks'][5]['id']")
st=$(status_of -X POST "$BASE/api/v1/recruitment/me/onboarding-task/subtasks/$NEWSUB/submission" -H "Authorization: Bearer $VISITOR" \
  -H 'Content-Type: application/json' -d '{"contentHtml":"<p>冒烟提交：新增项也已完成。</p>"}')
check "提交新增子任务的内容" "200" "$st"
st=$(status_of -X POST "$BASE/api/v1/recruitment/me/onboarding-task/submission" -H "Authorization: Bearer $VISITOR" \
  -H 'Content-Type: application/json' -d '{"completionNote":"新增项也已完成"}')
check "重新提交完成说明" "200" "$st"

echo "== 4. 管理员审核通过 → 直接转正 =="
OV=$(curl -s "$BASE/api/v1/admin/tasks/onboarding-overview" -H "Authorization: Bearer $TEACHER")
check_contains "总览包含该报名者" "冒烟测试同学" "$OV"

REV=$(curl -s -X PUT "$BASE/api/v1/admin/tasks/$OBTASK/assignments/$OBASSIGN/review" -H "Authorization: Bearer $TEACHER" \
  -H 'Content-Type: application/json' -d "{\"decision\":\"APPROVED\",\"comment\":\"全部完成，同意转正\",\"memberCode\":\"$MCODE\",\"skillTags\":[\"机器人控制\",\"Python\"]}")
check "审核通过" "APPROVED" "$(echo "$REV" | j "['data']['status']")"
check_nonempty "回写生成的成员档案 ID" "$(echo "$REV" | j "['data']['convertedProfileId']")"

st=$(status_of -X PUT "$BASE/api/v1/admin/tasks/$OBTASK/assignments/$OBASSIGN/review" -H "Authorization: Bearer $TEACHER" \
  -H 'Content-Type: application/json' -d "{\"decision\":\"APPROVED\",\"memberCode\":\"$MCODE\",\"skillTags\":[\"x\"]}")
check "重复审核被拒绝" "409" "$st"

NEWTOKEN=$(curl -s -X POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$EMAIL\",\"password\":\"$PW\"}" | j "['data']['accessToken']")
PROFILE=$(curl -s "$BASE/api/v1/member/profile" -H "Authorization: Bearer $NEWTOKEN")
check "转正后成员编号" "$MCODE" "$(echo "$PROFILE" | j "['data']['memberCode']")"
check "转正后账号角色" "MEMBER" "$(echo "$PROFILE" | j "['data']['role']")"

echo "== 5. 普通任务：发布 → 提交 → 审核计分 =="
MEMBER=$(curl -s -X POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"member","password":"OpenLIMS-Member-2026!"}' | j "['data']['accessToken']")
BEFORE=$(curl -s "$BASE/api/v1/member/points" -H "Authorization: Bearer $MEMBER" | j "['data']['totalPoints']")
echo "  （member 当前总积分：$BEFORE 分）"

CREATED=$(curl -s -X POST "$BASE/api/v1/admin/tasks" -H "Authorization: Bearer $TEACHER" -H 'Content-Type: application/json' \
  -d '{"title":"冒烟：整理仿真数据","contentHtml":"<p>请在截止日期前完成。</p>","startDate":"2026-09-01","endDate":"2026-12-31","points":17,"subtasks":[{"title":"导出数据","contentHtml":"<p>导出原始数据。</p>"},{"title":"整理表格"}],"rules":[{"dimension":"ROLE","value":"MEMBER"},{"dimension":"MEMBER_STATUS","value":"OFFICIAL"}],"memberProfileIds":[]}')
TASKID=$(echo "$CREATED" | j "['data']['id']")
check "创建任务草稿" "DRAFT" "$(echo "$CREATED" | j "['data']['status']")"

st=$(status_of -X POST "$BASE/api/v1/admin/tasks/$TASKID/publish" -H "Authorization: Bearer $TEACHER")
check "发布任务" "200" "$st"

st=$(status_of -X PUT "$BASE/api/v1/admin/tasks/$TASKID" -H "Authorization: Bearer $TEACHER" -H 'Content-Type: application/json' \
  -d '{"title":"冒烟：整理仿真数据","contentHtml":"<p>改</p>","points":999,"subtasks":[{"title":"导出数据","contentHtml":"<p>导出原始数据。</p>"}],"rules":[],"memberProfileIds":[]}')
check "已发布任务仍可改内容" "200" "$st"
AFTERPTS=$(curl -s "$BASE/api/v1/admin/tasks/$TASKID" -H "Authorization: Bearer $TEACHER" | j "['data']['points']")
check "发布后积分锁定为 17" "17" "$AFTERPTS"

PROG=$(curl -s "$BASE/api/v1/admin/tasks/$TASKID/progress" -H "Authorization: Bearer $TEACHER")
ASSIGN=$(echo "$PROG" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']['assignments']
m=[a for a in d if a['memberCode']=='S-001']
print(m[0]['assignmentId'] if m else '')")
check_nonempty "任务对象包含演示成员 S-001" "$ASSIGN"

MYTASKS=$(curl -s "$BASE/api/v1/tasks" -H "Authorization: Bearer $MEMBER")
check_contains "成员端能看到该任务" "冒烟：整理仿真数据" "$MYTASKS"

SUBID=$(curl -s "$BASE/api/v1/tasks/$ASSIGN" -H "Authorization: Bearer $MEMBER" | j "['data']['subtasks'][0]['id']")
MYSUB=$(curl -s "$BASE/api/v1/tasks/$ASSIGN/subtasks/$SUBID" -H "Authorization: Bearer $MEMBER")
check_contains "成员子任务页能读到富文本说明" "导出原始数据" "$MYSUB"
check "成员子任务页可编辑" "True" "$(echo "$MYSUB" | j "['data']['editable']")"
st=$(status_of -X POST "$BASE/api/v1/tasks/$ASSIGN/subtasks/$SUBID/submission" -H "Authorization: Bearer $MEMBER" \
  -H 'Content-Type: application/json' -d '{"contentHtml":"<p>冒烟提交：成员在子任务页提交的内容。</p>"}')
check "在子任务页提交完成内容" "200" "$st"
MYSUB_AFTER=$(curl -s "$BASE/api/v1/tasks/$ASSIGN/subtasks/$SUBID" -H "Authorization: Bearer $MEMBER")
check_contains "成员能读回自己提交的内容" "成员在子任务页提交的内容" "$MYSUB_AFTER"
ADMIN_SUBS=$(curl -s "$BASE/api/v1/admin/tasks/$TASKID/assignments/$ASSIGN/subtasks" -H "Authorization: Bearer $TEACHER")
check_contains "管理端能逐条看到成员提交的内容" "成员在子任务页提交的内容" "$ADMIN_SUBS"

st=$(status_of -X POST "$BASE/api/v1/tasks/$ASSIGN/submission" -H "Authorization: Bearer $MEMBER" \
  -H 'Content-Type: application/json' -d '{"completionNote":"已完成整理"}')
check "成员提交完成说明" "200" "$st"

st=$(status_of -X PUT "$BASE/api/v1/admin/tasks/$TASKID/assignments/$ASSIGN/review" -H "Authorization: Bearer $TEACHER" \
  -H 'Content-Type: application/json' -d '{"decision":"REJECTED"}')
check "驳回未填意见被拒绝" "400" "$st"

REV2=$(curl -s -X PUT "$BASE/api/v1/admin/tasks/$TASKID/assignments/$ASSIGN/review" -H "Authorization: Bearer $TEACHER" \
  -H 'Content-Type: application/json' -d '{"decision":"APPROVED","comment":"验收通过"}')
check "审核通过" "APPROVED" "$(echo "$REV2" | j "['data']['status']")"
check "审核通过只记录结论、不写入积分" "None" "$(echo "$REV2" | j "['data']['awardedPoints']")"

AFTER=$(curl -s "$BASE/api/v1/member/points" -H "Authorization: Bearer $MEMBER" | j "['data']['totalPoints']")
check "审核通过后成员总积分不变（积分等到期结算）" "$BEFORE" "$AFTER"

echo "== 6. 结束任务：同步结算，之后成员与管理员都不能再改 =="
st=$(status_of -X POST "$BASE/api/v1/admin/tasks/$TASKID/close" -H "Authorization: Bearer $TEACHER")
check "结束任务" "200" "$st"

AFTER2=$(curl -s "$BASE/api/v1/member/points" -H "Authorization: Bearer $MEMBER" | j "['data']['totalPoints']")
check "结束即结算，成员总积分增加 17" "$((BEFORE+17))" "$AFTER2"

LEDGER=$(curl -s "$BASE/api/v1/admin/points/grants" -H "Authorization: Bearer $TEACHER")
check_contains "积分流水来源为任务编号" "TASK:$TASKID" "$LEDGER"

st=$(status_of -X PUT "$BASE/api/v1/admin/tasks/$TASKID/assignments/$ASSIGN/review" -H "Authorization: Bearer $TEACHER" \
  -H 'Content-Type: application/json' -d '{"decision":"APPROVED"}')
check "结束（到期）后管理员也不能再审核" "409" "$st"

st=$(status_of -X PUT "$BASE/api/v1/admin/tasks/$TASKID/assignments/$ASSIGN/review" -H "Authorization: Bearer $TEACHER" \
  -H 'Content-Type: application/json' -d '{"decision":"REJECTED","comment":"不达标"}')
check "结束（到期）后管理员也不能再驳回" "409" "$st"

echo "== 7. 权限与状态精简 =="
st=$(status_of "$BASE/api/v1/admin/tasks" -H "Authorization: Bearer $MEMBER")
check "普通成员访问管理端被拒绝" "403" "$st"

MID=$(curl -s "$BASE/api/v1/admin/members" -H "Authorization: Bearer $TEACHER" | python3 -c "
import sys,json
print([m['id'] for m in json.load(sys.stdin)['data'] if m['memberCode']=='S-001'][0])")
st=$(status_of -X PUT "$BASE/api/v1/admin/members/$MID" -H "Authorization: Bearer $TEACHER" -H 'Content-Type: application/json' \
  -d '{"name":"示例成员","memberCode":"S-001","role":"MEMBER","major":"人工智能","className":"人工智能 2401","grade":"2024","internalContact":"m@x.com","status":"PAUSED","skillTags":["具身智能"]}')
check "停用状态 PAUSED 被拒绝" "400" "$st"

echo
echo "================ 结果 ================"
echo "通过：$PASS    失败：$FAIL"
[ "$FAIL" -eq 0 ] && echo "全部通过 ✅" || echo "存在失败项 ❌"
exit $FAIL

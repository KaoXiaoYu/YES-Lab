#!/usr/bin/env bash
# OpenLIMS 悬赏任务端到端冒烟测试（真实 HTTP，独立内存库）
#
# 用法：先以隔离内存库启动后端（见 DEVLOG 的启动命令），再执行本脚本。
# 覆盖：名额上限与先到先得、完成名次、先完成先得奖金、驳回后奖金顺延、
#       提交即完成、到期结算（只发已完成的、来源编号为 BOUNTY:）、到期后冻结、与普通任务隔离。
set +u
BASE=http://127.0.0.1:8080
PASS=0
FAIL=0
TEACHER=""; MEMBER=""; CORE=""; TASKID=""; ASSIGN_MEMBER=""; ASSIGN_CORE=""
BEFORE_MEMBER=0; BEFORE_CORE=0; AFTER=0; LEDGER=""; CLAIMS=""

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
check_contains() {
  case "$3" in *"$2"*) PASS=$((PASS+1)); echo "  ✓ $1";; *) FAIL=$((FAIL+1)); echo "  ✗ $1 —— 期望包含 [$2] 实际 [$3]";; esac
}
status_of() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
auth() { echo "Authorization: Bearer $1"; }

echo "== 1. 登录 =="
TEACHER=$(curl -s -X POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"teacher","password":"OpenLIMS-Teacher-2026!"}' | j "['data']['accessToken']")
MEMBER=$(curl -s -X POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"member","password":"OpenLIMS-Member-2026!"}' | j "['data']['accessToken']")
CORE=$(curl -s -X POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"core","password":"OpenLIMS-Core-2026!"}' | j "['data']['accessToken']")
check "教师登录" "True" "$([ -n "$TEACHER" ] && echo True)"
check "普通成员登录" "True" "$([ -n "$MEMBER" ] && echo True)"
check "核心学生登录" "True" "$([ -n "$CORE" ] && echo True)"

echo "== 2. 创建与发布悬赏（名额 2、奖金 1 份、每人 17 积分）=="
EXPIRE=$(python3 -c "import datetime; print(datetime.date.today() + datetime.timedelta(days=10))")
CREATED=$(curl -s -X POST "$BASE/api/v1/admin/bounties" -H "$(auth "$TEACHER")" -H 'Content-Type: application/json' \
  -d "{\"title\":\"冒烟：悬赏数据集标注\",\"contentHtml\":\"<p>最先完成者获得奖金。</p>\",\"prizeDescription\":\"机械键盘一把\",
       \"prizeSlots\":1,\"points\":17,\"headcountLimit\":2,\"startDate\":\"2026-09-01\",\"endDate\":\"$EXPIRE\",
       \"subtasks\":[{\"title\":\"导出数据\"}],\"rules\":[{\"dimension\":\"ROLE\",\"value\":\"MEMBER\"},{\"dimension\":\"ROLE\",\"value\":\"CORE_STUDENT\"}]}")
TASKID=$(echo "$CREATED" | j "['data']['id']")
check "创建悬赏草稿" "True" "$([ -n "$TASKID" ] && echo True)"
check "草稿状态" "DRAFT" "$(echo "$CREATED" | j "['data']['status']")"
check "奖励配置写入（每人 17 积分）" "17" "$(echo "$CREATED" | j "['data']['points']")"

st=$(status_of -X POST "$BASE/api/v1/admin/bounties/$TASKID/publish" -H "$(auth "$TEACHER")")
check "发布悬赏" "200" "$st"

SUMMARY=$(curl -s "$BASE/api/v1/admin/bounties" -H "$(auth "$TEACHER")" | python3 -c "
import sys,json
rows=json.load(sys.stdin)['data']
row=[r for r in rows if r['id']=='$TASKID']
print(json.dumps(row[0]) if row else '')")
check "奖金份数 1 份" "1" "$(echo "$SUMMARY" | j "['prizeSlots']")"
check "接取上限 2 人" "2" "$(echo "$SUMMARY" | j "['headcountLimit']")"

# 校验：奖金份数不能多于接取上限
BAD=$(status_of -X POST "$BASE/api/v1/admin/bounties" -H "$(auth "$TEACHER")" -H 'Content-Type: application/json' \
  -d "{\"title\":\"份数超过人数\",\"contentHtml\":\"<p>x</p>\",\"prizeDescription\":\"奖品\",\"prizeSlots\":3,\"points\":0,
       \"headcountLimit\":2,\"startDate\":null,\"endDate\":null,\"subtasks\":[],\"rules\":[]}")
check "奖金份数多于接取上限被拒" "400" "$BAD"

echo "== 3. 接取：先到先得、满员即止 =="
st=$(status_of -X POST "$BASE/api/v1/bounties/$TASKID/claim" -H "$(auth "$MEMBER")")
check "成员接取成功" "200" "$st"
st=$(status_of -X POST "$BASE/api/v1/bounties/$TASKID/claim" -H "$(auth "$CORE")")
check "核心学生接取成功" "200" "$st"
st=$(status_of -X POST "$BASE/api/v1/bounties/$TASKID/claim" -H "$(auth "$TEACHER")")
check "第三人接取被拒（名额已满）" "409" "$st"
st=$(status_of -X POST "$BASE/api/v1/bounties/$TASKID/claim" -H "$(auth "$MEMBER")")
check "同一人不能接取两次" "409" "$st"

CLAIMS=$(curl -s "$BASE/api/v1/admin/bounties/$TASKID/claims" -H "$(auth "$TEACHER")")
check_contains "名单里记录了两人" "S-001" "$CLAIMS"
check_contains "名单里记录了核心学生" "S-CORE-001" "$CLAIMS"
check "占用 2 个名额" "2" "$(echo "$CLAIMS" | j "['data']['occupied']")"
ASSIGN_MEMBER=$(echo "$CLAIMS" | python3 -c "
import sys,json
rows=json.load(sys.stdin)['data']['rows']
print([r['assignmentId'] for r in rows if r['memberCode']=='S-001'][0])")
ASSIGN_CORE=$(echo "$CLAIMS" | python3 -c "
import sys,json
rows=json.load(sys.stdin)['data']['rows']
print([r['assignmentId'] for r in rows if r['memberCode']=='S-CORE-001'][0])")

echo "== 4. 提交即完成与先完成先得 =="
BEFORE_MEMBER=$(curl -s "$BASE/api/v1/member/points" -H "$(auth "$MEMBER")" | j "['data']['totalPoints']")
BEFORE_CORE=$(curl -s "$BASE/api/v1/member/points" -H "$(auth "$CORE")" | j "['data']['totalPoints']")

ST=$(status_of -X POST "$BASE/api/v1/tasks/$ASSIGN_MEMBER/submission" -H "$(auth "$MEMBER")" \
  -H 'Content-Type: application/json' -d '{"completionNote":"我先完成"}')
check "成员提交完成说明" "200" "$ST"
MYTASK=$(curl -s "$BASE/api/v1/tasks/$ASSIGN_MEMBER" -H "$(auth "$MEMBER")")
check "提交即完成：状态直接为已通过" "APPROVED" "$(echo "$MYTASK" | j "['data']['status']")"
check "完成名次为第 1 名" "1" "$(echo "$MYTASK" | j "['data']['completionRank']")"
check "第 1 名持有奖金" "True" "$(echo "$MYTASK" | j "['data']['prizeAwarded']")"
check "提交时未发积分（等到期结算）" "$BEFORE_MEMBER" \
  "$(curl -s "$BASE/api/v1/member/points" -H "$(auth "$MEMBER")" | j "['data']['totalPoints']")"

st=$(status_of -X POST "$BASE/api/v1/tasks/$ASSIGN_CORE/submission" -H "$(auth "$CORE")" \
  -H 'Content-Type: application/json' -d '{"completionNote":"我稍后完成"}')
check "核心学生提交" "200" "$st"
CORE_TASK=$(curl -s "$BASE/api/v1/tasks/$ASSIGN_CORE" -H "$(auth "$CORE")")
check "第 2 名完成名次" "2" "$(echo "$CORE_TASK" | j "['data']['completionRank']")"
check "第 2 名未持有奖金" "False" "$(echo "$CORE_TASK" | j "['data']['prizeAwarded']")"

echo "== 5. 驳回第 1 名 → 奖金顺延 =="
st=$(status_of -X POST "$BASE/api/v1/admin/bounties/$TASKID/claims/$ASSIGN_MEMBER/revoke" -H "$(auth "$TEACHER")" \
  -H 'Content-Type: application/json' -d '{"comment":"成果不达标"}')
check "驳回第 1 名" "200" "$st"
CORE_TASK=$(curl -s "$BASE/api/v1/tasks/$ASSIGN_CORE" -H "$(auth "$CORE")")
check "奖金顺延给第 2 名" "True" "$(echo "$CORE_TASK" | j "['data']['prizeAwarded']")"
MYTASK=$(curl -s "$BASE/api/v1/tasks/$ASSIGN_MEMBER" -H "$(auth "$MEMBER")")
check "被驳回者归还奖金" "False" "$(echo "$MYTASK" | j "['data']['prizeAwarded']")"
check "被驳回者名次保留" "1" "$(echo "$MYTASK" | j "['data']['completionRank']")"

st=$(status_of -X POST "$BASE/api/v1/admin/bounties/$TASKID/claims/$ASSIGN_MEMBER/revoke" -H "$(auth "$TEACHER")" \
  -H 'Content-Type: application/json' -d '{"comment":"  "}')
check "驳回必须填意见" "400" "$st"
st=$(status_of -X POST "$BASE/api/v1/bounties/$TASKID/claim" -H "$(auth "$MEMBER")")
check "被驳回者不能重新接取" "409" "$st"

echo "== 6. 结束悬赏即结算（只发已完成的）=="
st=$(status_of -X POST "$BASE/api/v1/admin/bounties/$TASKID/close" -H "$(auth "$TEACHER")")
check "结束悬赏" "200" "$st"
AFTER=$(curl -s "$BASE/api/v1/member/points" -H "$(auth "$CORE")" | j "['data']['totalPoints']")
check "核心学生拿到 17 积分" "$((BEFORE_CORE+17))" "$AFTER"
check "被驳回者不发积分" "$BEFORE_MEMBER" \
  "$(curl -s "$BASE/api/v1/member/points" -H "$(auth "$MEMBER")" | j "['data']['totalPoints']")"
LEDGER=$(curl -s "$BASE/api/v1/admin/points/grants" -H "$(auth "$TEACHER")")
check_contains "积分流水来源为悬赏编号" "BOUNTY:$TASKID" "$LEDGER"

echo "== 7. 到期后冻结 =="
st=$(status_of -X POST "$BASE/api/v1/bounties/$TASKID/claim" -H "$(auth "$TEACHER")")
check "已结束的悬赏不能再接取" "409" "$st"
st=$(status_of -X POST "$BASE/api/v1/admin/bounties/$TASKID/claims/$ASSIGN_CORE/revoke" -H "$(auth "$TEACHER")" \
  -H 'Content-Type: application/json' -d '{"comment":"太晚了"}')
check "已结束的悬赏不能再驳回" "409" "$st"
st=$(status_of -X POST "$BASE/api/v1/tasks/$ASSIGN_MEMBER/submission" -H "$(auth "$MEMBER")" \
  -H 'Content-Type: application/json' -d '{"completionNote":"再交一次"}')
check "已结束的悬赏不能再提交" "409" "$st"

echo "== 8. 与普通任务流程隔离 =="
TASKS=$(curl -s "$BASE/api/v1/admin/tasks" -H "$(auth "$TEACHER")")
case "$TASKS" in *"$TASKID"*) FAIL=$((FAIL+1)); echo "  ✗ 悬赏不应出现在普通任务列表";; *) PASS=$((PASS+1)); echo "  ✓ 悬赏不出现在普通任务列表";; esac
st=$(status_of -X POST "$BASE/api/v1/admin/tasks/$TASKID/close" -H "$(auth "$TEACHER")")
check "悬赏不能用普通任务接口操作" "404" "$st"
st=$(status_of "$BASE/api/v1/admin/bounties" -H "$(auth "$MEMBER")")
check "普通成员访问悬赏管理端被拒" "403" "$st"

echo
echo "================ 结果 ================"
echo "通过：$PASS    失败：$FAIL"
[ "$FAIL" -eq 0 ] && echo "全部通过 ✅" || echo "存在失败 ❌"
exit $([ "$FAIL" -eq 0 ] && echo 0 || echo 1)

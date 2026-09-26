<script setup>
import { computed, onMounted, ref } from 'vue'
import { ArrowLeft, Gift, UserMinus, X } from '@lucide/vue'
import { useRoute } from 'vue-router'
import PortalShell from '../components/PortalShell.vue'
import { getBountyClaims, issueBountyPrize, removeBountyClaim, revokeBountyClaim } from '../services/authApi'

const route = useRoute()
const claims = ref(null)
const loading = ref(true)
const working = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const activeAssignmentId = ref('')
const revokeComment = ref('')

const statusLabels = {
  PENDING: '进行中',
  APPROVED: '已完成',
  REJECTED: '已驳回',
  ABANDONED: '已放弃',
}
const roleLabels = { TEACHER: '教师', CORE_STUDENT: '核心学生', MEMBER: '普通成员' }
const memberStatusLabels = { TRIAL: '试用', OFFICIAL: '正式' }
const fulfillmentLabels = {
  PENDING: '待发放',
  ISSUED: '已发放，待成员确认',
  RECEIVED: '已领取',
  REVOKED: '奖金资格已撤销',
}

const task = computed(() => claims.value?.task || null)

async function load() {
  loading.value = true
  try {
    claims.value = await getBountyClaims(route.params.taskId)
    return true
  } catch (error) {
    errorMessage.value = error.message
    return false
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openRevoke(assignmentId) {
  activeAssignmentId.value = assignmentId
  revokeComment.value = ''
}

/** 驳回即触发奖金顺延：确认文案里写清这一点，避免管理员以为只是改个状态。 */
async function submitRevoke(row) {
  if (!revokeComment.value.trim()) return
  if (!window.confirm(`确认驳回 ${row.name} 的完成结果？奖金会自动顺延给下一位完成者。`)) return
  working.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await revokeBountyClaim(task.value.id, row.assignmentId, { comment: revokeComment.value.trim() })
    activeAssignmentId.value = ''
    if (await load()) {
      successMessage.value = `已驳回 ${row.name} 的完成结果，奖金已按名次顺延。当前接取名额：${headcountSummary()}；${prizeSummary()}`
    } else {
      successMessage.value = `已驳回 ${row.name} 的完成结果并触发奖金顺延；最新名额与份数刷新失败，请手动刷新。`
    }
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    working.value = false
  }
}

async function removeClaim(row) {
  if (!window.confirm(`确认移除 ${row.name} 的接取？名额会归还，且该成员不能再接取这条悬赏。`)) return
  working.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await removeBountyClaim(task.value.id, row.assignmentId)
    if (await load()) {
      successMessage.value = `已移除 ${row.name} 的接取。当前接取名额：${headcountSummary()}；${prizeSummary()}`
    } else {
      successMessage.value = `已移除 ${row.name} 的接取；最新名额与份数刷新失败，请手动刷新。`
    }
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    working.value = false
  }
}

async function issuePrize(row) {
  if (
    !window.confirm(
      `请确认已在线下向 ${row.name} 实际发放「${task.value.prizeDescription}」。登记后该完成结果不能再驳回，且此记录不能撤销。`,
    )
  )
    return
  working.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await issueBountyPrize(task.value.id, row.assignmentId)
    if (await load()) successMessage.value = `已登记向 ${row.name} 发放奖金；等待成员本人确认领取。`
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    working.value = false
  }
}

function headcountSummary() {
  if (!claims.value) return '接取人数暂不可用'
  return claims.value.headcountLimit == null
    ? `已接 ${claims.value.claimed} 人，不限人数`
    : `已接 ${claims.value.occupied} / ${claims.value.headcountLimit} 人`
}

function prizeSummary() {
  if (!claims.value?.prizeSlots) return '未设置奖金份数'
  return `奖金已产生 ${claims.value.prizeIssued} / ${claims.value.prizeSlots} 份`
}
</script>

<template>
  <PortalShell
    eyebrow="ADMIN / BOUNTY CLAIMS"
    title="悬赏接取名单"
    description="接取名额、奖金发放/领取进度与逐人明细；登记线下实际发放后，由获奖成员本人确认领取。"
  >
    <RouterLink class="task-back" to="/admin/bounties"
      ><ArrowLeft :size="16" aria-hidden="true" />返回悬赏管理</RouterLink
    >

    <div v-if="loading" class="portal-state">正在读取接取名单…</div>
    <div v-else-if="errorMessage" class="portal-state error" role="alert">{{ errorMessage }}</div>
    <p v-if="successMessage" class="portal-state success" role="status" aria-atomic="true">
      {{ successMessage }}
    </p>

    <template v-if="claims && task && !loading && !errorMessage">
      <section class="task-summary" aria-labelledby="bounty-claims-title">
        <header>
          <div>
            <p>{{ task.status === 'CLOSED' ? '已结束' : task.status === 'DRAFT' ? '草稿' : '进行中' }}</p>
            <h2 id="bounty-claims-title">{{ task.title }}</h2>
            <span>{{ task.startDate || '未设置开始' }} — {{ task.endDate || '未设置截止' }}</span>
          </div>
          <b>{{ task.points > 0 ? `每个完成对象 ${task.points} 分` : '不计积分' }}</b>
        </header>
        <dl>
          <div>
            <dt>接取名额</dt>
            <dd>
              {{
                claims.headcountLimit == null
                  ? `已接 ${claims.claimed} 人 · 不限`
                  : `${claims.occupied} / ${claims.headcountLimit} 占用`
              }}
            </dd>
          </div>
          <div>
            <dt>已完成</dt>
            <dd>{{ claims.approved }}</dd>
          </div>
          <div v-if="claims.prizeSlots">
            <dt>奖金份数</dt>
            <dd>{{ claims.prizeIssued }} / {{ claims.prizeSlots }} 份已产生</dd>
          </div>
          <div>
            <dt>已放弃</dt>
            <dd>{{ claims.abandoned }}</dd>
          </div>
          <div>
            <dt>已驳回</dt>
            <dd>{{ claims.rejected }}</dd>
          </div>
          <div>
            <dt>积分结算</dt>
            <dd>{{ task.pointsSettledAt ? `已结算 ${task.pointsSettledAt.slice(0, 10)}` : '待结算' }}</dd>
          </div>
        </dl>
        <p v-if="task.expired" class="task-skip" role="status">
          悬赏已截止：不能再接取或驳回；线下奖金发放与成员领取确认仍可补录。
        </p>
        <p v-else-if="claims.occupied > 0" class="task-skip" role="status">
          驳回会立即把奖金顺延给名次最靠前的未获奖完成者；积分不随驳回回收。
        </p>
      </section>

      <section class="task-rows" aria-labelledby="bounty-rows-title">
        <h3 id="bounty-rows-title">逐人明细</h3>
        <p v-if="!claims.rows.length" class="empty-note">还没有成员接取这条悬赏。</p>
        <article v-for="row in claims.rows" :key="row.assignmentId" class="task-row">
          <header>
            <div>
              <strong>{{ row.name }}</strong>
              <span
                >{{ row.memberCode }} · {{ roleLabels[row.role] }} ·
                {{ memberStatusLabels[row.memberStatus] || row.memberStatus }} · {{ row.grade || '年级未填' }}</span
              >
            </div>
            <b :data-status="row.status">{{ statusLabels[row.status] }}</b>
          </header>
          <p>
            <template v-if="row.completionRank">完成名次第 {{ row.completionRank }} 名 · </template>
            <template v-if="row.prizeAwarded">持有奖金</template>
            <template v-else-if="claims.prizeSlots">未持有奖金</template>
            <span v-if="row.overdue" class="overdue"> · 已截止</span>
            <span v-if="row.awardedPoints != null"> · 已计 {{ row.awardedPoints }} 分</span>
            <span v-else-if="row.pointsSkippedReason" class="task-skip"> · 未计分：{{ row.pointsSkippedReason }}</span>
          </p>
          <p v-if="row.completionNote" class="task-row-note">完成说明：{{ row.completionNote }}</p>
          <p v-if="row.reviewComment" class="task-row-note">
            驳回意见：{{ row.reviewComment }}（{{ row.reviewedBy }}，{{ row.reviewedAt?.slice(0, 10) }}）
          </p>
          <p v-if="row.prizeFulfillmentStatus" class="task-row-note" role="status" aria-atomic="true">
            奖金履约：{{ fulfillmentLabels[row.prizeFulfillmentStatus] || '记录待核查' }}
            <template v-if="row.prizeIssuedAt">
              · {{ row.prizeIssuedAt.slice(0, 16).replace('T', ' ') }} 由 {{ row.prizeIssuedBy }} 登记发放</template
            >
            <template v-if="row.prizeReceivedAt">
              · {{ row.prizeReceivedAt.slice(0, 16).replace('T', ' ') }} 由 {{ row.prizeReceivedBy }} 确认领取</template
            >
            <template v-if="row.prizeRevokedAt">
              · {{ row.prizeRevokedAt.slice(0, 16).replace('T', ' ') }} 由 {{ row.prizeRevokedBy }} 撤销资格
              <template v-if="row.prizeRevokedReason">：{{ row.prizeRevokedReason }}</template>
            </template>
          </p>

          <div class="task-card-actions">
            <button
              v-if="row.status === 'APPROVED'"
              type="button"
              :disabled="working || row.overdue || ['ISSUED', 'RECEIVED'].includes(row.prizeFulfillmentStatus)"
              :title="
                row.overdue
                  ? '悬赏已截止，不能再驳回'
                  : ['ISSUED', 'RECEIVED'].includes(row.prizeFulfillmentStatus)
                    ? '奖金已发放或领取，不能再驳回'
                    : ''
              "
              @click="openRevoke(row.assignmentId)"
            >
              <Gift :size="15" aria-hidden="true" />驳回（顺延奖金）
            </button>
            <button
              v-if="row.status === 'APPROVED' && row.prizeAwarded && row.prizeFulfillmentStatus === 'PENDING'"
              type="button"
              :disabled="working"
              @click="issuePrize(row)"
            >
              <Gift :size="15" aria-hidden="true" />登记已线下发放
            </button>
            <button
              v-if="row.status === 'PENDING'"
              type="button"
              class="danger"
              :disabled="working"
              @click="removeClaim(row)"
            >
              <UserMinus :size="15" aria-hidden="true" />移除接取
            </button>
          </div>

          <form
            v-if="activeAssignmentId === row.assignmentId"
            class="task-review-form"
            @submit.prevent="submitRevoke(row)"
          >
            <p class="task-skip full">驳回必填意见；积分不回收，奖金会立即顺延给下一位完成者。</p>
            <label class="full">驳回意见<input v-model.trim="revokeComment" maxlength="1000" required /></label>
            <div class="task-form-actions">
              <button class="portal-primary" type="submit" :disabled="working || !revokeComment.trim()">
                {{ working ? '提交中…' : '确认驳回' }}
              </button>
              <button type="button" class="portal-secondary" @click="activeAssignmentId = ''">
                <X :size="15" aria-hidden="true" />取消
              </button>
            </div>
          </form>
        </article>
      </section>
    </template>
  </PortalShell>
</template>

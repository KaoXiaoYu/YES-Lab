<script setup>
import { ArrowLeft, CheckCircle2, ChevronRight, CircleDashed, Clock3, Gift, TriangleAlert } from '@lucide/vue'
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import PortalShell from '../components/PortalShell.vue'
import { confirmBountyPrizeReceived, getMyTask, submitMyTask } from '../services/authApi'

const route = useRoute()
const task = ref(null)
const loading = ref(true)
const working = ref(false)
const errorMessage = ref('')
const actionError = ref('')
const note = ref('')

const statusLabels = {
  PENDING: '待完成',
  SUBMITTED: '等待管理员确认',
  APPROVED: '已通过',
  REJECTED: '已驳回，请修改后重新提交',
}
const taskStatusLabels = { PUBLISHED: '进行中', CLOSED: '已结束' }
const bountyStatusLabels = { PENDING: '进行中', APPROVED: '已完成', REJECTED: '已驳回', ABANDONED: '已放弃' }
const fulfillmentLabels = {
  PENDING: '待管理员线下发放',
  ISSUED: '已发放，待你确认领取',
  RECEIVED: '已确认领取',
  REVOKED: '奖金资格已撤销',
}
const isBounty = computed(() => task.value?.taskType === 'BOUNTY')
const statusLabel = computed(() => {
  if (!task.value) return ''
  return isBounty.value
    ? bountyStatusLabels[task.value.status] || task.value.status
    : statusLabels[task.value.status] || task.value.status
})
// 可写性由后端裁定（到期即冻结是业务规则，前端不重复实现），避免界面能点、接口却返回 409。
const readOnly = computed(() => !task.value || !task.value.editable)
const readOnlyReason = computed(() => {
  if (!task.value) return ''
  if (task.value.status === 'APPROVED') {
    return task.value.points > 0 && !task.value.pointsSettled
      ? '任务已通过；积分将在任务到期后统一结算。'
      : '任务已通过，不能再修改。'
  }
  if (task.value.expired) {
    if (task.value.taskStatus === 'CLOSED') {
      return '任务已被管理员结束，不能再提交。'
    }
    if (task.value.status === 'SUBMITTED') {
      // 到期即结算、结算只认已通过的：这次提交未被及时审核，不会计分。必须讲清楚，否则成员会一直等。
      return task.value.points > 0
        ? '任务已截止，你的提交未能在截止前完成审核，本次不会获得积分；如确需补救，请联系管理员延长截止日期。'
        : '任务已截止，你的提交未能在截止前完成审核；如确需继续，请联系管理员延长截止日期。'
    }
    return '任务已截止，不能再提交；如确需继续，请联系管理员延长截止日期。'
  }
  return '任务当前不可提交，请联系管理员确认。'
})
const submittedCount = computed(() => task.value?.subtasks?.filter((item) => item.submitted).length ?? 0)
const totalCount = computed(() => task.value?.subtasks?.length ?? 0)
const progressPercent = computed(() =>
  totalCount.value ? Math.round((submittedCount.value / totalCount.value) * 100) : 0,
)

async function load() {
  try {
    task.value = await getMyTask(route.params.assignmentId)
    note.value = task.value.completionNote || ''
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function submit() {
  if (!note.value.trim()) return
  working.value = true
  actionError.value = ''
  try {
    task.value = await submitMyTask(task.value.assignmentId, note.value.trim())
  } catch (error) {
    actionError.value = error.message
  } finally {
    working.value = false
  }
}

async function confirmPrizeReceived() {
  if (!window.confirm('请确认你已实际收到这份线下奖金。确认后将记录你的账号和时间，不能撤销。')) return
  working.value = true
  actionError.value = ''
  try {
    task.value = await confirmBountyPrizeReceived(task.value.assignmentId)
  } catch (error) {
    actionError.value = error.message
  } finally {
    working.value = false
  }
}
</script>

<template>
  <PortalShell
    eyebrow="COLLABORATION / TASK"
    title="任务详情"
    :description="
      isBounty
        ? '查看悬赏完成名次、奖金履约与截止情况。'
        : '查看任务说明，逐个子任务提交完成内容后填写总完成说明，等待管理员人工确认。'
    "
  >
    <RouterLink class="task-back" to="/tasks"><ArrowLeft :size="16" aria-hidden="true" />返回我的任务</RouterLink>

    <div v-if="loading" class="portal-state">正在读取任务…</div>
    <div v-else-if="errorMessage" class="portal-state error" role="alert">{{ errorMessage }}</div>

    <template v-else-if="task">
      <section class="task-panel" aria-labelledby="task-detail-title">
        <header>
          <div>
            <p>{{ taskStatusLabels[task.taskStatus] || task.taskStatus }}</p>
            <h2 id="task-detail-title">{{ task.title }}</h2>
          </div>
        </header>
        <div class="task-panel-status" :data-status="task.status">
          <strong>{{ statusLabel }}</strong>
          <span :class="{ overdue: task.overdue || task.expired }">
            <TriangleAlert v-if="task.overdue || task.expired" :size="15" aria-hidden="true" />
            <Clock3 v-else :size="15" aria-hidden="true" />
            {{ task.startDate || '未设置开始' }} — {{ task.endDate || '未设置截止' }}
            <template v-if="task.expired">（已截止）</template>
            <template v-else-if="task.overdue">（已逾期）</template>
          </span>
          <div v-if="totalCount" class="task-progress">
            <div
              class="task-progress-track"
              role="progressbar"
              aria-valuemin="0"
              :aria-valuenow="submittedCount"
              :aria-valuemax="totalCount"
              aria-label="子任务完成进度"
            >
              <span :style="{ width: `${progressPercent}%` }"></span>
            </div>
            <span class="task-progress-label">已提交 {{ submittedCount }} / {{ totalCount }}</span>
          </div>
          <span v-if="task.points > 0">
            通过后 +{{ task.awardedPoints ?? task.points }} 积分
            <template v-if="task.pointsSkippedReason">（本次未计分：{{ task.pointsSkippedReason }}）</template>
          </span>
        </div>

        <p v-if="isBounty" class="bounty-prize">
          <template v-if="task.prizeSlots">奖金 {{ task.prizeSlots }} 份 · </template>
          <template v-if="task.prizeDescription">{{ task.prizeDescription }} · </template>
          <template v-if="task.completionRank">完成名次第 {{ task.completionRank }} 名 · </template>
          <template v-if="task.status === 'APPROVED' && task.prizeSlots">
            {{ task.prizeAwarded ? '已获得奖金' : '未获得奖金' }} ·
          </template>
          <small>系统记录线下发放与本人领取确认</small>
        </p>
        <section
          v-if="isBounty && (task.prizeAwarded || task.prizeFulfillmentStatus === 'REVOKED')"
          class="bounty-prize"
          aria-label="奖金发放状态"
        >
          <p role="status" aria-atomic="true">
            奖金状态：{{ fulfillmentLabels[task.prizeFulfillmentStatus] || '记录待核查' }}
            <template v-if="task.prizeIssuedAt">
              · 发放于 {{ task.prizeIssuedAt.slice(0, 16).replace('T', ' ') }}</template
            >
            <template v-if="task.prizeReceivedAt"> · 领取已登记</template>
          </p>
          <button
            v-if="task.prizeFulfillmentStatus === 'ISSUED'"
            type="button"
            class="portal-primary"
            :disabled="working"
            @click="confirmPrizeReceived"
          >
            <Gift :size="16" aria-hidden="true" />{{ working ? '提交中…' : '确认已领取奖金' }}
          </button>
        </section>

        <!-- eslint-disable-next-line vue/no-v-html -->
        <div class="task-panel-content" v-html="task.contentHtml"></div>

        <h3 v-if="totalCount" class="task-subtask-entries-title">
          子任务（已提交 {{ submittedCount }} / {{ totalCount }}）
        </h3>
        <ul v-if="totalCount" class="task-subtask-entries">
          <li v-for="subtask in task.subtasks" :key="subtask.id">
            <RouterLink :to="`/tasks/${task.assignmentId}/subtasks/${subtask.id}`">
              <CheckCircle2 v-if="subtask.submitted" :size="18" aria-hidden="true" />
              <CircleDashed v-else :size="18" aria-hidden="true" />
              <span class="task-subtask-entry-title">{{ subtask.title }}</span>
              <span class="task-subtask-entry-meta">
                {{ subtask.submitted ? '已提交' : '未提交' }}
                <template v-if="subtask.hasContent"> · 有说明</template>
              </span>
              <ChevronRight :size="16" aria-hidden="true" />
            </RouterLink>
          </li>
        </ul>
        <p v-else class="empty-note">本任务没有子任务，直接填写完成说明提交即可。</p>

        <p v-if="task.status === 'APPROVED'" class="task-panel-approved" role="status">
          <template v-if="isBounty"
            >你已完成这条悬赏<template v-if="task.prizeSlots"
              >，{{ task.prizeAwarded ? '获得奖金' : '未进入获奖名次' }}</template
            ><template v-if="task.pointsSettled && task.awardedPoints">，已结算 {{ task.awardedPoints }} 积分</template
            ><template v-else-if="task.points > 0">；积分将在悬赏到期后统一结算</template>。</template
          >
          <template v-else
            >管理员已确认通过<template v-if="task.pointsSettled && task.awardedPoints"
              >，已结算 {{ task.awardedPoints }} 积分</template
            ><template v-else-if="task.points > 0">；积分将在任务到期后统一结算</template>。</template
          >
        </p>
        <p v-else-if="task.status === 'REJECTED' && task.reviewComment" class="task-panel-reject" role="status">
          管理员意见：{{ task.reviewComment }}
        </p>

        <form v-if="!readOnly" class="task-panel-submit" @submit.prevent="submit">
          <label for="task-note">
            完成说明
            <textarea
              id="task-note"
              v-model="note"
              rows="4"
              maxlength="2000"
              placeholder="说明完成情况、产出或遇到的问题。"
              required
            ></textarea>
            <small v-if="isBounty">悬赏是「提交即完成」：提交后立即锁定完成名次，管理员只做事后复核。</small>
            <small v-else>提交后管理员会人工确认；驳回时可以修改说明并重新提交。积分在任务到期后统一结算。</small>
          </label>
          <button class="portal-primary" type="submit" :disabled="working || !note.trim()">
            {{
              working
                ? '提交中…'
                : isBounty
                  ? '提交并完成悬赏'
                  : task.status === 'SUBMITTED'
                    ? '更新完成说明'
                    : '提交完成说明'
            }}
          </button>
        </form>
        <p v-else class="empty-note" role="status">{{ readOnlyReason }}</p>

        <p v-if="actionError" class="portal-state error inline" role="alert">{{ actionError }}</p>
      </section>
    </template>
  </PortalShell>
</template>

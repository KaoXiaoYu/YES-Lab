<script setup>
import { ArrowLeft, CheckCircle2, ChevronRight, CircleDashed, Gift, TriangleAlert } from '@lucide/vue'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PortalShell from '../components/PortalShell.vue'
import { abandonBounty, claimBounty, getBountyDetail } from '../services/authApi'

const route = useRoute()
const router = useRouter()
const bounty = ref(null)
const loading = ref(true)
const working = ref(false)
const errorMessage = ref('')
const actionError = ref('')
const actionStatus = ref('')
const confirmingClaim = ref(false)

const myStatusLabels = {
  PENDING: '进行中',
  APPROVED: '已完成',
  REJECTED: '已驳回',
  ABANDONED: '已放弃',
}

const headcountText = computed(() => {
  const prize = bounty.value?.prize
  if (!prize) return ''
  return prize.headcountLimit == null
    ? `已接 ${prize.claimed} 人 · 不限人数`
    : `已接 ${prize.claimed} / ${prize.headcountLimit}`
})

async function load() {
  loading.value = true
  try {
    bounty.value = await getBountyDetail(route.params.taskId)
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function claim() {
  working.value = true
  actionError.value = ''
  try {
    const result = await claimBounty(bounty.value.taskId)
    confirmingClaim.value = false
    // 接取后直接进入任务详情页（复用「我的任务」的提交与子任务页面）。
    await router.push(`/tasks/${result.assignmentId}`)
  } catch (error) {
    actionError.value = error.message
  } finally {
    working.value = false
  }
}

async function abandon() {
  if (!window.confirm('确认放弃这条悬赏？放弃后名额会归还，但你不能再接取它。')) return
  working.value = true
  actionError.value = ''
  actionStatus.value = ''
  try {
    const result = await abandonBounty(bounty.value.taskId)
    const title = bounty.value.title
    await load()
    const prize = bounty.value.prize
    const prizeStatus = prize.prizeSlots
      ? `奖金已产生 ${prize.prizeIssued} / ${prize.prizeSlots} 份。`
      : '该悬赏未设置奖金份数。'
    const headcountStatus =
      bounty.value.prize.headcountLimit == null
        ? `已接 ${result.occupied} 人，不限人数`
        : `已接 ${result.occupied} / ${bounty.value.prize.headcountLimit} 人`
    actionStatus.value = `已放弃「${title}」。当前${headcountStatus}；${prizeStatus}`
  } catch (error) {
    actionError.value = error.message
  } finally {
    working.value = false
  }
}
</script>

<template>
  <PortalShell
    eyebrow="COLLABORATION / BOUNTY"
    title="悬赏详情"
    description="接取后进入我的任务提交完成说明；提交即完成，最先完成的 m 人获得奖金，积分在悬赏到期后统一结算。"
  >
    <RouterLink class="task-back" to="/bounties"><ArrowLeft :size="16" aria-hidden="true" />返回悬赏榜</RouterLink>

    <div v-if="loading" class="portal-state">正在读取悬赏…</div>
    <div v-else-if="errorMessage" class="portal-state error" role="alert">{{ errorMessage }}</div>
    <p v-if="actionStatus" class="portal-state success" role="status" aria-atomic="true">
      {{ actionStatus }}
    </p>

    <template v-if="bounty && !loading && !errorMessage">
      <section class="task-panel" aria-labelledby="bounty-title">
        <header>
          <div>
            <p>{{ bounty.windowOpen ? '可接取' : '不可接取' }}</p>
            <h2 id="bounty-title">{{ bounty.title }}</h2>
          </div>
        </header>

        <div class="task-panel-status">
          <span :class="{ overdue: !bounty.windowOpen }">
            <TriangleAlert v-if="!bounty.windowOpen" :size="15" aria-hidden="true" />
            {{ bounty.startDate || '未设置开始' }} — {{ bounty.endDate || '未设置截止' }}
            <template v-if="!bounty.windowOpen">（已截止）</template>
            <template v-else-if="bounty.endDate">（剩余 {{ bounty.daysRemaining }} 天）</template>
          </span>
          <span>{{ headcountText }}</span>
          <span v-if="bounty.prize.prizeSlots">
            奖金 {{ bounty.prize.prizeIssued }} / {{ bounty.prize.prizeSlots }} 份已产生
          </span>
          <span v-if="bounty.prize.points > 0">
            完成 +{{ bounty.prize.points }} 积分
            <template v-if="bounty.prize.pointsSettled">（已结算）</template>
            <template v-else>（到期后统一结算）</template>
          </span>
        </div>

        <p v-if="bounty.prize.prizeDescription" class="bounty-prize">
          <Gift :size="16" aria-hidden="true" />{{ bounty.prize.prizeDescription }}
          <small>（奖金在线下发放，系统记录逐人发放与领取状态）</small>
        </p>

        <!-- eslint-disable-next-line vue/no-v-html -->
        <div class="task-panel-content" v-html="bounty.contentHtml"></div>

        <h3 v-if="bounty.subtasks.length" class="task-subtask-entries-title">
          子任务（{{ bounty.subtasks.length }} 项）
        </h3>
        <ul v-if="bounty.subtasks.length" class="task-subtask-entries">
          <li v-for="subtask in bounty.subtasks" :key="subtask.id">
            <RouterLink v-if="bounty.myAssignmentId" :to="`/tasks/${bounty.myAssignmentId}/subtasks/${subtask.id}`">
              <CircleDashed :size="18" aria-hidden="true" />
              <span class="task-subtask-entry-title">{{ subtask.title }}</span>
              <span class="task-subtask-entry-meta"><template v-if="subtask.hasContent">有说明</template></span>
              <ChevronRight :size="16" aria-hidden="true" />
            </RouterLink>
            <span v-else class="bounty-subtask-static">
              <CircleDashed :size="18" aria-hidden="true" />
              <span class="task-subtask-entry-title">{{ subtask.title }}</span>
              <span class="task-subtask-entry-meta"><template v-if="subtask.hasContent">有说明</template></span>
            </span>
          </li>
        </ul>

        <p v-if="bounty.myAssignmentId" class="bounty-mine" role="status">
          我的状态：{{ myStatusLabels[bounty.myStatus] || bounty.myStatus }}
          <template v-if="bounty.myRank">· 完成名次第 {{ bounty.myRank }} 名</template>
          <template v-if="bounty.myStatus === 'APPROVED'">
            · {{ bounty.myPrizeAwarded ? '已获奖' : '未获奖' }}
          </template>
        </p>
        <p v-else-if="bounty.claimBlockedReason" class="task-skip" role="status">
          {{ bounty.claimBlockedReason }}
        </p>

        <div v-if="!bounty.myAssignmentId" class="task-card-actions">
          <template v-if="bounty.claimable">
            <button
              v-if="!confirmingClaim"
              class="portal-primary"
              type="button"
              :disabled="working"
              @click="confirmingClaim = true"
            >
              <Gift :size="15" aria-hidden="true" />立即接取
            </button>
            <div v-else class="bounty-confirm" role="group" aria-label="确认接取">
              <p>
                <TriangleAlert :size="15" aria-hidden="true" />
                接取后名额即被占用，且你只能接取这条悬赏一次；放弃后无法再次接取。
              </p>
              <button class="portal-primary" type="button" :disabled="working" @click="claim">
                {{ working ? '接取中…' : '确认接取' }}
              </button>
              <button class="portal-secondary" type="button" @click="confirmingClaim = false">取消</button>
            </div>
          </template>
        </div>

        <div v-else class="task-card-actions">
          <RouterLink class="portal-primary" :to="`/tasks/${bounty.myAssignmentId}`">
            <CheckCircle2 :size="15" aria-hidden="true" />进入任务并提交
          </RouterLink>
          <button
            v-if="bounty.myStatus === 'PENDING' && bounty.windowOpen"
            class="portal-secondary"
            type="button"
            :disabled="working"
            @click="abandon"
          >
            放弃接取
          </button>
        </div>

        <p v-if="actionError" class="portal-state error inline" role="alert">{{ actionError }}</p>
      </section>
    </template>
  </PortalShell>
</template>

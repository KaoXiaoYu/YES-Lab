<script setup>
import { ArrowLeft, CheckCircle2, ChevronRight, CircleDashed, Gift, TriangleAlert } from '@lucide/vue'
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import PortalShell from '../components/PortalShell.vue'
import LoadingSkeleton from '../components/LoadingSkeleton.vue'
import { abandonBounty, claimBounty, getBountyDetail } from '../services/authApi'
import { celebrate } from '../services/celebrate'
import { confirmAction } from '../services/confirm'
import { toast } from '../services/toast'

const route = useRoute()
const bounty = ref(null)
const loading = ref(true)
const working = ref(false)
const errorMessage = ref('')
const actionError = ref('')

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

async function load({ quiet = false } = {}) {
  if (!quiet) loading.value = true
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
  const prize = bounty.value.prize
  if (
    !(await confirmAction({
      title: `接取「${bounty.value.title}」？`,
      message: '接取会占用一个名额，提交即完成并锁定名次。',
      details: [
        prize?.prizeSlots ? `最先完成的 ${prize.prizeSlots} 人获得奖金。` : '这条悬赏没有设置奖金份数。',
        '放弃后名额会归还，但你不能再次接取。',
      ],
      confirmText: '接取',
    }))
  )
    return
  working.value = true
  actionError.value = ''
  try {
    const result = await claimBounty(bounty.value.taskId)
    await load({ quiet: true })
    // 留在悬赏页庆祝，再由成员决定何时开始。
    celebrate({
      title: '接取成功',
      message: `「${bounty.value.title}」已加入你的任务，越早提交名次越靠前。`,
      actionLabel: '去开始',
      actionTo: `/tasks/${result.assignmentId}`,
    })
  } catch (error) {
    actionError.value = error.message
  } finally {
    working.value = false
  }
}

async function abandon() {
  if (
    !(await confirmAction({
      title: '放弃这条悬赏？',
      message: '放弃后名额会归还，但你不能再接取它。',
      confirmText: '放弃悬赏',
      cancelText: '继续完成',
      tone: 'danger',
    }))
  )
    return
  working.value = true
  actionError.value = ''
  try {
    const result = await abandonBounty(bounty.value.taskId)
    const title = bounty.value.title
    await load({ quiet: true })
    const prize = bounty.value.prize
    const prizeStatus = prize.prizeSlots
      ? `奖金已产生 ${prize.prizeIssued} / ${prize.prizeSlots} 份。`
      : '该悬赏未设置奖金份数。'
    const headcountStatus =
      bounty.value.prize.headcountLimit == null
        ? `已接 ${result.occupied} 人，不限人数`
        : `已接 ${result.occupied} / ${bounty.value.prize.headcountLimit} 人`
    toast.info(`当前${headcountStatus}；${prizeStatus}`, { title: `已放弃「${title}」` })
  } catch (error) {
    actionError.value = error.message
  } finally {
    working.value = false
  }
}
</script>

<template>
  <PortalShell title="悬赏详情" description="查看奖励与名额；接取后提交即完成。">
    <RouterLink class="task-back" to="/bounties"><ArrowLeft :size="16" aria-hidden="true" />返回悬赏榜</RouterLink>

    <LoadingSkeleton v-if="loading" variant="detail" :rows="3" label="正在读取悬赏" />
    <div v-else-if="errorMessage" class="portal-state error" role="alert">{{ errorMessage }}</div>

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
          <button v-if="bounty.claimable" class="portal-primary" type="button" :disabled="working" @click="claim">
            <Gift :size="15" aria-hidden="true" />{{ working ? '接取中…' : '接取悬赏' }}
          </button>
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

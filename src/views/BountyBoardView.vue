<script setup>
import { computed, onMounted, ref } from 'vue'
import { Gift, ListChecks, TriangleAlert } from '@lucide/vue'
import PortalShell from '../components/PortalShell.vue'
import { claimBounty, getBountyBoard } from '../services/authApi'

const items = ref([])
const loading = ref(true)
const working = ref(false)
const errorMessage = ref('')
const actionError = ref('')
const actionStatus = ref('')
/** 同时只对一条悬赏做接取确认，避免多处弹确认框。 */
const confirmingId = ref('')
const filter = ref('ALL')
const searchQuery = ref('')

const filters = [
  { id: 'ALL', label: '全部' },
  { id: 'OPEN', label: '可接取' },
  { id: 'MINE', label: '我已接取' },
  { id: 'CLOSED', label: '暂不可接' },
]

const filterCounts = computed(() => ({
  ALL: items.value.length,
  OPEN: items.value.filter((item) => item.claimable).length,
  MINE: items.value.filter((item) => item.myAssignmentId).length,
  CLOSED: items.value.filter((item) => !item.claimable && !item.myAssignmentId).length,
}))

const myStatusLabels = {
  PENDING: '进行中',
  APPROVED: '已完成',
  REJECTED: '已驳回',
  ABANDONED: '已放弃',
}

const filtered = computed(() => {
  const query = searchQuery.value.trim().toLocaleLowerCase()
  return items.value.filter((item) => {
    if (filter.value === 'OPEN' && !item.claimable) return false
    if (filter.value === 'MINE' && !item.myAssignmentId) return false
    if (filter.value === 'CLOSED' && (item.claimable || item.myAssignmentId)) return false
    return !query || item.title.toLocaleLowerCase().includes(query)
  })
})

function prizeText(item) {
  const prize = item.prize
  if (!prize.prizeSlots) return ''
  return `${prize.prizeIssued} / ${prize.prizeSlots} 份已产生`
}

function headcountText(item) {
  const prize = item.prize
  return prize.headcountLimit == null
    ? `已接 ${prize.claimed} 人 · 不限人数`
    : `已接 ${prize.claimed} / ${prize.headcountLimit}`
}

async function load() {
  loading.value = true
  try {
    items.value = await getBountyBoard()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function confirmClaim(item) {
  working.value = true
  actionError.value = ''
  actionStatus.value = ''
  try {
    const claimResult = await claimBounty(item.taskId)
    confirmingId.value = ''
    await load()
    const updated = items.value.find((entry) => entry.taskId === item.taskId)
    if (updated) {
      const prize = updated.prize.prizeSlots
        ? `奖金已产生 ${updated.prize.prizeIssued} / ${updated.prize.prizeSlots} 份。`
        : '该悬赏未设置奖金份数。'
      actionStatus.value = `已接取「${updated.title}」。当前已接取 ${claimResult.claimed} 人；${prize}`
    }
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
    title="悬赏榜"
    description="浏览悬赏、查看名额与奖励；接取后按要求提交即可完成。"
  >
    <section class="bounty-list-tools" aria-label="悬赏筛选与搜索">
      <label class="bounty-search">
        搜索悬赏
        <input v-model.trim="searchQuery" type="search" placeholder="输入标题" />
      </label>
      <nav class="bounty-filters" aria-label="悬赏状态">
        <button
          v-for="item in filters"
          :key="item.id"
          type="button"
          :aria-pressed="filter === item.id"
          @click="filter = item.id"
        >
          {{ item.label }} <span>{{ filterCounts[item.id] }}</span>
        </button>
      </nav>
    </section>

    <p v-if="actionError" class="portal-state error inline" role="alert">{{ actionError }}</p>
    <p v-if="actionStatus" class="portal-state success" role="status" aria-atomic="true">
      {{ actionStatus }}
    </p>

    <div v-if="loading" class="portal-state">正在读取悬赏…</div>
    <div v-else-if="errorMessage" class="portal-state error" role="alert">{{ errorMessage }}</div>
    <div v-else-if="!filtered.length" class="portal-state project-empty">
      <Gift :size="28" aria-hidden="true" /><strong>没有匹配的悬赏</strong><span>换个筛选条件或搜索词试试。</span>
    </div>

    <TransitionGroup v-else tag="section" name="task-list" class="task-card-grid" aria-label="悬赏列表">
      <article v-for="item in filtered" :key="item.taskId" class="task-card bounty-card">
        <header>
          <span :data-status="item.myStatus || (item.claimable ? 'OPEN' : 'CLOSED')">
            {{
              item.myStatus ? myStatusLabels[item.myStatus] || item.myStatus : item.claimable ? '可接取' : '不可接取'
            }}
          </span>
          <b>{{ item.prize.points > 0 ? `+${item.prize.points} 积分` : '不计积分' }}</b>
        </header>
        <h2>{{ item.title }}</h2>
        <p class="task-card-dates">
          {{ item.startDate || '未设置开始' }} — {{ item.endDate || '未设置截止' }}
          <span v-if="!item.windowOpen" class="overdue">已截止</span>
          <span v-else-if="item.endDate">剩余 {{ item.daysRemaining }} 天</span>
        </p>

        <!-- 两个数字各自标注含义，避免被误读成同一个名额 -->
        <dl class="bounty-metrics">
          <div>
            <dt>接取</dt>
            <dd>{{ headcountText(item) }}</dd>
          </div>
          <div v-if="item.prize.prizeSlots">
            <dt>奖金</dt>
            <dd>{{ prizeText(item) }}</dd>
          </div>
        </dl>
        <p v-if="item.prize.points > 0" class="task-card-settle">
          {{
            item.prize.pointsSettled
              ? `积分已结算：+${item.prize.points}`
              : `积分待结算：+${item.prize.points}，悬赏到期后统一发放`
          }}
        </p>
        <p v-if="item.prize.prizeDescription" class="bounty-prize">
          {{ item.prize.prizeDescription }}
          <small>线下发放 · 可查领取状态</small>
        </p>

        <p v-if="item.myAssignmentId" class="bounty-mine" role="status">
          我的状态：{{ myStatusLabels[item.myStatus] || item.myStatus }}
          <template v-if="item.myRank">· 完成名次第 {{ item.myRank }} 名</template>
          <template v-if="item.myStatus === 'APPROVED'"> · {{ item.myPrizeAwarded ? '已获奖' : '未获奖' }} </template>
        </p>
        <p v-else-if="!item.claimable && item.claimBlockedReason" class="task-skip" role="status">
          {{ item.claimBlockedReason }}
        </p>

        <div class="task-card-actions">
          <RouterLink class="portal-secondary" :to="`/bounties/${item.taskId}`">查看详情</RouterLink>
          <RouterLink v-if="item.myAssignmentId" class="portal-secondary" :to="`/tasks/${item.myAssignmentId}`">
            <ListChecks :size="15" aria-hidden="true" />进入任务
          </RouterLink>
          <template v-else-if="item.claimable">
            <button
              v-if="confirmingId !== item.taskId"
              class="portal-primary"
              type="button"
              :disabled="working"
              @click="confirmingId = item.taskId"
            >
              <Gift :size="15" aria-hidden="true" />立即接取
            </button>
            <Transition name="task-reveal">
              <div v-if="confirmingId === item.taskId" class="bounty-confirm" role="group" aria-label="确认接取">
                <p>
                  <TriangleAlert :size="15" aria-hidden="true" />
                  接取会占用名额；放弃后不能再次接取。
                </p>
                <button class="portal-primary" type="button" :disabled="working" @click="confirmClaim(item)">
                  {{ working ? '接取中…' : '确认接取' }}
                </button>
                <button class="portal-secondary" type="button" @click="confirmingId = ''">取消</button>
              </div>
            </Transition>
          </template>
        </div>
      </article>
    </TransitionGroup>
  </PortalShell>
</template>

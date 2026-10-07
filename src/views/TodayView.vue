<script setup>
import { animate } from 'motion'
import { ArrowRight, CalendarClock, Gift, ListChecks, Search, Sparkles, Trophy } from '@lucide/vue'
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import PortalShell from '../components/PortalShell.vue'
import LoadingSkeleton from '../components/LoadingSkeleton.vue'
import { authState, getBountyBoard, getDeadlines, getMyTasks, getOwnPoints } from '../services/authApi'
import { commandShortcutLabel, openCommandPalette } from '../services/commandPalette'

const loading = ref(true)
const errorMessage = ref('')
const tasks = ref([])
const deadlines = ref([])
const points = ref(null)
const bounties = ref([])
const serverToday = ref('')
const statEls = ref([])

const kinds = { STANDARD: '任务', BOUNTY: '悬赏', COMPETITION: '比赛', PROJECT: '项目', ONBOARDING: '新手任务' }

const name = computed(() => authState.account?.displayName || authState.account?.username || '')
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 5) return '夜深了'
  if (hour < 11) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})
const dateLabel = computed(() =>
  new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }).format(new Date()),
)

const activeTasks = computed(() =>
  tasks.value.filter((task) => task.status === 'PENDING' || task.status === 'REJECTED').filter((task) => !task.expired),
)
const waitingTasks = computed(() => tasks.value.filter((task) => task.status === 'SUBMITTED'))
const focusTask = computed(
  () =>
    [...activeTasks.value].sort((a, b) => String(a.endDate || '9999').localeCompare(String(b.endDate || '9999')))[0] ||
    null,
)
const otherTasks = computed(() => activeTasks.value.filter((task) => task !== focusTask.value).slice(0, 4))

const today = computed(() => serverToday.value || new Date().toISOString().slice(0, 10))
function daysUntil(date) {
  if (!date) return null
  return Math.round((Date.parse(String(date).slice(0, 10)) - Date.parse(today.value)) / 86400000)
}
const upcoming = computed(() =>
  deadlines.value
    .map((entry) => ({ ...entry, days: daysUntil(entry.deadlineDate || entry.deadlineAt) }))
    .filter((entry) => entry.days != null && entry.days >= 0 && entry.days <= 7),
)
const monthPoints = computed(() => {
  const prefix = today.value.slice(0, 7)
  return (points.value?.dailyPoints || [])
    .filter((item) => item.date.startsWith(prefix))
    .reduce((sum, item) => sum + Number(item.points || 0), 0)
})
const weekBars = computed(() => {
  const map = new Map((points.value?.dailyPoints || []).map((item) => [item.date, Number(item.points) || 0]))
  const base = Date.parse(today.value)
  const days = Array.from({ length: 14 }, (_, index) => {
    const date = new Date(base - (13 - index) * 86400000).toISOString().slice(0, 10)
    return { date, points: map.get(date) || 0 }
  })
  const max = Math.max(1, ...days.map((day) => day.points))
  return days.map((day) => ({ ...day, height: Math.max(6, Math.round((day.points / max) * 100)) }))
})
const openBounties = computed(() => bounties.value.filter((item) => item.claimable).slice(0, 3))

const stats = computed(() => [
  { label: '进行中', value: activeTasks.value.length, hint: '待完成或需修改' },
  { label: '等待确认', value: waitingTasks.value.length, hint: '已提交，等管理员审核' },
  { label: '7 天内截止', value: upcoming.value.length, hint: '任务、比赛与项目', tone: 'warning' },
  {
    label: '本月积分',
    value: monthPoints.value,
    hint: points.value ? `总排名第 ${points.value.totalRank || '—'}` : '',
  },
])

function progress(task) {
  if (!task?.totalSubtasks) return 0
  return Math.round((task.submittedSubtasks / task.totalSubtasks) * 100)
}
function remainingLabel(days) {
  if (days === 0) return '今天'
  if (days === 1) return '明天'
  return `${days} 天后`
}

async function countUp() {
  await nextTick()
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
  statEls.value.forEach((element, index) => {
    const target = stats.value[index]?.value || 0
    if (!element || !target) return
    animate(0, target, {
      duration: 0.9,
      delay: index * 0.06,
      ease: [0.22, 1, 0.36, 1],
      onUpdate: (latest) => (element.textContent = Math.round(latest).toLocaleString('zh-CN')),
    })
  })
}

async function load() {
  errorMessage.value = ''
  try {
    const [taskList, deadlineData, pointData, bountyList] = await Promise.all([
      getMyTasks(),
      getDeadlines(true, { page: 0, pageSize: 30 }).catch(() => null),
      getOwnPoints().catch(() => null),
      getBountyBoard().catch(() => []),
    ])
    tasks.value = taskList
    deadlines.value = deadlineData?.entries || []
    serverToday.value = deadlineData?.serverDate || ''
    points.value = pointData
    bounties.value = bountyList
    loading.value = false
    countUp()
  } catch (error) {
    errorMessage.value = error.message
    loading.value = false
  }
}

function refresh() {
  if (!loading.value) load()
}

onMounted(() => {
  load()
  window.addEventListener('yeslab:deadlines-changed', refresh)
})
onBeforeUnmount(() => window.removeEventListener('yeslab:deadlines-changed', refresh))
</script>

<template>
  <PortalShell :title="`${greeting}，${name}`" :description="`今天是${dateLabel}。这里是你今天需要知道的事。`">
    <template #actions>
      <button class="portal-secondary today-search" type="button" @click="openCommandPalette">
        <Search :size="16" aria-hidden="true" />找点什么<kbd>{{ commandShortcutLabel }}</kbd>
      </button>
    </template>

    <LoadingSkeleton v-if="loading" variant="cards" :rows="4" label="正在准备今日概览" />
    <div v-else-if="errorMessage" class="portal-state error" role="alert">{{ errorMessage }}</div>

    <div v-else class="today">
      <section class="today-stats" aria-label="今日概览">
        <div v-for="(stat, index) in stats" :key="stat.label" class="today-stat" :data-tone="stat.tone">
          <small>{{ stat.label }}</small>
          <strong :ref="(element) => (statEls[index] = element)">{{ stat.value.toLocaleString('zh-CN') }}</strong>
          <span>{{ stat.hint }}</span>
        </div>
      </section>

      <div class="today-grid">
        <section class="today-focus" aria-labelledby="today-focus-title">
          <header class="today-section-head">
            <h2 id="today-focus-title"><ListChecks :size="18" aria-hidden="true" />现在最该做的</h2>
            <RouterLink to="/tasks">全部任务<ArrowRight :size="15" aria-hidden="true" /></RouterLink>
          </header>
          <RouterLink
            v-if="focusTask"
            class="today-focus-card today-card-link"
            :to="`/tasks/${focusTask.assignmentId}`"
          >
            <div class="today-ring" :style="{ '--p': progress(focusTask) }" aria-hidden="true">
              <b
                >{{ focusTask.submittedSubtasks || 0 }}<small>/{{ focusTask.totalSubtasks || 0 }}</small></b
              >
            </div>
            <div>
              <span class="today-chip">{{ kinds[focusTask.taskType] || '任务' }}</span>
              <h3>{{ focusTask.title }}</h3>
              <p v-if="focusTask.status === 'REJECTED'">管理员退回了提交，修改后再提交一次。</p>
              <p v-else-if="focusTask.totalSubtasks">
                已完成 {{ focusTask.submittedSubtasks }} / {{ focusTask.totalSubtasks }} 个子任务
              </p>
              <p v-else>完成后提交说明，由管理员确认。</p>
            </div>
            <div v-if="focusTask.endDate" class="today-due">
              <strong>{{ remainingLabel(daysUntil(focusTask.endDate)) }}</strong>
              <small>{{ String(focusTask.endDate).slice(5, 10).replace('-', ' 月 ') }} 日截止</small>
            </div>
          </RouterLink>
          <div v-else class="today-empty">
            <Sparkles :size="22" aria-hidden="true" />
            <div>
              <strong>手上没有待办任务</strong>
              <span>去悬赏榜看看，或者在讨论板聊聊最近的进展。</span>
            </div>
            <RouterLink class="portal-secondary" to="/bounties">看看悬赏</RouterLink>
          </div>
          <ul v-if="otherTasks.length" class="today-list">
            <li v-for="task in otherTasks" :key="task.assignmentId">
              <RouterLink :to="`/tasks/${task.assignmentId}`">
                <i :data-status="task.status" aria-hidden="true"></i>
                <span
                  ><b>{{ task.title }}</b
                  ><small
                    >{{ kinds[task.taskType] || '任务'
                    }}{{ task.totalSubtasks ? ` · ${task.submittedSubtasks}/${task.totalSubtasks}` : '' }}</small
                  ></span
                >
                <time v-if="task.endDate">{{ String(task.endDate).slice(5, 10) }}</time>
              </RouterLink>
            </li>
          </ul>
        </section>

        <section class="today-week" aria-labelledby="today-week-title">
          <header class="today-section-head">
            <h2 id="today-week-title"><CalendarClock :size="18" aria-hidden="true" />接下来 7 天</h2>
            <RouterLink to="/profile">完整日程<ArrowRight :size="15" aria-hidden="true" /></RouterLink>
          </header>
          <ol v-if="upcoming.length" class="today-timeline">
            <li v-for="entry in upcoming" :key="`${entry.sourceType}-${entry.sourceId}-${entry.milestoneKey}`">
              <span class="today-when" :data-soon="entry.days <= 1">{{ remainingLabel(entry.days) }}</span>
              <RouterLink v-if="entry.href" :to="entry.href"
                ><b>{{ entry.title }}</b
                ><small>{{ kinds[entry.sourceType] || '' }} · {{ entry.milestone }}</small></RouterLink
              >
              <div v-else>
                <b>{{ entry.title }}</b
                ><small>{{ kinds[entry.sourceType] || '' }} · {{ entry.milestone }}</small>
              </div>
            </li>
          </ol>
          <p v-else class="today-quiet">接下来 7 天没有截止事项。</p>
        </section>

        <section class="today-points" aria-labelledby="today-points-title">
          <header class="today-section-head">
            <h2 id="today-points-title"><Trophy :size="18" aria-hidden="true" />近两周积分</h2>
            <RouterLink to="/points">积分榜<ArrowRight :size="15" aria-hidden="true" /></RouterLink>
          </header>
          <div class="today-bars" role="img" :aria-label="`近 14 天积分，本月共 ${monthPoints} 分`">
            <span
              v-for="(bar, index) in weekBars"
              :key="bar.date"
              :style="{ '--h': `${bar.height}%`, '--i': index }"
              :data-empty="bar.points === 0"
              :title="`${bar.date}：${bar.points} 分`"
            ></span>
          </div>
          <p class="today-quiet">
            总积分 <b>{{ points?.totalPoints ?? 0 }}</b
            ><template v-if="points?.totalRank"> · 总排名第 {{ points.totalRank }}</template>
          </p>
        </section>

        <section class="today-bounties" aria-labelledby="today-bounty-title">
          <header class="today-section-head">
            <h2 id="today-bounty-title"><Gift :size="18" aria-hidden="true" />可以接的悬赏</h2>
            <RouterLink to="/bounties">悬赏榜<ArrowRight :size="15" aria-hidden="true" /></RouterLink>
          </header>
          <ul v-if="openBounties.length" class="today-list">
            <li v-for="bounty in openBounties" :key="bounty.taskId">
              <RouterLink :to="`/bounties/${bounty.taskId}`">
                <i data-status="BOUNTY" aria-hidden="true"></i>
                <span
                  ><b>{{ bounty.title }}</b
                  ><small
                    >{{ bounty.prize?.points > 0 ? `+${bounty.prize.points} 积分` : '不计积分' }} ·
                    {{ bounty.daysRemaining != null ? `还剩 ${bounty.daysRemaining} 天` : '' }}</small
                  ></span
                >
              </RouterLink>
            </li>
          </ul>
          <p v-else class="today-quiet">暂时没有可接取的悬赏。</p>
        </section>
      </div>
    </div>
  </PortalShell>
</template>

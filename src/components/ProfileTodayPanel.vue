<script setup>
import { animate } from 'motion'
import { ArrowRight, Gift, ListChecks, Sparkles } from '@lucide/vue'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import DeadlinePanel from '../components/DeadlinePanel.vue'
import LoadingSkeleton from '../components/LoadingSkeleton.vue'
import { getBountyBoard, getDeadlines, getMyTasks } from '../services/authApi'
const props = defineProps({ pointsSummary: { type: Object, default: null } })

const loading = ref(true)
const errorMessage = ref('')
const tasksReady = ref(false)
const tasks = ref([])
const deadlines = ref([])
const points = computed(() => props.pointsSummary)
const deadlineData = ref(null)
const bounties = ref([])
const serverToday = ref('')
const statEls = ref([])
let counters = []

const kinds = { STANDARD: '任务', BOUNTY: '悬赏', COMPETITION: '比赛', PROJECT: '项目', ONBOARDING: '新手任务' }

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
const openBounties = computed(() => bounties.value.filter((item) => item.claimable).slice(0, 3))

const stats = computed(() => [
  { label: '进行中', value: tasksReady.value ? activeTasks.value.length : null, hint: '待完成或需修改' },
  { label: '等待确认', value: tasksReady.value ? waitingTasks.value.length : null, hint: '已提交，等管理员审核' },
  {
    label: '7 天内截止',
    value: deadlineData.value ? upcoming.value.length : null,
    hint: '任务、比赛与项目',
    tone: 'warning',
  },
  {
    label: '本月积分',
    value: points.value ? monthPoints.value : null,
    hint: points.value?.totalRank ? `总排名第 ${points.value.totalRank}` : '积分汇总',
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
  counters.forEach((control) => control.stop())
  counters = []
  await nextTick()
  if (loading.value || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
  statEls.value.forEach((element, index) => {
    const target = stats.value[index]?.value || 0
    if (!element || !target) return
    counters.push(
      animate(0, target, {
        duration: 0.9,
        delay: index * 0.06,
        ease: [0.22, 1, 0.36, 1],
        onUpdate: (latest) => (element.textContent = Math.round(latest).toLocaleString('zh-CN')),
      }),
    )
  })
}

async function load() {
  errorMessage.value = ''
  try {
    const [taskResult, loadedDeadlines, bountyList] = await Promise.all([
      getMyTasks()
        .then((data) => ({ data }))
        .catch((error) => ({ error })),
      getDeadlines(true, { page: 0, pageSize: 30 }).catch(() => null),
      getBountyBoard().catch(() => []),
    ])
    tasksReady.value = !taskResult.error
    tasks.value = taskResult.data || []
    errorMessage.value = taskResult.error?.message || ''
    deadlineData.value = loadedDeadlines
    deadlines.value = loadedDeadlines?.entries || []
    serverToday.value = loadedDeadlines?.serverDate || ''
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
watch(stats, countUp)
onBeforeUnmount(() => {
  window.removeEventListener('yeslab:deadlines-changed', refresh)
  counters.forEach((control) => control.stop())
})
</script>

<template>
  <div class="profile-today-panel">
    <LoadingSkeleton v-if="loading" variant="cards" :rows="4" label="正在准备今日概览" />
    <div v-else-if="errorMessage" class="portal-state error" role="alert">
      {{ errorMessage }} <button class="portal-secondary" type="button" @click="load">重试</button>
    </div>

    <div v-if="!loading" class="today">
      <section class="today-stats" aria-label="今日概览">
        <div v-for="(stat, index) in stats" :key="stat.label" class="today-stat" :data-tone="stat.tone">
          <small>{{ stat.label }}</small>
          <strong :ref="(element) => (statEls[index] = element)">{{
            stat.value == null ? '—' : stat.value.toLocaleString('zh-CN')
          }}</strong>
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
          <div v-else-if="tasksReady" class="today-empty">
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

        <DeadlinePanel personal :initial-data="deadlineData" />
      </div>
    </div>
    <slot />
    <div v-if="!loading && !errorMessage" class="profile-bounty-recommendations">
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
</template>

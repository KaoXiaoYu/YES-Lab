<script setup>
import { CalendarClock, ChevronDown, ChevronUp } from '@lucide/vue'
import { computed, onBeforeUnmount, onMounted, ref, watch, useId } from 'vue'
import { useRouter } from 'vue-router'
import { authState, getDeadlines } from '../services/authApi'
import { labTime } from '../services/fundFormat'
const props = defineProps({ personal: Boolean })
const router = useRouter()
const disclosureId = useId()
const kinds = {
  ALL: '全部',
  STANDARD: '任务',
  BOUNTY: '悬赏',
  COMPETITION: '比赛',
  PROJECT: '项目',
  ONBOARDING: '新手任务',
}
const type = ref('ALL')
const page = ref(0)
const expanded = ref(false)
const entries = ref([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const hint = ref('')
const updatedAt = ref('')
const now = ref(Date.now())
const member = computed(() => ['TEACHER', 'CORE_STUDENT', 'MEMBER'].includes(authState.account?.role))
const pageSize = computed(() => (expanded.value ? 10 : 3))
let offset = 0
let version = 0
let clockTimer
let boundaryTimer
let unmounted = false
let lastDay = ''
function day(time) {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(time))
}
function overdue(entry) {
  return entry.deadlineAt ? Date.parse(entry.deadlineAt) <= now.value : entry.deadlineDate < day(now.value)
}
const groups = computed(() =>
  [
    { label: '即将到期', rows: entries.value.filter((e) => !overdue(e)) },
    { label: '已逾期', rows: entries.value.filter(overdue) },
  ].filter((g) => g.rows.length),
)
function remaining(entry) {
  if (entry.deadlineAt) {
    const minutes = Math.ceil((Date.parse(entry.deadlineAt) - now.value) / 60000)
    if (minutes <= 0) return '重交已到期'
    return minutes < 60 ? `剩余 ${minutes} 分钟` : `剩余 ${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分钟`
  }
  const days = Math.round((Date.parse(entry.deadlineDate) - Date.parse(day(now.value))) / 86400000)
  if (days === 0) return entry.sourceType === 'COMPETITION' ? '今天比赛' : '今天截止'
  if (days < 0) return entry.sourceType === 'COMPETITION' ? `已过 ${-days} 天，待更新` : `已逾期 ${-days} 天`
  return `剩余 ${days} 天`
}
function scheduleBoundary() {
  clearTimeout(boundaryTimer)
  const time = Date.now() + offset
  const midnight = Date.parse(`${day(time)}T00:00:00+08:00`) + 86400000
  const next = Math.min(
    midnight,
    ...entries.value
      .filter((e) => e.deadlineAt && Date.parse(e.deadlineAt) > time)
      .map((e) => Date.parse(e.deadlineAt)),
  )
  boundaryTimer = setTimeout(
    () => {
      now.value = Date.now() + offset
      load()
    },
    Math.max(1, next - time + 1),
  )
}
async function load() {
  if (unmounted || (props.personal && !member.value)) return
  const ticket = ++version
  loading.value = true
  error.value = ''
  try {
    const query = { page: page.value, pageSize: pageSize.value }
    if (type.value !== 'ALL') query.type = type.value
    const data = await getDeadlines(props.personal, query)
    if (ticket !== version || unmounted) return
    entries.value = data.entries
    total.value = data.totalCount
    updatedAt.value = data.generatedAt
    offset = Date.parse(data.generatedAt) - Date.now()
    now.value = Date.now() + offset
    lastDay = data.serverDate
    scheduleBoundary()
  } catch (e) {
    if (ticket === version) error.value = e.message
  } finally {
    if (ticket === version) loading.value = false
  }
}
function filter(kind) {
  if (type.value === kind) return
  type.value = kind
  page.value = 0
  entries.value = []
  load()
}
function toggle() {
  expanded.value = !expanded.value
  page.value = 0
  load()
}
function changePage(delta) {
  page.value += delta
  load()
}
function open(entry) {
  hint.value = ''
  if (!member.value) {
    hint.value = '事项详情需成员登录，并按各模块权限查看。'
    return
  }
  router.push(entry.href)
}
function resume() {
  if (document.visibilityState !== 'visible') return
  now.value = Date.now() + offset
  load()
}
function storage(event) {
  if (event.key === 'yeslab-deadlines-changed') load()
}
watch(
  () => authState.account?.id,
  () => {
    if (props.personal) {
      ++version
      entries.value = []
      total.value = 0
      page.value = 0
      if (member.value) load()
    }
  },
)
onMounted(() => {
  load()
  clockTimer = setInterval(() => {
    now.value = Date.now() + offset
    if (day(now.value) !== lastDay && !loading.value) load()
  }, 60000)
  document.addEventListener('visibilitychange', resume)
  window.addEventListener('focus', resume)
  window.addEventListener('yeslab:deadlines-changed', load)
  window.addEventListener('storage', storage)
})
onBeforeUnmount(() => {
  unmounted = true
  ++version
  clearInterval(clockTimer)
  clearTimeout(boundaryTimer)
  document.removeEventListener('visibilitychange', resume)
  window.removeEventListener('focus', resume)
  window.removeEventListener('yeslab:deadlines-changed', load)
  window.removeEventListener('storage', storage)
})
</script>
<template>
  <section class="lab-info-card deadline-panel" :aria-label="personal ? '我的倒计时' : '实验室倒计时'">
    <header>
      <div>
        <p>{{ personal ? 'MY DEADLINES' : 'LAB DEADLINES' }}</p>
        <h2><CalendarClock :size="22" aria-hidden="true" />{{ personal ? '我的倒计时' : '实验室倒计时' }}</h2>
      </div>
      <span>共 {{ total }} 项</span>
    </header>
    <div class="deadline-filters" aria-label="倒计时类型">
      <button
        v-for="(label, kind) in kinds"
        :key="kind"
        type="button"
        :aria-pressed="type === kind"
        :class="{ active: type === kind }"
        @click="filter(kind)"
      >
        {{ label }}
      </button>
    </div>
    <p v-if="loading && !entries.length" role="status">正在读取倒计时…</p>
    <p v-if="error" role="alert">{{ error }} <button type="button" @click="load">重试</button></p>
    <p v-else-if="!loading && !entries.length" class="ledger-muted">
      {{ personal ? '当前没有你参与的有期限事项。' : '当前没有该类型的进行中事项。' }}
    </p>
    <div :id="disclosureId" class="deadline-body" :class="{ expanded }" :aria-busy="loading">
      <div v-for="group in groups" :key="group.label" class="deadline-group">
        <h3>{{ group.label }}</h3>
        <ol>
          <li
            v-for="entry in group.rows"
            :key="`${entry.sourceType}-${entry.sourceId}-${entry.milestoneKey}`"
            :data-source-type="entry.sourceType"
          >
            <div>
              <span>{{ kinds[entry.sourceType] }} · {{ entry.milestone }}</span
              ><button type="button" class="deadline-title" @click="open(entry)">{{ entry.title }}</button
              ><time :datetime="entry.deadlineAt || entry.deadlineDate">{{
                entry.deadlineAt ? labTime(entry.deadlineAt) : entry.deadlineDate
              }}</time>
            </div>
            <strong :class="{ overdue: overdue(entry) }">{{ remaining(entry) }}</strong>
          </li>
        </ol>
      </div>
      <div v-if="expanded && total > pageSize" class="ledger-pagination">
        <button type="button" :disabled="loading || page === 0" @click="changePage(-1)">上一页</button
        ><span>{{ page + 1 }} / {{ Math.ceil(total / pageSize) }}</span
        ><button type="button" :disabled="loading || (page + 1) * pageSize >= total" @click="changePage(1)">
          下一页
        </button>
      </div>
    </div>
    <p v-if="hint" role="status">
      {{ hint }} <RouterLink v-if="!authState.account" to="/login?redirect=/">成员登录</RouterLink>
    </p>
    <footer class="deadline-footer">
      <button
        type="button"
        class="disclosure-button"
        :aria-expanded="expanded"
        :aria-controls="disclosureId"
        @click="toggle"
      >
        <ChevronUp v-if="expanded" :size="18" aria-hidden="true" /><ChevronDown
          v-else
          :size="18"
          aria-hidden="true"
        />{{ expanded ? '收起日程' : '展开全部日程' }}</button
      ><span v-if="updatedAt" class="ledger-muted">更新于 {{ labTime(updatedAt) }}</span>
    </footer>
  </section>
</template>

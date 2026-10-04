<script setup>
import { CalendarClock, ChevronLeft, ChevronRight, Pause, Play, Users } from '@lucide/vue'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import { useRouter } from 'vue-router'
import { authState, getDeadlines } from '../services/authApi'
import { labTime } from '../services/fundFormat'

const router = useRouter()
const id = useId()
const root = ref(null)
const viewport = ref(null)
const original = ref(null)
const kinds = {
  ALL: '全部',
  STANDARD: '任务',
  BOUNTY: '悬赏任务',
  COMPETITION: '比赛',
  PROJECT: '项目',
  ONBOARDING: '新手任务',
}
const entries = ref([])
const presentationStart = ref(0)
const presentedEntries = computed(() => [
  ...entries.value.slice(presentationStart.value),
  ...entries.value.slice(0, presentationStart.value),
])
const type = ref('ALL')
const loading = ref(false)
const error = ref('')
const hint = ref('')
const updatedAt = ref('')
const now = ref(Date.now())
const viewportWidth = ref(0)
const cycleWidth = ref(0)
const manualPaused = ref(false)
const hovered = ref(false)
const focused = ref(false)
const touching = ref(false)
const visible = ref(false)
const hidden = ref(false)
const reduced = ref(false)
const expanded = ref(null)
const canLoop = computed(() => entries.value.length >= 3)
const paused = computed(
  () =>
    manualPaused.value ||
    hovered.value ||
    focused.value ||
    touching.value ||
    !visible.value ||
    hidden.value ||
    reduced.value ||
    expanded.value !== null,
)
const copyCount = computed(() =>
  canLoop.value && !paused.value && cycleWidth.value > 16
    ? Math.max(2, Math.ceil(viewportWidth.value / (cycleWidth.value || 1)) + 1)
    : 1,
)
const member = computed(() => ['TEACHER', 'CORE_STUDENT', 'MEMBER'].includes(authState.account?.role))
let sequence = 0
let disposed = false
let offset = 0
let queueWidth = 0
let cursor = 0
let stabilizing = false
let frame, previousTime, clockTimer, boundaryTimer, resizeObserver, intersectionObserver, media
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
function remaining(entry) {
  if (entry.deadlineAt) {
    const minutes = Math.ceil((Date.parse(entry.deadlineAt) - now.value) / 60000)
    if (minutes <= 0) return '重交已到期'
    return minutes < 60 ? `剩余 ${minutes} 分钟` : `剩余 ${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分钟`
  }
  const days = Math.round((Date.parse(entry.deadlineDate) - Date.parse(day(now.value))) / 86400000)
  if (days < 0) return entry.sourceType === 'COMPETITION' ? `已过 ${-days} 天，待更新` : `已逾期 ${-days} 天`
  if (!days) return entry.sourceType === 'COMPETITION' ? '今天比赛' : '今天截止'
  return `剩余 ${days} 天`
}
function key(entry) {
  return `${entry.sourceType}-${entry.sourceId}-${entry.milestoneKey}`
}
function scheduleBoundary() {
  clearTimeout(boundaryTimer)
  const time = Date.now() + offset
  const midnight = Date.parse(`${day(time)}T00:00:00+08:00`) + 86400000
  const next = entries.value.reduce((boundary, entry) => {
    if (!entry.deadlineAt) return boundary
    const deadline = Date.parse(entry.deadlineAt)
    const expiry = deadline + 14 * 86400000
    for (const instant of [deadline, expiry]) if (instant > time) boundary = Math.min(boundary, instant)
    return boundary
  }, midnight)
  boundaryTimer = setTimeout(
    () => {
      now.value = Date.now() + offset
      load()
    },
    Math.max(1, next - time + 1),
  )
}
async function load(kind = type.value) {
  if (typeof kind !== 'string') kind = type.value
  if (disposed) return
  const ticket = ++sequence
  loading.value = true
  error.value = ''
  try {
    const query = { page: 0, pageSize: 50, homepageWindow: true, ...(kind === 'ALL' ? {} : { type: kind }) }
    const first = await getDeadlines(false, query)
    if (ticket !== sequence || disposed) return
    const rows = [...first.entries]
    for (let page = 1; page * 50 < first.totalCount; page++) {
      const data = await getDeadlines(false, { ...query, page })
      if (ticket !== sequence || disposed) return
      if (data.totalCount !== first.totalCount) throw new Error('日程在读取时发生变化，请重试。')
      rows.push(...data.entries)
    }
    const unique = [...new Map(rows.map((entry) => [key(entry), entry])).values()]
    if (unique.length !== first.totalCount) throw new Error('未能完整读取日程，请重试。')
    const sameQueue =
      kind === type.value &&
      unique.length === entries.value.length &&
      unique.every((entry, index) => key(entry) === key(entries.value[index]))
    const previousScroll = sameQueue ? viewport.value?.scrollLeft || 0 : 0
    const previousExpanded =
      kind === type.value && unique.some((entry) => key(entry) === expanded.value) ? expanded.value : null
    entries.value = unique
    if (!sameQueue) presentationStart.value = 0
    type.value = kind
    expanded.value = previousExpanded
    updatedAt.value = first.generatedAt
    offset = Date.parse(first.generatedAt) - Date.now()
    now.value = Date.now() + offset
    scheduleBoundary()
    await nextTick()
    if (ticket !== sequence || disposed) return
    measure()
    if (viewport.value) viewport.value.scrollLeft = previousScroll
    previousTime = undefined
  } catch (e) {
    if (ticket === sequence && !disposed)
      error.value = `${e.message}${entries.value.length ? ' 当前保留上次完整日程。' : ''}`
  } finally {
    if (ticket === sequence && !disposed) loading.value = false
  }
}
function measure() {
  if (!viewport.value || !original.value) return
  const width = original.value.getBoundingClientRect().width
  queueWidth = width + 16
  cycleWidth.value = queueWidth
  viewportWidth.value = viewport.value.clientWidth
}
function animate(time) {
  if (disposed) return
  if (canLoop.value && !paused.value && previousTime !== undefined && queueWidth > 0) {
    cursor = (cursor + Math.min(time - previousTime, 100) * 0.024) % queueWidth
    viewport.value.scrollLeft = cursor
  } else {
    cursor = viewport.value?.scrollLeft || 0
  }
  previousTime = canLoop.value && !paused.value ? time : undefined
  frame = requestAnimationFrame(animate)
}
async function stabilizeForReading() {
  if (stabilizing || !canLoop.value || !original.value || !viewport.value) return
  const scroll = viewport.value.scrollLeft % queueWidth
  const cards = original.value.children
  const stride = cards[1]?.offsetLeft - cards[0]?.offsetLeft
  const index = Math.floor(scroll / stride)
  if (!stride) return
  const activeElement = document.activeElement
  const keepFocus = original.value.contains(activeElement)
  stabilizing = true
  presentationStart.value = (presentationStart.value + index) % entries.value.length
  await nextTick()
  if (!disposed && viewport.value) {
    if (keepFocus && original.value?.contains(activeElement)) activeElement.focus({ preventScroll: true })
    viewport.value.scrollLeft = scroll - index * stride
    cursor = viewport.value.scrollLeft
    previousTime = undefined
  }
  stabilizing = false
}
watch(paused, (value) => {
  if (value) stabilizeForReading()
})
watch(canLoop, (value) => {
  if (!value) {
    presentationStart.value = 0
    if (viewport.value) viewport.value.scrollLeft = 0
  }
})
function step(direction) {
  if (!viewport.value || !original.value) return
  manualPaused.value = true
  expanded.value = null
  const cards = original.value.children
  const stride = cards[1]?.offsetLeft - cards[0]?.offsetLeft
  const current = stride ? Math.floor((viewport.value.scrollLeft % queueWidth) / stride) : 0
  presentationStart.value =
    (presentationStart.value + current + direction + entries.value.length) % entries.value.length
  viewport.value.scrollLeft = 0
  cursor = 0
  previousTime = undefined
}

function focusOut(event) {
  if (!root.value.contains(event.relatedTarget)) focused.value = false
}
function toggleMembers(entry) {
  expanded.value = expanded.value === key(entry) ? null : key(entry)
}
function open(entry) {
  if (!member.value) {
    hint.value = '事项详情需成员登录，并按各模块权限查看。'
    return
  }
  router.push(entry.href)
}
function resume() {
  hidden.value = document.visibilityState !== 'visible'
  if (!hidden.value) {
    now.value = Date.now() + offset
    load()
  }
}
function storage(event) {
  if (event.key === 'openlims-deadlines-changed') load()
}
function mediaChange() {
  reduced.value = media.matches
}
function releaseTouch() {
  touching.value = false
}
onMounted(() => {
  load()
  media = matchMedia('(prefers-reduced-motion: reduce)')
  mediaChange()
  hidden.value = document.visibilityState !== 'visible'
  media.addEventListener('change', mediaChange)
  resizeObserver = new ResizeObserver(measure)
  resizeObserver.observe(viewport.value)
  resizeObserver.observe(original.value)
  intersectionObserver = new IntersectionObserver(([entry]) => {
    visible.value = entry.isIntersecting
  })
  intersectionObserver.observe(root.value)
  frame = requestAnimationFrame(animate)
  clockTimer = setInterval(() => {
    now.value = Date.now() + offset
  }, 1000)
  document.addEventListener('visibilitychange', resume)
  window.addEventListener('focus', resume)
  window.addEventListener('pointerup', releaseTouch)
  window.addEventListener('pointercancel', releaseTouch)
  window.addEventListener('openlims:deadlines-changed', load)
  window.addEventListener('storage', storage)
})
onBeforeUnmount(() => {
  disposed = true
  ++sequence
  cancelAnimationFrame(frame)
  clearInterval(clockTimer)
  clearTimeout(boundaryTimer)
  resizeObserver?.disconnect()
  intersectionObserver?.disconnect()
  media?.removeEventListener('change', mediaChange)
  document.removeEventListener('visibilitychange', resume)
  window.removeEventListener('focus', resume)
  window.removeEventListener('pointerup', releaseTouch)
  window.removeEventListener('pointercancel', releaseTouch)
  window.removeEventListener('openlims:deadlines-changed', load)
  window.removeEventListener('storage', storage)
})
</script>

<template>
  <section
    ref="root"
    class="lab-info-card deadline-conveyor"
    aria-label="实验室倒计时"
    :data-mode="canLoop ? 'conveyor' : 'static'"
    :data-playing="canLoop && !paused"
    @mouseenter="hovered = true"
    @mouseleave="hovered = false"
    @focusin="focused = true"
    @focusout="focusOut"
  >
    <header class="conveyor-header">
      <div>
        <p>LAB DEADLINES</p>
        <h2><CalendarClock :size="22" aria-hidden="true" />实验室倒计时</h2>
      </div>
      <span>共 {{ entries.length }} 项</span>
    </header>
    <div class="conveyor-filters" aria-label="倒计时类型">
      <button
        v-for="(label, kind) in kinds"
        :key="kind"
        type="button"
        :aria-pressed="type === kind"
        :class="{ active: type === kind }"
        @click="load(kind)"
      >
        {{ label }}
      </button>
    </div>
    <p v-if="loading && !entries.length" role="status">正在读取全部倒计时…</p>
    <p v-if="error" role="alert">{{ error }} <button type="button" @click="load()">重试</button></p>
    <p v-else-if="!loading && !entries.length" class="conveyor-muted">当前没有该类型的进行中事项。</p>
    <div
      :id="id"
      ref="viewport"
      class="conveyor-viewport"
      :class="{ 'few-entries': entries.length <= 2 }"
      :aria-busy="loading"
      @pointerdown="touching = true"
    >
      <div class="conveyor-track">
        <ol
          v-for="copy in copyCount"
          :key="copy"
          :ref="
            (element) => {
              if (copy === 1) original = element
            }
          "
          class="conveyor-queue"
          :aria-hidden="copy > 1 ? true : undefined"
          :inert="copy > 1 ? true : undefined"
        >
          <li
            v-for="entry in presentedEntries"
            :key="key(entry)"
            class="conveyor-card"
            :class="{ 'is-overdue': overdue(entry) }"
            :data-source-type="entry.sourceType"
          >
            <div class="conveyor-card-top">
              <span class="conveyor-kind">{{ kinds[entry.sourceType] }}</span
              ><span class="conveyor-status">{{ overdue(entry) ? '已逾期' : '即将到期' }}</span>
            </div>
            <button type="button" class="conveyor-title" @click="open(entry)">{{ entry.title }}</button>
            <strong class="conveyor-remaining">{{ remaining(entry) }}</strong>
            <p class="conveyor-date">
              {{ entry.milestone }}<br /><time :datetime="entry.deadlineAt || entry.deadlineDate">{{
                entry.deadlineAt ? labTime(entry.deadlineAt) : entry.deadlineDate
              }}</time>
            </p>
            <div class="conveyor-members">
              <p class="conveyor-member-heading">
                <Users :size="16" aria-hidden="true" />参加成员 · {{ entry.participantCount }} 人
              </p>
              <p v-if="entry.participantVisibility === 'AGGREGATE_ONLY'" class="conveyor-muted">招新参与者仅公开人数</p>
              <p v-else-if="!entry.participants.length" class="conveyor-muted">
                {{ entry.sourceType === 'BOUNTY' ? '暂无人接取' : '待分配' }}
              </p>
              <ul
                v-else
                :id="`${id}-${copy}-${key(entry)}`"
                class="conveyor-member-list"
                :class="{ 'all-members': expanded === key(entry) }"
              >
                <li
                  v-for="(participant, index) in expanded === key(entry)
                    ? entry.participants
                    : entry.participants.slice(0, 3)"
                  :key="index"
                >
                  <span>{{ participant.name }}</span
                  ><small>{{ participant.role }}</small>
                </li>
              </ul>
              <button
                v-if="entry.participants.length > 3"
                type="button"
                class="conveyor-member-toggle"
                :aria-expanded="expanded === key(entry)"
                :aria-controls="`${id}-${copy}-${key(entry)}`"
                @click="toggleMembers(entry)"
              >
                {{ expanded === key(entry) ? '收起成员' : `查看全部 ${entry.participantCount} 位` }}
              </button>
            </div>
          </li>
        </ol>
      </div>
    </div>
    <p v-if="hint" role="status">
      {{ hint }} <RouterLink v-if="!authState.account" to="/login?redirect=/">成员登录</RouterLink>
    </p>
    <div class="conveyor-footer">
      <div v-if="canLoop" class="conveyor-controls" aria-label="倒计时播放控制">
        <button type="button" aria-label="上一项倒计时" :aria-controls="id" @click="step(-1)">
          <ChevronLeft :size="20" aria-hidden="true" />
        </button>
        <button
          v-if="canLoop && !reduced"
          type="button"
          :aria-pressed="manualPaused"
          @click="manualPaused = !manualPaused"
        >
          <Play v-if="manualPaused" :size="18" aria-hidden="true" /><Pause v-else :size="18" aria-hidden="true" />{{
            manualPaused ? '继续播放' : '暂停播放'
          }}
        </button>
        <button type="button" aria-label="下一项倒计时" :aria-controls="id" @click="step(1)">
          <ChevronRight :size="20" aria-hidden="true" />
        </button>
      </div>
      <span class="conveyor-muted">{{
        canLoop
          ? reduced
            ? '减少动态 · 可手动切换'
            : paused
              ? '已停住 · 可查看成员'
              : '循环播放 · 悬停可暂停'
          : '全部日程静态展示'
      }}</span>
      <span v-if="updatedAt" class="conveyor-updated">更新于 {{ labTime(updatedAt) }}</span>
    </div>
  </section>
</template>

<style scoped>
.deadline-conveyor {
  min-width: 0;
  overflow: hidden;
}
.conveyor-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}
.conveyor-header p {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: var(--color-muted-foreground);
  margin: 0 0 6px;
}
.conveyor-header h2 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 22px;
  margin: 0;
}
.conveyor-header > span {
  white-space: nowrap;
  font-size: 14px;
}
.conveyor-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 20px 0;
}
.deadline-conveyor button,
.deadline-conveyor a {
  min-height: 44px;
  min-width: 44px;
}
.deadline-conveyor button {
  cursor: pointer;
  color: var(--color-foreground);
  font: inherit;
}
.deadline-conveyor button:focus-visible,
.deadline-conveyor a:focus-visible {
  outline: 3px solid var(--color-ring);
  outline-offset: -3px;
}
.conveyor-filters button,
.conveyor-controls button {
  border: 1px solid var(--color-border);
  background: var(--color-card);
  border-radius: 10px;
  padding: 8px 12px;
}
.conveyor-filters .active {
  color: var(--color-on-primary);
  background: var(--color-primary-fill);
  border-color: var(--color-primary-fill);
}
.conveyor-viewport {
  overflow-x: auto;
  overscroll-behavior-x: contain;
  scrollbar-width: none;
  position: relative;
}
.conveyor-viewport::-webkit-scrollbar {
  display: none;
}
.conveyor-track {
  display: flex;
  gap: 16px;
  width: max-content;
}
.conveyor-queue {
  display: flex;
  gap: 16px;
  list-style: none;
  padding: 0;
  margin: 0;
}
.conveyor-card {
  width: 276px;
  flex: 0 0 276px;
  padding: 18px;
  box-sizing: border-box;
  border: 1px solid var(--color-border);
  border-radius: 14px;
  background: var(--color-background);
}
.conveyor-card-top {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  font-weight: 700;
}
.conveyor-kind {
  color: var(--color-primary);
}
.conveyor-status,
.conveyor-muted,
.conveyor-updated {
  color: var(--color-muted-foreground);
  font-size: 13px;
}
.conveyor-title {
  display: block;
  width: 100%;
  text-align: left;
  border: 0;
  padding: 10px 0;
  background: transparent;
  font-weight: 700 !important;
  font-size: 17px !important;
  overflow-wrap: anywhere;
  line-height: 1.55;
}
.conveyor-remaining {
  display: block;
  color: var(--color-primary);
  font-size: 24px;
  line-height: 1.5;
  margin: 4px 0;
}
.is-overdue .conveyor-remaining,
.is-overdue .conveyor-status {
  color: var(--color-danger, var(--color-destructive));
}
.conveyor-date {
  font-size: 13px;
  line-height: 1.8;
  margin: 8px 0 16px;
  color: var(--color-muted-foreground);
}
.conveyor-members {
  border-top: 1px solid var(--color-border);
  padding-top: 12px;
}
.conveyor-member-heading {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: 13px;
  font-weight: 700;
  margin: 0 0 8px;
}
.conveyor-member-list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.conveyor-member-list li {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 14px;
  padding: 4px 0;
  overflow-wrap: anywhere;
}
.conveyor-member-list small {
  font-size: 12px;
  color: var(--color-muted-foreground);
  text-align: right;
}
.conveyor-member-list.all-members {
  max-height: 160px;
  overflow-y: auto;
}
.conveyor-member-toggle {
  border: 0;
  background: transparent;
  text-decoration: underline;
  padding: 8px 0;
  font-size: 13px !important;
}
.conveyor-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-top: 16px;
}
.conveyor-controls {
  display: flex;
  gap: 8px;
}
.conveyor-controls button {
  display: inline-flex;
  justify-content: center;
  align-items: center;
  gap: 6px;
}
.conveyor-updated {
  width: 100%;
}
.deadline-conveyor a {
  display: inline-flex;
  align-items: center;
}
.few-entries .conveyor-track,
.few-entries .conveyor-queue {
  width: 100%;
}
.few-entries .conveyor-card {
  flex: 1 1 0;
  width: 0;
  min-width: 0;
}
@media (max-width: 600px) {
  .conveyor-header h2 {
    font-size: 20px;
  }
  .conveyor-card {
    width: min(276px, 72vw);
    flex-basis: min(276px, 72vw);
  }
  .few-entries .conveyor-queue {
    flex-direction: column;
  }
  .few-entries .conveyor-card {
    width: 100%;
    flex: none;
  }
}
@media (prefers-reduced-motion: reduce) {
  .deadline-conveyor * {
    scroll-behavior: auto;
  }
}
</style>

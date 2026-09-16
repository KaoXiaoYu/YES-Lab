<script setup>
import { ArrowRight } from '@lucide/vue'
import { computed, nextTick, onMounted, ref } from 'vue'

const props = defineProps({
  dailyPoints: { type: Array, default: () => [] },
  totalPoints: { type: Number, default: 0 },
})

const grid = ref(null)
const scroll = ref(null)
const tooltip = ref({ visible: false, x: 0, y: 0, pointerOffset: 0, text: '' })
const today = startOfDay(new Date())
const todayKey = dateKey(today)
const selectedDate = ref(todayKey)
const pointMap = computed(() => new Map(props.dailyPoints.map((item) => [item.date, Number(item.points) || 0])))
const positiveDays = computed(() => props.dailyPoints.filter((item) => Number(item.points) > 0))
const yearPoints = computed(() => props.dailyPoints.reduce((total, item) => total + Number(item.points || 0), 0))
const monthPrefix = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}`
const monthDays = computed(() => positiveDays.value.filter((item) => item.date.startsWith(monthPrefix)))
const monthPoints = computed(() => monthDays.value.reduce((total, item) => total + Number(item.points || 0), 0))
const longestStreak = computed(() => {
  const dates = positiveDays.value.map((item) => Date.parse(`${item.date}T00:00:00Z`)).sort((a, b) => a - b)
  let longest = 0
  let current = 0
  let previous = null
  for (const date of dates) {
    current = previous !== null && date - previous === 86_400_000 ? current + 1 : 1
    longest = Math.max(longest, current)
    previous = date
  }
  return longest
})
const weeks = computed(() => {
  const start = new Date(today)
  start.setDate(start.getDate() - start.getDay() - 52 * 7)
  return Array.from({ length: 53 }, (_, weekIndex) => {
    const days = Array.from({ length: 7 }, (_, dayIndex) => {
      const date = new Date(start)
      date.setDate(start.getDate() + weekIndex * 7 + dayIndex)
      const key = dateKey(date)
      const points = pointMap.value.get(key) || 0
      return { key, date, points, future: date > today, level: intensity(points) }
    })
    const monthStart = days.find((day) => day.date.getDate() === 1)
    return {
      key: days[0].key,
      days,
      monthLabel:
        weekIndex === 0 || monthStart
          ? new Intl.DateTimeFormat('zh-CN', { month: 'short' }).format(monthStart?.date || days[0].date)
          : '',
    }
  })
})
const days = computed(() => weeks.value.flatMap((week) => week.days))
onMounted(() =>
  nextTick(() => {
    if (scroll.value) scroll.value.scrollLeft = scroll.value.scrollWidth
  }),
)

function intensity(points) {
  if (points <= 0) return 0
  if (points < 20) return 1
  if (points < 50) return 2
  if (points < 100) return 3
  return 4
}

function startOfDay(value) {
  return new Date(value.getFullYear(), value.getMonth(), value.getDate())
}

function dateKey(value) {
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function formatDate(value) {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'short',
  }).format(new Date(`${value}T00:00:00`))
}

function selectDay(day) {
  if (!day.future) selectedDate.value = day.key
}

function showTooltip(day, event) {
  if (day.future) return
  const target = event.currentTarget.getBoundingClientRect()
  const anchorX = target.left + target.width / 2
  const safeHalfWidth = Math.min(120, window.innerWidth / 2 - 12)
  const x = Math.max(safeHalfWidth, Math.min(window.innerWidth - safeHalfWidth, anchorX))
  tooltip.value = {
    visible: true,
    x,
    y: target.top - 12,
    pointerOffset: anchorX - x,
    text: `${day.points} 积分 · ${formatDate(day.key)}`,
  }
}

function hideTooltip() {
  tooltip.value.visible = false
}

async function moveFocus(index, offset) {
  const targetIndex = Math.max(0, Math.min(days.value.length - 1, index + offset))
  const target = days.value[targetIndex]
  if (target.future) return
  selectedDate.value = target.key
  await nextTick()
  grid.value?.querySelector(`[data-day-index="${targetIndex}"]`)?.focus()
}

function handleKeydown(event, index) {
  const offsets = { ArrowLeft: -7, ArrowRight: 7, ArrowUp: -1, ArrowDown: 1 }
  if (event.key in offsets) {
    event.preventDefault()
    moveFocus(index, offsets[event.key])
  } else if (event.key === 'Home') {
    event.preventDefault()
    const todayIndex = days.value.findIndex((day) => day.key === todayKey)
    moveFocus(todayIndex, 0)
  }
}
</script>

<template>
  <section class="points-heatmap-card" aria-labelledby="points-heatmap-title">
    <header class="points-heatmap-heading">
      <h2 id="points-heatmap-title">积分总览</h2>
    </header>

    <div class="points-heatmap-body">
      <div ref="scroll" class="points-heatmap-scroll" aria-label="过去一年积分活动，可使用方向键移动日期">
        <div class="points-heatmap-chart">
          <div class="points-heatmap-months" aria-hidden="true">
            <span v-for="week in weeks" :key="week.key">{{ week.monthLabel }}</span>
          </div>
          <div class="points-heatmap-calendar">
            <div class="points-heatmap-weekdays" aria-hidden="true"><span>一</span><span>三</span><span>五</span></div>
            <div ref="grid" class="points-heatmap-grid" role="grid" aria-readonly="true">
              <template v-for="(day, index) in days" :key="day.key">
                <span v-if="day.future" class="points-heatmap-future" aria-hidden="true"></span>
                <button
                  v-else
                  type="button"
                  role="gridcell"
                  :data-day-index="index"
                  :data-level="day.level"
                  :class="{ today: day.key === todayKey }"
                  :tabindex="selectedDate === day.key ? 0 : -1"
                  :aria-label="`${formatDate(day.key)}，获得 ${day.points} 分`"
                  @mouseenter="showTooltip(day, $event)"
                  @mouseleave="hideTooltip"
                  @focus="(selectDay(day), showTooltip(day, $event))"
                  @blur="hideTooltip"
                  @click="(selectDay(day), showTooltip(day, $event))"
                  @keydown="handleKeydown($event, index)"
                ></button>
              </template>
            </div>
          </div>
        </div>
      </div>

      <div class="points-heatmap-stats" aria-label="积分统计">
        <p>
          <strong>{{ totalPoints }}</strong
          ><span>总积分</span><small>历史累计</small>
        </p>
        <p>
          <strong>{{ yearPoints }}</strong
          ><span>积分</span><small>过去一年</small>
        </p>
        <p>
          <strong>{{ monthPoints }}</strong
          ><span>积分</span><small>本月累计</small>
        </p>
        <p>
          <strong>{{ positiveDays.length }}</strong
          ><span>天</span><small>过去一年活跃</small>
        </p>
        <p>
          <strong>{{ longestStreak }}</strong
          ><span>天</span><small>最长连续活跃</small>
        </p>
        <p>
          <strong>{{ monthDays.length }}</strong
          ><span>天</span><small>本月活跃</small>
        </p>
      </div>

      <footer>
        <RouterLink to="/points">查看积分榜<ArrowRight :size="15" aria-hidden="true" /></RouterLink>
        <div class="points-heatmap-legend" aria-label="颜色深浅图例：颜色越深，积分越高">
          <span>少</span><i v-for="level in [0, 1, 2, 3, 4]" :key="level" :data-level="level"></i><span>多</span>
        </div>
      </footer>
    </div>
  </section>

  <Teleport to="body">
    <div
      v-if="tooltip.visible"
      class="points-heatmap-tooltip"
      role="tooltip"
      :style="{
        left: `${tooltip.x}px`,
        top: `${tooltip.y}px`,
        '--points-tooltip-pointer-offset': `${tooltip.pointerOffset}px`,
      }"
    >
      {{ tooltip.text }}
    </div>
  </Teleport>
</template>

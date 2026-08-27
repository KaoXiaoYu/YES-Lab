<script setup>
import { CalendarDays, Clock3 } from 'lucide-vue-next'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { authState, getOwnCompetitionCountdown } from '../services/authApi'
import { fetchPublicCompetitionCountdown } from '../services/publicApi'

const item = ref(null)
const todayKey = ref(labTodayKey())
let refreshTimer
let requestVersion = 0

const isLabMember = computed(() => ['TEACHER', 'CORE_STUDENT', 'MEMBER'].includes(authState.account?.role))
const daysRemaining = computed(() => {
  if (!item.value?.date) return null
  return Math.max(0, dayOrdinal(item.value.date) - dayOrdinal(todayKey.value))
})
const formattedDate = computed(() => {
  if (!item.value?.date) return ''
  return new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'short' }).format(parseLocalDate(item.value.date))
})
const destination = computed(() => isLabMember.value ? '/competitions' : null)

watch(
  () => `${authState.ready}:${authState.account?.role || 'ANONYMOUS'}:${authState.account?.username || ''}`,
  loadCountdown,
  { immediate: true },
)

refreshTimer = window.setInterval(() => {
  const nextToday = labTodayKey()
  if (nextToday !== todayKey.value) {
    todayKey.value = nextToday
    loadCountdown()
  }
}, 60_000)
window.addEventListener('yeslab:competitions-changed', loadCountdown)

onBeforeUnmount(() => {
  window.clearInterval(refreshTimer)
  window.removeEventListener('yeslab:competitions-changed', loadCountdown)
})

async function loadCountdown() {
  if (!authState.ready) return
  const version = ++requestVersion
  try {
    let result = null
    if (isLabMember.value) result = await getOwnCompetitionCountdown()
    else if (!authState.account) result = await fetchPublicCompetitionCountdown()
    if (version === requestVersion) item.value = result
  } catch {
    if (version === requestVersion) item.value = null
  }
}

function parseLocalDate(value) {
  const [year, month, day] = value.split('-').map(Number)
  return new Date(year, month - 1, day)
}

function dayOrdinal(value) {
  const [year, month, day] = value.split('-').map(Number)
  return Math.floor(Date.UTC(year, month - 1, day) / 86_400_000)
}

function labTodayKey() {
  const parts = new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(new Date())
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]))
  const year = values.year
  const month = values.month
  const day = values.day
  return `${year}-${month}-${day}`
}
</script>

<template>
  <Transition name="countdown-pop">
    <aside v-if="item && daysRemaining !== null" class="competition-countdown" aria-label="最近比赛倒计时">
      <div class="competition-countdown-mark" aria-hidden="true"><Clock3 :size="21" /></div>
      <div class="competition-countdown-copy">
        <p>{{ isLabMember ? '我的最近比赛' : '最近的比赛' }} · {{ item.stage }}</p>
        <strong>{{ item.name }}</strong>
        <span><CalendarDays :size="14" aria-hidden="true" />{{ formattedDate }}<small v-if="item.track">{{ item.track }}</small></span>
      </div>
      <div class="competition-countdown-days"><b>{{ daysRemaining }}</b><span>天</span></div>
      <RouterLink v-if="destination" :to="destination" aria-label="进入我的比赛列表">查看比赛</RouterLink>
    </aside>
  </Transition>
</template>

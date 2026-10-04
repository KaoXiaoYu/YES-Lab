<script setup>
import { ChevronDown, ChevronUp, Users } from '@lucide/vue'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { authState, getMemberRankingPage } from '../services/authApi'
const props = defineProps({
  boards: { type: Object, required: true },
  totalCount: { type: Number, default: 0 },
  updatedAt: { type: String, default: '' },
  loaded: Boolean,
  error: { type: String, default: '' },
})
const emit = defineEmits(['retry'])
const router = useRouter()
const element = ref(null)
const list = ref(null)
const header = ref(null)
const footer = ref(null)
const active = ref('总榜')
const expanded = ref(false)
const previewCount = ref(3)
const full = ref([])
const page = ref(0)
const count = ref(0)
const busy = ref(false)
const fullError = ref('')
const hint = ref('')
const canRead = computed(() => ['TEACHER', 'CORE_STUDENT', 'MEMBER'].includes(authState.account?.role))
const rows = computed(() =>
  expanded.value ? full.value : (props.boards[active.value] || []).slice(0, previewCount.value),
)
let observer
let frame
let version = 0
function measure() {
  cancelAnimationFrame(frame)
  frame = requestAnimationFrame(() => {
    const panel = element.value
    if (!panel || expanded.value) return
    const neighbor = panel.parentElement.querySelector('.member-showcase')
    const sideBySide =
      neighbor && Math.abs(neighbor.getBoundingClientRect().top - panel.getBoundingClientRect().top) < 8
    const budget = sideBySide ? Math.max(320, neighbor.getBoundingClientRect().height) : 470
    const padding = parseFloat(getComputedStyle(panel).paddingTop) + parseFloat(getComputedStyle(panel).paddingBottom)
    const fixed = header.value?.getBoundingClientRect().height || 0
    const bottom = footer.value?.getBoundingClientRect().height || 0
    const heights = [...(list.value?.children || [])].map((row) => row.getBoundingClientRect().height)
    const rowHeight = Math.max(67, ...heights)
    previewCount.value = Math.max(1, Math.min(6, Math.floor((budget - padding - fixed - bottom - 12) / rowHeight)))
  })
}
async function loadFull() {
  const ticket = ++version
  busy.value = true
  fullError.value = ''
  try {
    const response = await getMemberRankingPage(
      { 总榜: 'TOTAL', 月榜: 'MONTH', 年榜: 'YEAR' }[active.value],
      page.value,
    )
    if (ticket !== version || !expanded.value || !canRead.value) return
    full.value = response.entries
    count.value = response.totalCount
  } catch (error) {
    if (ticket === version) fullError.value = error.message
  } finally {
    if (ticket === version) busy.value = false
  }
}
async function toggle() {
  hint.value = ''
  if (!canRead.value) {
    hint.value = authState.account ? '完整榜单仅对实验室成员开放。' : '登录成员账号后可查看完整榜单。'
    return
  }
  expanded.value = !expanded.value
  full.value = []
  page.value = 0
  fullError.value = ''
  if (expanded.value) await loadFull()
  else {
    ++version
    busy.value = false
    await nextTick()
    measure()
  }
}
watch(active, async () => {
  page.value = 0
  full.value = []
  if (expanded.value) await loadFull()
  else {
    await nextTick()
    measure()
  }
})
watch(page, () => {
  if (expanded.value) loadFull()
})
watch(
  () => props.updatedAt,
  async () => {
    if (expanded.value) await loadFull()
    await nextTick()
    measure()
  },
)
watch(canRead, async (allowed) => {
  if (!allowed) {
    ++version
    expanded.value = false
    full.value = []
    busy.value = false
    fullError.value = ''
    await nextTick()
    measure()
  }
})
onMounted(async () => {
  await nextTick()
  observer = new ResizeObserver(measure)
  for (const target of [
    element.value?.parentElement,
    element.value?.parentElement.querySelector('.member-showcase'),
    header.value,
    footer.value,
    list.value,
  ])
    if (target) observer.observe(target)
  window.addEventListener('resize', measure)
  measure()
})
onBeforeUnmount(() => {
  ++version
  observer?.disconnect()
  cancelAnimationFrame(frame)
  window.removeEventListener('resize', measure)
})
</script>
<template>
  <aside ref="element" class="leaderboard compact-leaderboard" data-reveal>
    <div ref="header">
      <div class="leaderboard-head">
        <span><Users :size="19" aria-hidden="true" />成员积分榜单</span><small>每日刷新</small>
      </div>
      <div class="ranking-tabs">
        <button
          v-for="tab in ['总榜', '月榜', '年榜']"
          :key="tab"
          :class="{ active: active === tab }"
          :aria-pressed="active === tab"
          @click="active = tab"
        >
          {{ tab }}
        </button>
      </div>
      <p v-if="error" class="ranking-state" role="alert">
        {{ error }} <button type="button" @click="emit('retry')">重试</button>
      </p>
    </div>
    <div id="home-full-leaderboard" class="compact-ranking-body" :class="{ expanded }" :aria-busy="busy">
      <p v-if="busy || !loaded" role="status">正在读取积分榜单…</p>
      <p v-else-if="fullError" role="alert">{{ fullError }} <button type="button" @click="loadFull">重试</button></p>
      <p v-else-if="!rows.length" class="ranking-state">暂无参榜成员。</p>
      <ol ref="list">
        <li v-for="member in rows" :key="member.memberSlug">
          <RouterLink :to="`/members/${member.memberSlug}`" :aria-label="`查看${member.name}的公开主页`"
            ><span>{{ member.rank }}</span>
            <div>
              <strong>{{ member.name }}</strong
              ><small>{{ member.primaryTag }}</small>
            </div>
            <b>{{ member.points }}</b></RouterLink
          >
        </li>
      </ol>
      <div v-if="expanded && count > 25" class="ledger-pagination">
        <button type="button" :disabled="busy || page === 0" @click="page--">上一页</button
        ><span>{{ page + 1 }} / {{ Math.ceil(count / 25) }}</span
        ><button type="button" :disabled="busy || (page + 1) * 25 >= count" @click="page++">下一页</button>
      </div>
    </div>
    <footer ref="footer" class="compact-ranking-footer">
      <p v-if="updatedAt" class="ranking-updated">
        更新于
        {{
          new Intl.DateTimeFormat('zh-CN', {
            timeZone: 'Asia/Shanghai',
            month: 'numeric',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
          }).format(new Date(updatedAt))
        }}
        · 共 {{ totalCount }} 人
      </p>
      <button
        type="button"
        class="disclosure-button"
        :aria-expanded="expanded"
        aria-controls="home-full-leaderboard"
        @click="toggle"
      >
        <ChevronUp v-if="expanded" :size="18" aria-hidden="true" /><ChevronDown
          v-else
          :size="18"
          aria-hidden="true"
        />{{ expanded ? '收起榜单' : '展开完整榜单' }}
      </button>
      <p v-if="hint" role="status" class="member-access-hint">
        {{ hint }}
        <button v-if="!authState.account" type="button" @click="router.push('/login?redirect=/')">成员登录</button>
      </p>
      <RouterLink v-if="expanded" class="compact-ranking-link" to="/points">进入积分榜</RouterLink>
    </footer>
  </aside>
</template>

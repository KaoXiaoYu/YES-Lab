<script setup>
import { animate } from 'motion'
import { CornerDownLeft, FileText, FolderKanban, Gift, ListChecks, Search, UserRound } from '@lucide/vue'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { authState, getBountyBoard, getMyTasks, listProjects } from '../services/authApi'
import { fetchPublicMemberDirectory } from '../services/publicApi'
import { closeCommandPalette, commandPaletteState, openCommandPalette } from '../services/commandPalette'

const router = useRouter()
const query = ref('')
const active = ref(0)
const input = ref(null)
const panel = ref(null)
const listbox = ref(null)
const records = ref({ tasks: [], bounties: [], projects: [], members: [] })
const loading = ref(false)
let loadedAt = 0
let returnFocus = null

const role = computed(() => authState.account?.role)
const isMember = computed(() => role.value && role.value !== 'VISITOR')

const pages = computed(() => {
  const list = [{ title: '公开首页', to: '/', keywords: 'home 首页 主页' }]
  if (isMember.value) {
    list.push(
      { title: '今日', to: '/today', keywords: 'today 待办 截止 dashboard' },
      { title: '个人主页', to: '/profile', keywords: 'profile 资料 名片' },
      { title: '编辑个人资料', to: '/profile/edit', keywords: 'edit 资料 头像' },
      { title: '我的任务', to: '/tasks', keywords: 'tasks 任务' },
      { title: '悬赏榜', to: '/bounties', keywords: 'bounty 悬赏 奖金' },
      { title: '项目团队', to: '/projects', keywords: 'projects 项目' },
      { title: '比赛管理', to: '/competitions', keywords: 'competition 比赛 竞赛 成果' },
      { title: '积分榜', to: '/points', keywords: 'points 积分 排行' },
      { title: '实验室基金', to: '/fund', keywords: 'fund 基金 经费' },
    )
  }
  if (role.value === 'VISITOR') list.push({ title: '我的报名', to: '/application', keywords: 'application 报名 招新' })
  list.push({ title: '讨论板', to: '/discussions', keywords: 'discussion 讨论 帖子' })
  if (authState.account) list.push({ title: '站内消息', to: '/inbox', keywords: 'inbox 消息 通知' })
  if (authState.account?.systemAdmin) {
    list.push(
      { title: '后台总览', to: '/admin', keywords: 'admin 后台 待处理 overview' },
      { title: '成员管理', to: '/admin/members', keywords: 'admin members 成员' },
      { title: '招新管理', to: '/admin/recruitment', keywords: 'admin recruitment 招新 面试' },
      { title: '任务管理', to: '/admin/tasks', keywords: 'admin tasks 任务 发放' },
      { title: '悬赏管理', to: '/admin/bounties', keywords: 'admin bounty 悬赏' },
      { title: '积分管理', to: '/admin/points', keywords: 'admin points 积分 发放' },
      { title: '成果管理', to: '/admin/achievements', keywords: 'admin achievements 成果 新闻 审核' },
      { title: '主页编辑', to: '/admin/homepage', keywords: 'admin homepage 主页 编辑' },
    )
  }
  return list.map((item) => ({ ...item, group: '页面', icon: FileText }))
})

const dataItems = computed(() => [
  ...records.value.tasks.map((task) => ({
    group: '我的任务',
    icon: ListChecks,
    title: task.title,
    subtitle: task.endDate ? `截止 ${String(task.endDate).slice(0, 10)}` : '',
    to: `/tasks/${task.assignmentId}`,
  })),
  ...records.value.bounties.map((bounty) => ({
    group: '悬赏',
    icon: Gift,
    title: bounty.title,
    subtitle: bounty.prize?.points > 0 ? `+${bounty.prize.points} 积分` : '',
    to: `/bounties/${bounty.taskId}`,
  })),
  ...records.value.projects.map((project) => ({
    group: '项目',
    icon: FolderKanban,
    title: project.projectName,
    subtitle: project.teamName || '',
    to: `/projects/${project.id}`,
  })),
  ...records.value.members.map((member) => ({
    group: '成员',
    icon: UserRound,
    title: member.name,
    subtitle: [member.grade, member.major].filter(Boolean).join(' · '),
    keywords: (member.skillTags || []).join(' '),
    to: `/members/${member.id}`,
  })),
])

const results = computed(() => {
  const terms = query.value.trim().toLocaleLowerCase().split(/\s+/).filter(Boolean)
  const matches = (item) => {
    const haystack = `${item.title} ${item.subtitle || ''} ${item.keywords || ''}`.toLocaleLowerCase()
    return terms.every((term) => haystack.includes(term))
  }
  const source = terms.length ? [...pages.value, ...dataItems.value] : pages.value.slice(0, 8)
  const grouped = new Map()
  for (const item of source) {
    if (!item.title || (terms.length && !matches(item))) continue
    const bucket = grouped.get(item.group) || []
    if (bucket.length < 6) bucket.push(item)
    grouped.set(item.group, bucket)
  }
  let index = 0
  return [...grouped.entries()].map(([group, items]) => ({
    group,
    items: items.map((item) => ({ ...item, index: index++ })),
  }))
})
const flat = computed(() => results.value.flatMap((section) => section.items))

watch(query, () => (active.value = 0))

watch(
  () => commandPaletteState.open,
  async (open) => {
    if (!open) {
      document.body.classList.remove('command-palette-open')
      if (returnFocus?.isConnected) returnFocus.focus()
      returnFocus = null
      return
    }
    returnFocus = document.activeElement
    query.value = ''
    active.value = 0
    document.body.classList.add('command-palette-open')
    await nextTick()
    input.value?.focus()
    if (panel.value && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      animate(
        panel.value,
        { opacity: [0, 1], transform: ['translateY(-8px) scale(0.97)', 'translateY(0) scale(1)'] },
        {
          type: 'spring',
          stiffness: 520,
          damping: 32,
        },
      )
    }
    loadRecords()
  },
)

async function loadRecords() {
  if (loading.value || Date.now() - loadedAt < 60_000) return
  loading.value = true
  const settle = (promise) => promise.then((value) => (Array.isArray(value) ? value : [])).catch(() => [])
  const [tasks, bounties, projects, members] = await Promise.all([
    isMember.value ? settle(getMyTasks()) : [],
    isMember.value ? settle(getBountyBoard()) : [],
    isMember.value ? settle(listProjects()) : [],
    settle(fetchPublicMemberDirectory()),
  ])
  records.value = { tasks, bounties, projects, members }
  loadedAt = Date.now()
  loading.value = false
}

function go(item) {
  if (!item) return
  closeCommandPalette()
  router.push(item.to)
}

function move(step) {
  if (!flat.value.length) return
  active.value = (active.value + step + flat.value.length) % flat.value.length
  nextTick(() => listbox.value?.querySelector('[aria-selected="true"]')?.scrollIntoView({ block: 'nearest' }))
}

function onInputKeydown(event) {
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    move(1)
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    move(-1)
  } else if (event.key === 'Enter') {
    event.preventDefault()
    go(flat.value[active.value])
  } else if (event.key === 'Escape') {
    event.preventDefault()
    closeCommandPalette()
  } else if (event.key === 'Tab') {
    event.preventDefault()
    move(event.shiftKey ? -1 : 1)
  }
}

function onGlobalKeydown(event) {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    if (commandPaletteState.open) closeCommandPalette()
    else openCommandPalette()
  }
}

onMounted(() => window.addEventListener('keydown', onGlobalKeydown))
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onGlobalKeydown)
  document.body.classList.remove('command-palette-open')
})
</script>

<template>
  <Transition name="fade">
    <div v-if="commandPaletteState.open" class="ui-palette-scrim" @mousedown.self="closeCommandPalette">
      <div ref="panel" class="ui-palette" role="dialog" aria-modal="true" aria-label="搜索与跳转">
        <label class="ui-palette-search">
          <Search :size="18" aria-hidden="true" />
          <input
            ref="input"
            v-model="query"
            type="text"
            placeholder="搜索页面、任务、悬赏、项目或成员…"
            role="combobox"
            aria-expanded="true"
            aria-controls="command-palette-results"
            :aria-activedescendant="flat.length ? `command-item-${active}` : undefined"
            autocomplete="off"
            spellcheck="false"
            @keydown="onInputKeydown"
          />
          <kbd>Esc</kbd>
        </label>
        <div id="command-palette-results" ref="listbox" class="ui-palette-results" role="listbox" aria-label="结果">
          <section v-for="section in results" :key="section.group" role="group" :aria-label="section.group">
            <h2>{{ section.group }}</h2>
            <button
              v-for="item in section.items"
              :id="`command-item-${item.index}`"
              :key="`${section.group}-${item.to}`"
              type="button"
              role="option"
              tabindex="-1"
              :aria-selected="item.index === active"
              @mousemove="active = item.index"
              @click="go(item)"
            >
              <component :is="item.icon" :size="17" aria-hidden="true" />
              <span
                ><strong>{{ item.title }}</strong
                ><small v-if="item.subtitle">{{ item.subtitle }}</small></span
              >
              <CornerDownLeft v-if="item.index === active" :size="15" aria-hidden="true" />
            </button>
          </section>
          <p v-if="!flat.length" class="ui-palette-empty">
            {{ loading ? '正在载入任务、悬赏与成员…' : `没有找到「${query}」相关的内容。` }}
          </p>
        </div>
        <footer class="ui-palette-foot">
          <span><kbd>↑</kbd><kbd>↓</kbd> 选择</span><span><kbd>Enter</kbd> 打开</span
          ><span v-if="loading">正在载入数据…</span>
        </footer>
      </div>
    </div>
  </Transition>
</template>

<script setup>
import {
  ArrowDownRight, ArrowRight, ArrowUpRight, BookOpen, ExternalLink,
  Github, Menu, Users, X,
} from 'lucide-vue-next'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { authState } from '../services/authApi'
import { fetchPublicHome } from '../services/publicApi'

const menuOpen = ref(false)
const activeRanking = ref('总榜')
const activeProjectFilter = ref('全部')
const selectedProject = ref(null)
const modalClose = ref(null)
const homepageReady = ref(false)
let revealObserver
let rankingsRefreshTimer
const publicHomeSnapshotKey = 'yeslab_public_home_snapshot_v1'
const publicHomeSnapshotMaxAge = 7 * 24 * 60 * 60 * 1000

const defaultProjects = [
  {
    number: '01', category: '无人机', title: '无人机自主飞行\n与环境感知',
    summary: '面向复杂环境，探索飞行平台的自主感知、定位、规划与控制。', status: '研究中',
    lead: '范桌轩大王', members: '待补充', tech: ['自主飞行', '环境感知', '运动规划'],
    result: '研究方向建设中，后续将公开阶段性原型、比赛记录与技术文档。',
  },
  {
    number: '02', category: '空地协同', title: '无人机 × 机器狗\n空地协同系统',
    summary: '连接空中视野与地面行动能力，研究多智能体协同感知和任务执行。', status: '重点方向',
    lead: '范桌轩大王', members: '待补充', tech: ['协同感知', '任务分配', '异构机器人'],
    result: '围绕无人机与机器狗协同开展系统设计、算法验证与工程实践。',
  },
  {
    number: '03', category: '具身智能', title: '具身智能\n学习与实践平台',
    summary: '让智能体在真实环境中感知、理解并行动，推动算法走出屏幕。', status: '方向建设',
    lead: '范桌轩大王', members: '待补充', tech: ['多模态感知', '智能决策', '机器人学习'],
    result: '面向校内学生建设从基础训练到项目实战的人才培养路径。',
  },
]

const defaultMembers = [
  { initials: 'FZ', name: '范桌轩大王', role: '2023 · 计算机科学', tags: ['无人机系统', '工程实现'], points: 2480, rank: 1, core: true },
  { initials: 'YX', name: '范桌轩大王', role: '2022 · 人工智能', tags: ['计算机视觉', '具身智能'], points: 2210, rank: 2, core: true },
  { initials: 'LC', name: '范桌轩大王', role: '2024 · 电子信息', tags: ['嵌入式', '机器人控制'], points: 1980, rank: 3, core: true },
  { initials: 'WQ', name: '范桌轩大王', role: '2023 · 自动化', tags: ['多智能体', '系统设计'], points: 1750, rank: 4, core: false },
]

const defaultAdvisor = {
  initials: 'TH', name: '汤洪大王', role: 'YES Lab 指导老师',
  description: '负责实验室研究方向、项目实践与人才培养指导。',
  tags: ['研究指导', '人才培养'],
}

const defaultRankingData = {
  总榜: [2480, 2210, 1980, 1750], 月榜: [380, 350, 290, 265], 年榜: [1240, 1180, 960, 845],
  无人机: [920, 860, 740, 620], 空地协同: [880, 810, 790, 650], 具身智能: [850, 820, 760, 690],
}

const defaultUpdates = [
  { date: '荣誉', type: '竞赛成果', title: 'YES Lab 获得计算机设计大赛全国二等奖' },
  { date: '荣誉', type: '竞赛成果', title: '江西省智能机器人大赛飞行巡航定点赛道省赛二等奖' },
  { date: '荣誉', type: '竞赛成果', title: '全国智能汽车大赛平衡轮腿组华东赛赛区三等奖' },
  { date: '方向', type: '研究动态', title: '推进无人机与机器狗空地协同系统研究' },
  { date: '伙伴', type: '企业支持', title: 'CUAV 成为 YES Lab 企业赞助伙伴' },
]

const defaultAwards = [
  { competition: '计算机设计大赛', category: '全国赛', level: '全国', prize: '二等奖' },
  { competition: '江西省智能机器人大赛', category: '飞行巡航定点赛道', level: '省赛', prize: '二等奖' },
  { competition: '全国智能汽车大赛', category: '平衡轮腿组 · 华东赛', level: '赛区', prize: '三等奖' },
]

const competitionLevelLabels = {
  SCHOOL: '校级', PROVINCIAL: '省级', REGIONAL: '赛区', NATIONAL: '国家级', INTERNATIONAL: '国际级', OTHER: '其他',
}

const defaultSponsors = [{
  name: 'CUAV', type: '企业赞助伙伴',
  description: '感谢 CUAV 对 YES Lab 无人系统研究、工程实践与人才培养的支持。',
  focus: ['无人机系统', '工程实践', '人才培养'], logoUrl: '/sponsors/cuav-logo.jpg', websiteUrl: 'https://www.cuav.net/',
}]

const defaultHomepageContent = {
  profile: {
    heroEyebrow: 'YES LAB · ROBOTICS RESEARCH / 2026', heroTitle: '让无人设备带上、\n你的', heroAccent: '眼眸',
    primaryActionLabel: '浏览研究项目', secondaryActionLabel: '了解合作伙伴',
    researchDirectionItems: [
      { name: '无人机', url: '#projects' },
      { name: '空地协同', url: '#projects' },
      { name: '具身智能', url: '#projects' },
    ],
  },
  sections: {
    projects: { eyebrow: '01 / SELECTED RESEARCH', title: '研究与工程实践', description: '从算法、硬件到系统集成，我们以可运行、可验证的真实项目建立研究能力。' },
    about: {
      eyebrow: '02 / ABOUT', title: '把研究做成\n可以触碰的现场',
      paragraphOne: 'YES Lab 是一个处于起步阶段的实验室，主要研究无人机、无人机与机器狗空地协同、具身智能等方向。',
      paragraphTwo: '我们以竞赛与真实工程项目为牵引，为学校培养兼具算法、硬件和系统能力的复合型人才。',
      principles: ['面向真实场景', '强调应用实践', '培养工程人才'],
      featureEyebrow: 'HOW WE WORK', featureTitle: '从研究方向走向工程现场',
      features: [
        { title: '真实问题驱动', description: '从无人系统的真实任务出发，把研究目标拆解为可以验证的算法、硬件与系统方案。' },
        { title: '跨平台协同', description: '连接无人机、机器狗与具身智能平台，在异构系统协同中训练完整工程能力。' },
        { title: '项目制人才培养', description: '以竞赛和科研项目贯穿学习路径，让成员在实践、复盘和公开成果中持续成长。' },
      ],
    },
    members: { eyebrow: '03 / PEOPLE', title: '共同成长的研究者', description: '榜单每30s刷新' },
    partners: { eyebrow: '04 / PARTNERS', title: '赞助与合作伙伴', description: '感谢企业伙伴为无人系统研究、工程实践与人才培养提供支持。' },
    achievements: { eyebrow: '05 / ACHIEVEMENTS', title: '成果与外部报道', description: '新闻按发布日期自动排序' },
    contact: { eyebrow: '06 / CONNECT', title: '下一次探索，\n从这里开始。', description: '关注我们的研究、比赛和开源进展。' },
    footerText: '© 2026 YES Lab · INTELLIGENCE IN MOTION',
  },
  proofItems: [
    { label: '01 / AWARDS', value: '3 项奖项', detail: '全国 / 省赛 / 赛区' },
    { label: '02 / FOCUS', value: '3 个方向', detail: '无人系统与具身智能' },
    { label: '03 / PARTNER', value: 'CUAV', detail: '企业赞助伙伴' },
    { label: '04 / STATUS', value: '持续建设', detail: '开放、实践、成长' },
  ],
  externalLinks: [
    { platform: 'github', label: '开源仓库', url: 'https://github.com', enabled: true },
    { platform: 'bilibili', label: '哔哩哔哩', url: '', enabled: false },
    { platform: 'wechat', label: '微信公众号', url: '', enabled: false },
    { platform: 'douyin', label: '抖音', url: '', enabled: false },
  ],
}

const profile = ref({
  name: 'YES Lab', displayName: 'YES Lab 实验室', fullName: 'Yichun Embodied Science', slogan: '探索空地协同，培养未来工程人才',
  description: '一个面向无人系统与具身智能的初创实验室，以真实项目连接科研、竞赛与人才培养。',
  researchDirections: ['无人机', '空地协同', '具身智能'],
})
const projects = ref(defaultProjects)
const members = ref(defaultMembers)
const advisor = ref(defaultAdvisor)
const rankingData = ref(defaultRankingData)
const newsItems = ref(defaultUpdates)
const competitionResults = ref(defaultAwards.map((item, index) => ({ id: null, name: item.competition, track: item.category, awardName: item.prize, level: item.level, competitionDate: `历史成果 ${index + 1}` })))
const sponsors = ref(defaultSponsors)
const homepageContent = ref(defaultHomepageContent)

const projectFilters = computed(() => ['全部', ...new Set(projects.value.map((project) => project.category))])
const filteredProjects = computed(() => activeProjectFilter.value === '全部'
  ? projects.value
  : projects.value.filter((project) => project.category === activeProjectFilter.value))
const rankings = computed(() => members.value.map((member, index) => ({
  ...member, points: rankingData.value[activeRanking.value]?.[index] ?? member.points,
})).sort((a, b) => b.points - a.points))
const coreMembers = computed(() => {
  const designatedMembers = members.value.filter((member) => member.core)
  return designatedMembers.length ? designatedMembers : members.value.slice(0, 3)
})
const accountDestination = computed(() => authState.account?.role === 'VISITOR' ? '/application' : '/profile')
const accountName = computed(() => authState.account?.displayName || authState.account?.username || '')
const enabledExternalLinks = computed(() => (homepageContent.value.externalLinks || []).filter((link) => link.enabled && link.url))
const researchDirections = computed(() => {
  const configured = homepageContent.value.profile?.researchDirectionItems
  if (configured?.length) return configured
  return (profile.value.researchDirections || []).map((name) => ({ name, url: '' }))
})
const externalIcon = (platform) => ({ github: Github, wechat: BookOpen }[platform?.toLowerCase()] || ExternalLink)

const scrollTo = (id) => {
  menuOpen.value = false
  const target = document.querySelector(id)
  if (!target) return

  const headerHeight = document.querySelector('.site-header')?.getBoundingClientRect().height ?? 80
  const targetTop = target.getBoundingClientRect().top + window.scrollY - headerHeight
  window.scrollTo({ top: Math.max(0, targetTop), behavior: 'smooth' })
}

const handleDirectionClick = (event, url) => {
  if (!url?.startsWith('#')) return
  event.preventDefault()
  scrollTo(url)
}

const directionTarget = (url) => /^https?:\/\//i.test(url || '') ? '_blank' : undefined

const applyCoreHome = (home) => {
  if (!home) return
  profile.value = home.profile
  homepageContent.value = home.homepageContent || defaultHomepageContent
  sponsors.value = home.sponsors || []
}

const applyCompleteHome = (home) => {
  if (!home) return
  applyCoreHome(home)
  projects.value = home.projects
  members.value = home.members
  advisor.value = home.advisor || defaultAdvisor
  rankingData.value = home.rankingData
  newsItems.value = home.news?.length ? home.news : home.updates
  competitionResults.value = home.competitionResults?.length
    ? home.competitionResults
    : (home.awards || []).map((item, index) => ({ id: null, name: item.competition, track: item.category, awardName: item.prize, level: item.level, competitionDate: `历史成果 ${index + 1}` }))
}

const readPublicHomeSnapshot = () => {
  try {
    const snapshot = JSON.parse(localStorage.getItem(publicHomeSnapshotKey) || 'null')
    if (!snapshot?.home || !snapshot.savedAt || Date.now() - snapshot.savedAt > publicHomeSnapshotMaxAge) return null
    return snapshot.home
  } catch {
    return null
  }
}

const savePublicHomeSnapshot = (home) => {
  if (!home) return
  try {
    localStorage.setItem(publicHomeSnapshotKey, JSON.stringify({ savedAt: Date.now(), home }))
  } catch {
    // 浏览器禁用或存储空间不足时继续使用内置兜底，不影响页面访问。
  }
}

const syncPublicHome = async () => {
  const home = await fetchPublicHome(applyCoreHome)
  applyCompleteHome(home)
  savePublicHomeSnapshot(home)
  homepageReady.value = true
}

const cachedPublicHome = readPublicHomeSnapshot()
if (cachedPublicHome) {
  applyCompleteHome(cachedPublicHome)
  homepageReady.value = true
}

const handleKeydown = (event) => {
  if (event.key !== 'Escape') return
  selectedProject.value = null
  menuOpen.value = false
}

watch(selectedProject, async (project) => {
  document.body.classList.toggle('modal-open', Boolean(project))
  if (project) {
    await nextTick()
    modalClose.value?.focus()
  }
})

onMounted(async () => {
  document.addEventListener('keydown', handleKeydown)
  revealObserver = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (!entry.isIntersecting) return
      entry.target.classList.add('is-visible')
      revealObserver.unobserve(entry.target)
    })
  }, { threshold: 0.12 })
  document.querySelectorAll('[data-reveal]').forEach((element) => revealObserver.observe(element))

  await syncPublicHome()
  rankingsRefreshTimer = window.setInterval(syncPublicHome, 30_000)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleKeydown)
  document.body.classList.remove('modal-open')
  revealObserver?.disconnect()
  window.clearInterval(rankingsRefreshTimer)
})
</script>

<template>
  <main :class="['site-shell', { 'awaiting-home': !homepageReady }]">
    <div v-if="!homepageReady" class="home-bootstrap-state" role="status" aria-live="polite">
      <img src="/yes-lab-logo.png" alt="" width="900" height="506" />
      <p>正在同步 YES Lab 最新公开内容…</p>
    </div>
    <a class="skip-link" href="#top">跳到主要内容</a>
    <header class="site-header">
      <a class="brand" href="#top" aria-label="YES Lab 首页" @click.prevent="scrollTo('#top')">
        <img src="/yes-lab-logo.png" alt="YES Lab" width="900" height="506" />
        <span>PUBLIC SHOWCASE</span>
      </a>

      <nav :class="['top-nav', { open: menuOpen }]" aria-label="主导航">
        <button @click="scrollTo('#projects')">研究与成果</button>
        <button @click="scrollTo('#updates')">竞赛 / 新闻</button>
        <button @click="scrollTo('#about')">关于我们</button>
        <button @click="scrollTo('#members')">成员</button>
        <button @click="scrollTo('#partners')">赞助伙伴</button>
        <span class="nav-divider" aria-hidden="true"></span>
        <span class="future-link" aria-disabled="true">协作平台 <small>预留</small></span>
        <RouterLink v-if="authState.account" class="public-account-chip" :to="accountDestination" :aria-label="`进入${accountName}的成员页面`">
          <span class="public-account-avatar"><img v-if="authState.account.avatarUrl" :src="authState.account.avatarUrl" alt="" /><b v-else>{{ accountName.slice(0, 1) }}</b></span>
          <strong>{{ accountName }}</strong>
        </RouterLink>
        <span v-else class="auth-entry"><RouterLink to="/login">登录</RouterLink><i>/</i><RouterLink to="/register">注册</RouterLink></span>
      </nav>

      <button class="menu-button" :aria-expanded="menuOpen" :aria-label="menuOpen ? '关闭菜单' : '打开菜单'" @click="menuOpen = !menuOpen">
        <X v-if="menuOpen" :size="22" aria-hidden="true" /><Menu v-else :size="22" aria-hidden="true" />
      </button>
    </header>

    <section id="top" class="hero" tabindex="-1">
      <div class="hero-main" data-reveal>
        <div class="hero-intro">
          <p class="eyebrow">{{ homepageContent.profile.heroEyebrow }}</p>
          <h1><template v-for="(line, index) in homepageContent.profile.heroTitle.split('\n')" :key="`${line}-${index}`">{{ line }}<br v-if="index < homepageContent.profile.heroTitle.split('\n').length - 1" /></template><em>{{ homepageContent.profile.heroAccent }}</em></h1>
          <p class="hero-description">{{ profile.description }}</p>
          <div class="hero-actions">
            <button class="primary-action" @click="scrollTo('#projects')">{{ homepageContent.profile.primaryActionLabel }} <ArrowDownRight :size="19" aria-hidden="true" /></button>
            <button class="text-action" @click="scrollTo('#partners')">{{ homepageContent.profile.secondaryActionLabel }} <ArrowRight :size="18" aria-hidden="true" /></button>
          </div>
        </div>

        <div class="hero-identity">
          <span class="identity-index">Y / E / S</span>
          <img src="/yes-lab-logo.png" alt="YES Lab，Y 为蓝色、E 为黄色、S 为红色" width="900" height="506" />
          <p>{{ profile.fullName?.toUpperCase() || 'YICHUN EMBODIED SCIENCE' }}</p>
        </div>
      </div>

      <div class="hero-directory" data-reveal>
        <p>RESEARCH DIRECTORY</p>
        <ol><li v-for="(direction, index) in researchDirections" :key="`${direction.name}-${direction.url}`"><a v-if="direction.url" :href="direction.url" :target="directionTarget(direction.url)" :rel="directionTarget(direction.url) ? 'noopener noreferrer' : undefined" @click="handleDirectionClick($event, direction.url)"><span>0{{ index + 1 }}</span><strong>{{ direction.name }}</strong><ArrowUpRight :size="18" aria-hidden="true" /></a><div v-else><span>0{{ index + 1 }}</span><strong>{{ direction.name }}</strong></div></li></ol>
      </div>
    </section>

    <section class="proof-bar" aria-label="实验室成果概览">
      <div v-for="item in homepageContent.proofItems" :key="`${item.label}-${item.value}`"><span>{{ item.label }}</span><strong>{{ item.value }}</strong><small>{{ item.detail }}</small></div>
    </section>

    <section class="section projects-section">
      <header id="projects" class="section-header" data-reveal>
        <div><p class="section-index">{{ homepageContent.sections.projects.eyebrow }}</p><h2>{{ homepageContent.sections.projects.title }}</h2></div>
        <p>{{ homepageContent.sections.projects.description }}</p>
      </header>

      <div class="project-toolbar" data-reveal>
        <div class="filter-tabs" aria-label="按研究方向筛选项目">
          <button v-for="filter in projectFilters" :key="filter" :class="{ active: activeProjectFilter === filter }" :aria-pressed="activeProjectFilter === filter" @click="activeProjectFilter = filter">{{ filter }}</button>
        </div>
        <span>{{ String(filteredProjects.length).padStart(2, '0') }} PROJECTS</span>
      </div>

      <TransitionGroup name="project-list" tag="div" class="project-grid">
        <article v-for="project in filteredProjects" :key="project.number" class="project-card" role="button" tabindex="0" :aria-label="`查看${project.title.replace('\n', '')}项目详情`" @click="selectedProject = project" @keydown.enter="selectedProject = project" @keydown.space.prevent="selectedProject = project">
          <div class="project-card-head"><span>{{ project.number }}</span><small>{{ project.status }}</small></div>
          <div class="project-graphic" :class="[{ 'is-default': !project.coverImageUrl }, `graphic-${project.number}`]">
            <img :src="project.coverImageUrl || '/yes-lab-logo.png'" :alt="`${project.title.replace('\n', '')}项目主图`" loading="lazy" />
            <b>{{ project.category }}</b>
          </div>
          <div class="project-copy">
            <p>{{ project.category }}</p><h3><template v-for="line in project.title.split('\n')" :key="line">{{ line }}<br /></template></h3>
            <span>{{ project.summary }}</span>
          </div>
          <div class="project-card-foot"><div><small v-for="item in project.tech" :key="item">{{ item }}</small></div><ArrowUpRight :size="22" aria-hidden="true" /></div>
        </article>
      </TransitionGroup>
    </section>

    <section class="section about-section">
      <div id="about" class="about-grid">
        <div class="about-title" data-reveal><p class="section-index">{{ homepageContent.sections.about.eyebrow }}</p><h2 class="preserve-lines">{{ homepageContent.sections.about.title }}</h2></div>
        <div class="about-copy" data-reveal>
          <p>{{ homepageContent.sections.about.paragraphOne }}</p>
          <p>{{ homepageContent.sections.about.paragraphTwo }}</p>
          <div class="principle-list"><div v-for="(principle, index) in homepageContent.sections.about.principles" :key="principle"><span>{{ String(index + 1).padStart(2, '0') }}</span><strong>{{ principle }}</strong></div></div>
        </div>
      </div>
      <div class="about-feature-board" data-reveal>
        <header><p>{{ homepageContent.sections.about.featureEyebrow }}</p><h3>{{ homepageContent.sections.about.featureTitle }}</h3></header>
        <div><article v-for="(feature, index) in homepageContent.sections.about.features" :key="`${feature.title}-${index}`"><span>{{ String(index + 1).padStart(2, '0') }}</span><h4>{{ feature.title }}</h4><p>{{ feature.description }}</p></article></div>
      </div>
    </section>

    <section class="section members-section">
      <header id="members" class="section-header inverse" data-reveal><div><p class="section-index">{{ homepageContent.sections.members.eyebrow }}</p><h2>{{ homepageContent.sections.members.title }}</h2></div><p>{{ homepageContent.sections.members.description }}</p></header>
      <div class="people-grid">
        <div class="member-showcase" data-reveal>
          <article class="advisor-card">
            <RouterLink v-if="advisor.profileId" class="member-card-link" :to="`/members/${advisor.profileId}`" :aria-label="`查看${advisor.name}的公开主页`">
              <div class="people-label"><span>ADVISOR / 01</span><small>固定展示</small></div>
              <div class="advisor-body"><span class="advisor-avatar"><img v-if="advisor.avatarUrl" :src="advisor.avatarUrl" alt="" /><b v-else>{{ advisor.initials }}</b></span><div><p>指导老师</p><h3>{{ advisor.name }}</h3><span>{{ advisor.role }}</span></div></div>
              <p>{{ advisor.description }}</p>
              <div class="member-tags"><small v-for="tag in advisor.tags" :key="tag">{{ tag }}</small></div>
            </RouterLink>
            <template v-else>
              <div class="people-label"><span>ADVISOR / 01</span><small>固定展示</small></div>
              <div class="advisor-body"><span class="advisor-avatar">{{ advisor.initials }}</span><div><p>指导老师</p><h3>{{ advisor.name }}</h3><span>{{ advisor.role }}</span></div></div>
              <p>{{ advisor.description }}</p>
              <div class="member-tags"><small v-for="tag in advisor.tags" :key="tag">{{ tag }}</small></div>
            </template>
          </article>

          <div class="core-team">
            <div class="core-head"><span>CORE MEMBERS</span><small>{{ String(coreMembers.length).padStart(2, '0') }} PEOPLE</small></div>
            <article v-for="(member, index) in coreMembers" :key="member.profileId || member.slug || member.initials">
              <RouterLink class="member-card-link core-member-link" :to="`/members/${member.profileId || member.slug}`" :aria-label="`查看${member.name}的公开主页`">
                <span class="member-rank">{{ String(index + 1).padStart(2, '0') }}</span><span class="avatar"><img v-if="member.avatarUrl" :src="member.avatarUrl" alt="" /><b v-else>{{ member.initials }}</b></span><div><h3>{{ member.name }}</h3><p>{{ member.role }}</p></div><div class="member-tags"><small v-for="tag in member.tags" :key="tag">{{ tag }}</small></div>
              </RouterLink>
            </article>
          </div>
        </div>

        <aside class="leaderboard" data-reveal>
          <div class="leaderboard-head"><span><Users :size="19" aria-hidden="true" /> 实时成员榜单</span><small><i aria-hidden="true"></i> 30S SYNC</small></div>
          <div class="ranking-tabs"><button v-for="tab in Object.keys(rankingData)" :key="tab" :class="{ active: activeRanking === tab }" :aria-pressed="activeRanking === tab" @click="activeRanking = tab">{{ tab }}</button></div>
          <ol><li v-for="(member, index) in rankings" :key="member.profileId || member.slug || member.initials"><RouterLink :to="`/members/${member.profileId || member.slug}`" :aria-label="`查看${member.name}的公开主页`"><span>{{ index + 1 }}</span><div><strong>{{ member.name }}</strong><small>{{ member.tags[0] }}</small></div><b>{{ member.points }}</b></RouterLink></li></ol>
        </aside>
      </div>
    </section>

    <section class="section partners-section">
      <header id="partners" class="section-header" data-reveal><div><p class="section-index">{{ homepageContent.sections.partners.eyebrow }}</p><h2>{{ homepageContent.sections.partners.title }}</h2></div><p>{{ homepageContent.sections.partners.description }}</p></header>
      <div class="sponsor-list">
        <article v-for="(sponsor, index) in sponsors" :key="sponsor.name" class="sponsor-card" data-reveal>
          <div class="sponsor-index">PARTNER / {{ String(index + 1).padStart(2, '0') }}</div>
          <a class="sponsor-logo" :href="sponsor.websiteUrl" target="_blank" rel="noreferrer" :aria-label="`访问 ${sponsor.name} 官网`"><img :src="sponsor.logoUrl" :alt="`${sponsor.name} 官方 Logo`" width="512" height="512" loading="lazy" /></a>
          <div class="sponsor-copy"><p>{{ sponsor.type }}</p><h3>{{ sponsor.name }}</h3><span>{{ sponsor.description }}</span><ul><li v-for="item in sponsor.focus" :key="item">{{ item }}</li></ul><a :href="sponsor.websiteUrl" target="_blank" rel="noreferrer">访问官方网站 <ExternalLink :size="18" aria-hidden="true" /></a></div>
        </article>
      </div>
    </section>

    <section class="section updates-section">
      <header id="updates" class="section-header" data-reveal><div><p class="section-index">{{ homepageContent.sections.achievements.eyebrow }}</p><h2>{{ homepageContent.sections.achievements.title }}</h2></div><p>{{ homepageContent.sections.achievements.description }}</p></header>
      <div class="achievement-columns" data-reveal>
        <section class="home-news-column"><header><span>NEWS / 时间排序</span><h3>相关新闻</h3></header><div><component :is="item.url ? 'a' : 'article'" v-for="(item, index) in newsItems" :key="item.id || item.title" :href="item.url || undefined" :target="item.url ? '_blank' : undefined" :rel="item.url ? 'noopener noreferrer' : undefined"><time>{{ item.date }}</time><small>{{ item.type }}</small><h4>{{ item.title }}</h4><p v-if="item.summary">{{ item.summary }}</p><ArrowUpRight :size="18" aria-hidden="true" /></component></div></section>
        <section class="home-competition-column"><header><span>COMPETITIONS / 手动排序</span><h3>比赛成果</h3></header><div><component :is="item.id ? 'RouterLink' : 'article'" v-for="(item, index) in competitionResults" :key="item.id || `${item.name}-${index}`" :to="item.id ? `/competition-results/${item.id}` : undefined"><span>{{ String(index + 1).padStart(2, '0') }}</span><div><small>{{ competitionLevelLabels[item.level] || item.level }} · {{ item.competitionDate }}</small><h4>{{ item.name }}</h4><p>{{ item.track || '综合赛道' }}</p></div><strong>{{ item.awardName }}</strong><ArrowRight v-if="item.id" :size="18" aria-hidden="true" /></component></div></section>
      </div>
    </section>

    <section class="contact-section">
      <div data-reveal><p>{{ homepageContent.sections.contact.eyebrow }}</p><h2 class="preserve-lines">{{ homepageContent.sections.contact.title }}</h2></div>
      <div class="contact-panel" data-reveal><p>{{ homepageContent.sections.contact.description }}</p><div><a v-for="link in enabledExternalLinks" :key="`${link.platform}-${link.label}`" :href="link.url" target="_blank" rel="noopener noreferrer"><component :is="externalIcon(link.platform)" :size="19" aria-hidden="true" /> {{ link.label }} <ArrowUpRight :size="15" aria-hidden="true" /></a><span v-if="!enabledExternalLinks.length">暂无公开外部入口</span></div></div>
    </section>

    <footer><a class="brand" href="#top" aria-label="返回 YES Lab 首页" @click.prevent="scrollTo('#top')"><img src="/yes-lab-logo.png" alt="YES Lab" width="900" height="506" /><span>PUBLIC SHOWCASE</span></a><p>{{ homepageContent.sections.footerText }}</p></footer>

    <Transition name="modal">
      <div v-if="selectedProject" class="modal-backdrop" @click.self="selectedProject = null">
        <section class="project-modal" role="dialog" aria-modal="true" :aria-labelledby="`project-title-${selectedProject.number}`">
          <button ref="modalClose" class="modal-close" aria-label="关闭项目详情" @click="selectedProject = null"><X :size="22" aria-hidden="true" /></button>
          <div class="project-modal-cover" :class="{ 'is-default': !selectedProject.coverImageUrl }"><img :src="selectedProject.coverImageUrl || '/yes-lab-logo.png'" :alt="`${selectedProject.title.replace('\n', '')}项目主图`" /></div>
          <p>PROJECT {{ selectedProject.number }} / {{ selectedProject.category }}</p><h2 :id="`project-title-${selectedProject.number}`">{{ selectedProject.title.replace('\n', '') }}</h2><span class="modal-summary">{{ selectedProject.summary }}</span>
          <dl><div><dt>当前状态</dt><dd>{{ selectedProject.status }}</dd></div><div><dt>负责人</dt><dd>{{ selectedProject.lead }}</dd></div><div><dt>指导老师</dt><dd>{{ selectedProject.advisor || '暂未关联' }}</dd></div><div><dt>参与成员</dt><dd>{{ selectedProject.members }}</dd></div><div><dt>技术方向</dt><dd>{{ selectedProject.tech.join(' / ') }}</dd></div><div class="full"><dt>阶段成果</dt><dd>{{ selectedProject.result }}</dd></div></dl>
          <div class="project-modal-actions">
            <a v-if="selectedProject.repositoryUrl" class="primary-action" :href="selectedProject.repositoryUrl" target="_blank" rel="noopener noreferrer">打开项目仓库 <ArrowRight :size="18" aria-hidden="true" /></a>
            <a v-if="selectedProject.documentUrl" class="text-action" :href="selectedProject.documentUrl" target="_blank" rel="noopener noreferrer">查看项目文档 <ArrowRight :size="18" aria-hidden="true" /></a>
            <span v-if="!selectedProject.repositoryUrl && !selectedProject.documentUrl" class="project-link-pending">项目仓库与文档暂未公开</span>
          </div>
        </section>
      </div>
    </Transition>
  </main>
</template>

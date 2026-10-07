<script setup>
import { brand } from '../config/site'
import ThemeToggle from '../components/ThemeToggle.vue'
import ResearchVisual from '../components/ResearchVisual.vue'
import HomePointerGlow from '../components/HomePointerGlow.vue'
import CompactLeaderboard from '../components/CompactLeaderboard.vue'
import PublicFundCard from '../components/PublicFundCard.vue'
import HomepageDeadlineConveyor from '../components/HomepageDeadlineConveyor.vue'
import { animate } from 'motion'
import {
  ArrowRight,
  ArrowUpRight,
  BookOpen,
  ChevronLeft,
  ChevronRight,
  Code2,
  ExternalLink,
  Menu,
  Plus,
  X,
} from '@lucide/vue'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { authState } from '../services/authApi'
import { fetchPublicHome } from '../services/publicApi'

const menuOpen = ref(false)
const activeProjectFilter = ref('全部')
const selectedProject = ref(null)
const modalClose = ref(null)
const homepageReady = ref(false)
const homeElement = ref(null)
const heroElement = ref(null)
const heroProgress = ref(0)
const peopleRail = ref(null)
const awardRail = ref(null)
const selectedProjectIndex = ref(0)
const prefersReducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches
const useViewTransitions = typeof document !== 'undefined' && typeof document.startViewTransition === 'function'
const recruitmentSteps = ['报名', '初筛', '面试', '技能测试', '正式成员']
const personTones = ['#2a6df4', '#e8743b', '#14a37f', '#8b5cf6', '#d4a017', '#0ea5c6']
let statObserver
let heroFrame = 0
let revealObserver
let revealMutationObserver
let rankingsRefreshTimer
let homeUnmounted = false
const publicHomeSnapshotKey = `yeslab_public_home_snapshot_v5:${brand.name}:${brand.displayName}`
try {
  window.localStorage.removeItem('yeslab_public_home_snapshot_v2')
  window.localStorage.removeItem('yeslab_public_home_snapshot_v3')
} catch {
  /* Storage may be unavailable. */
}
const publicHomeSnapshotMaxAge = 7 * 24 * 60 * 60 * 1000

const defaultProjects = []

const defaultMembers = []

const defaultRankingData = { 总榜: [], 月榜: [], 年榜: [] }

const defaultUpdates = []

const defaultAwards = []

const competitionLevelLabels = {
  SCHOOL: '校级',
  PROVINCIAL: '省级',
  REGIONAL: '赛区',
  NATIONAL: '国家级',
  INTERNATIONAL: '国际级',
  OTHER: '其他',
}

const defaultSponsors = []

const defaultHomepageContent = {
  profile: {
    heroEyebrow: 'YES LAB · ROBOTICS RESEARCH / 2026',
    heroTitle: '让无人设备带上、\n你的',
    heroAccent: '眼眸',
    primaryActionLabel: '浏览研究项目',
    primaryActionUrl: '#projects',
    primaryActionEnabled: true,
    secondaryActionLabel: '了解合作伙伴',
    secondaryActionUrl: '#partners',
    secondaryActionEnabled: true,
    researchDirectionItems: [
      { name: '无人机', url: '#projects' },
      { name: '空地协同', url: '#projects' },
      { name: '具身智能', url: '#projects' },
    ],
  },
  sections: {
    projects: {
      eyebrow: '01 / SELECTED RESEARCH',
      title: '研究与工程实践',
      description: '从算法、硬件到系统集成，我们以可运行、可验证的真实项目建立研究能力。',
    },
    about: {
      eyebrow: '02 / ABOUT',
      title: '把研究做成\n可以触碰的现场',
      paragraphOne: 'YES Lab 是一个处于起步阶段的实验室，主要研究无人机、无人机与机器狗空地协同、具身智能等方向。',
      paragraphTwo: '我们以竞赛与真实工程项目为牵引，为学校培养兼具算法、硬件和系统能力的复合型人才。',
      principles: ['面向真实场景', '强调应用实践', '培养工程人才'],
      featureEyebrow: 'HOW WE WORK',
      featureTitle: '从研究方向走向工程现场',
      features: [
        {
          title: '真实问题驱动',
          description: '从无人系统的真实任务出发，把研究目标拆解为可以验证的算法、硬件与系统方案。',
        },
        { title: '跨平台协同', description: '连接无人机、机器狗与具身智能平台，在异构系统协同中训练完整工程能力。' },
        {
          title: '项目制人才培养',
          description: '以竞赛和科研项目贯穿学习路径，让成员在实践、复盘和公开成果中持续成长。',
        },
      ],
    },
    members: { eyebrow: '03 / PEOPLE', title: '共同成长的研究者', description: '榜单每日刷新' },
    partners: {
      eyebrow: '04 / PARTNERS',
      title: '赞助与合作伙伴',
      description: '感谢企业伙伴为无人系统研究、工程实践与人才培养提供支持。',
    },
    achievements: { eyebrow: '05 / ACHIEVEMENTS', title: '成果与外部报道', description: '新闻按发布日期自动排序' },
    contact: {
      eyebrow: '06 / CONNECT',
      title: '下一次探索，\n从这里开始。',
      description: '关注我们的研究、比赛和开源进展。',
    },
    footerText: '© 2026 YES Lab · INTELLIGENCE IN MOTION',
  },
  proofItems: [
    { label: '01 / AWARDS', value: '3 项奖项', detail: '全国 / 省赛 / 赛区', metric: 'AWARDS', target: '#updates' },
    { label: '02 / FOCUS', value: '3 个方向', detail: '无人系统与具身智能', metric: 'DIRECTIONS', target: '#projects' },
    { label: '03 / PARTNER', value: 'CUAV', detail: '企业赞助伙伴', metric: 'PARTNERS', target: '#partners' },
    {
      label: '项目状态',
      value: '持续建设',
      detail: '开放、实践、成长',
      metric: 'PROJECT_STATUS',
      target: '#projects',
    },
  ],
  externalLinks: [
    { platform: 'github', label: '开源仓库', url: 'https://github.com', enabled: true },
    { platform: 'bilibili', label: '哔哩哔哩', url: '', enabled: false },
    { platform: 'wechat', label: '微信公众号', url: '', enabled: false },
    { platform: 'douyin', label: '抖音', url: '', enabled: false },
  ],
  heroModels: [
    {
      title: 'Unitree Go2',
      description: '四足机器人 · 具身智能研究平台',
      modelUrl: '/models/go2.glb',
      enabled: true,
    },
    {
      title: 'Skydio X2',
      description: '自主飞行 · 空中感知平台',
      modelUrl: '/models/skydio-x2.glb',
      enabled: true,
    },
  ],
  featuredAdvisorProfileIds: [],
  display: {
    showProjects: true,
    showAbout: true,
    showMembers: true,
    showPartners: true,
    showAchievements: true,
    showContact: true,
    showAdvisor: true,
    showCoreMembers: true,
    showLeaderboard: true,
    advisorSelectionMode: 'AUTO',
    memberSelectionMode: 'AUTO',
    projectSelectionMode: 'AUTO',
    advisorLimit: 6,
    projectLimit: 12,
    memberLimit: 12,
    newsLimit: 20,
    competitionLimit: 30,
    sponsorLimit: 20,
  },
}

function normalizeHomepageContent(value) {
  const source = value || {}
  const sourceDisplay = source.display || {}
  const featuredAdvisorProfileIds = source.featuredAdvisorProfileIds?.length
    ? source.featuredAdvisorProfileIds
    : source.advisorProfileId
      ? [source.advisorProfileId]
      : []
  const legacyProofMetrics = ['AWARDS', 'DIRECTIONS', 'PARTNERS', 'PROJECT_STATUS']
  const proofTargets = {
    AWARDS: '#updates',
    DIRECTIONS: '#projects',
    PARTNERS: '#partners',
    PROJECT_STATUS: '#projects',
    CUSTOM: '#projects',
  }
  return {
    ...defaultHomepageContent,
    ...source,
    profile: { ...defaultHomepageContent.profile, ...(source.profile || {}) },
    sections: {
      ...defaultHomepageContent.sections,
      ...(source.sections || {}),
      projects: { ...defaultHomepageContent.sections.projects, ...(source.sections?.projects || {}) },
      about: { ...defaultHomepageContent.sections.about, ...(source.sections?.about || {}) },
      members: { ...defaultHomepageContent.sections.members, ...(source.sections?.members || {}) },
      partners: { ...defaultHomepageContent.sections.partners, ...(source.sections?.partners || {}) },
      achievements: { ...defaultHomepageContent.sections.achievements, ...(source.sections?.achievements || {}) },
      contact: { ...defaultHomepageContent.sections.contact, ...(source.sections?.contact || {}) },
    },
    proofItems: (source.proofItems || defaultHomepageContent.proofItems).map((item, index) => {
      const metric = item.metric || legacyProofMetrics[index] || 'CUSTOM'
      return { ...item, metric, target: item.target || proofTargets[metric] }
    }),
    externalLinks: source.externalLinks || defaultHomepageContent.externalLinks,
    heroModels: Array.isArray(source.heroModels) ? source.heroModels : defaultHomepageContent.heroModels,
    featuredAdvisorProfileIds,
    display: {
      ...defaultHomepageContent.display,
      ...sourceDisplay,
      advisorSelectionMode:
        sourceDisplay.advisorSelectionMode || (featuredAdvisorProfileIds.length ? 'SELECTED' : 'AUTO'),
      memberSelectionMode:
        sourceDisplay.memberSelectionMode || (source.featuredMemberProfileIds?.length ? 'SELECTED' : 'AUTO'),
      projectSelectionMode:
        sourceDisplay.projectSelectionMode || (source.featuredProjectIds?.length ? 'SELECTED' : 'AUTO'),
    },
  }
}

const profile = ref({
  name: brand.name,
  displayName: brand.displayName,
  fullName: brand.fullName,
  slogan: '探索空地协同，培养未来工程人才',
  description: '一个面向无人系统与具身智能的初创实验室，以真实项目连接科研、竞赛与人才培养。',
  researchDirections: ['无人机', '空地协同', '具身智能'],
})
const projects = ref(defaultProjects)
const members = ref(defaultMembers)
const advisors = ref([])
const rankingData = ref(defaultRankingData)
const rankingsLoaded = ref(false)
const rankingsError = ref('')
const rankingsUpdatedAt = ref('')
let lastHomeDay = ''
let homeRequestVersion = 0
const newsItems = ref(defaultUpdates)
const competitionResults = ref(
  defaultAwards.map((item, index) => ({
    id: null,
    name: item.competition,
    track: item.category,
    awardName: item.prize,
    level: item.level,
    competitionDate: `历史成果 ${index + 1}`,
  })),
)
const sponsors = ref(defaultSponsors)
const homepageContent = ref(normalizeHomepageContent(defaultHomepageContent))

const projectFilters = computed(() => ['全部', ...new Set(projects.value.map((project) => project.category))])
const displayOptions = computed(() => homepageContent.value.display)
const filteredProjects = computed(() => {
  if (displayOptions.value.projectSelectionMode === 'HIDDEN') return []
  const selectedProjectIds = homepageContent.value.featuredProjectIds || []
  const sourceProjects =
    displayOptions.value.projectSelectionMode === 'SELECTED'
      ? projects.value.filter((project) => selectedProjectIds.includes(project.slug))
      : projects.value
  return (
    activeProjectFilter.value === '全部'
      ? sourceProjects
      : sourceProjects.filter((project) => project.category === activeProjectFilter.value)
  ).slice(0, displayOptions.value.projectLimit)
})
const rankingTotalCount = ref(0)
const coreMembers = computed(() => {
  if (displayOptions.value.memberSelectionMode === 'HIDDEN') return []
  const designatedMembers = members.value.filter((member) => member.core)
  const selected = designatedMembers.length ? designatedMembers : members.value.slice(0, 3)
  return selected.slice(0, displayOptions.value.memberLimit)
})
const visibleAdvisors = computed(() => {
  if (displayOptions.value.advisorSelectionMode === 'HIDDEN') return []
  const selectedIds = homepageContent.value.featuredAdvisorProfileIds || []
  const sourceAdvisors =
    displayOptions.value.advisorSelectionMode === 'SELECTED'
      ? advisors.value
          .filter((item) => selectedIds.includes(item.profileId))
          .sort((left, right) => selectedIds.indexOf(left.profileId) - selectedIds.indexOf(right.profileId))
      : advisors.value
  return sourceAdvisors.slice(0, displayOptions.value.advisorLimit)
})
const showAdvisorModule = computed(() => displayOptions.value.showAdvisor && visibleAdvisors.value.length > 0)
const showCoreMembersModule = computed(
  () => displayOptions.value.showCoreMembers && displayOptions.value.memberSelectionMode !== 'HIDDEN',
)
const showMemberShowcase = computed(() => showAdvisorModule.value || showCoreMembersModule.value)
const peopleCards = computed(() => {
  const advisorsList = showAdvisorModule.value
    ? visibleAdvisors.value.map((advisor) => ({ ...advisor, kind: 'advisor' }))
    : []
  const coreList = showCoreMembersModule.value
    ? coreMembers.value.map((member) => ({ ...member, profileId: member.profileId || member.slug, kind: 'core' }))
    : []
  return [...advisorsList, ...coreList].map((person, index) => ({
    ...person,
    // A single surname reads better than two characters on the large portrait.
    initials: /^[\u4e00-\u9fff]/.test(person.name || '') ? person.name.slice(0, 1) : person.initials,
    tone: personTones[index % personTones.length],
  }))
})
const visibleSponsors = computed(() => sponsors.value.slice(0, displayOptions.value.sponsorLimit))
const visibleNewsItems = computed(() => newsItems.value.slice(0, displayOptions.value.newsLimit))
const visibleCompetitionResults = computed(() =>
  competitionResults.value.slice(0, displayOptions.value.competitionLimit),
)
const accountDestination = computed(() => (authState.account?.role === 'VISITOR' ? '/application' : '/today'))
const accountName = computed(() => authState.account?.displayName || authState.account?.username || '')
const enabledExternalLinks = computed(() =>
  (homepageContent.value.externalLinks || []).filter((link) => link.enabled && link.url),
)
const researchDirections = computed(() => {
  const configured = homepageContent.value.profile?.researchDirectionItems
  if (configured?.length) return configured
  return (profile.value.researchDirections || []).map((name) => ({ name, url: '' }))
})
const liveProofItems = computed(() => {
  const configured = homepageContent.value.proofItems || []
  const awardLevels = [
    ...new Set(
      competitionResults.value.map((item) => competitionLevelLabels[item.level] || item.level).filter(Boolean),
    ),
  ]
  const directionNames = [...new Set(researchDirections.value.map((item) => item.name).filter(Boolean))]
  const partnerTypes = [...new Set(sponsors.value.map((item) => item.type).filter(Boolean))]
  const projectStatuses = [...new Set(projects.value.map((item) => item.status).filter(Boolean))]
  const isBuilding = projects.value.some((item) =>
    ['研究中', '重点方向', '方向建设', '进行中', 'ACTIVE', 'PLANNING'].includes(item.status),
  )
  const automatic = {
    AWARDS: {
      value: `${competitionResults.value.length} 项奖项`,
      detail: awardLevels.join(' / ') || '成果持续更新',
      sectionName: '竞赛成果',
    },
    DIRECTIONS: {
      value: `${directionNames.length} 个方向`,
      detail: directionNames.join(' / ') || '研究方向持续更新',
      sectionName: '研究项目',
    },
    PARTNERS: {
      value: sponsors.value.length === 1 ? sponsors.value[0].name : `${sponsors.value.length} 家伙伴`,
      detail: partnerTypes.join(' / ') || '合作伙伴持续更新',
      sectionName: '赞助伙伴',
    },
    PROJECT_STATUS: {
      value: isBuilding ? '持续建设' : '筹备中',
      detail: projectStatuses.join(' / ') || '开放、实践、成长',
      sectionName: '项目状态',
    },
  }
  return configured.map((item) => ({
    ...item,
    ...(item.metric === 'CUSTOM' ? { sectionName: item.label } : automatic[item.metric]),
    target: item.target || '#top',
  }))
})
// Older saved homepages still carry decorative English labels; show their Chinese meaning instead.
const proofMetricLabels = { AWARDS: '获奖', DIRECTIONS: '研究方向', PARTNERS: '合作伙伴', PROJECT_STATUS: '项目状态' }
const proofLabel = (item) =>
  /^\d+\s*\/\s*[A-Z][A-Z\s]*$/.test(item.label || '') ? proofMetricLabels[item.metric] || '' : item.label
const heroKicker = computed(() => {
  const value = homepageContent.value.profile?.heroEyebrow || ''
  return /RESEARCH & COLLABORATION$/.test(value) ? `${brand.name} · 研究与协作` : value
})
const footerText = computed(() =>
  (homepageContent.value.sections?.footerText || '').replace(/ · RESEARCH & COLLABORATION$/, ' · 开放实验室平台'),
)
const visibleProofItems = computed(() =>
  liveProofItems.value.filter((item) => item.metric === 'CUSTOM' || !/^0\s/.test(String(item.value))),
)
const externalIcon = (platform) => ({ github: Code2, wechat: BookOpen })[platform?.toLowerCase()] || ExternalLink

const scrollTo = (id) => {
  menuOpen.value = false
  const target = document.querySelector(id)
  if (!target) return

  const headerHeight = document.querySelector('.ah-nav')?.getBoundingClientRect().height ?? 64
  const targetTop = target.getBoundingClientRect().top + window.scrollY - headerHeight
  window.scrollTo({
    top: Math.max(0, targetTop),
    behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
  })
}

const handleDirectionClick = (event, url) => {
  if (!url?.startsWith('#')) return
  event.preventDefault()
  scrollTo(url)
}

const directionTarget = (url) => (/^https?:\/\//i.test(url || '') ? '_blank' : undefined)

const applyCoreHome = (home) => {
  if (!home) return
  profile.value = { ...home.profile, name: brand.name, displayName: brand.displayName, fullName: brand.fullName }
  homepageContent.value = normalizeHomepageContent(home.homepageContent)
  sponsors.value = home.sponsors || []
}

const applyCompleteHome = (home) => {
  if (!home) return
  applyCoreHome(home)
  projects.value = home.projects
  members.value = home.members
  advisors.value = home.advisors?.length ? home.advisors : home.advisor ? [home.advisor] : []
  rankingData.value = Object.fromEntries(
    Object.keys(defaultRankingData).map((board) => [board, home.rankingData?.[board] || []]),
  )
  rankingTotalCount.value = home.rankingTotalCount || 0
  rankingsLoaded.value = true
  rankingsUpdatedAt.value = home.rankingsUpdatedAt || ''
  newsItems.value = home.news?.length ? home.news : home.updates
  competitionResults.value = home.competitionResults?.length
    ? home.competitionResults
    : (home.awards || []).map((item, index) => ({
        id: null,
        name: item.competition,
        track: item.category,
        awardName: item.prize,
        level: item.level,
        competitionDate: `历史成果 ${index + 1}`,
      }))
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

const beijingDay = () => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' }).format(new Date())
const scheduleDailyRefresh = () => {
  window.clearTimeout(rankingsRefreshTimer)
  const [year, month, day] = beijingDay().split('-').map(Number)
  const nextMidnight = Date.UTC(year, month - 1, day + 1) - 8 * 60 * 60 * 1000
  rankingsRefreshTimer = window.setTimeout(
    async () => {
      if (document.visibilityState === 'visible') await syncPublicHome()
      if (!homeUnmounted) scheduleDailyRefresh()
    },
    Math.max(1000, nextMidnight - Date.now()),
  )
}
const syncPublicHome = async () => {
  const version = ++homeRequestVersion
  const home = await fetchPublicHome((core) => {
    if (!homeUnmounted && version === homeRequestVersion) applyCoreHome(core)
  })
  if (homeUnmounted || version !== homeRequestVersion) return
  if (home) {
    applyCompleteHome(home)
    savePublicHomeSnapshot(home)
    lastHomeDay = beijingDay()
    rankingsError.value = ''
  } else {
    rankingsError.value = rankingsLoaded.value ? '榜单刷新失败，暂显示上次结果。' : '榜单读取失败，请重试。'
  }
  homepageReady.value = true
}
const refreshVisibleHome = () => {
  if (document.visibilityState === 'visible' && lastHomeDay && lastHomeDay !== beijingDay()) syncPublicHome()
}

const cachedPublicHome = readPublicHomeSnapshot()
if (cachedPublicHome) {
  applyCompleteHome(cachedPublicHome)
  lastHomeDay = beijingDay()
  homepageReady.value = true
}

const handleKeydown = (event) => {
  if (event.key !== 'Escape') return
  if (selectedProject.value) closeProject()
  menuOpen.value = false
}

// Project tiles grow into the detail sheet (View Transitions); other browsers fall back to a fade.
let projectTrigger = null
function withTransition(update) {
  if (!useViewTransitions || prefersReducedMotion()) return update()
  return document.startViewTransition(update).finished
}
function setTransitionNames(art, title) {
  if (art) art.style.viewTransitionName = 'ah-project-art'
  if (title) title.style.viewTransitionName = 'ah-project-title'
}
function clearTransitionNames(...elements) {
  elements.forEach((element) => element && (element.style.viewTransitionName = ''))
}
async function openProject(project, event) {
  projectTrigger = event?.currentTarget || null
  const art = projectTrigger?.querySelector('.ah-tile-art')
  const title = projectTrigger?.querySelector('.ah-tile-title')
  setTransitionNames(art, title)
  await withTransition(async () => {
    clearTransitionNames(art, title)
    selectedProjectIndex.value = Math.max(0, filteredProjects.value.indexOf(project))
    selectedProject.value = project
    await nextTick()
    setTransitionNames(document.querySelector('.ah-sheet-hero'), document.querySelector('.ah-sheet-hero h2'))
  })
  clearTransitionNames(document.querySelector('.ah-sheet-hero'), document.querySelector('.ah-sheet-hero h2'))
}
async function closeProject() {
  if (!selectedProject.value) return
  const trigger = projectTrigger
  setTransitionNames(document.querySelector('.ah-sheet-hero'), document.querySelector('.ah-sheet-hero h2'))
  await withTransition(async () => {
    selectedProject.value = null
    await nextTick()
    setTransitionNames(trigger?.querySelector('.ah-tile-art'), trigger?.querySelector('.ah-tile-title'))
  })
  clearTransitionNames(trigger?.querySelector('.ah-tile-art'), trigger?.querySelector('.ah-tile-title'))
  trigger?.focus({ preventScroll: true })
}

function scrollRail(element, direction) {
  if (!element) return
  element.scrollBy({
    left: direction * element.clientWidth * 0.8,
    behavior: prefersReducedMotion() ? 'auto' : 'smooth',
  })
}

function updateHeroProgress() {
  heroFrame = 0
  const hero = heroElement.value
  if (!hero) return
  const rect = hero.getBoundingClientRect()
  heroProgress.value = Math.min(1, Math.max(0, -rect.top / Math.max(1, rect.height * 0.8)))
}
function onHeroScroll() {
  if (!heroFrame) heroFrame = requestAnimationFrame(updateHeroProgress)
}

// Figures in the「实验室此刻」strip count up the first time they scroll into view.
function initializeStatCounters() {
  if (statObserver || !homeElement.value || prefersReducedMotion() || !('IntersectionObserver' in window)) return
  statObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return
        statObserver.unobserve(entry.target)
        const text = entry.target.dataset.countText || ''
        const match = text.match(/^(\d+)(.*)$/)
        if (!match) return
        const target = Number(match[1])
        const suffix = match[2]
        if (!target) return
        animate(0, target, {
          duration: 1.1,
          ease: [0.22, 1, 0.36, 1],
          onUpdate: (latest) => (entry.target.textContent = `${Math.round(latest)}${suffix}`),
        })
      })
    },
    { threshold: 0.6 },
  )
  homeElement.value.querySelectorAll('[data-count-text]').forEach((element) => statObserver.observe(element))
}

watch(selectedProject, async (project) => {
  document.body.classList.toggle('modal-open', Boolean(project))
  if (project) {
    await nextTick()
    modalClose.value?.focus()
  }
})

function initializeReveals() {
  if (revealObserver || !homeElement.value || !homepageReady.value) return
  if (!('IntersectionObserver' in window) || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
  homeElement.value.classList.add('reveal-ready')
  revealObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        // Sections the reader jumped past (anchor links, fast scrolling) are shown too.
        if (!entry.isIntersecting && entry.boundingClientRect.bottom > 0) return
        entry.target.classList.add('is-visible')
        revealObserver.unobserve(entry.target)
      })
    },
    { threshold: 0.12 },
  )
  const observeNew = () =>
    homeElement.value
      ?.querySelectorAll('[data-reveal]:not(.is-visible):not([data-reveal-observed])')
      .forEach((element) => {
        element.setAttribute('data-reveal-observed', '')
        revealObserver.observe(element)
      })
  observeNew()
  // Sections that appear once their data arrives (awards, news, leaderboard) join the reveal as well.
  revealMutationObserver = new MutationObserver(observeNew)
  revealMutationObserver.observe(homeElement.value, { childList: true, subtree: true })
}

onMounted(async () => {
  document.addEventListener('keydown', handleKeydown)
  window.addEventListener('scroll', onHeroScroll, { passive: true })
  updateHeroProgress()
  document.addEventListener('visibilitychange', refreshVisibleHome)
  window.addEventListener('focus', refreshVisibleHome)
  initializeReveals()
  await syncPublicHome()
  if (homeUnmounted) return
  await nextTick()
  if (homeUnmounted) return
  initializeReveals()
  initializeStatCounters()
  scheduleDailyRefresh()
})

onBeforeUnmount(() => {
  homeUnmounted = true
  document.removeEventListener('keydown', handleKeydown)
  document.removeEventListener('visibilitychange', refreshVisibleHome)
  window.removeEventListener('focus', refreshVisibleHome)
  document.body.classList.remove('modal-open')
  revealObserver?.disconnect()
  revealMutationObserver?.disconnect()
  statObserver?.disconnect()
  window.removeEventListener('scroll', onHeroScroll)
  cancelAnimationFrame(heroFrame)
  window.clearTimeout(rankingsRefreshTimer)
})
</script>

<template>
  <main ref="homeElement" :class="['site-shell', 'ah', { 'awaiting-home': !homepageReady }]">
    <HomePointerGlow />
    <div v-if="!homepageReady" class="home-bootstrap-state" role="status" aria-live="polite">
      <img :src="brand.logo" alt="" width="900" height="300" />
      <p>正在同步 {{ profile.name }} 最新公开内容…</p>
    </div>
    <a class="skip-link" href="#top">跳到主要内容</a>

    <header class="ah-nav">
      <a class="ah-brand" href="#top" :aria-label="`${profile.displayName}首页`" @click.prevent="scrollTo('#top')">
        <img :src="brand.logo" :alt="profile.name" width="900" height="300" />
      </a>
      <nav id="homepage-navigation" :class="['ah-nav-links', { open: menuOpen }]" aria-label="主导航">
        <button v-if="displayOptions.showProjects" type="button" @click="scrollTo('#projects')">项目</button>
        <button v-if="displayOptions.showAbout" type="button" @click="scrollTo('#about')">关于</button>
        <button v-if="displayOptions.showMembers" type="button" @click="scrollTo('#members')">成员</button>
        <button v-if="displayOptions.showAchievements" type="button" @click="scrollTo('#updates')">成果</button>
        <button v-if="displayOptions.showPartners" type="button" @click="scrollTo('#partners')">伙伴</button>
        <RouterLink to="/discussions">讨论板</RouterLink>
      </nav>
      <div class="ah-nav-tools">
        <ThemeToggle />
        <RouterLink
          v-if="authState.account"
          class="ah-account"
          :to="accountDestination"
          :aria-label="`进入${accountName}的成员页面`"
        >
          <span class="ah-account-avatar"
            ><img v-if="authState.account.avatarUrl" :src="authState.account.avatarUrl" alt="" /><b v-else>{{
              accountName.slice(0, 1)
            }}</b></span
          ><span class="ah-account-name">{{ accountName }}</span>
        </RouterLink>
        <template v-else>
          <RouterLink class="ah-nav-login" to="/login">登录</RouterLink>
          <RouterLink class="ah-pill" to="/register">报名加入</RouterLink>
        </template>
        <button
          class="ah-menu-button"
          type="button"
          aria-controls="homepage-navigation"
          :aria-expanded="menuOpen"
          :aria-label="menuOpen ? '关闭栏目导航' : '打开栏目导航'"
          @click="menuOpen = !menuOpen"
        >
          <X v-if="menuOpen" :size="20" aria-hidden="true" /><Menu v-else :size="20" aria-hidden="true" />
        </button>
      </div>
    </header>

    <section id="top" ref="heroElement" class="ah-hero" tabindex="-1" :style="{ '--hero-p': heroProgress }">
      <div class="ah-hero-glow" aria-hidden="true"></div>
      <div class="ah-hero-copy">
        <p v-if="heroKicker" class="ah-kicker">{{ heroKicker }}</p>
        <h1>
          <template v-for="(line, index) in homepageContent.profile.heroTitle.split('\n')" :key="`${line}-${index}`"
            >{{ line }}<br v-if="index < homepageContent.profile.heroTitle.split('\n').length - 1" /></template
          ><em>{{ homepageContent.profile.heroAccent }}</em>
        </h1>
        <p class="ah-hero-lead">{{ profile.slogan }}</p>
        <p class="ah-hero-sub">{{ profile.description }}</p>
        <div class="ah-hero-actions">
          <a
            v-if="homepageContent.profile.primaryActionEnabled"
            class="ah-pill ah-pill--lg"
            :href="homepageContent.profile.primaryActionUrl"
            :target="directionTarget(homepageContent.profile.primaryActionUrl)"
            :rel="directionTarget(homepageContent.profile.primaryActionUrl) ? 'noopener noreferrer' : undefined"
            @click="handleDirectionClick($event, homepageContent.profile.primaryActionUrl)"
            >{{ homepageContent.profile.primaryActionLabel }}</a
          >
          <a
            v-if="homepageContent.profile.secondaryActionEnabled"
            class="ah-link"
            :href="homepageContent.profile.secondaryActionUrl"
            :target="directionTarget(homepageContent.profile.secondaryActionUrl)"
            :rel="directionTarget(homepageContent.profile.secondaryActionUrl) ? 'noopener noreferrer' : undefined"
            @click="handleDirectionClick($event, homepageContent.profile.secondaryActionUrl)"
            >{{ homepageContent.profile.secondaryActionLabel }}<ArrowRight :size="17" aria-hidden="true"
          /></a>
        </div>
      </div>

      <div class="ah-hero-visual">
        <ResearchVisual
          v-if="homepageReady"
          :full-name="profile.fullName"
          :display-name="profile.displayName"
          :models="homepageContent.heroModels"
        />
      </div>

      <ul v-if="researchDirections.length" class="ah-directions" aria-label="研究方向">
        <li v-for="direction in researchDirections" :key="`${direction.name}-${direction.url}`">
          <a
            v-if="direction.url"
            :href="direction.url"
            :target="directionTarget(direction.url)"
            :rel="directionTarget(direction.url) ? 'noopener noreferrer' : undefined"
            @click="handleDirectionClick($event, direction.url)"
            >{{ direction.name }}<ArrowUpRight :size="15" aria-hidden="true"
          /></a>
          <span v-else>{{ direction.name }}</span>
        </li>
      </ul>
    </section>

    <section class="ah-chapter ah-chapter--soft" aria-labelledby="ah-now-title">
      <div class="ah-wrap">
        <header class="ah-head" data-reveal>
          <h2 id="ah-now-title">实验室此刻。</h2>
          <p>基金、截止日程和阶段成果，都是平台里的实时数据。</p>
        </header>
        <div v-if="visibleProofItems.length" class="ah-stats" data-reveal>
          <a
            v-for="item in visibleProofItems"
            :key="`${item.label}-${item.target}`"
            class="ah-stat"
            :href="item.target"
            :target="directionTarget(item.target)"
            :rel="directionTarget(item.target) ? 'noopener noreferrer' : undefined"
            :aria-label="`${proofLabel(item)}：${item.value}，跳转到${item.sectionName}`"
            @click="handleDirectionClick($event, item.target)"
          >
            <small>{{ proofLabel(item) }}</small>
            <strong :data-count-text="item.value">{{ item.value }}</strong>
            <span>{{ item.detail }}</span>
          </a>
        </div>
        <div class="ah-now-panels" data-reveal>
          <PublicFundCard compact />
          <HomepageDeadlineConveyor />
        </div>
      </div>
    </section>

    <section v-if="displayOptions.showProjects" class="ah-chapter" aria-labelledby="projects">
      <div class="ah-wrap ah-wrap--wide">
        <header class="ah-head ah-head--split" data-reveal>
          <div>
            <h2 id="projects">{{ homepageContent.sections.projects.title }}</h2>
            <p>{{ homepageContent.sections.projects.description }}</p>
          </div>
          <div v-if="projectFilters.length > 2" class="ah-segmented" role="group" aria-label="按类别筛选项目">
            <button
              v-for="filter in projectFilters"
              :key="filter"
              type="button"
              :aria-pressed="activeProjectFilter === filter"
              @click="activeProjectFilter = filter"
            >
              {{ filter }}
            </button>
          </div>
        </header>

        <TransitionGroup
          v-if="filteredProjects.length"
          name="ah-tiles"
          tag="div"
          class="ah-bento"
          :class="`ah-bento--${Math.min(filteredProjects.length, 4)}`"
          data-reveal
        >
          <button
            v-for="(project, index) in filteredProjects"
            :key="project.slug || project.number"
            type="button"
            class="ah-tile"
            :class="{
              'ah-tile--feature': index === 0 && filteredProjects.length > 2,
              'has-cover': project.coverImageUrl,
            }"
            :aria-label="`查看${project.title.replace('\n', '')}项目详情`"
            @click="openProject(project, $event)"
          >
            <span class="ah-tile-art" :class="`ah-art-${index % 5}`" aria-hidden="true">
              <img v-if="project.coverImageUrl" :src="project.coverImageUrl" alt="" loading="lazy" />
            </span>
            <span class="ah-tile-body">
              <small>{{ project.category }} · {{ project.status }}</small>
              <strong class="ah-tile-title">{{ project.title.replace('\n', '') }}</strong>
              <span v-if="index === 0 || filteredProjects.length <= 2" class="ah-tile-summary">{{
                project.summary
              }}</span>
              <span v-if="project.tech.length && index === 0" class="ah-tile-tags">
                <i v-for="item in project.tech.slice(0, 4)" :key="item">{{ item }}</i>
              </span>
            </span>
            <span class="ah-tile-plus" aria-hidden="true"><Plus :size="18" /></span>
          </button>
        </TransitionGroup>
        <p v-else class="ah-empty" data-reveal>项目团队建立并公开后，会出现在这里。</p>
      </div>
    </section>

    <section v-if="displayOptions.showAbout" class="ah-chapter ah-chapter--soft" aria-labelledby="about">
      <div class="ah-wrap">
        <div class="ah-about" data-reveal>
          <h2 id="about" class="preserve-lines">{{ homepageContent.sections.about.title }}</h2>
          <div>
            <p>{{ homepageContent.sections.about.paragraphOne }}</p>
            <p>{{ homepageContent.sections.about.paragraphTwo }}</p>
          </div>
        </div>
        <ul v-if="homepageContent.sections.about.principles?.length" class="ah-principles" data-reveal>
          <li v-for="principle in homepageContent.sections.about.principles" :key="principle">{{ principle }}</li>
        </ul>
        <div v-if="homepageContent.sections.about.features?.length" class="ah-features" data-reveal>
          <h3>{{ homepageContent.sections.about.featureTitle }}</h3>
          <div>
            <article
              v-for="(feature, index) in homepageContent.sections.about.features"
              :key="`${feature.title}-${index}`"
            >
              <h4>{{ feature.title }}</h4>
              <p>{{ feature.description }}</p>
            </article>
          </div>
        </div>
      </div>
    </section>

    <section v-if="displayOptions.showMembers" class="ah-chapter ah-chapter--dark" aria-labelledby="members">
      <div class="ah-wrap ah-wrap--wide">
        <header class="ah-head ah-head--split" data-reveal>
          <div>
            <h2 id="members">{{ homepageContent.sections.members.title }}</h2>
            <p>点开任意一位，查看公开主页。榜单每日刷新。</p>
          </div>
          <div v-if="peopleCards.length > 3" class="ah-rail-controls">
            <button type="button" class="ah-circle" aria-label="向左滚动成员" @click="scrollRail(peopleRail, -1)">
              <ChevronLeft :size="20" aria-hidden="true" />
            </button>
            <button type="button" class="ah-circle" aria-label="向右滚动成员" @click="scrollRail(peopleRail, 1)">
              <ChevronRight :size="20" aria-hidden="true" />
            </button>
          </div>
        </header>

        <div v-if="showMemberShowcase && peopleCards.length" ref="peopleRail" class="ah-people" data-reveal>
          <component
            :is="person.profileId ? 'RouterLink' : 'article'"
            v-for="person in peopleCards"
            :key="`${person.kind}-${person.profileId || person.name}`"
            class="ah-person"
            :class="`ah-person--${person.kind}`"
            :to="person.profileId ? `/members/${person.profileId}` : undefined"
            :aria-label="person.profileId ? `查看${person.name}的公开主页` : undefined"
          >
            <span class="ah-person-photo" :style="{ '--tone': person.tone }">
              <img v-if="person.avatarUrl" :src="person.avatarUrl" alt="" loading="lazy" />
              <b v-else>{{ person.initials }}</b>
            </span>
            <span class="ah-person-info">
              <small>{{ person.kind === 'advisor' ? '指导老师' : '核心成员' }}</small>
              <strong>
                <span class="ah-person-name-outline" aria-hidden="true">{{ person.name }}</span>
                <span class="ah-person-name">{{ person.name }}</span>
              </strong>
              <span>{{ person.role }}</span>
              <span v-if="person.tags?.length" class="ah-person-tags">
                <i v-for="tag in person.tags.slice(0, 3)" :key="tag">{{ tag }}</i>
              </span>
            </span>
          </component>
        </div>

        <div v-if="displayOptions.showLeaderboard" class="ah-leaderboard" data-reveal>
          <CompactLeaderboard
            :boards="rankingData"
            :total-count="rankingTotalCount"
            :updated-at="rankingsUpdatedAt"
            :loaded="rankingsLoaded"
            :error="rankingsError"
            @retry="syncPublicHome"
          />
        </div>
      </div>
    </section>

    <section
      v-if="displayOptions.showAchievements"
      class="ah-chapter ah-chapter--dark ah-chapter--flush"
      aria-labelledby="updates"
    >
      <div class="ah-wrap ah-wrap--wide">
        <header class="ah-head ah-head--split" data-reveal>
          <div>
            <h2 id="updates">{{ homepageContent.sections.achievements.title }}</h2>
            <p>{{ homepageContent.sections.achievements.description }}</p>
          </div>
          <div v-if="visibleCompetitionResults.length > 2" class="ah-rail-controls">
            <button type="button" class="ah-circle" aria-label="向左滚动成果" @click="scrollRail(awardRail, -1)">
              <ChevronLeft :size="20" aria-hidden="true" />
            </button>
            <button type="button" class="ah-circle" aria-label="向右滚动成果" @click="scrollRail(awardRail, 1)">
              <ChevronRight :size="20" aria-hidden="true" />
            </button>
          </div>
        </header>

        <div v-if="visibleCompetitionResults.length" ref="awardRail" class="ah-awards" data-reveal>
          <component
            :is="item.id ? 'RouterLink' : 'article'"
            v-for="(item, index) in visibleCompetitionResults"
            :key="item.id || `${item.name}-${index}`"
            class="ah-award"
            :to="item.id ? `/competition-results/${item.id}` : undefined"
          >
            <time>{{ competitionLevelLabels[item.level] || item.level }} · {{ item.competitionDate }}</time>
            <strong>{{ item.awardName }}</strong>
            <span
              ><b>{{ item.name }}</b
              >{{ item.track || '综合赛道' }}</span
            >
          </component>
        </div>

        <div v-if="visibleNewsItems.length" class="ah-news" data-reveal>
          <h3>相关新闻</h3>
          <component
            :is="item.url ? 'a' : 'article'"
            v-for="item in visibleNewsItems"
            :key="item.id || item.title"
            :href="item.url || undefined"
            :target="item.url ? '_blank' : undefined"
            :rel="item.url ? 'noopener noreferrer' : undefined"
          >
            <time>{{ item.date }}</time>
            <span
              ><strong>{{ item.title }}</strong
              ><small v-if="item.summary">{{ item.summary }}</small></span
            >
            <ArrowUpRight v-if="item.url" :size="18" aria-hidden="true" />
          </component>
        </div>
        <p
          v-if="!visibleCompetitionResults.length && !visibleNewsItems.length"
          class="ah-empty ah-empty--dark"
          data-reveal
        >
          获奖记录审核通过、新闻引用发布后会出现在这里。
        </p>
      </div>
    </section>

    <section v-if="displayOptions.showPartners && visibleSponsors.length" class="ah-chapter" aria-labelledby="partners">
      <div class="ah-wrap ah-wrap--wide">
        <header class="ah-head" data-reveal>
          <h2 id="partners">{{ homepageContent.sections.partners.title }}</h2>
          <p>{{ homepageContent.sections.partners.description }}</p>
        </header>
        <div class="ah-partners" data-reveal>
          <article v-for="sponsor in visibleSponsors" :key="sponsor.name" class="ah-partner">
            <a
              class="ah-partner-logo"
              :href="sponsor.websiteUrl"
              target="_blank"
              rel="noreferrer"
              :aria-label="`访问 ${sponsor.name} 官网`"
              ><img :src="sponsor.logoUrl" :alt="`${sponsor.name} 官方 Logo`" width="512" height="512" loading="lazy"
            /></a>
            <small>{{ sponsor.type }}</small>
            <h3>{{ sponsor.name }}</h3>
            <p>{{ sponsor.description }}</p>
            <p v-if="sponsor.cooperationDescription" class="ah-partner-coop">{{ sponsor.cooperationDescription }}</p>
            <ul v-if="sponsor.focus?.length">
              <li v-for="item in sponsor.focus" :key="item">{{ item }}</li>
            </ul>
            <a class="ah-link" :href="sponsor.websiteUrl" target="_blank" rel="noreferrer"
              >访问官网<ExternalLink :size="15" aria-hidden="true"
            /></a>
          </article>
        </div>
      </div>
    </section>

    <section
      v-if="displayOptions.showContact"
      class="ah-chapter ah-chapter--dark ah-join"
      aria-labelledby="ah-contact-title"
    >
      <div class="ah-wrap" data-reveal>
        <h2 id="ah-contact-title" class="preserve-lines">{{ homepageContent.sections.contact.title }}</h2>
        <p>{{ homepageContent.sections.contact.description }}</p>
        <ol class="ah-flow" aria-label="招新流程">
          <li v-for="(step, index) in recruitmentSteps" :key="step">
            <span>{{ index + 1 }}</span
            >{{ step }}
          </li>
        </ol>
        <div class="ah-join-actions">
          <RouterLink class="ah-pill ah-pill--lg" :to="authState.account ? accountDestination : '/register'">{{
            authState.account ? '进入成员系统' : '报名加入'
          }}</RouterLink>
          <a
            v-for="link in enabledExternalLinks"
            :key="`${link.platform}-${link.label}`"
            class="ah-link ah-link--light"
            :href="link.url"
            target="_blank"
            rel="noopener noreferrer"
            ><component :is="externalIcon(link.platform)" :size="17" aria-hidden="true" />{{ link.label }}</a
          >
        </div>
      </div>
    </section>

    <footer class="ah-footer">
      <a class="ah-brand" href="#top" :aria-label="`返回${profile.displayName}首页`" @click.prevent="scrollTo('#top')"
        ><img :src="brand.logo" :alt="profile.name" width="900" height="300"
      /></a>
      <p>{{ profile.name }} · {{ profile.fullName }}</p>
      <p>{{ footerText }}</p>
      <a class="ah-link" :href="brand.repositoryUrl" target="_blank" rel="noopener noreferrer"
        >{{ brand.repositoryUrl.replace(/^https?:\/\//, '') }}<ArrowUpRight :size="15" aria-hidden="true"
      /></a>
    </footer>

    <Transition :css="!useViewTransitions" name="modal">
      <div v-if="selectedProject" class="ah-sheet-scrim" @click.self="closeProject">
        <section
          class="ah-sheet"
          role="dialog"
          aria-modal="true"
          :aria-labelledby="`project-title-${selectedProject.number}`"
        >
          <div ref="sheetHero" class="ah-sheet-hero" :class="`ah-art-${selectedProjectIndex % 5}`">
            <img v-if="selectedProject.coverImageUrl" :src="selectedProject.coverImageUrl" alt="" />
            <button
              ref="modalClose"
              type="button"
              class="ah-sheet-close"
              aria-label="关闭项目详情"
              @click="closeProject"
            >
              <X :size="20" aria-hidden="true" />
            </button>
            <small>{{ selectedProject.category }} · {{ selectedProject.status }}</small>
            <h2 :id="`project-title-${selectedProject.number}`" ref="sheetTitle">
              {{ selectedProject.title.replace('\n', '') }}
            </h2>
          </div>
          <div class="ah-sheet-body">
            <p class="ah-sheet-summary">{{ selectedProject.summary }}</p>
            <dl class="ah-facts">
              <div>
                <dt>负责人</dt>
                <dd>{{ selectedProject.lead }}</dd>
              </div>
              <div>
                <dt>指导老师</dt>
                <dd>{{ selectedProject.advisor || '暂未关联' }}</dd>
              </div>
              <div>
                <dt>参与成员</dt>
                <dd>{{ selectedProject.members }}</dd>
              </div>
            </dl>
            <div v-if="selectedProject.tech.length" class="ah-tile-tags ah-tile-tags--plain">
              <i v-for="item in selectedProject.tech" :key="item">{{ item }}</i>
            </div>
            <div class="ah-sheet-result">
              <h3>阶段成果</h3>
              <p>{{ selectedProject.result }}</p>
            </div>
            <div class="ah-sheet-actions">
              <a
                v-if="selectedProject.repositoryUrl"
                class="ah-pill ah-pill--lg"
                :href="selectedProject.repositoryUrl"
                target="_blank"
                rel="noopener noreferrer"
                >打开项目仓库</a
              >
              <a
                v-if="selectedProject.documentUrl"
                class="ah-link"
                :href="selectedProject.documentUrl"
                target="_blank"
                rel="noopener noreferrer"
                >查看项目文档<ArrowRight :size="17" aria-hidden="true"
              /></a>
              <span v-if="!selectedProject.repositoryUrl && !selectedProject.documentUrl" class="ah-muted"
                >项目仓库与文档暂未公开</span
              >
            </div>
          </div>
        </section>
      </div>
    </Transition>
  </main>
</template>

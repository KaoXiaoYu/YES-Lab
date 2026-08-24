<script setup>
import {
  ArrowDownRight,
  ArrowRight,
  ArrowUpRight,
  Award,
  Code2,
  ExternalLink,
  Github,
  Menu,
  Play,
  Sparkles,
  Trophy,
  Users,
  X,
} from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import { fetchPublicHome } from './services/publicApi'

const menuOpen = ref(false)
const activeRanking = ref('总榜')
const selectedProject = ref(null)

const defaultProjects = [
  {
    number: '01', category: '智能交互', title: '低成本多模态\n实验工作台',
    summary: '让视觉、语言与传感器数据在同一套轻量系统中协同工作。', status: '进行中',
    lead: '范桌轩大王', members: '6 人', tech: ['Vue 3', 'Java', 'Edge AI'],
    result: '已完成原型验证与第一轮设备联调，计划开放核心工具链。',
  },
  {
    number: '02', category: '教育科技', title: '自适应学习\n反馈引擎',
    summary: '把学习过程转化为可理解、可调整、可持续的成长反馈。', status: '已发布',
    lead: '范桌轩大王', members: '4 人', tech: ['Spring Boot', 'NLP', 'Data Viz'],
    result: '完成校内测试，形成 1 项软件著作权与公开演示版本。',
  },
  {
    number: '03', category: '开放硬件', title: '城市环境\n感知节点',
    summary: '用开放硬件和低功耗网络观察校园与城市的微小变化。', status: '内测中',
    lead: '范桌轩大王', members: '8 人', tech: ['IoT', 'LoRa', 'Open Data'],
    result: '完成 12 个节点布设，累计采集环境数据 240 万条。',
  },
]

const defaultMembers = [
  { initials: 'FZ', name: '范桌轩大王', role: '2023 · 计算机科学', tags: ['全栈开发', '产品设计'], points: 2480, rank: 1 },
  { initials: 'YX', name: '范桌轩大王', role: '2022 · 人工智能', tags: ['算法研究', '计算机视觉'], points: 2210, rank: 2 },
  { initials: 'LC', name: '范桌轩大王', role: '2024 · 电子信息', tags: ['嵌入式', '开放硬件'], points: 1980, rank: 3 },
  { initials: 'WQ', name: '范桌轩大王', role: '2023 · 数字媒体', tags: ['交互设计', '内容创作'], points: 1750, rank: 4 },
]

const defaultRankingData = {
  总榜: [2480, 2210, 1980, 1750],
  月榜: [380, 350, 290, 265],
  年榜: [1240, 1180, 960, 845],
  'AI 应用': [920, 860, 740, 620],
  工程实现: [880, 810, 790, 650],
}

const defaultUpdates = [
  { date: '08.18', type: '项目动态', title: '低成本多模态实验工作台完成第一轮设备联调' },
  { date: '08.06', type: '竞赛成果', title: '范桌轩大王团队获得范桌轩大王创新挑战赛一等奖' },
  { date: '07.24', type: '开源发布', title: '城市环境感知节点数据处理工具正式开放' },
]

const profile = ref({
  name: 'YES Lab',
  displayName: '范桌轩大王实验室',
  slogan: '让想法被验证',
  description: '我们是一群持续发问、快速行动的人。在技术与真实世界相遇的地方，创造值得发生的答案。',
})
const statistics = ref({ activeProjects: 12, members: 28, achievements: 36 })
const projects = ref(defaultProjects)
const members = ref(defaultMembers)
const rankingData = ref(defaultRankingData)
const updates = ref(defaultUpdates)

const rankings = computed(() => members.value.map((member, index) => ({
  ...member,
  points: rankingData.value[activeRanking.value]?.[index] ?? member.points,
})).sort((a, b) => b.points - a.points))

onMounted(async () => {
  const home = await fetchPublicHome()
  if (!home) return

  profile.value = home.profile
  statistics.value = home.statistics
  projects.value = home.projects
  members.value = home.members
  rankingData.value = home.rankingData
  updates.value = home.updates
})

const scrollTo = (id) => {
  menuOpen.value = false
  document.querySelector(id)?.scrollIntoView({ behavior: 'smooth' })
}
</script>

<template>
  <main>
    <header class="site-header">
      <a class="brand" href="#top" aria-label="YES Lab 首页"><span class="brand-mark">Y</span><span>YES LAB</span></a>
      <nav :class="['top-nav', { open: menuOpen }]" aria-label="主导航">
        <button class="active" @click="scrollTo('#top')">公开展示</button>
        <button @click="scrollTo('#projects')">项目与成果</button>
        <button @click="scrollTo('#members')">成员社区</button>
        <span class="nav-divider"></span>
        <a href="#" @click.prevent>协作平台 <span>即将开放</span></a>
        <a href="#" @click.prevent>管理中心 <span>即将开放</span></a>
      </nav>
      <button class="menu-button" :aria-label="menuOpen ? '关闭菜单' : '打开菜单'" @click="menuOpen = !menuOpen"><X v-if="menuOpen" :size="22" /><Menu v-else :size="22" /></button>
    </header>

    <section id="top" class="hero">
      <div class="hero-noise"></div><div class="hero-orbit orbit-one"></div><div class="hero-orbit orbit-two"></div>
      <div class="hero-content">
        <p class="eyebrow"><Sparkles :size="15" /> {{ profile.name.toUpperCase() }} · {{ profile.displayName }}</p>
        <h1>让想法<br /><em>被验证</em></h1>
        <p class="hero-description">我们是一群持续发问、快速行动的人。<br />在技术与真实世界相遇的地方，创造值得发生的答案。</p>
        <div class="hero-actions"><button class="primary-action" @click="scrollTo('#projects')">探索我们的工作 <ArrowRight :size="18" /></button><button class="text-action" @click="scrollTo('#about')">认识 YES Lab <ArrowDownRight :size="18" /></button></div>
      </div>
      <div class="hero-data" aria-label="实验室概览"><div><strong>{{ statistics.activeProjects }}</strong><span>在研项目</span></div><div><strong>{{ statistics.members }}</strong><span>实验室成员</span></div><div><strong>{{ statistics.achievements }}</strong><span>公开成果</span></div></div>
      <div class="signal-card"><span class="signal-dot"></span><div><small>LAB STATUS</small><strong>持续探索中</strong></div><ExternalLink :size="16" /></div>
    </section>

    <section class="preview-band"><p>YES / THINK / BUILD / SHARE</p><span>公开 · 协作 · 成长</span></section>

    <section id="about" class="about section-pad">
      <div class="section-index">01 — ABOUT</div>
      <div class="about-copy"><p class="overline">关于我们</p><h2>不只做“正确”的题，<br />也寻找<em>值得回答</em>的问题。</h2></div>
      <div class="about-detail"><p>YES Lab 聚焦智能交互、教育科技与开放硬件。我们用真实项目连接研究、工程与表达，让每位成员都能在动手创造中找到自己的方向。</p><div class="principles"><span>01 先做出来</span><span>02 保持公开</span><span>03 彼此成就</span></div></div>
    </section>

    <section id="projects" class="projects section-pad">
      <div class="section-heading"><div><p class="section-index">02 — PROJECTS</p><h2>正在发生的<br /><em>项目与成果</em></h2></div><p>从一张草图到真实世界中的产品，记录每一次验证、迭代与开放。</p></div>
      <div class="project-grid">
        <article v-for="project in projects" :key="project.number" class="project-card" @click="selectedProject = project">
          <div class="project-meta"><span>{{ project.number }}</span><span>{{ project.category }}</span></div>
          <div class="project-visual" :class="`visual-${project.number}`" aria-hidden="true"><span></span><span></span><span></span></div>
          <h3><template v-for="line in project.title.split('\n')" :key="line">{{ line }}<br /></template></h3>
          <p>{{ project.summary }}</p>
          <div class="project-footer"><span><i></i>{{ project.status }}</span><button aria-label="查看项目详情"><ArrowUpRight :size="20" /></button></div>
        </article>
      </div>
      <div class="outcomes"><div><Award :size="24" /><strong>18</strong><span>竞赛奖项</span></div><div><Code2 :size="24" /><strong>9</strong><span>开源项目</span></div><div><Trophy :size="24" /><strong>5</strong><span>论文 / 专利</span></div><div><Play :size="24" /><strong>4</strong><span>视频作品</span></div></div>
    </section>

    <section id="members" class="members section-pad">
      <div class="section-heading light"><div><p class="section-index">03 — PEOPLE</p><h2>优秀的人，<br /><em>彼此照亮</em></h2></div><p>每位成员都有独立的成长轨迹。公开主页将沉淀参与项目、比赛经历与成果。</p></div>
      <div class="people-layout">
        <div class="member-grid">
          <article v-for="member in members" :key="member.rank" class="member-card">
            <div class="avatar">{{ member.initials }}</div><span class="rank-no">NO. {{ String(member.rank).padStart(2, '0') }}</span><h3>{{ member.name }}</h3><p>{{ member.role }}</p>
            <div class="tags"><span v-for="tag in member.tags" :key="tag">{{ tag }}</span></div><div class="member-bottom"><span>{{ member.points }} 积分</span><button aria-label="查看成员主页"><ArrowUpRight :size="18" /></button></div>
          </article>
        </div>
        <aside class="leaderboard">
          <div class="leaderboard-title"><span><Users :size="18" /> 成员排行榜</span><small>实时公开</small></div>
          <div class="ranking-tabs"><button v-for="tab in Object.keys(rankingData)" :key="tab" :class="{ active: activeRanking === tab }" @click="activeRanking = tab">{{ tab }}</button></div>
          <ol><li v-for="(member, index) in rankings" :key="member.initials"><strong>{{ String(index + 1).padStart(2, '0') }}</strong><span class="mini-avatar">{{ member.initials }}</span><div><b>{{ member.name }}</b><small>{{ member.tags[0] }}</small></div><em>{{ member.points }}</em></li></ol>
        </aside>
      </div>
    </section>

    <section id="updates" class="updates section-pad">
      <div class="section-heading"><div><p class="section-index">04 — UPDATES</p><h2>实验室<br /><em>最新动态</em></h2></div><p>公开每一个值得记住的节点。这里将由后台统一发布并自动同步到首页。</p></div>
      <div class="update-list"><article v-for="item in updates" :key="item.date"><time>2026.{{ item.date }}</time><span>{{ item.type }}</span><h3>{{ item.title }}</h3><button aria-label="查看动态"><ArrowUpRight :size="20" /></button></article></div>
    </section>

    <section class="connect"><div><p class="section-index">CONNECT WITH US</p><h2>保持好奇，<br />欢迎来找我们。</h2></div><div class="connect-links"><a href="https://github.com" target="_blank" rel="noreferrer"><Github :size="20" /> 开源仓库 <ArrowUpRight :size="18" /></a><a href="#" @click.prevent>哔哩哔哩 <ArrowUpRight :size="18" /></a><a href="#" @click.prevent>微信公众号 <ArrowUpRight :size="18" /></a><a href="#" @click.prevent>抖音 <ArrowUpRight :size="18" /></a></div></section>

    <footer><a class="brand" href="#top"><span class="brand-mark">Y</span><span>YES LAB</span></a><p>© 2026 范桌轩大王实验室 · BUILD WITH CURIOSITY</p></footer>

    <div v-if="selectedProject" class="modal-backdrop" @click.self="selectedProject = null">
      <section class="project-modal" role="dialog" aria-modal="true" :aria-label="`${selectedProject.title}项目详情`">
        <button class="modal-close" aria-label="关闭详情" @click="selectedProject = null"><X :size="22" /></button><p class="overline">PROJECT {{ selectedProject.number }} · {{ selectedProject.category }}</p><h2>{{ selectedProject.title.replace('\n', '') }}</h2><p class="modal-summary">{{ selectedProject.summary }}</p>
        <dl><div><dt>当前状态</dt><dd>{{ selectedProject.status }}</dd></div><div><dt>负责人</dt><dd>{{ selectedProject.lead }}</dd></div><div><dt>参与成员</dt><dd>{{ selectedProject.members }}</dd></div><div><dt>技术方向</dt><dd>{{ selectedProject.tech.join(' / ') }}</dd></div><div class="full"><dt>阶段成果</dt><dd>{{ selectedProject.result }}</dd></div></dl>
        <button class="primary-action">访问项目仓库 <ArrowRight :size="18" /></button>
      </section>
    </div>
  </main>
</template>

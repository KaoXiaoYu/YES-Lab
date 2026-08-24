<script setup>
import {
  ArrowDownRight,
  ArrowRight,
  ArrowUpRight,
  ExternalLink,
  Github,
  GraduationCap,
  Handshake,
  Menu,
  Orbit,
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
  { initials: 'FZ', name: '范桌轩大王', role: '2023 · 计算机科学', tags: ['无人机系统', '工程实现'], points: 2480, rank: 1 },
  { initials: 'YX', name: '范桌轩大王', role: '2022 · 人工智能', tags: ['计算机视觉', '具身智能'], points: 2210, rank: 2 },
  { initials: 'LC', name: '范桌轩大王', role: '2024 · 电子信息', tags: ['嵌入式', '机器人控制'], points: 1980, rank: 3 },
  { initials: 'WQ', name: '范桌轩大王', role: '2023 · 自动化', tags: ['多智能体', '系统设计'], points: 1750, rank: 4 },
]

const defaultRankingData = {
  总榜: [2480, 2210, 1980, 1750],
  月榜: [380, 350, 290, 265],
  年榜: [1240, 1180, 960, 845],
  无人机: [920, 860, 740, 620],
  空地协同: [880, 810, 790, 650],
  具身智能: [850, 820, 760, 690],
}

const defaultUpdates = [
  { date: '荣誉', type: '竞赛成果', title: 'YES Lab 获得计算机设计大赛全国二等奖' },
  { date: '方向', type: '研究动态', title: '推进无人机与机器狗空地协同系统研究' },
  { date: '伙伴', type: '企业支持', title: 'CUAV 成为 YES Lab 企业赞助伙伴' },
]

const defaultSponsors = [
  {
    name: 'CUAV',
    type: '企业赞助伙伴',
    description: '感谢 CUAV 对 YES Lab 无人系统研究、工程实践与人才培养的支持。',
    focus: ['无人机系统', '工程实践', '人才培养'],
  },
]

const profile = ref({
  name: 'YES Lab',
  displayName: 'YES Lab 实验室',
  slogan: '探索空地协同，培养未来工程人才',
  description: '一个面向无人系统与具身智能的初创实验室，以真实项目连接科研、竞赛与人才培养。',
  researchDirections: ['无人机', '空地协同', '具身智能'],
})
const statistics = ref({ activeProjects: 3, members: 0, achievements: 1 })
const projects = ref(defaultProjects)
const members = ref(defaultMembers)
const rankingData = ref(defaultRankingData)
const updates = ref(defaultUpdates)
const sponsors = ref(defaultSponsors)

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
  sponsors.value = home.sponsors?.length ? home.sponsors : defaultSponsors
})

const scrollTo = (id) => {
  menuOpen.value = false
  document.querySelector(id)?.scrollIntoView({ behavior: 'smooth' })
}
</script>

<template>
  <main>
    <header class="site-header">
      <a class="brand" href="#top" aria-label="YES Lab 首页"><span class="brand-logo"><img src="/yes-lab-logo.png" alt="YES Lab" width="900" height="506" /></span><small>PUBLIC / 01</small></a>
      <nav :class="['top-nav', { open: menuOpen }]" aria-label="主导航">
        <button class="active" @click="scrollTo('#top')">公开展示</button>
        <button @click="scrollTo('#projects')">项目与成果</button>
        <button @click="scrollTo('#members')">成员社区</button>
        <button @click="scrollTo('#partners')">赞助伙伴</button>
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
        <h1>探索空地<br /><em>协同未来</em></h1>
        <p class="hero-description">{{ profile.description }}<br />从无人机到机器狗，让智能体在真实世界中感知、协作与行动。</p>
        <div class="hero-actions"><button class="primary-action" @click="scrollTo('#projects')">探索我们的工作 <ArrowRight :size="18" /></button><button class="text-action" @click="scrollTo('#about')">认识 YES Lab <ArrowDownRight :size="18" /></button></div>
      </div>
      <div class="hero-data" aria-label="实验室概览"><div><strong>NATIONAL</strong><span>计算机设计大赛全国二等奖</span></div><div><strong>3</strong><span>核心研究方向</span></div><div><strong>CUAV</strong><span>企业赞助伙伴</span></div></div>
      <div class="signal-card"><span class="signal-dot"></span><div><small>LAB STATUS</small><strong>持续探索中</strong></div><ExternalLink :size="16" /></div>
    </section>

    <section class="preview-band"><p>FLY / PERCEIVE / COLLABORATE / ACT</p><span>无人机 · 空地协同 · 具身智能</span></section>

    <section id="about" class="about section-pad">
      <div class="section-index">01 — ABOUT</div>
      <div class="about-copy"><p class="overline">关于我们</p><h2>让智能体<br />真正走进<em>物理世界</em>。</h2></div>
      <div class="about-detail"><p>YES Lab 是一个处于起步阶段的实验室，主要研究无人机、无人机与机器狗空地协同、具身智能等方向。我们以竞赛与真实工程项目为牵引，为学校培养具备算法、硬件和系统能力的复合型人才。</p><div class="principles"><span>01 面向真实场景</span><span>02 强调系统协同</span><span>03 培养工程人才</span></div></div>
    </section>

    <section id="projects" class="projects section-pad">
      <div class="section-heading"><div><p class="section-index">02 — RESEARCH</p><h2>面向真实世界的<br /><em>研究与工程</em></h2></div><p>围绕空中、地面与智能体之间的协同关系，持续完成从算法到系统的验证。</p></div>
      <div class="project-grid">
        <article v-for="project in projects" :key="project.number" class="project-card" @click="selectedProject = project">
          <div class="project-meta"><span>{{ project.number }}</span><span>{{ project.category }}</span></div>
          <div class="project-visual" :class="`visual-${project.number}`" aria-hidden="true"><span></span><span></span><span></span></div>
          <h3><template v-for="line in project.title.split('\n')" :key="line">{{ line }}<br /></template></h3>
          <p>{{ project.summary }}</p>
          <div class="project-footer"><span><i></i>{{ project.status }}</span><button aria-label="查看项目详情"><ArrowUpRight :size="20" /></button></div>
        </article>
      </div>
      <div class="outcomes"><div><Trophy :size="24" /><strong>全国二等奖</strong><span>计算机设计大赛</span></div><div><Orbit :size="24" /><strong>空地协同</strong><span>异构无人系统</span></div><div><GraduationCap :size="24" /><strong>人才培养</strong><span>项目驱动成长</span></div><div><Handshake :size="24" /><strong>CUAV</strong><span>企业赞助伙伴</span></div></div>
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

    <section id="partners" class="partners section-pad">
      <div class="section-heading light"><div><p class="section-index">04 — PARTNERS</p><h2>一起把探索<br /><em>推向真实世界</em></h2></div><p>感谢企业伙伴为实验室的研究实践与人才培养提供支持。</p></div>
      <div class="sponsor-list">
        <article v-for="sponsor in sponsors" :key="sponsor.name" class="sponsor-card">
          <div class="sponsor-code">PARTNER / {{ String(sponsors.indexOf(sponsor) + 1).padStart(2, '0') }}</div>
          <strong>{{ sponsor.name }}</strong>
          <div><p>{{ sponsor.type }}</p><span>{{ sponsor.description }}</span></div>
          <ul><li v-for="item in sponsor.focus" :key="item">{{ item }}</li></ul>
        </article>
      </div>
    </section>

    <section id="updates" class="updates section-pad">
      <div class="section-heading"><div><p class="section-index">05 — UPDATES</p><h2>实验室<br /><em>最新动态</em></h2></div><p>公开每一个值得记住的节点，持续记录研究、竞赛与合作进展。</p></div>
      <div class="update-list"><article v-for="item in updates" :key="item.title"><time>{{ item.date }}</time><span>{{ item.type }}</span><h3>{{ item.title }}</h3><button aria-label="查看动态"><ArrowUpRight :size="20" /></button></article></div>
    </section>

    <section class="connect"><div><p class="section-index">CONNECT WITH US</p><h2>保持好奇，<br />欢迎来找我们。</h2></div><div class="connect-links"><a href="https://github.com" target="_blank" rel="noreferrer"><Github :size="20" /> 开源仓库 <ArrowUpRight :size="18" /></a><a href="#" @click.prevent>哔哩哔哩 <ArrowUpRight :size="18" /></a><a href="#" @click.prevent>微信公众号 <ArrowUpRight :size="18" /></a><a href="#" @click.prevent>抖音 <ArrowUpRight :size="18" /></a></div></section>

    <footer><a class="brand" href="#top" aria-label="返回 YES Lab 首页"><span class="brand-logo"><img src="/yes-lab-logo.png" alt="YES Lab" width="900" height="506" /></span><small>PUBLIC / 01</small></a><p>© 2026 YES Lab · INTELLIGENCE IN MOTION</p></footer>

    <div v-if="selectedProject" class="modal-backdrop" @click.self="selectedProject = null">
      <section class="project-modal" role="dialog" aria-modal="true" :aria-label="`${selectedProject.title}项目详情`">
        <button class="modal-close" aria-label="关闭详情" @click="selectedProject = null"><X :size="22" /></button><p class="overline">PROJECT {{ selectedProject.number }} · {{ selectedProject.category }}</p><h2>{{ selectedProject.title.replace('\n', '') }}</h2><p class="modal-summary">{{ selectedProject.summary }}</p>
        <dl><div><dt>当前状态</dt><dd>{{ selectedProject.status }}</dd></div><div><dt>负责人</dt><dd>{{ selectedProject.lead }}</dd></div><div><dt>参与成员</dt><dd>{{ selectedProject.members }}</dd></div><div><dt>技术方向</dt><dd>{{ selectedProject.tech.join(' / ') }}</dd></div><div class="full"><dt>阶段成果</dt><dd>{{ selectedProject.result }}</dd></div></dl>
        <button class="primary-action">访问项目仓库 <ArrowRight :size="18" /></button>
      </section>
    </div>
  </main>
</template>

<script setup>
import {
  ArrowDown, ArrowUp, Award, Building2, Check, ExternalLink, Eye, FileText,
  FolderKanban, LayoutTemplate, Link2, Plus, Save, Search, Trash2, UsersRound,
} from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import PortalShell from '../components/PortalShell.vue'
import SearchableMemberSelect from '../components/SearchableMemberSelect.vue'
import {
  getHomepageContent, listMembers, listProjects, updateHomepageContent,
} from '../services/authApi'

const tabs = [
  { id: 'identity', label: '品牌与首屏', icon: LayoutTemplate },
  { id: 'sections', label: '栏目文案', icon: FileText },
  { id: 'display', label: '展示选择', icon: Eye },
  { id: 'proof', label: '概览与比赛', icon: Award },
  { id: 'updates', label: '首页动态', icon: FileText },
  { id: 'sponsors', label: '赞助伙伴', icon: Building2 },
  { id: 'links', label: '外部入口', icon: Link2 },
]

const activeTab = ref('identity')
const content = ref(null)
const members = ref([])
const projects = ref([])
const loading = ref(true)
const saving = ref(false)
const message = ref('')
const errorMessage = ref('')
const updatedAt = ref(null)
const updatedBy = ref('')
const featuredMemberSearch = ref('')

const teacherOptions = computed(() => members.value.filter((member) => member.role === 'TEACHER'))
const memberOptions = computed(() => members.value.filter((member) => member.role !== 'TEACHER' && ['OFFICIAL', 'TRIAL'].includes(member.status)))
const selectedMembers = computed(() => orderedOptions(content.value?.featuredMemberProfileIds, memberOptions.value))
const selectedProjects = computed(() => orderedOptions(content.value?.featuredProjectIds, projects.value))
const filteredMemberOptions = computed(() => {
  const keyword = featuredMemberSearch.value.trim().toLocaleLowerCase()
  return keyword ? memberOptions.value.filter((item) => `${item.name} ${item.memberCode || ''}`.toLocaleLowerCase().includes(keyword)) : memberOptions.value
})

onMounted(async () => {
  try {
    const [homepage, memberData, projectData] = await Promise.all([
      getHomepageContent(), listMembers(), listProjects(),
    ])
    content.value = structuredClone(homepage.content)
    members.value = memberData
    projects.value = projectData
    updatedAt.value = homepage.updatedAt
    updatedBy.value = homepage.updatedBy || ''
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
})

function orderedOptions(ids = [], options = []) {
  const byId = new Map(options.map((item) => [item.id, item]))
  return ids.map((id) => byId.get(id)).filter(Boolean)
}

function toggleSelection(list, id, checked) {
  const index = list.indexOf(id)
  if (checked && index < 0) list.push(id)
  if (!checked && index >= 0) list.splice(index, 1)
}

function move(list, index, delta) {
  const next = index + delta
  if (next < 0 || next >= list.length) return
  const [item] = list.splice(index, 1)
  list.splice(next, 0, item)
}

function remove(list, index) {
  list.splice(index, 1)
}

function addText(list) {
  list.push('范桌轩大王')
}

function addDirection() {
  content.value.profile.researchDirectionItems.push({ name: '范桌轩大王', url: '#projects' })
}

function addAboutFeature() {
  content.value.sections.about.features.push({ title: '范桌轩大王', description: '待补充特色说明' })
}

function splitList(value) {
  return value.split(/[,，\n]/).map((item) => item.trim()).filter(Boolean)
}

function addProof() {
  content.value.proofItems.push({ label: 'NEW / ITEM', value: '范桌轩大王', detail: '待补充说明' })
}

function addAward() {
  content.value.awards.push({ competition: '范桌轩大王', category: '范桌轩大王', level: '级别', prize: '奖项' })
}

function addUpdate() {
  content.value.updates.push({ publishedAt: '最新', type: '实验室动态', title: '范桌轩大王', slug: '' })
}

function addSponsor() {
  content.value.sponsors.push({ name: '范桌轩大王', type: '合作伙伴', description: '范桌轩大王', focus: ['人才培养'], logoUrl: '/yes-lab-logo.png', websiteUrl: 'https://example.com' })
}

function addLink() {
  content.value.externalLinks.push({ platform: 'website', label: '外部入口', url: 'https://example.com', enabled: false })
}

async function save() {
  saving.value = true
  message.value = ''
  errorMessage.value = ''
  try {
    const saved = await updateHomepageContent(content.value)
    content.value = structuredClone(saved.content)
    updatedAt.value = saved.updatedAt
    updatedBy.value = saved.updatedBy || ''
    message.value = '主页内容已保存，公开展示页刷新后生效。'
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } catch (error) {
    const firstFieldError = Object.values(error.fields || {})[0]
    errorMessage.value = firstFieldError ? `${error.message}：${firstFieldError}` : error.message
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } finally {
    saving.value = false
  }
}

function formatTime(value) {
  return value ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '尚未保存过'
}
</script>

<template>
  <PortalShell eyebrow="ADMIN / PUBLIC HOMEPAGE" title="主页编辑" description="统一维护公开展示页内容；成员、项目、比赛和新闻的详细数据继续由对应业务模块管理。">
    <div v-if="message" class="save-message" role="status"><Check :size="17" aria-hidden="true" />{{ message }}</div>
    <div v-if="errorMessage" class="form-alert" role="alert">{{ errorMessage }}</div>
    <div v-if="loading" class="portal-state">正在读取主页内容…</div>

    <form v-else-if="content" class="homepage-editor" @submit.prevent="save">
      <aside class="homepage-editor-nav">
        <header><p>CONTENT SECTIONS</p><h2>编辑目录</h2></header>
        <button v-for="tab in tabs" :key="tab.id" type="button" :class="{ active: activeTab === tab.id }" @click="activeTab = tab.id"><component :is="tab.icon" :size="17" aria-hidden="true" />{{ tab.label }}</button>
        <div><small>最后保存</small><strong>{{ formatTime(updatedAt) }}</strong><span v-if="updatedBy">操作人：{{ updatedBy }}</span></div>
      </aside>

      <main class="homepage-editor-main">
        <section v-show="activeTab === 'identity'" class="homepage-editor-section">
          <header><p>01 / IDENTITY</p><h2>品牌与首屏</h2><span>维护实验室名称、首屏标题、简介和研究方向。</span></header>
          <div class="homepage-field-grid">
            <label>简称<input v-model.trim="content.profile.name" required maxlength="80" /></label>
            <label>展示名称<input v-model.trim="content.profile.displayName" required maxlength="120" /></label>
            <label class="full">英文全称<input v-model.trim="content.profile.fullName" required maxlength="160" /></label>
            <label class="full">实验室口号<input v-model.trim="content.profile.slogan" required maxlength="180" /></label>
            <label class="full">实验室简介<textarea v-model.trim="content.profile.description" required rows="5" maxlength="5000"></textarea></label>
            <label class="full">首屏眉题<input v-model.trim="content.profile.heroEyebrow" required maxlength="120" /></label>
            <label>首屏主标题<textarea v-model="content.profile.heroTitle" required rows="3" maxlength="180"></textarea><small>支持换行。</small></label>
            <label>强调文字<input v-model.trim="content.profile.heroAccent" required maxlength="80" /></label>
            <label>主按钮文字<input v-model.trim="content.profile.primaryActionLabel" required maxlength="60" /></label>
            <label>次按钮文字<input v-model.trim="content.profile.secondaryActionLabel" required maxlength="60" /></label>
          </div>
          <div class="homepage-array-block">
            <header><div><h3>研究方向</h3><p>可跳转到页内分区、站内页面或外部网站。页内可用 #projects、#about、#members、#partners 或 #updates。</p></div><button type="button" :disabled="content.profile.researchDirectionItems.length >= 12" @click="addDirection"><Plus :size="16" aria-hidden="true" />添加</button></header>
            <div class="homepage-direction-list">
              <article v-for="(item, index) in content.profile.researchDirectionItems" :key="`${item.name}-${index}`" class="homepage-direction-row">
                <span>{{ String(index + 1).padStart(2, '0') }}</span>
                <label>方向名称<input v-model.trim="item.name" required maxlength="80" /></label>
                <label>跳转地址（可选）<input v-model.trim="item.url" maxlength="800" placeholder="#projects 或 https://example.com" /></label>
                <button type="button" :disabled="content.profile.researchDirectionItems.length <= 1" aria-label="删除研究方向" @click="remove(content.profile.researchDirectionItems, index)"><Trash2 :size="16" aria-hidden="true" /></button>
              </article>
            </div>
          </div>
        </section>

        <section v-show="activeTab === 'sections'" class="homepage-editor-section">
          <header><p>02 / SECTION COPY</p><h2>栏目文案</h2><span>栏目顺序由页面设计固定，所有展示标题和说明可在此修改。</span></header>
          <article v-for="(section, key) in { projects: content.sections.projects, members: content.sections.members, partners: content.sections.partners, achievements: content.sections.achievements }" :key="key" class="homepage-copy-card">
            <h3>{{ { projects: '项目栏目', members: '成员栏目', partners: '赞助伙伴栏目', achievements: '成果栏目' }[key] }}</h3>
            <div class="homepage-field-grid"><label>眉题<input v-model.trim="section.eyebrow" required maxlength="80" /></label><label>标题<input v-model.trim="section.title" required maxlength="160" /></label><label class="full">说明<textarea v-model.trim="section.description" required rows="3" maxlength="500"></textarea></label></div>
          </article>
          <article class="homepage-copy-card">
            <h3>关于我们</h3>
            <div class="homepage-field-grid"><label>眉题<input v-model.trim="content.sections.about.eyebrow" required maxlength="80" /></label><label>标题<textarea v-model="content.sections.about.title" required rows="2" maxlength="200"></textarea></label><label class="full">第一段<textarea v-model.trim="content.sections.about.paragraphOne" required rows="4" maxlength="2000"></textarea></label><label class="full">第二段<textarea v-model.trim="content.sections.about.paragraphTwo" required rows="4" maxlength="2000"></textarea></label></div>
            <div class="homepage-array-block compact"><header><div><h3>实验室原则</h3><p>用简短关键词概括实验室的研究与培养方式。</p></div><button type="button" :disabled="content.sections.about.principles.length >= 8" @click="addText(content.sections.about.principles)"><Plus :size="16" aria-hidden="true" />添加</button></header><div class="homepage-simple-list"><div v-for="(item, index) in content.sections.about.principles" :key="index"><span>{{ index + 1 }}</span><input v-model.trim="content.sections.about.principles[index]" required maxlength="120" :aria-label="`实验室原则 ${index + 1}`" /><button type="button" aria-label="删除原则" @click="remove(content.sections.about.principles, index)"><Trash2 :size="16" aria-hidden="true" /></button></div></div></div>
            <div class="homepage-array-block compact">
              <header><div><h3>特色展示卡片</h3><p>代替与成果栏重复的奖项列表，用于完整说明实验室的研究方法和人才培养特色。</p></div><button type="button" :disabled="content.sections.about.features.length >= 8" @click="addAboutFeature"><Plus :size="16" aria-hidden="true" />添加</button></header>
              <div class="homepage-field-grid"><label>卡片区眉题<input v-model.trim="content.sections.about.featureEyebrow" required maxlength="80" /></label><label>卡片区标题<input v-model.trim="content.sections.about.featureTitle" required maxlength="180" /></label></div>
              <div class="homepage-about-feature-list">
                <article v-for="(item, index) in content.sections.about.features" :key="`${item.title}-${index}`" class="homepage-about-feature-editor">
                  <header><span>{{ String(index + 1).padStart(2, '0') }}</span><strong>{{ item.title || '未命名特色' }}</strong><div class="row-actions"><button type="button" :disabled="index === 0" aria-label="上移特色" @click="move(content.sections.about.features, index, -1)"><ArrowUp :size="15" aria-hidden="true" /></button><button type="button" :disabled="index === content.sections.about.features.length - 1" aria-label="下移特色" @click="move(content.sections.about.features, index, 1)"><ArrowDown :size="15" aria-hidden="true" /></button><button type="button" aria-label="删除特色" @click="remove(content.sections.about.features, index)"><Trash2 :size="15" aria-hidden="true" /></button></div></header>
                  <div class="homepage-field-grid"><label>标题<input v-model.trim="item.title" required maxlength="120" /></label><label class="full">说明<textarea v-model.trim="item.description" required rows="3" maxlength="600"></textarea></label></div>
                </article>
              </div>
            </div>
          </article>
          <article class="homepage-copy-card"><h3>联系区域与页脚</h3><div class="homepage-field-grid"><label>眉题<input v-model.trim="content.sections.contact.eyebrow" required /></label><label>标题<textarea v-model="content.sections.contact.title" required rows="2"></textarea></label><label class="full">说明<textarea v-model.trim="content.sections.contact.description" required rows="3"></textarea></label><label class="full">页脚文字<input v-model.trim="content.sections.footerText" required maxlength="180" /></label></div></article>
        </section>

        <section v-show="activeTab === 'display'" class="homepage-editor-section">
          <header><p>03 / FEATURED CONTENT</p><h2>展示选择</h2><span>选择首页固定展示的指导老师、核心成员和项目，并通过顺序按钮调整排列。</span></header>
          <article class="homepage-selection-card"><header><div><h3>指导老师</h3><p>详细资料请前往成员管理修改。</p></div><RouterLink to="/admin/members">成员管理 <ExternalLink :size="15" aria-hidden="true" /></RouterLink></header><SearchableMemberSelect v-model="content.advisorProfileId" :options="teacherOptions" label="首页指导老师" empty-label="自动选择第一位教师" null-on-empty /></article>
          <article class="homepage-selection-card"><header><div><h3>核心成员</h3><p>勾选后按选择顺序展示；未选择时自动使用核心学生账号。</p></div></header><label class="member-picker-search"><Search :size="16" aria-hidden="true" /><span class="sr-only">搜索首页展示成员</span><input v-model.trim="featuredMemberSearch" type="search" placeholder="按姓名或学号搜索成员" /></label><div class="homepage-option-grid"><label v-for="member in filteredMemberOptions" :key="member.id"><input type="checkbox" :checked="content.featuredMemberProfileIds.includes(member.id)" @change="toggleSelection(content.featuredMemberProfileIds, member.id, $event.target.checked)" /><span><strong>{{ member.name }}</strong><small>{{ member.memberCode }} · {{ member.role }} · {{ member.status }}</small></span></label><p v-if="!filteredMemberOptions.length" class="empty-note">没有匹配姓名或学号的成员。</p></div><ol class="homepage-order-list"><li v-for="(member, index) in selectedMembers" :key="member.id"><span>{{ index + 1 }}</span><strong>{{ member.name }}</strong><button type="button" :disabled="index === 0" aria-label="上移成员" @click="move(content.featuredMemberProfileIds, index, -1)"><ArrowUp :size="15" aria-hidden="true" /></button><button type="button" :disabled="index === selectedMembers.length - 1" aria-label="下移成员" @click="move(content.featuredMemberProfileIds, index, 1)"><ArrowDown :size="15" aria-hidden="true" /></button></li></ol></article>
          <article class="homepage-selection-card"><header><div><h3>首页项目</h3><p>项目还必须在项目资料中开启“允许公开展示”；未选择时展示全部公开项目。</p></div><RouterLink to="/projects">项目团队 <ExternalLink :size="15" aria-hidden="true" /></RouterLink></header><div class="homepage-option-grid"><label v-for="project in projects" :key="project.id"><input type="checkbox" :checked="content.featuredProjectIds.includes(project.id)" @change="toggleSelection(content.featuredProjectIds, project.id, $event.target.checked)" /><span><strong>{{ project.projectName }}</strong><small>{{ project.externallyVisible ? '已公开' : '尚未开启公开展示' }}</small></span></label></div><ol class="homepage-order-list"><li v-for="(project, index) in selectedProjects" :key="project.id"><span>{{ index + 1 }}</span><strong>{{ project.projectName }}</strong><button type="button" :disabled="index === 0" aria-label="上移项目" @click="move(content.featuredProjectIds, index, -1)"><ArrowUp :size="15" aria-hidden="true" /></button><button type="button" :disabled="index === selectedProjects.length - 1" aria-label="下移项目" @click="move(content.featuredProjectIds, index, 1)"><ArrowDown :size="15" aria-hidden="true" /></button></li></ol></article>
        </section>

        <section v-show="activeTab === 'proof'" class="homepage-editor-section">
          <header><p>04 / PROOF & COMPETITIONS</p><h2>概览与备用比赛成果</h2><span>维护首屏下方概览条；比赛列表只在成果管理中暂无公开比赛时作为备用内容，不再与首页第二栏重复。</span></header>
          <div class="homepage-array-block"><header><div><h3>概览条</h3><p>建议保留 4 项，最多 6 项。</p></div><button type="button" :disabled="content.proofItems.length >= 6" @click="addProof"><Plus :size="16" aria-hidden="true" />添加</button></header><article v-for="(item, index) in content.proofItems" :key="index" class="homepage-row-card three"><input v-model.trim="item.label" required placeholder="标签" aria-label="概览标签" /><input v-model.trim="item.value" required placeholder="主要内容" aria-label="概览主要内容" /><input v-model.trim="item.detail" required placeholder="补充说明" aria-label="概览补充说明" /><button type="button" aria-label="删除概览项" @click="remove(content.proofItems, index)"><Trash2 :size="16" aria-hidden="true" /></button></article></div>
          <div class="homepage-array-block"><header><div><h3>备用比赛成果</h3><p>正式成果请在“成果与新闻管理”中审核和排序；此处仅用于无正式数据时的备用展示。</p></div><button type="button" @click="addAward"><Plus :size="16" aria-hidden="true" />添加</button></header><article v-for="(item, index) in content.awards" :key="index" class="homepage-row-card award-row"><input v-model.trim="item.competition" required placeholder="比赛名称" aria-label="比赛名称" /><input v-model.trim="item.category" required placeholder="赛道/组别" aria-label="赛道或组别" /><input v-model.trim="item.level" required placeholder="级别" aria-label="比赛级别" /><input v-model.trim="item.prize" required placeholder="奖项" aria-label="获奖等级" /><span class="row-actions"><button type="button" :disabled="index === 0" aria-label="上移奖项" @click="move(content.awards, index, -1)"><ArrowUp :size="15" aria-hidden="true" /></button><button type="button" :disabled="index === content.awards.length - 1" aria-label="下移奖项" @click="move(content.awards, index, 1)"><ArrowDown :size="15" aria-hidden="true" /></button><button type="button" aria-label="删除奖项" @click="remove(content.awards, index)"><Trash2 :size="15" aria-hidden="true" /></button></span></article><div class="homepage-section-action"><RouterLink to="/admin/achievements">前往成果与新闻管理 <ExternalLink :size="16" aria-hidden="true" /></RouterLink></div></div>
        </section>

        <section v-show="activeTab === 'updates'" class="homepage-editor-section">
          <header><p>05 / FALLBACK UPDATES</p><h2>首页动态</h2><span>当新闻模块暂无公开新闻时显示这些动态；新闻内容仍在成果管理维护。</span></header>
          <div class="homepage-section-action"><RouterLink to="/admin/achievements">前往成果与新闻管理 <ExternalLink :size="16" aria-hidden="true" /></RouterLink><button type="button" @click="addUpdate"><Plus :size="16" aria-hidden="true" />添加动态</button></div>
          <article v-for="(item, index) in content.updates" :key="index" class="homepage-row-card update-row"><input v-model.trim="item.publishedAt" required placeholder="时间标签" aria-label="动态时间标签" /><input v-model.trim="item.type" required placeholder="类型" aria-label="动态类型" /><input v-model.trim="item.title" required placeholder="标题" aria-label="动态标题" /><input v-model.trim="item.slug" placeholder="内部标识（可选）" aria-label="动态内部标识" /><span class="row-actions"><button type="button" :disabled="index === 0" aria-label="上移动态" @click="move(content.updates, index, -1)"><ArrowUp :size="15" aria-hidden="true" /></button><button type="button" :disabled="index === content.updates.length - 1" aria-label="下移动态" @click="move(content.updates, index, 1)"><ArrowDown :size="15" aria-hidden="true" /></button><button type="button" aria-label="删除动态" @click="remove(content.updates, index)"><Trash2 :size="15" aria-hidden="true" /></button></span></article>
        </section>

        <section v-show="activeTab === 'sponsors'" class="homepage-editor-section">
          <header><p>06 / PARTNERS</p><h2>赞助伙伴</h2><span>维护企业名称、说明、Logo 地址、官网和展示顺序。</span></header>
          <div class="homepage-section-action"><span>Logo 可使用 https 地址或以 / 开头的站内路径。</span><button type="button" @click="addSponsor"><Plus :size="16" aria-hidden="true" />添加伙伴</button></div>
          <article v-for="(item, index) in content.sponsors" :key="index" class="homepage-sponsor-editor"><header><div><span>{{ String(index + 1).padStart(2, '0') }}</span><h3>{{ item.name || '未命名伙伴' }}</h3></div><div><button type="button" :disabled="index === 0" aria-label="上移伙伴" @click="move(content.sponsors, index, -1)"><ArrowUp :size="15" aria-hidden="true" /></button><button type="button" :disabled="index === content.sponsors.length - 1" aria-label="下移伙伴" @click="move(content.sponsors, index, 1)"><ArrowDown :size="15" aria-hidden="true" /></button><button type="button" aria-label="删除伙伴" @click="remove(content.sponsors, index)"><Trash2 :size="15" aria-hidden="true" /></button></div></header><div class="homepage-field-grid"><label>企业名称<input v-model.trim="item.name" required /></label><label>合作类型<input v-model.trim="item.type" required /></label><label class="full">介绍<textarea v-model.trim="item.description" required rows="4"></textarea></label><label>Logo 地址<input v-model.trim="item.logoUrl" required /></label><label>官方网站<input v-model.trim="item.websiteUrl" type="url" required /></label><label class="full">合作方向<input :value="item.focus.join('，')" placeholder="使用逗号分隔" @input="item.focus = splitList($event.target.value)" /></label></div></article>
        </section>

        <section v-show="activeTab === 'links'" class="homepage-editor-section">
          <header><p>07 / EXTERNAL LINKS</p><h2>外部入口</h2><span>管理首页底部的开源仓库、公众号、视频平台等入口。</span></header>
          <div class="homepage-section-action"><span>关闭的入口不会在公开首页显示。</span><button type="button" @click="addLink"><Plus :size="16" aria-hidden="true" />添加入口</button></div>
          <article v-for="(item, index) in content.externalLinks" :key="index" class="homepage-link-row"><label>平台标识<input v-model.trim="item.platform" required maxlength="40" /></label><label>显示名称<input v-model.trim="item.label" required maxlength="80" /></label><label>链接地址<input v-model.trim="item.url" :required="item.enabled" type="url" /></label><label class="homepage-inline-switch"><input v-model="item.enabled" type="checkbox" /><span>公开显示</span></label><button type="button" aria-label="删除外部入口" @click="remove(content.externalLinks, index)"><Trash2 :size="16" aria-hidden="true" /></button></article>
          <section class="homepage-module-links"><h3>其他内容管理入口</h3><div><RouterLink to="/admin/members"><UsersRound :size="19" aria-hidden="true" /><span><strong>成员管理</strong><small>成员资料、角色与公开主页</small></span></RouterLink><RouterLink to="/projects"><FolderKanban :size="19" aria-hidden="true" /><span><strong>项目团队</strong><small>项目资料、主图与公开开关</small></span></RouterLink><RouterLink to="/admin/achievements"><Award :size="19" aria-hidden="true" /><span><strong>成果管理</strong><small>比赛首页排序与新闻内容</small></span></RouterLink></div></section>
        </section>

        <footer class="homepage-save-bar"><div><strong>保存整份主页配置</strong><span>所有分区会作为一个版本同时更新。</span></div><a href="/" target="_blank" rel="noopener noreferrer">预览公开首页 <ExternalLink :size="16" aria-hidden="true" /></a><button class="portal-primary" type="submit" :disabled="saving"><Save :size="17" aria-hidden="true" />{{ saving ? '保存中…' : '保存主页内容' }}</button></footer>
      </main>
    </form>
  </PortalShell>
</template>

<script setup>
import { ArrowRight, CalendarDays, FolderKanban, Plus, ShieldCheck, UserRound } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import PortalShell from '../components/PortalShell.vue'
import ProjectCoverImage from '../components/ProjectCoverImage.vue'
import { authState, listProjects } from '../services/authApi'

const projects = ref([])
const loading = ref(true)
const errorMessage = ref('')
const typeFilter = ref('ALL')
const statusFilter = ref('ALL')

const typeLabels = { COMPETITION: '竞赛', RESEARCH: '科研', INTERNAL: '内部项目', OPEN_SOURCE: '开源' }
const statusLabels = { PLANNING: '筹备中', ACTIVE: '进行中', PAUSED: '已暂停', COMPLETED: '已完成', ARCHIVED: '已归档' }
const filteredProjects = computed(() => projects.value.filter((project) =>
  (typeFilter.value === 'ALL' || project.type === typeFilter.value)
  && (statusFilter.value === 'ALL' || project.status === statusFilter.value)))

onMounted(async () => {
  try {
    projects.value = await listProjects()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
})

function dateRange(project) {
  if (!project.startDate && !project.endDate) return '时间待定'
  return `${project.startDate || '待定'} — ${project.endDate || '长期'} `
}
</script>

<template>
  <PortalShell eyebrow="COLLABORATION / PROJECTS" title="项目团队" description="查看你参与的项目空间。系统管理员可创建团队，负责人负责成员组织与协作角色。">
    <section class="project-index-toolbar" aria-label="项目筛选">
      <div>
        <label>项目类型<select v-model="typeFilter"><option value="ALL">全部类型</option><option v-for="(label, value) in typeLabels" :key="value" :value="value">{{ label }}</option></select></label>
        <label>项目状态<select v-model="statusFilter"><option value="ALL">全部状态</option><option v-for="(label, value) in statusLabels" :key="value" :value="value">{{ label }}</option></select></label>
      </div>
      <RouterLink v-if="authState.account?.systemAdmin" class="portal-primary" to="/projects/new"><Plus :size="17" aria-hidden="true" />创建项目</RouterLink>
    </section>

    <div v-if="loading" class="portal-state">正在读取项目团队…</div>
    <div v-else-if="errorMessage" class="portal-state error" role="alert">{{ errorMessage }}</div>
    <div v-else-if="!filteredProjects.length" class="portal-state project-empty"><FolderKanban :size="28" aria-hidden="true" /><strong>暂无符合条件的项目</strong><span>调整筛选条件，或由系统管理员创建新的项目团队。</span></div>

    <section v-else class="portal-project-card-grid" aria-label="项目团队列表">
      <RouterLink v-for="project in filteredProjects" :key="project.id" class="portal-project-card" :to="`/projects/${project.id}`">
        <header>
          <span>{{ typeLabels[project.type] }}</span>
          <b :data-status="project.status">{{ statusLabels[project.status] }}</b>
        </header>
        <ProjectCoverImage :cover-url="project.coverImageUrl" :alt="`${project.projectName}项目主图`" />
        <p>{{ project.teamName }}</p>
        <h2>{{ project.projectName }}</h2>
        <div class="portal-project-card-description">{{ project.description }}</div>
        <ul class="portal-project-tag-list"><li v-for="tag in project.requiredSkillTags.slice(0, 5)" :key="tag">{{ tag }}</li></ul>
        <dl>
          <div><dt><UserRound :size="15" aria-hidden="true" />负责人</dt><dd>{{ project.leader.name }}</dd></div>
          <div><dt><ShieldCheck :size="15" aria-hidden="true" />指导老师</dt><dd>{{ project.advisor?.name || '暂未关联' }}</dd></div>
          <div><dt><CalendarDays :size="15" aria-hidden="true" />周期</dt><dd>{{ dateRange(project) }}</dd></div>
        </dl>
        <footer><span>{{ project.members.length }} 名成员 · {{ project.administrators.length }} 名项目管理员</span><b>进入团队空间 <ArrowRight :size="16" aria-hidden="true" /></b></footer>
      </RouterLink>
    </section>
  </PortalShell>
</template>

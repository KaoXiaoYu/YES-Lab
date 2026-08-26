<script setup>
import { ExternalLink, Save, Search, UsersRound } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import PortalShell from '../components/PortalShell.vue'
import { listMembers, updateMember } from '../services/authApi'

const members = ref([])
const selectedId = ref(null)
const search = ref('')
const roleFilter = ref('ALL')
const loading = ref(true)
const saving = ref(false)
const message = ref('')
const errorMessage = ref('')
const form = reactive({
  name: '', memberCode: '', role: 'MEMBER', major: '', className: '', grade: '',
  internalContact: '', status: 'OFFICIAL', skillTagsText: '',
})

const roleLabels = { TEACHER: '指导教师', CORE_STUDENT: '核心成员', MEMBER: '普通成员' }
const statusLabels = { CANDIDATE: '候选', TRIAL: '试用', OFFICIAL: '正式', PAUSED: '暂停', EXITED: '退出' }
const selected = computed(() => members.value.find((member) => member.id === selectedId.value) || null)
const filteredMembers = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  return members.value.filter((member) => {
    const matchesRole = roleFilter.value === 'ALL' || member.role === roleFilter.value
    const haystack = `${member.name} ${member.memberCode} ${member.username} ${member.skillTags.join(' ')}`.toLowerCase()
    return matchesRole && (!keyword || haystack.includes(keyword))
  })
})
const isTeacher = computed(() => form.role === 'TEACHER')

watch(selected, (member) => {
  if (!member) return
  Object.assign(form, {
    name: member.name,
    memberCode: member.memberCode,
    role: member.role,
    major: member.major || '',
    className: member.className || '',
    grade: member.grade || '',
    internalContact: member.internalContact || '',
    status: member.status,
    skillTagsText: member.skillTags.join('，'),
  })
  message.value = ''
  errorMessage.value = ''
}, { immediate: true })

onMounted(async () => {
  try {
    members.value = await listMembers()
    selectedId.value = members.value[0]?.id || null
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
})

function selectMember(member) {
  selectedId.value = member.id
}

async function saveMember() {
  if (!selected.value) return
  const skillTags = form.skillTagsText.split(/[,，\n]/).map((tag) => tag.trim()).filter(Boolean)
  if (!skillTags.length) {
    errorMessage.value = '至少填写一个能力标签。'
    return
  }
  saving.value = true
  message.value = ''
  errorMessage.value = ''
  try {
    const updated = await updateMember(selected.value.id, {
      name: form.name,
      memberCode: form.memberCode,
      role: form.role,
      major: isTeacher.value ? null : form.major || null,
      className: isTeacher.value ? null : form.className || null,
      grade: isTeacher.value ? null : form.grade || null,
      internalContact: form.internalContact || null,
      status: form.status,
      skillTags,
    })
    const index = members.value.findIndex((member) => member.id === updated.id)
    members.value.splice(index, 1, updated)
    message.value = '成员资料已保存。角色变更将在该成员下次登录后生效。'
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <PortalShell eyebrow="ADMIN / MEMBERS" title="成员管理" description="维护成员身份、状态和规范字段；个人主页正文仍由成员本人编辑。">
    <div v-if="loading" class="portal-state">正在读取成员列表…</div>
    <div v-else-if="errorMessage && !members.length" class="portal-state error" role="alert">{{ errorMessage }}</div>

    <div v-else class="admin-members-layout">
      <aside class="member-admin-list">
        <header><div><p>MEMBER DIRECTORY</p><h2>全部成员</h2></div><span>{{ filteredMembers.length }}</span></header>
        <div class="member-admin-tools">
          <label><Search :size="17" aria-hidden="true" /><span class="sr-only">搜索成员</span><input v-model="search" type="search" placeholder="姓名、编号或标签" /></label>
          <label><span class="sr-only">筛选角色</span><select v-model="roleFilter"><option value="ALL">全部角色</option><option value="TEACHER">指导教师</option><option value="CORE_STUDENT">核心成员</option><option value="MEMBER">普通成员</option></select></label>
        </div>
        <div class="member-admin-rows" role="listbox" aria-label="成员列表">
          <button v-for="member in filteredMembers" :key="member.id" type="button" :class="{ active: selectedId === member.id }" :aria-selected="selectedId === member.id" role="option" @click="selectMember(member)">
            <span class="mini-avatar"><img v-if="member.avatarUrl" :src="member.avatarUrl" alt="" /><b v-else>{{ member.name.slice(0, 1) }}</b></span>
            <span><strong>{{ member.name }}</strong><small>{{ roleLabels[member.role] }} · {{ member.memberCode }}</small></span>
            <i>{{ statusLabels[member.status] }}</i>
          </button>
          <p v-if="!filteredMembers.length" class="empty-note">没有符合条件的成员。</p>
        </div>
      </aside>

      <section v-if="selected" class="member-admin-detail">
        <header class="member-admin-head">
          <div><p>MEMBER / {{ selected.memberCode }}</p><h2>{{ selected.name }}</h2><span>@{{ selected.username }}</span></div>
          <RouterLink :to="`/members/${selected.id}`" target="_blank">查看公开主页<ExternalLink :size="16" aria-hidden="true" /></RouterLink>
        </header>

        <div v-if="message" class="save-message" role="status">{{ message }}</div>
        <div v-if="errorMessage" class="form-alert" role="alert">{{ errorMessage }}</div>

        <form class="member-admin-form" @submit.prevent="saveMember">
          <fieldset><legend>账号与身份</legend><div class="admin-form-grid">
            <label>姓名<input v-model.trim="form.name" required maxlength="80" /></label>
            <label>学号 / 内部编号<input v-model.trim="form.memberCode" required maxlength="64" /></label>
            <label>成员角色<select v-model="form.role"><option value="TEACHER">指导教师</option><option value="CORE_STUDENT">核心成员</option><option value="MEMBER">普通成员</option></select></label>
            <label>成员状态<select v-model="form.status"><option v-for="(label, value) in statusLabels" :key="value" :value="value">{{ label }}</option></select></label>
          </div></fieldset>

          <fieldset v-if="!isTeacher"><legend>学籍信息</legend><div class="admin-form-grid">
            <label>专业<input v-model.trim="form.major" maxlength="100" /></label>
            <label>班级<input v-model.trim="form.className" maxlength="100" /></label>
            <label>年级<input v-model.trim="form.grade" maxlength="30" /></label>
          </div></fieldset>

          <fieldset><legend>管理信息</legend><div class="admin-form-grid">
            <label>内部联系方式<input v-model.trim="form.internalContact" maxlength="200" /></label>
            <label class="full">能力标签<input v-model.trim="form.skillTagsText" required placeholder="使用中文逗号分隔，例如：无人机，工程实现" /><small>至少 1 个，最多 12 个。</small></label>
          </div></fieldset>

          <div class="member-admin-submit"><p>头像、主页标语和公开介绍由成员本人在个人主页中维护。</p><button type="submit" :disabled="saving"><Save :size="17" aria-hidden="true" />{{ saving ? '保存中…' : '保存成员资料' }}</button></div>
        </form>
      </section>
      <section v-else class="portal-state"><UsersRound :size="24" aria-hidden="true" />请选择一名成员。</section>
    </div>
  </PortalShell>
</template>

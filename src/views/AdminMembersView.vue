<script setup>
import { Camera, Eye, EyeOff, ExternalLink, Plus, Save, Search, Trash2, Upload, UsersRound, X } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import PortalShell from '../components/PortalShell.vue'
import {
  authState, createCoreStudent, deleteManagedMemberAvatar, listMembers,
  replaceManagedMemberAvatar, updateMember,
} from '../services/authApi'

const members = ref([])
const selectedId = ref(null)
const search = ref('')
const roleFilter = ref('ALL')
const loading = ref(true)
const saving = ref(false)
const creating = ref(false)
const createSaving = ref(false)
const avatarSaving = ref(false)
const avatarFile = ref(null)
const avatarPreview = ref('')
const avatarInput = ref(null)
const showCreatePassword = ref(false)
const message = ref('')
const errorMessage = ref('')
const form = reactive({
  name: '', memberCode: '', role: 'MEMBER', major: '', className: '', grade: '',
  internalContact: '', status: 'OFFICIAL', skillTagsText: '',
})
const createForm = reactive({
  username: '', temporaryPassword: '', name: '', memberCode: '', major: '', className: '', grade: '',
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
  clearAvatarSelection()
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

onBeforeUnmount(clearAvatarSelection)

function selectMember(member) {
  creating.value = false
  selectedId.value = member.id
}

function startCreate() {
  clearAvatarSelection()
  creating.value = true
  selectedId.value = null
  message.value = ''
  errorMessage.value = ''
  showCreatePassword.value = false
  Object.assign(createForm, {
    username: '', temporaryPassword: '', name: '', memberCode: '', major: '', className: '', grade: '',
    internalContact: '', status: 'OFFICIAL', skillTagsText: '',
  })
}

function cancelCreate() {
  creating.value = false
  selectedId.value = members.value[0]?.id || null
}

async function submitCoreStudent() {
  const skillTags = splitTags(createForm.skillTagsText)
  if (!skillTags.length) {
    errorMessage.value = '至少填写一个能力标签。'
    return
  }
  createSaving.value = true
  message.value = ''
  errorMessage.value = ''
  try {
    const created = await createCoreStudent({
      username: createForm.username,
      temporaryPassword: createForm.temporaryPassword,
      name: createForm.name,
      memberCode: createForm.memberCode,
      major: createForm.major || null,
      className: createForm.className || null,
      grade: createForm.grade || null,
      internalContact: createForm.internalContact || null,
      status: createForm.status,
      skillTags,
    })
    members.value.push(created)
    creating.value = false
    selectedId.value = created.id
    message.value = '学生管理员账号已创建，可使用设置的账号和初始密码登录。'
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    createSaving.value = false
  }
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

function selectAvatar(event) {
  const file = event.target.files?.[0]
  errorMessage.value = ''
  if (!file) return
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
    errorMessage.value = '头像仅支持 JPG、PNG 或 WebP。'
    event.target.value = ''
    return
  }
  if (file.size > 4 * 1024 * 1024) {
    errorMessage.value = '头像图片不能超过 4MB。'
    event.target.value = ''
    return
  }
  clearAvatarSelection()
  avatarFile.value = file
  avatarPreview.value = URL.createObjectURL(file)
}

async function uploadAvatar() {
  if (!selected.value || !avatarFile.value) return
  avatarSaving.value = true
  message.value = ''
  errorMessage.value = ''
  try {
    replaceMember(await replaceManagedMemberAvatar(selected.value.id, avatarFile.value))
    clearAvatarSelection()
    if (avatarInput.value) avatarInput.value.value = ''
    message.value = '成员头像已更新。'
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    avatarSaving.value = false
  }
}

async function removeAvatar() {
  if (!selected.value) return
  if (!window.confirm(`确定移除 ${selected.value.name} 的头像吗？`)) return
  avatarSaving.value = true
  message.value = ''
  errorMessage.value = ''
  try {
    replaceMember(await deleteManagedMemberAvatar(selected.value.id))
    clearAvatarSelection()
    if (avatarInput.value) avatarInput.value.value = ''
    message.value = '成员头像已移除。'
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    avatarSaving.value = false
  }
}

function replaceMember(updated) {
  const index = members.value.findIndex((member) => member.id === updated.id)
  if (index >= 0) members.value.splice(index, 1, updated)
  if (authState.account?.username === updated.username) authState.account.avatarUrl = updated.avatarUrl
}

function clearAvatarSelection() {
  if (avatarPreview.value) URL.revokeObjectURL(avatarPreview.value)
  avatarPreview.value = ''
  avatarFile.value = null
}

function splitTags(value) {
  return value.split(/[,，\n]/).map((tag) => tag.trim()).filter(Boolean)
}
</script>

<template>
  <PortalShell eyebrow="ADMIN / MEMBERS" title="成员管理" description="维护成员身份、状态和规范字段；个人主页正文仍由成员本人编辑。">
    <div v-if="loading" class="portal-state">正在读取成员列表…</div>
    <div v-else-if="errorMessage && !members.length" class="portal-state error" role="alert">{{ errorMessage }}</div>

    <div v-else class="admin-members-layout">
      <aside class="member-admin-list">
        <header><div><p>MEMBER DIRECTORY</p><h2>全部成员</h2></div><div class="member-list-actions"><span>{{ filteredMembers.length }}</span><button type="button" aria-label="新增学生管理员" title="新增学生管理员" @click="startCreate"><Plus :size="18" aria-hidden="true" /></button></div></header>
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

      <section v-if="creating" class="member-admin-detail create-core-student">
        <header class="member-admin-head">
          <div><p>ACCOUNT / CORE STUDENT</p><h2>新增学生管理员</h2><span>创建后立即拥有与教师相同的系统管理权限。</span></div>
          <button type="button" class="admin-close-button" @click="cancelCreate"><X :size="17" aria-hidden="true" />取消</button>
        </header>

        <div v-if="errorMessage" class="form-alert" role="alert">{{ errorMessage }}</div>
        <form class="member-admin-form" @submit.prevent="submitCoreStudent">
          <fieldset><legend>登录账号</legend><div class="admin-form-grid">
            <label>账号<input v-model.trim="createForm.username" required maxlength="190" autocomplete="username" placeholder="邮箱、手机号或内部账号" /><small>邮箱、手机号，或 4—32 位字母数字内部账号。</small></label>
            <label>初始密码<div class="admin-password-input"><input v-model="createForm.temporaryPassword" required minlength="10" maxlength="72" :type="showCreatePassword ? 'text' : 'password'" autocomplete="new-password" placeholder="至少 10 位" /><button type="button" :aria-label="showCreatePassword ? '隐藏初始密码' : '显示初始密码'" @click="showCreatePassword = !showCreatePassword"><EyeOff v-if="showCreatePassword" :size="17" aria-hidden="true" /><Eye v-else :size="17" aria-hidden="true" /></button></div><small>请通过可信渠道交给本人；当前版本尚未提供强制首次改密。</small></label>
          </div></fieldset>
          <fieldset><legend>成员资料</legend><div class="admin-form-grid">
            <label>姓名<input v-model.trim="createForm.name" required maxlength="80" /></label>
            <label>学号 / 内部编号<input v-model.trim="createForm.memberCode" required maxlength="64" /></label>
            <label>专业<input v-model.trim="createForm.major" maxlength="100" /></label>
            <label>班级<input v-model.trim="createForm.className" maxlength="100" /></label>
            <label>年级<input v-model.trim="createForm.grade" maxlength="30" /></label>
            <label>成员状态<select v-model="createForm.status"><option v-for="(label, value) in statusLabels" :key="value" :value="value">{{ label }}</option></select></label>
            <label>内部联系方式<input v-model.trim="createForm.internalContact" maxlength="200" /></label>
            <label class="full">能力标签<input v-model.trim="createForm.skillTagsText" required placeholder="使用中文逗号分隔，例如：无人机，工程实现" /><small>至少 1 个，最多 12 个。</small></label>
          </div></fieldset>
          <div class="admin-permission-notice"><strong>权限说明</strong><p>该账号角色固定为“核心学生”，可管理成员、招新、标签、项目、成果和主页内容，并保留普通成员功能。</p></div>
          <div class="member-admin-submit"><p>账号创建后不会发送短信或邮件，请由管理员安全告知本人。</p><button type="submit" :disabled="createSaving"><Plus :size="17" aria-hidden="true" />{{ createSaving ? '创建中…' : '创建学生管理员' }}</button></div>
        </form>
      </section>

      <section v-else-if="selected" class="member-admin-detail">
        <header class="member-admin-head">
          <div><p>MEMBER / {{ selected.memberCode }}</p><h2>{{ selected.name }}</h2><span>@{{ selected.username }}</span></div>
          <RouterLink :to="`/members/${selected.id}`" target="_blank">查看公开主页<ExternalLink :size="16" aria-hidden="true" /></RouterLink>
        </header>

        <div v-if="message" class="save-message" role="status">{{ message }}</div>
        <div v-if="errorMessage" class="form-alert" role="alert">{{ errorMessage }}</div>

        <form class="member-admin-form" @submit.prevent="saveMember">
          <fieldset><legend>成员头像</legend><div class="managed-avatar-editor">
            <span class="avatar-editor-preview"><img v-if="avatarPreview || selected.avatarUrl" :src="avatarPreview || selected.avatarUrl" alt="成员头像预览" /><Camera v-else :size="28" aria-hidden="true" /></span>
            <div><p>支持 JPG、PNG、WebP，文件不超过 4MB。</p><div class="avatar-editor-actions">
              <label class="avatar-file-button"><Camera :size="17" aria-hidden="true" />选择图片<input ref="avatarInput" type="file" accept="image/jpeg,image/png,image/webp" @change="selectAvatar" /></label>
              <button type="button" :disabled="!avatarFile || avatarSaving" @click="uploadAvatar"><Upload :size="17" aria-hidden="true" />{{ avatarSaving && avatarFile ? '上传中…' : '上传头像' }}</button>
              <button v-if="selected.avatarUrl" class="danger" type="button" :disabled="avatarSaving" @click="removeAvatar"><Trash2 :size="17" aria-hidden="true" />移除头像</button>
            </div></div>
          </div></fieldset>
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

          <div class="member-admin-submit"><p>主页标语和公开介绍仍由成员本人维护；管理员可协助更换头像。</p><button type="submit" :disabled="saving"><Save :size="17" aria-hidden="true" />{{ saving ? '保存中…' : '保存成员资料' }}</button></div>
        </form>
      </section>
      <section v-else class="portal-state"><UsersRound :size="24" aria-hidden="true" />请选择一名成员。</section>
    </div>
  </PortalShell>
</template>

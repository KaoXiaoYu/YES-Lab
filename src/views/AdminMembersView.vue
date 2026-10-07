<script setup>
import {
  Bot,
  Camera,
  ChevronDown,
  Eye,
  EyeOff,
  ExternalLink,
  KeyRound,
  Plus,
  Search,
  ShieldAlert,
  Trash2,
  Upload,
  UsersRound,
} from '@lucide/vue'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import PortalShell from '../components/PortalShell.vue'
import AdminDrawer from '../components/AdminDrawer.vue'
import LoadingSkeleton from '../components/LoadingSkeleton.vue'
import SaveBar from '../components/SaveBar.vue'
import MelinaVisibilityManager from '../components/MelinaVisibilityManager.vue'
import {
  authState,
  createCoreStudent,
  deleteManagedMemberAvatar,
  listMembers,
  replaceManagedMemberAvatar,
  resetMemberPassword,
  updateMember,
} from '../services/authApi'
import { confirmAction } from '../services/confirm'
import { toast } from '../services/toast'
import { useUnsavedGuard } from '../composables/useUnsavedGuard'

const members = ref([])
const selectedId = ref(null)
const search = ref('')
const roleFilter = ref('ALL')
const loading = ref(true)
const loadError = ref('')
const saving = ref(false)
const createOpen = ref(false)
const createSaving = ref(false)
const mascotOpen = ref(false)
const avatarSaving = ref(false)
const avatarFile = ref(null)
const avatarPreview = ref('')
const avatarInput = ref(null)
const showCreatePassword = ref(false)
const passwordSaving = ref(false)
const formError = ref('')
const createError = ref('')
const flashId = ref(null)
const rowList = ref(null)
const snapshot = ref('')

const emptyCreateForm = () => ({
  username: '',
  temporaryPassword: '',
  name: '',
  memberCode: '',
  major: '',
  className: '',
  grade: '',
  internalContact: '',
  status: 'OFFICIAL',
  skillTagsText: '',
})
const form = reactive({
  name: '',
  memberCode: '',
  role: 'MEMBER',
  major: '',
  className: '',
  grade: '',
  internalContact: '',
  status: 'OFFICIAL',
  skillTagsText: '',
})
const createForm = reactive(emptyCreateForm())
const createSnapshot = JSON.stringify(emptyCreateForm())

const roleLabels = { TEACHER: '指导教师', CORE_STUDENT: '核心成员', MEMBER: '普通成员' }
const roleFilters = [
  { value: 'ALL', label: '全部' },
  { value: 'TEACHER', label: '教师' },
  { value: 'CORE_STUDENT', label: '核心' },
  { value: 'MEMBER', label: '成员' },
]
// 全量标签用于只读展示；可选状态只保留「试用 / 正式」，候选 / 暂停 / 退出 已停用。
const statusLabels = { CANDIDATE: '候选', TRIAL: '试用', OFFICIAL: '正式', PAUSED: '暂停', EXITED: '退出' }
const selectableStatuses = { TRIAL: '试用', OFFICIAL: '正式' }
const selected = computed(() => members.value.find((member) => member.id === selectedId.value) || null)
const roleCounts = computed(() =>
  members.value.reduce((counts, member) => ({ ...counts, [member.role]: (counts[member.role] || 0) + 1 }), {
    ALL: members.value.length,
  }),
)
const filteredMembers = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  return members.value.filter((member) => {
    const matchesRole = roleFilter.value === 'ALL' || member.role === roleFilter.value
    const haystack =
      `${member.name} ${member.memberCode} ${member.username} ${member.skillTags.join(' ')}`.toLowerCase()
    return matchesRole && (!keyword || haystack.includes(keyword))
  })
})
const isTeacher = computed(() => form.role === 'TEACHER')
const formDirty = computed(() => Boolean(selected.value) && JSON.stringify(form) !== snapshot.value)
const createDirty = computed(() => JSON.stringify(createForm) !== createSnapshot)

const { confirmDiscard } = useUnsavedGuard(() => formDirty.value || Boolean(avatarFile.value))

watch(selected, (member) => loadForm(member), { immediate: true })

onMounted(async () => {
  try {
    members.value = await listMembers()
    selectedId.value = members.value[0]?.id || null
  } catch (error) {
    loadError.value = error.message
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(clearAvatarSelection)

function loadForm(member) {
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
  snapshot.value = JSON.stringify(form)
  formError.value = ''
  clearAvatarSelection()
}

async function selectMember(member, { focus = false } = {}) {
  if (member.id === selectedId.value) return
  if (!(await confirmDiscard({ message: `${selected.value?.name || '当前成员'}的资料还没有保存，切换后将丢失。` })))
    return
  selectedId.value = member.id
  if (focus) {
    await nextTick()
    rowList.value?.querySelector(`[data-member-id="${member.id}"]`)?.focus()
  }
}

function moveSelection(event) {
  const keys = { ArrowDown: 1, ArrowUp: -1, Home: -Infinity, End: Infinity }
  if (!(event.key in keys) || !filteredMembers.value.length) return
  event.preventDefault()
  const list = filteredMembers.value
  const current = Math.max(
    0,
    list.findIndex((member) => member.id === selectedId.value),
  )
  const step = keys[event.key]
  const next = Number.isFinite(step)
    ? Math.min(list.length - 1, Math.max(0, current + step))
    : step < 0
      ? 0
      : list.length - 1
  selectMember(list[next], { focus: true })
}

function discardChanges() {
  loadForm(selected.value)
}

function openCreate() {
  Object.assign(createForm, emptyCreateForm())
  createError.value = ''
  showCreatePassword.value = false
  createOpen.value = true
}

async function submitCoreStudent() {
  if (createSaving.value) return
  const skillTags = splitTags(createForm.skillTagsText)
  if (!skillTags.length) {
    createError.value = '至少填写一个能力标签。'
    return
  }
  createSaving.value = true
  createError.value = ''
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
    Object.assign(createForm, emptyCreateForm())
    createOpen.value = false
    search.value = ''
    roleFilter.value = 'ALL'
    selectedId.value = created.id
    highlight(created.id)
    toast.success(`${created.name} 现在可以使用设置的账号和初始密码登录。`, { title: '学生管理员账号已创建' })
  } catch (error) {
    createError.value = error.message
  } finally {
    createSaving.value = false
  }
}

async function saveMember() {
  if (!selected.value || saving.value) return
  const skillTags = splitTags(form.skillTagsText)
  if (!skillTags.length) {
    formError.value = '至少填写一个能力标签。'
    return
  }
  saving.value = true
  formError.value = ''
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
    const roleChanged = updated.role !== selected.value.role
    replaceMember(updated)
    loadForm(updated)
    highlight(updated.id)
    toast.success(roleChanged ? '角色变更将在该成员下次登录后生效。' : `${updated.name} 的资料已更新。`, {
      title: '成员资料已保存',
    })
  } catch (error) {
    formError.value = error.message
    toast.error(error.message, { title: '保存失败' })
  } finally {
    saving.value = false
  }
}

async function resetSelectedPassword() {
  if (!selected.value) return
  const member = selected.value
  const confirmed = await confirmAction({
    title: `重置 ${member.name} 的密码？`,
    message: '密码将被重置为默认密码 yeslab521。',
    details: ['该账号在其他设备上的续期登录状态将失效。', '请通过可信渠道告知本人，并提醒登录后尽快修改。'],
    confirmText: '重置密码',
    tone: 'danger',
  })
  if (!confirmed) return

  passwordSaving.value = true
  try {
    await resetMemberPassword(member.id)
    toast.success(`已将 ${member.name} 的密码重置为 yeslab521，请提醒本人登录后尽快修改。`, {
      title: '密码已重置',
      duration: 8000,
    })
  } catch (error) {
    toast.error(error.message, { title: '重置失败' })
  } finally {
    passwordSaving.value = false
  }
}

function selectAvatar(event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
    toast.error('头像仅支持 JPG、PNG 或 WebP。')
    event.target.value = ''
    return
  }
  if (file.size > 4 * 1024 * 1024) {
    toast.error('头像图片不能超过 4MB。')
    event.target.value = ''
    return
  }
  clearAvatarSelection()
  avatarFile.value = file
  avatarPreview.value = URL.createObjectURL(file)
}

function cancelAvatar() {
  clearAvatarSelection()
  if (avatarInput.value) avatarInput.value.value = ''
}

async function uploadAvatar() {
  if (!selected.value || !avatarFile.value) return
  avatarSaving.value = true
  try {
    replaceMember(await replaceManagedMemberAvatar(selected.value.id, avatarFile.value))
    cancelAvatar()
    toast.success('成员头像已更新。')
  } catch (error) {
    toast.error(error.message, { title: '头像上传失败' })
  } finally {
    avatarSaving.value = false
  }
}

async function removeAvatar() {
  if (!selected.value) return
  const confirmed = await confirmAction({
    title: `移除 ${selected.value.name} 的头像？`,
    message: '移除后将显示姓名首字，成员本人或管理员可以重新上传。',
    confirmText: '移除头像',
    tone: 'danger',
  })
  if (!confirmed) return
  avatarSaving.value = true
  try {
    replaceMember(await deleteManagedMemberAvatar(selected.value.id))
    cancelAvatar()
    toast.success('成员头像已移除。')
  } catch (error) {
    toast.error(error.message, { title: '移除失败' })
  } finally {
    avatarSaving.value = false
  }
}

function replaceMember(updated) {
  const index = members.value.findIndex((member) => member.id === updated.id)
  if (index >= 0) members.value.splice(index, 1, updated)
  if (authState.account?.username === updated.username) authState.account.avatarUrl = updated.avatarUrl
}

function highlight(id) {
  flashId.value = null
  nextTick(() => {
    flashId.value = id
    rowList.value?.querySelector(`[data-member-id="${id}"]`)?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  })
}

function clearAvatarSelection() {
  if (avatarPreview.value) URL.revokeObjectURL(avatarPreview.value)
  avatarPreview.value = ''
  avatarFile.value = null
}

function splitTags(value) {
  return value
    .split(/[,，\n]/)
    .map((tag) => tag.trim())
    .filter(Boolean)
}
</script>

<template>
  <PortalShell title="成员管理" description="维护成员身份、状态和规范字段；个人主页正文仍由成员本人编辑。">
    <template #actions>
      <button
        v-if="authState.account?.role === 'TEACHER'"
        type="button"
        class="ui-btn ui-btn--secondary"
        @click="mascotOpen = true"
      >
        <Bot :size="17" aria-hidden="true" />吉祥物与展示设置
      </button>
      <button type="button" class="ui-btn ui-btn--primary" @click="openCreate">
        <Plus :size="17" aria-hidden="true" />新增学生管理员
      </button>
    </template>

    <div v-if="loading" class="admin-members-layout">
      <div class="member-admin-list"><LoadingSkeleton :rows="6" /></div>
      <div class="member-admin-detail"><LoadingSkeleton variant="detail" :rows="3" /></div>
    </div>
    <div v-else-if="loadError" class="portal-state error" role="alert">{{ loadError }}</div>

    <div v-else class="admin-members-layout">
      <aside class="member-admin-list">
        <header>
          <div>
            <h2>全部成员</h2>
          </div>
          <span class="admin-count" aria-live="polite"
            >{{ filteredMembers.length
            }}<small v-if="filteredMembers.length !== members.length"> / {{ members.length }}</small></span
          >
        </header>
        <div class="member-admin-tools">
          <label
            ><Search :size="17" aria-hidden="true" /><span class="sr-only">搜索成员</span
            ><input v-model="search" type="search" placeholder="姓名、编号或标签"
          /></label>
        </div>
        <div class="admin-chip-filters" role="group" aria-label="按角色筛选">
          <button
            v-for="item in roleFilters"
            :key="item.value"
            type="button"
            :class="{ active: roleFilter === item.value }"
            :aria-pressed="roleFilter === item.value"
            @click="roleFilter = item.value"
          >
            {{ item.label }}<span>{{ roleCounts[item.value] || 0 }}</span>
          </button>
        </div>
        <div
          ref="rowList"
          class="member-admin-rows"
          role="listbox"
          aria-label="成员列表（上下方向键切换）"
          @keydown="moveSelection"
        >
          <TransitionGroup name="list">
            <button
              v-for="member in filteredMembers"
              :key="member.id"
              type="button"
              :data-member-id="member.id"
              :class="{ active: selectedId === member.id, 'ui-flash': flashId === member.id }"
              :aria-selected="selectedId === member.id"
              :tabindex="selectedId === member.id ? 0 : -1"
              role="option"
              @click="selectMember(member)"
            >
              <span class="mini-avatar"
                ><img v-if="member.avatarUrl" :src="member.avatarUrl" alt="" /><b v-else>{{
                  member.name.slice(0, 1)
                }}</b></span
              >
              <span
                ><strong>{{ member.name }}</strong
                ><small>{{ roleLabels[member.role] }} · {{ member.memberCode }}</small></span
              >
              <i :class="`status-${member.status.toLowerCase()}`">{{ statusLabels[member.status] }}</i>
            </button>
          </TransitionGroup>
          <p v-if="!filteredMembers.length" class="empty-note">没有符合条件的成员。</p>
        </div>
      </aside>

      <Transition name="fade" mode="out-in">
        <section v-if="selected" :key="selected.id" class="member-admin-detail">
          <header class="member-admin-head">
            <div>
              <p>MEMBER / {{ selected.memberCode }}</p>
              <h2>{{ selected.name }}</h2>
              <span>@{{ selected.username }} · {{ roleLabels[selected.role] }}</span>
            </div>
            <RouterLink :to="`/members/${selected.id}`" target="_blank"
              >查看公开主页<ExternalLink :size="16" aria-hidden="true"
            /></RouterLink>
          </header>

          <form id="member-admin-form" class="member-admin-form" @submit.prevent="saveMember">
            <fieldset>
              <legend>成员头像</legend>
              <div class="managed-avatar-editor">
                <span class="avatar-editor-preview"
                  ><img
                    v-if="avatarPreview || selected.avatarUrl"
                    :src="avatarPreview || selected.avatarUrl"
                    alt="成员头像预览" /><Camera v-else :size="28" aria-hidden="true"
                /></span>
                <div>
                  <p>
                    {{
                      avatarFile ? `已选择 ${avatarFile.name}，上传后生效。` : '支持 JPG、PNG、WebP，文件不超过 4MB。'
                    }}
                  </p>
                  <div class="avatar-editor-actions">
                    <template v-if="avatarFile">
                      <button
                        type="button"
                        class="ui-btn ui-btn--primary"
                        :disabled="avatarSaving"
                        @click="uploadAvatar"
                      >
                        <Upload :size="17" aria-hidden="true" />{{ avatarSaving ? '上传中…' : '上传头像' }}
                      </button>
                      <button type="button" class="ui-btn ui-btn--ghost" :disabled="avatarSaving" @click="cancelAvatar">
                        取消
                      </button>
                    </template>
                    <template v-else>
                      <label class="avatar-file-button"
                        ><Camera :size="17" aria-hidden="true" />{{ selected.avatarUrl ? '更换图片' : '选择图片'
                        }}<input
                          ref="avatarInput"
                          type="file"
                          accept="image/jpeg,image/png,image/webp"
                          @change="selectAvatar"
                      /></label>
                      <button
                        v-if="selected.avatarUrl"
                        class="danger"
                        type="button"
                        :disabled="avatarSaving"
                        @click="removeAvatar"
                      >
                        <Trash2 :size="17" aria-hidden="true" />移除头像
                      </button>
                    </template>
                  </div>
                </div>
              </div>
            </fieldset>
            <fieldset>
              <legend>账号与身份</legend>
              <div class="admin-form-grid">
                <label>姓名<input v-model.trim="form.name" required maxlength="80" /></label>
                <label>学号 / 内部编号<input v-model.trim="form.memberCode" required maxlength="64" /></label>
                <label
                  >成员角色<select v-model="form.role">
                    <option value="TEACHER">指导教师</option>
                    <option value="CORE_STUDENT">核心成员</option>
                    <option value="MEMBER">普通成员</option>
                  </select></label
                >
                <label
                  >成员状态<select v-model="form.status">
                    <option v-if="!selectableStatuses[form.status]" :value="form.status" disabled>
                      {{ statusLabels[form.status] }}（已停用，请改选）
                    </option>
                    <option v-for="(label, value) in selectableStatuses" :key="value" :value="value">
                      {{ label }}
                    </option>
                  </select></label
                >
              </div>
              <p v-if="form.role !== selected.role" class="admin-inline-note">角色变更将在该成员下次登录后生效。</p>
            </fieldset>

            <Transition name="reveal">
              <fieldset v-if="!isTeacher">
                <legend>学籍信息</legend>
                <div class="admin-form-grid">
                  <label>专业<input v-model.trim="form.major" maxlength="100" /></label>
                  <label>班级<input v-model.trim="form.className" maxlength="100" /></label>
                  <label>年级<input v-model.trim="form.grade" maxlength="30" /></label>
                </div>
              </fieldset>
            </Transition>

            <fieldset>
              <legend>管理信息</legend>
              <div class="admin-form-grid">
                <label>内部联系方式<input v-model.trim="form.internalContact" maxlength="200" /></label>
                <label class="full"
                  >能力标签<input
                    v-model.trim="form.skillTagsText"
                    required
                    placeholder="使用中文逗号分隔，例如：无人机，工程实现"
                    :aria-invalid="Boolean(formError)"
                  /><small>至少 1 个，最多 12 个。</small></label
                >
              </div>
            </fieldset>
            <div v-if="formError" class="form-alert" role="alert">{{ formError }}</div>
            <p class="admin-form-hint">主页标语和公开介绍仍由成员本人维护；管理员可协助更换头像。</p>

            <SaveBar
              :show="formDirty"
              :busy="saving"
              form="member-admin-form"
              save-text="保存成员资料"
              @discard="discardChanges"
            />
          </form>

          <details class="admin-danger-zone">
            <summary>
              <ShieldAlert :size="17" aria-hidden="true" />
              <span><strong>账号安全</strong><small>重置登录密码</small></span>
              <ChevronDown class="admin-disclosure-icon" :size="17" aria-hidden="true" />
            </summary>
            <div class="default-password-reset">
              <p>
                重置后密码变为默认密码
                <strong>yeslab521</strong>，该成员需要重新登录。请通过可信渠道告知本人，并提醒登录后尽快修改。
              </p>
              <button
                class="ui-btn ui-btn--danger"
                type="button"
                :disabled="passwordSaving"
                @click="resetSelectedPassword"
              >
                <KeyRound :size="17" aria-hidden="true" />{{ passwordSaving ? '重置中…' : '重置为默认密码' }}
              </button>
            </div>
          </details>
        </section>
        <section v-else class="portal-state"><UsersRound :size="24" aria-hidden="true" />请选择一名成员。</section>
      </Transition>
    </div>

    <AdminDrawer
      v-model:open="createOpen"
      title="新增学生管理员"
      description="创建后立即拥有与教师相同的系统管理权限。"
      size="lg"
      :dirty="createDirty"
      :busy="createSaving"
      submit-text="创建学生管理员"
      busy-text="创建中…"
      @submit="submitCoreStudent"
    >
      <div class="member-admin-form">
        <div v-if="createError" class="form-alert" role="alert">{{ createError }}</div>
        <fieldset>
          <legend>登录账号</legend>
          <div class="admin-form-grid">
            <label
              >账号<input
                v-model.trim="createForm.username"
                required
                maxlength="190"
                autocomplete="username"
                placeholder="邮箱、手机号或内部账号"
                autofocus
              /><small>邮箱、手机号，或 4—32 位字母数字内部账号。</small></label
            >
            <label
              >初始密码
              <div class="admin-password-input">
                <input
                  v-model="createForm.temporaryPassword"
                  required
                  minlength="10"
                  maxlength="72"
                  :type="showCreatePassword ? 'text' : 'password'"
                  autocomplete="new-password"
                  placeholder="至少 10 位"
                /><button
                  type="button"
                  :aria-label="showCreatePassword ? '隐藏初始密码' : '显示初始密码'"
                  @click="showCreatePassword = !showCreatePassword"
                >
                  <EyeOff v-if="showCreatePassword" :size="17" aria-hidden="true" /><Eye
                    v-else
                    :size="17"
                    aria-hidden="true"
                  />
                </button>
              </div>
              <small>请通过可信渠道交给本人；当前版本尚未提供强制首次改密。</small></label
            >
          </div>
        </fieldset>
        <fieldset>
          <legend>成员资料</legend>
          <div class="admin-form-grid">
            <label>姓名<input v-model.trim="createForm.name" required maxlength="80" /></label>
            <label>学号 / 内部编号<input v-model.trim="createForm.memberCode" required maxlength="64" /></label>
            <label>专业<input v-model.trim="createForm.major" maxlength="100" /></label>
            <label>班级<input v-model.trim="createForm.className" maxlength="100" /></label>
            <label>年级<input v-model.trim="createForm.grade" maxlength="30" /></label>
            <label
              >成员状态<select v-model="createForm.status">
                <option v-for="(label, value) in selectableStatuses" :key="value" :value="value">{{ label }}</option>
              </select></label
            >
            <label>内部联系方式<input v-model.trim="createForm.internalContact" maxlength="200" /></label>
            <label class="full"
              >能力标签<input
                v-model.trim="createForm.skillTagsText"
                required
                placeholder="使用中文逗号分隔，例如：无人机，工程实现"
              /><small>至少 1 个，最多 12 个。</small></label
            >
          </div>
        </fieldset>
        <div class="admin-permission-notice">
          <strong>权限说明</strong>
          <p>该账号角色固定为“核心学生”，可管理成员、招新、标签、项目、成果和主页内容，并保留普通成员功能。</p>
          <p>账号创建后不会发送短信或邮件，请由管理员安全告知本人。</p>
        </div>
      </div>
    </AdminDrawer>

    <AdminDrawer
      v-if="authState.account?.role === 'TEACHER'"
      v-model:open="mascotOpen"
      title="吉祥物与展示设置"
      description="仅指导老师可修改。按账号选择梅琳娜或奶龙，也可以单独设置显示范围。"
      size="lg"
      hide-footer
    >
      <MelinaVisibilityManager class="is-embedded" />
    </AdminDrawer>
  </PortalShell>
</template>

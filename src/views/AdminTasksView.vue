<script setup>
import { ClipboardCheck, Eye, Pencil, Plus, Send, Square, Trash2, Users } from '@lucide/vue'
import { computed, onMounted, reactive, ref } from 'vue'
import PortalShell from '../components/PortalShell.vue'
import DiscussionRichTextEditor from '../components/DiscussionRichTextEditor.vue'
import TaskSubtaskEditor from '../components/TaskSubtaskEditor.vue'
import {
  closeTask,
  createTask,
  deleteTask,
  getAdminSubtask,
  getTask,
  listTaskMemberOptions,
  listTasks,
  previewTaskAudience,
  publishTask,
  updateTask,
} from '../services/authApi'

const tasks = ref([])
const memberOptions = ref([])
const loading = ref(true)
const working = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const statusFilter = ref('ALL')
const keyword = ref('')
const preview = ref(null)
const editingId = ref(null)
const showForm = ref(false)

const statusLabels = { DRAFT: '草稿', PUBLISHED: '已发布', CLOSED: '已结束' }
const roleLabels = { TEACHER: '教师', CORE_STUDENT: '核心学生', MEMBER: '普通成员' }
const memberStatusLabels = { TRIAL: '试用', OFFICIAL: '正式', CANDIDATE: '候选', PAUSED: '暂停', EXITED: '退出' }

const form = reactive({
  title: '',
  contentHtml: '<p></p>',
  startDate: '',
  endDate: '',
  points: 0,
  subtasks: [],
  roles: [],
  statuses: [],
  gradesText: '',
  tagsText: '',
  memberProfileIds: [],
})

const filtered = computed(() =>
  tasks.value.filter((task) => statusFilter.value === 'ALL' || task.status === statusFilter.value),
)
const editingTask = computed(() => tasks.value.find((task) => task.id === editingId.value) || null)
const published = computed(() => editingTask.value?.status === 'PUBLISHED')

onMounted(async () => {
  await Promise.all([loadTasks(), loadMemberOptions()])
})

async function loadTasks() {
  loading.value = true
  try {
    tasks.value = await listTasks()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

async function loadMemberOptions() {
  try {
    memberOptions.value = await listTaskMemberOptions()
  } catch {
    memberOptions.value = []
  }
}

function resetForm() {
  editingId.value = null
  form.title = ''
  form.contentHtml = '<p></p>'
  form.startDate = ''
  form.endDate = ''
  form.points = 0
  form.subtasks = []
  form.roles = []
  form.statuses = []
  form.gradesText = ''
  form.tagsText = ''
  form.memberProfileIds = []
  preview.value = null
}

async function openCreate() {
  resetForm()
  showForm.value = true
}

async function openEdit(task) {
  resetForm()
  working.value = true
  try {
    const detail = await getTask(task.id)
    editingId.value = detail.id
    form.title = detail.title
    form.contentHtml = detail.contentHtml
    form.startDate = detail.startDate || ''
    form.endDate = detail.endDate || ''
    form.points = detail.points
    form.subtasks = detail.subtasks.map((item) => ({
      id: item.id,
      title: item.title,
      hasContent: item.hasContent,
    }))
    form.roles = detail.rules.filter((r) => r.dimension === 'ROLE').map((r) => r.value)
    form.statuses = detail.rules.filter((r) => r.dimension === 'MEMBER_STATUS').map((r) => r.value)
    form.gradesText = detail.rules
      .filter((r) => r.dimension === 'GRADE')
      .map((r) => r.value)
      .join('、')
    form.tagsText = detail.rules
      .filter((r) => r.dimension === 'SKILL_TAG')
      .map((r) => r.value)
      .join('、')
    showForm.value = true
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    working.value = false
  }
}

function loadSubtaskContent(subtaskId) {
  if (!editingId.value) return Promise.resolve({ contentHtml: null })
  return getAdminSubtask(editingId.value, subtaskId)
}

function splitList(value) {
  return value
    .split(/[\n、,，]/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function buildRules() {
  const rules = []
  form.roles.forEach((value) => rules.push({ dimension: 'ROLE', value }))
  form.statuses.forEach((value) => rules.push({ dimension: 'MEMBER_STATUS', value }))
  splitList(form.gradesText).forEach((value) => rules.push({ dimension: 'GRADE', value }))
  splitList(form.tagsText).forEach((value) => rules.push({ dimension: 'SKILL_TAG', value }))
  return rules
}

/**
 * 保存前把「已存在、有说明但这次没展开」的子任务正文补齐：
 * 不补就会把已有说明当成空写掉，补齐失败时抛错并中止保存。
 */
async function resolveSubtaskContents() {
  const resolved = []
  for (const item of form.subtasks.filter((entry) => entry.title.trim())) {
    if (item.id && item.hasContent && item.contentHtml === undefined) {
      const detail = await loadSubtaskContent(item.id)
      resolved.push({ ...item, contentHtml: detail?.contentHtml ?? null })
    } else {
      resolved.push(item)
    }
  }
  return resolved
}

async function buildPayload() {
  const items = await resolveSubtaskContents()
  return {
    title: form.title.trim(),
    contentHtml: form.contentHtml,
    startDate: form.startDate || null,
    endDate: form.endDate || null,
    points: Number(form.points) || 0,
    subtasks: items.map((item) => ({
      id: item.id ?? null,
      title: item.title.trim(),
      contentHtml: item.contentHtml ?? null,
    })),
    rules: buildRules(),
    memberProfileIds: form.memberProfileIds,
  }
}

async function runPreview() {
  working.value = true
  errorMessage.value = ''
  try {
    preview.value = await previewTaskAudience({
      rules: buildRules(),
      memberProfileIds: form.memberProfileIds,
    })
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    working.value = false
  }
}

async function save() {
  working.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    if (editingId.value) {
      await updateTask(editingId.value, await buildPayload())
      successMessage.value = '任务已保存。'
    } else {
      const created = await createTask(await buildPayload())
      editingId.value = created.id
      successMessage.value = '任务草稿已创建，确认无误后发布。'
    }
    await loadTasks()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    working.value = false
  }
}

async function runAction(action, task, confirmText) {
  if (confirmText && !window.confirm(confirmText)) return
  working.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await action(task.id)
    await loadTasks()
    successMessage.value = '操作已完成。'
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    working.value = false
  }
}
</script>

<template>
  <PortalShell
    eyebrow="ADMIN / TASKS"
    title="任务管理"
    description="按角色、成员状态、年级与能力标签发放任务，或直接指定成员。发布后积分与发放对象锁定；积分在任务到期后统一结算。"
  >
    <section class="task-toolbar" aria-label="任务筛选与操作">
      <div>
        <label
          >任务状态<select v-model="statusFilter">
            <option value="ALL">全部状态</option>
            <option v-for="(label, value) in statusLabels" :key="value" :value="value">{{ label }}</option>
          </select></label
        >
        <label>关键词<input v-model.trim="keyword" placeholder="任务标题" aria-label="按标题搜索任务" /></label>
      </div>
      <div class="task-toolbar-actions">
        <RouterLink class="portal-secondary" to="/admin/tasks/onboarding">新手任务与模板</RouterLink>
        <button class="portal-primary" type="button" @click="openCreate">
          <Plus :size="17" aria-hidden="true" />创建任务
        </button>
      </div>
    </section>

    <p v-if="errorMessage" class="portal-state error" role="alert">{{ errorMessage }}</p>
    <p v-if="successMessage" class="portal-state success" role="status">{{ successMessage }}</p>

    <section v-if="showForm" class="admin-form-card" aria-labelledby="task-form-title">
      <header>
        <ClipboardCheck :size="22" aria-hidden="true" />
        <div>
          <p>{{ editingId ? 'EDIT TASK' : 'NEW TASK' }}</p>
          <h3 id="task-form-title">{{ editingId ? '编辑任务' : '创建任务' }}</h3>
        </div>
      </header>
      <p v-if="published" class="task-locked-note" role="status">
        该任务已发布：发放条件、指定成员与积分已锁定，只能修改标题、正文、起止日期与子任务。
      </p>

      <div class="admin-form-grid">
        <label class="full">任务标题<input v-model.trim="form.title" required maxlength="160" /></label>
        <div class="full task-editor-field">
          <span class="task-editor-label">大任务描述</span>
          <DiscussionRichTextEditor v-model="form.contentHtml" label="大任务描述" :max-length="20000" />
        </div>
        <label>开始日期<input v-model="form.startDate" type="date" /></label>
        <label
          >截止日期<input v-model="form.endDate" type="date" />
          <small>到期后成员不能再提交；管理员仍可审核已提交内容。截止时触发积分结算；留空表示不设截止。</small></label
        >
        <label
          >每个通过对象可得积分
          <input v-model.number="form.points" type="number" min="0" max="100000" :disabled="published" />
          <small
            >0 表示该任务不计分。发布后不可修改；积分在任务到期后统一结算，只发给结算时已通过审核的对象。</small
          ></label
        >
        <TaskSubtaskEditor
          v-model="form.subtasks"
          class="full"
          label="子任务"
          hint="子任务可以不设——不设时成员直接提交完成说明。展开箭头可为每项写富文本说明；成员点子任务进入独立页面阅读并提交内容。已发布的任务也可增删子任务，删除会清掉对应的提交记录。"
          :load-content="loadSubtaskContent"
        />
      </div>

      <fieldset class="task-rule-fieldset" :disabled="published">
        <legend>发放条件（同一维度取「或」，不同维度取「且」）</legend>
        <div class="task-rule-grid">
          <div>
            <span>角色</span>
            <label v-for="(label, value) in roleLabels" :key="value" class="task-check"
              ><input v-model="form.roles" type="checkbox" :value="value" />{{ label }}</label
            >
          </div>
          <div>
            <span>成员状态</span>
            <label class="task-check"><input v-model="form.statuses" type="checkbox" value="TRIAL" />试用</label>
            <label class="task-check"><input v-model="form.statuses" type="checkbox" value="OFFICIAL" />正式</label>
          </div>
          <label>年级（用、分隔）<input v-model.trim="form.gradesText" placeholder="24级、大二" /></label>
          <label>能力标签（用、分隔）<input v-model.trim="form.tagsText" placeholder="无人机、视觉" /></label>
        </div>
        <label class="full"
          >直接指定成员（可多选，按住 Ctrl / Command）
          <select v-model="form.memberProfileIds" multiple size="6">
            <option v-for="option in memberOptions" :key="option.profileId" :value="option.profileId">
              {{ option.name }} · {{ option.memberCode }} · {{ memberStatusLabels[option.memberStatus] }}
            </option>
          </select>
          <small>指定成员与条件命中的并集会被去重。</small></label
        >
      </fieldset>

      <div class="task-form-actions">
        <button class="portal-secondary" type="button" :disabled="working" @click="runPreview">
          <Users :size="16" aria-hidden="true" />预览命中名单
        </button>
        <button class="portal-primary" type="button" :disabled="working || !form.title" @click="save">
          {{ editingId ? '保存修改' : '保存草稿' }}
        </button>
        <button type="button" class="portal-secondary" @click="showForm = false">收起</button>
      </div>

      <div v-if="preview" class="task-preview" role="status">
        <strong>命中 {{ preview.total }} 人，其中 {{ preview.pointEligibleCount }} 人可计分</strong>
        <ul>
          <li v-for="member in preview.members" :key="member.memberProfileId">
            {{ member.name }} · {{ member.memberCode }} · {{ roleLabels[member.role] }}
            <span v-if="member.pointEligible">可计分</span>
            <span v-else class="task-skip">不可计分：{{ member.pointIneligibleReason }}</span>
          </li>
        </ul>
        <p v-if="preview.pointsConfigured && !preview.pointEligibleCount" class="task-skip">
          该任务配置了积分，但当前名单中没有人可以计分。
        </p>
      </div>
    </section>

    <div v-if="loading" class="portal-state">正在读取任务…</div>
    <div v-else-if="!filtered.length" class="portal-state project-empty">
      <ClipboardCheck :size="28" aria-hidden="true" /><strong>暂无任务</strong
      ><span>创建任务草稿，确认命中名单后发布。</span>
    </div>

    <section v-else class="task-card-grid" aria-label="任务列表">
      <article v-for="task in filtered" :key="task.id" class="task-card">
        <header>
          <span :data-status="task.status">{{ statusLabels[task.status] }}</span>
          <b>{{ task.points > 0 ? `${task.points} 分` : '不计分' }}</b>
        </header>
        <h2>{{ task.title }}</h2>
        <p class="task-card-dates">{{ task.startDate || '未设置开始' }} — {{ task.endDate || '未设置截止' }}</p>
        <p class="task-card-progress">
          对象 {{ task.assignmentCount }} 人 · 已通过 {{ task.approvedCount }} · 待审核 {{ task.submittedCount }} ·
          进行中 {{ task.pendingCount }} · 已驳回 {{ task.rejectedCount }}
        </p>
        <p v-if="task.points > 0" class="task-card-settle">
          {{ task.pointsSettledAt ? `积分已结算（${task.pointsSettledAt.slice(0, 10)}）` : '积分待结算' }}
        </p>
        <p v-if="task.status === 'CLOSED'" class="task-skip">
          任务已由管理员结束：成员不能再提交，管理员也不能再审核或驳回。
        </p>
        <p v-else-if="task.expired" class="task-skip">任务已截止：成员不能再提交，管理员仍可审核已提交内容。</p>
        <p v-else-if="task.submittedCount > 0" class="task-skip">
          还有 {{ task.submittedCount }} 人待审核；截止后仍可审核，驳回后本人有 24 小时补交。
        </p>
        <div class="task-card-actions">
          <button type="button" :disabled="working" @click="openEdit(task)">
            <Pencil :size="15" aria-hidden="true" />编辑
          </button>
          <button
            v-if="task.status === 'DRAFT'"
            type="button"
            :disabled="working"
            @click="runAction(publishTask, task, `确认发布「${task.title}」？发布后将锁定积分与发放对象。`)"
          >
            <Send :size="15" aria-hidden="true" />发布
          </button>
          <button
            v-if="task.status === 'PUBLISHED'"
            type="button"
            :disabled="working"
            @click="
              runAction(
                closeTask,
                task,
                `确认结束「${task.title}」？结束后成员不能再提交，管理员也不能再审核或驳回，且不可撤销。`,
              )
            "
          >
            <Square :size="15" aria-hidden="true" />结束
          </button>
          <button
            v-if="task.status === 'DRAFT'"
            type="button"
            class="danger"
            :disabled="working"
            @click="runAction(deleteTask, task, `确认删除草稿「${task.title}」？`)"
          >
            <Trash2 :size="15" aria-hidden="true" />删除
          </button>
          <RouterLink v-if="task.status !== 'DRAFT'" :to="`/admin/tasks/${task.id}/progress`">
            <Eye :size="15" aria-hidden="true" />完成情况
          </RouterLink>
        </div>
      </article>
    </section>
  </PortalShell>
</template>

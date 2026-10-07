<script setup>
import { ClipboardCheck, Eye, Pencil, Plus, Search, Send, Square, Trash2, Users } from '@lucide/vue'
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import PortalShell from '../components/PortalShell.vue'
import AdminDrawer from '../components/AdminDrawer.vue'
import LoadingSkeleton from '../components/LoadingSkeleton.vue'
import { confirmAction } from '../services/confirm'
import { toast } from '../services/toast'
import { useUnsavedGuard } from '../composables/useUnsavedGuard'
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
const members = ref([])
const loading = ref(true)
const working = ref(false)
const errorMessage = ref('')
const memberError = ref('')
const fields = ref({})
const errorBox = ref(null)
const keyword = ref('')
const statusFilter = ref('ALL')
const sort = ref('priority')
const memberKeyword = ref('')
const onlySelected = ref(false)
const showForm = ref(false)
const editing = ref(null)
const preview = ref(null)
const savedSnapshot = ref('')
const originalSubtaskContents = ref({})
const flashId = ref(null)
const emptyForm = () => ({
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
const form = reactive(emptyForm())
const statusLabels = { DRAFT: '草稿', PUBLISHED: '已发布', CLOSED: '已结束' }
const roleLabels = { TEACHER: '教师', CORE_STUDENT: '核心学生', MEMBER: '普通成员' }
const memberStatusLabels = { TRIAL: '试用', OFFICIAL: '正式' }
const published = computed(() => editing.value?.status === 'PUBLISHED')
function snapshot(value) {
  return JSON.stringify({
    ...value,
    subtasks: value.subtasks.map((item) => ({
      ...item,
      contentHtml: item.contentHtml === undefined ? originalSubtaskContents.value[item.id] : item.contentHtml,
    })),
  })
}
const dirty = computed(() => showForm.value && snapshot(form) !== snapshot(JSON.parse(savedSnapshot.value)))
const reviewCount = computed(() =>
  tasks.value.filter((t) => t.status === 'PUBLISHED').reduce((sum, t) => sum + t.submittedCount, 0),
)
const matchesStatus = (task, status) =>
  status === 'ALL' ||
  (status === 'REVIEW' ? task.status === 'PUBLISHED' && task.submittedCount > 0 : task.status === status)
const filters = computed(() =>
  Object.entries({ ALL: '全部', REVIEW: '待审核', PUBLISHED: '已发布', DRAFT: '草稿', CLOSED: '已结束' }).map(
    ([value, label]) => ({ value, label, count: tasks.value.filter((t) => matchesStatus(t, value)).length }),
  ),
)
const priority = (task) =>
  task.status === 'CLOSED' ? 4 : task.status === 'DRAFT' ? 3 : task.submittedCount > 0 ? 0 : task.expired ? 2 : 1
const filtered = computed(() =>
  tasks.value
    .filter((t) => matchesStatus(t, statusFilter.value) && t.title.toLowerCase().includes(keyword.value.toLowerCase()))
    .sort((a, b) =>
      sort.value === 'title'
        ? a.title.localeCompare(b.title, 'zh-CN')
        : sort.value === 'deadline'
          ? (a.endDate || '9999').localeCompare(b.endDate || '9999')
          : priority(a) - priority(b),
    ),
)
const visibleMembers = computed(() =>
  members.value.filter(
    (m) =>
      (!onlySelected.value || form.memberProfileIds.includes(m.profileId)) &&
      [m.name, m.memberCode, m.grade].join(' ').toLowerCase().includes(memberKeyword.value.toLowerCase()),
  ),
)
watch(
  () => JSON.stringify([form.roles, form.statuses, form.gradesText, form.tagsText, form.memberProfileIds]),
  () => {
    preview.value = null
  },
)
onMounted(() => Promise.all([loadTasks(), loadMembers()]))
onBeforeRouteLeave(() => !working.value)
useUnsavedGuard(dirty)

function clearFeedback() {
  errorMessage.value = ''
  fields.value = {}
}
async function report(error) {
  const message = error.status >= 500 ? '请求失败，请稍后重试。当前修改已保留。' : error.message
  if (!showForm.value) {
    toast.error(message)
    return
  }
  errorMessage.value = message
  fields.value = error.fields || {}
  await nextTick()
  errorBox.value?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  errorBox.value?.focus({ preventScroll: true })
}
let hasLoaded = false
async function loadTasks() {
  // Keep current content on screen while refreshing after an action.
  if (!hasLoaded) loading.value = true
  try {
    tasks.value = await listTasks()
  } catch (error) {
    await report(error)
  } finally {
    hasLoaded = true
    loading.value = false
  }
}
async function loadMembers() {
  memberError.value = ''
  try {
    members.value = await listTaskMemberOptions()
  } catch {
    memberError.value = '成员列表加载失败'
  }
}
function applyDetail(detail, savedSubtasks = []) {
  editing.value = detail
  originalSubtaskContents.value = {}
  const rules = (dimension) => detail.rules.filter((r) => r.dimension === dimension).map((r) => r.value)
  Object.assign(form, {
    title: detail.title,
    contentHtml: detail.contentHtml,
    startDate: detail.startDate || '',
    endDate: detail.endDate || '',
    points: detail.points,
    subtasks: detail.subtasks.map((s, index) => ({
      id: s.id,
      title: s.title,
      hasContent: s.hasContent,
      ...(savedSubtasks[index] ? { contentHtml: savedSubtasks[index].contentHtml } : {}),
    })),
    roles: rules('ROLE'),
    statuses: rules('MEMBER_STATUS'),
    gradesText: rules('GRADE').join('、'),
    tagsText: rules('SKILL_TAG').join('、'),
    memberProfileIds: [...(detail.memberProfileIds || [])],
  })
  savedSnapshot.value = JSON.stringify(form)
}
function highlight(id) {
  flashId.value = null
  nextTick(() => {
    flashId.value = id
  })
}
function openCreate() {
  clearFeedback()
  editing.value = null
  preview.value = null
  Object.assign(form, emptyForm())
  savedSnapshot.value = JSON.stringify(form)
  memberKeyword.value = ''
  onlySelected.value = false
  showForm.value = true
}
async function openEdit(task, withPreview = false) {
  clearFeedback()
  working.value = true
  try {
    applyDetail(await getTask(task.id))
    preview.value = null
    memberKeyword.value = ''
    onlySelected.value = false
    showForm.value = true
  } catch (error) {
    await report(error)
  } finally {
    working.value = false
  }
  await nextTick()
  if (withPreview && showForm.value) await runPreview()
}
const splitList = (value) => [
  ...new Set(
    value
      .split(/[\n、,，]/)
      .map((s) => s.trim())
      .filter(Boolean),
  ),
]
function buildRules() {
  return [
    ['ROLE', form.roles],
    ['MEMBER_STATUS', form.statuses],
    ['GRADE', splitList(form.gradesText)],
    ['SKILL_TAG', splitList(form.tagsText)],
  ].flatMap(([dimension, values]) => values.map((value) => ({ dimension, value })))
}
async function loadSubtaskContent(id) {
  const detail = await getAdminSubtask(editing.value.id, id)
  originalSubtaskContents.value[id] = detail.contentHtml ?? null
  return detail
}
async function buildPayload() {
  const subtasks = await Promise.all(
    form.subtasks.map(async (item) => ({
      id: item.id || null,
      title: item.title.trim(),
      contentHtml:
        item.id && item.hasContent && item.contentHtml === undefined
          ? (await loadSubtaskContent(item.id)).contentHtml
          : (item.contentHtml ?? null),
    })),
  )
  return {
    title: form.title.trim(),
    contentHtml: form.contentHtml,
    startDate: form.startDate || null,
    endDate: form.endDate || null,
    points: Number(form.points),
    subtasks,
    rules: buildRules(),
    memberProfileIds: form.memberProfileIds,
  }
}
async function runPreview() {
  working.value = true
  clearFeedback()
  try {
    preview.value = await previewTaskAudience({ rules: buildRules(), memberProfileIds: form.memberProfileIds })
  } catch (error) {
    await report(error)
  } finally {
    working.value = false
  }
}
async function save() {
  if (working.value) return
  clearFeedback()
  const plain = document.createElement('div')
  plain.innerHTML = form.contentHtml
  if (!plain.textContent.trim() && !plain.querySelector('img')) fields.value.contentHtml = '请填写任务说明'
  if (form.startDate && form.endDate && form.endDate < form.startDate) fields.value.endDate = '截止日期不能早于开始日期'
  if (form.subtasks.some((s) => !s.title.trim())) fields.value.subtasks = '请补全子任务标题，或删除空白项'
  if (form.memberProfileIds.length > 100) fields.value.memberProfileIds = '最多指定 100 位成员'
  if (Object.keys(fields.value).length) {
    await report({ message: '请检查以下内容后重试', fields: fields.value })
    return
  }
  if (
    published.value &&
    editing.value.subtasks.some((s) => !form.subtasks.some((item) => item.id === s.id)) &&
    !(await confirmAction({
      title: '删除已发布任务的子任务？',
      message: '删除子任务会同时删除其提交记录，且无法恢复。',
      confirmText: '仍然保存',
      tone: 'danger',
    }))
  )
    return
  working.value = true
  try {
    const payload = await buildPayload()
    const detail = editing.value ? await updateTask(editing.value.id, payload) : await createTask(payload)
    const created = !editing.value
    applyDetail(detail, payload.subtasks)
    toast.success(created ? '草稿已创建，预览发放名单后即可发布。' : '修改已保存。', { title: detail.title })
    await loadTasks()
    highlight(detail.id)
  } catch (error) {
    await report(error)
  } finally {
    working.value = false
  }
}
async function runAction(action, task, options, doneMessage) {
  if (!(await confirmAction(options))) return
  working.value = true
  clearFeedback()
  try {
    await action(task.id)
    showForm.value = false
    toast.success(doneMessage, { title: task.title })
    await loadTasks()
    highlight(task.id)
  } catch (error) {
    await report(error)
  } finally {
    working.value = false
  }
}
const deleteDraft = (task) =>
  runAction(
    deleteTask,
    task,
    {
      title: '删除草稿？',
      message: `确定删除草稿「${task.title}」？删除后无法恢复。`,
      confirmText: '删除草稿',
      tone: 'danger',
    },
    '草稿已删除。',
  )
const finishTask = (task) =>
  runAction(
    closeTask,
    task,
    {
      title: '结束任务？',
      message: `确定结束「${task.title}」？`,
      details: ['结束后成员不能再提交，管理员也不能再审核。', '此操作无法撤销。'],
      confirmText: '结束任务',
      tone: 'danger',
    },
    '任务已结束。',
  )
const publishCurrent = () =>
  runAction(
    publishTask,
    editing.value,
    {
      title: `发布给 ${preview.value.total} 人？`,
      message: `确定发布「${form.title}」？`,
      details: [
        '发布后积分与发放对象将被锁定。',
        `${preview.value.pointEligibleCount} 人满足计分条件，积分在到期后统一结算。`,
      ],
      confirmText: '确认发布',
    },
    `已向 ${preview.value.total} 人发布。`,
  )
const fieldTargets = {
  title: 'task-title',
  contentHtml: 'task-content',
  endDate: 'task-end',
  subtasks: 'task-subtasks',
  memberProfileIds: 'task-members',
}
</script>

<template>
  <PortalShell title="任务管理" description="发布任务、查看进度与处理审核。">
    <div class="tm">
      <nav class="tm-modules" aria-label="任务模块">
        <RouterLink to="/admin/tasks" aria-current="page">普通任务</RouterLink>
        <RouterLink to="/admin/tasks/onboarding">新手任务</RouterLink>
        <RouterLink to="/admin/bounties">悬赏任务</RouterLink>
      </nav>
      <section>
        <header class="tm-heading">
          <div>
            <h2 id="task-list-title" tabindex="-1">
              普通任务 <span>{{ tasks.length }}</span>
            </h2>
            <p>{{ reviewCount ? `${reviewCount} 份提交待审核` : '所有任务集中管理' }}</p>
          </div>
          <button class="portal-primary" :disabled="working" @click="openCreate">
            <Plus :size="18" aria-hidden="true" />新建任务
          </button>
        </header>
        <div class="tm-toolbar">
          <label class="tm-search"
            ><Search :size="18" aria-hidden="true" /><input
              v-model.trim="keyword"
              placeholder="搜索任务名称"
              aria-label="搜索任务名称"
          /></label>
          <label class="tm-sort"
            >排序<select v-model="sort">
              <option value="priority">待办优先</option>
              <option value="deadline">截止日期</option>
              <option value="title">任务名称</option>
            </select></label
          >
        </div>
        <div class="tm-filters" aria-label="任务状态筛选">
          <button
            v-for="filter in filters"
            :key="filter.value"
            :aria-pressed="statusFilter === filter.value"
            @click="statusFilter = filter.value"
          >
            {{ filter.label }}<span>{{ filter.count }}</span>
          </button>
        </div>
        <LoadingSkeleton v-if="loading" variant="cards" :rows="3" label="正在读取任务" />
        <div v-else-if="!filtered.length" class="tm-empty">
          <ClipboardCheck :size="32" aria-hidden="true" />
          <h3>{{ tasks.length ? '没有匹配的任务' : '还没有普通任务' }}</h3>
          <p>{{ tasks.length ? '试试其他关键词或状态' : '新建草稿，确认发放对象后发布' }}</p>
        </div>
        <TransitionGroup v-else tag="div" name="list" class="tm-list">
          <article v-for="task in filtered" :key="task.id" class="tm-row" :class="{ 'ui-flash': flashId === task.id }">
            <div class="tm-task-info">
              <div class="tm-meta">
                <span class="tm-badge" :data-status="task.status">{{
                  task.expired && task.status === 'PUBLISHED' ? '已截止' : statusLabels[task.status]
                }}</span
                ><span>{{ task.points ? `${task.points} 积分` : '不计分' }}</span>
              </div>
              <h3>{{ task.title }}</h3>
              <p>
                {{ task.endDate ? `${task.endDate} 截止` : '不限截止日期'
                }}<span v-if="task.points"> · {{ task.pointsSettledAt ? '积分已结算' : '到期结算积分' }}</span>
              </p>
              <p v-if="task.expired && task.status === 'PUBLISHED'">已停止提交，仍可审核</p>
            </div>
            <dl class="tm-stats">
              <div>
                <dt>已分配</dt>
                <dd>{{ task.assignmentCount }}</dd>
              </div>
              <div :class="{ 'tm-attention': task.status === 'PUBLISHED' && task.submittedCount }">
                <dt>待审核</dt>
                <dd>{{ task.submittedCount }}</dd>
              </div>
              <div>
                <dt>已通过</dt>
                <dd>{{ task.approvedCount }}</dd>
              </div>
            </dl>
            <div class="tm-row-actions">
              <RouterLink
                v-if="task.status !== 'DRAFT'"
                :class="task.status === 'PUBLISHED' && task.submittedCount ? 'portal-primary' : 'portal-secondary'"
                :to="`/admin/tasks/${task.id}/progress`"
                ><Eye :size="16" aria-hidden="true" />{{
                  task.status === 'PUBLISHED' && task.submittedCount ? '去审核' : '查看进度'
                }}</RouterLink
              >
              <button
                v-if="task.status !== 'CLOSED'"
                class="portal-secondary"
                :disabled="working"
                @click="openEdit(task)"
              >
                <Pencil :size="16" aria-hidden="true" />编辑
              </button>
              <button
                v-if="task.status === 'DRAFT'"
                class="portal-primary"
                :disabled="working"
                @click="openEdit(task, true)"
              >
                <Send :size="16" aria-hidden="true" />预览发布
              </button>
              <button
                v-if="task.status === 'DRAFT'"
                class="tm-icon tm-danger"
                :aria-label="`删除草稿：${task.title}`"
                :disabled="working"
                @click="deleteDraft(task)"
              >
                <Trash2 :size="18" aria-hidden="true" />
              </button>
              <button
                v-if="task.status === 'PUBLISHED'"
                class="tm-icon"
                :aria-label="`结束任务：${task.title}`"
                :disabled="working"
                @click="finishTask(task)"
              >
                <Square :size="17" aria-hidden="true" />
              </button>
            </div>
          </article>
        </TransitionGroup>
      </section>
    </div>

    <AdminDrawer
      v-model:open="showForm"
      :title="editing ? '编辑任务' : '新建任务'"
      :description="editing ? editing.title : '保存为草稿后，预览发放名单即可发布。'"
      size="lg"
      :dirty="dirty"
      :busy="working"
      @submit="save"
    >
      <div class="tm tm--drawer">
        <div v-if="errorMessage" ref="errorBox" class="tm-feedback tm-error" role="alert" tabindex="-1">
          <strong>{{ errorMessage }}</strong>
          <ul v-if="Object.keys(fields).length">
            <li v-for="(message, key) in fields" :key="key">
              <a v-if="fieldTargets[key]" :href="`#${fieldTargets[key]}`">{{ message }}</a
              ><span v-else>{{ message }}</span>
            </li>
          </ul>
        </div>
        <p v-if="published" class="tm-note">已发布：积分和发放对象不可修改。</p>
        <fieldset class="tm-body" :disabled="working" :inert="working" :aria-busy="working">
          <section class="tm-section" aria-labelledby="task-settings">
            <header>
              <span>01</span>
              <div>
                <h3 id="task-settings">任务设置</h3>
                <p>名称、时间与积分</p>
              </div>
            </header>
            <div class="tm-fields">
              <label class="tm-full"
                >任务名称<input
                  id="task-title"
                  v-model="form.title"
                  required
                  maxlength="160"
                  placeholder="例如：完成 3D 建模练习"
                  :aria-invalid="!!fields.title"
                /><small v-if="fields.title" class="tm-danger">{{ fields.title }}</small></label
              >
              <label>开始日期<input v-model="form.startDate" type="date" /><small>留空即发布后可开始</small></label>
              <label
                >截止日期<input
                  id="task-end"
                  v-model="form.endDate"
                  type="date"
                  :min="form.startDate || undefined"
                  :aria-invalid="!!fields.endDate"
                /><small>仅截止提交，不影响审核</small></label
              >
              <label
                >奖励积分<input
                  v-model.number="form.points"
                  type="number"
                  min="0"
                  max="100000"
                  required
                  :disabled="published"
                /><small>0 表示不计分；到期结算</small></label
              >
            </div>
          </section>
          <section class="tm-section" aria-labelledby="task-content-heading">
            <header>
              <span>02</span>
              <div>
                <h3 id="task-content-heading">内容与子任务</h3>
                <p>说明完成要求，按需拆分步骤</p>
              </div>
            </header>
            <div id="task-content" tabindex="-1">
              <DiscussionRichTextEditor v-model="form.contentHtml" label="任务说明" :max-length="20000" />
            </div>
            <div id="task-subtasks" tabindex="-1">
              <TaskSubtaskEditor
                :key="editing?.id || 'new'"
                v-model="form.subtasks"
                label="子任务"
                hint="可选。无子任务时，成员直接提交完成说明。"
                :load-content="loadSubtaskContent"
                :disabled="working"
              />
            </div>
          </section>
          <section class="tm-section" aria-labelledby="task-audience">
            <header>
              <span>03</span>
              <div>
                <h3 id="task-audience">发放对象</h3>
                <p>{{ published ? '已锁定发布时的发放配置' : '按条件筛选，也可直接指定成员' }}</p>
              </div>
            </header>
            <fieldset class="tm-body tm-audience" :disabled="published">
              <div class="tm-conditions">
                <div class="tm-panel-heading">
                  <h4>条件筛选</h4>
                  <span>可选</span>
                </div>
                <p class="tm-help">同组任选，各组同时满足。</p>
                <div class="tm-condition-row" role="group" aria-labelledby="task-role-label">
                  <span id="task-role-label" class="tm-label">角色</span>
                  <div class="tm-checks">
                    <label v-for="(label, value) in roleLabels" :key="value" class="tm-check"
                      ><input v-model="form.roles" type="checkbox" :value="value" />{{ label }}</label
                    >
                  </div>
                </div>
                <div class="tm-condition-row" role="group" aria-labelledby="task-status-label">
                  <span id="task-status-label" class="tm-label">成员状态</span>
                  <div class="tm-checks">
                    <label v-for="(label, value) in memberStatusLabels" :key="value" class="tm-check"
                      ><input v-model="form.statuses" type="checkbox" :value="value" />{{ label }}</label
                    >
                  </div>
                </div>
                <div class="tm-condition-fields">
                  <label class="tm-field"
                    >年级<input
                      v-model="form.gradesText"
                      placeholder="24级、大二"
                      aria-describedby="task-condition-hint"
                  /></label>
                  <label class="tm-field"
                    >能力标签<input
                      v-model="form.tagsText"
                      placeholder="无人机、视觉"
                      aria-describedby="task-condition-hint"
                  /></label>
                </div>
                <p id="task-condition-hint" class="tm-help">多个年级或标签用顿号分隔。</p>
              </div>
              <div id="task-members" class="tm-members" tabindex="-1">
                <div class="tm-member-heading tm-panel-heading">
                  <h4>指定成员</h4>
                  <span class="tm-selection-count" role="status" aria-atomic="true"
                    >已选 {{ form.memberProfileIds.length }} 人</span
                  >
                  <button
                    v-if="form.memberProfileIds.length"
                    class="tm-back"
                    type="button"
                    @click="form.memberProfileIds = []"
                  >
                    清空选择
                  </button>
                </div>
                <p class="tm-help">与筛选结果合并，不会重复发放。</p>
                <label class="tm-search"
                  ><Search :size="16" aria-hidden="true" /><input
                    v-model.trim="memberKeyword"
                    placeholder="搜索姓名、学号或年级"
                    aria-label="搜索成员"
                    @keydown.enter.prevent
                /></label>
                <div class="tm-member-tools">
                  <span>{{ memberKeyword ? '匹配' : '显示' }} {{ visibleMembers.length }} 人</span
                  ><label class="tm-check tm-selected"><input v-model="onlySelected" type="checkbox" />仅看已选</label>
                </div>
                <div v-if="memberError" class="tm-help" role="alert">
                  {{ memberError }}<button class="tm-back" type="button" @click="loadMembers">重试</button>
                </div>
                <div v-else class="tm-member-list">
                  <label v-for="member in visibleMembers" :key="member.profileId" class="tm-member"
                    ><input v-model="form.memberProfileIds" type="checkbox" :value="member.profileId" /><span
                      ><strong>{{ member.name }}</strong
                      ><small
                        >{{ member.memberCode || '未填学号' }} ·
                        {{ memberStatusLabels[member.memberStatus] || member.memberStatus }}</small
                      ></span
                    ></label
                  >
                  <p v-if="!visibleMembers.length" class="tm-help">没有匹配的成员</p>
                </div>
              </div>
            </fieldset>
            <div v-if="!published" class="tm-preview">
              <button class="portal-secondary" type="button" @click="runPreview">
                <Users :size="17" aria-hidden="true" />预览发放名单</button
              ><template v-if="preview"
                ><strong role="status">共 {{ preview.total }} 人 · {{ preview.pointEligibleCount }} 人可计分</strong>
                <details v-if="preview.members.length">
                  <summary>查看名单</summary>
                  <ul>
                    <li v-for="member in preview.members" :key="member.memberProfileId">
                      {{ member.name }} · {{ member.memberCode || '未填学号'
                      }}<span v-if="!member.pointEligible"> · {{ member.pointIneligibleReason }}</span>
                    </li>
                  </ul>
                </details></template
              >
            </div>
          </section>
        </fieldset>
        <p v-if="preview && !published && (!preview.total || dirty)" class="tm-help tm-publish-hint" role="status">
          {{ !preview.total ? '名单为空，请调整发放条件或指定成员。' : '请先保存修改，再发布任务。' }}
        </p>
      </div>
      <template #status>{{
        working ? '正在处理…' : dirty ? '修改尚未保存' : editing ? '已保存全部修改' : '保存后可发布'
      }}</template>
      <template #footer="{ requestClose }">
        <button type="button" class="ui-btn ui-btn--secondary" :disabled="working" @click="requestClose">
          {{ dirty ? '取消' : '关闭' }}
        </button>
        <button
          type="submit"
          class="ui-btn"
          :class="editing && !published && preview ? 'ui-btn--secondary' : 'ui-btn--primary'"
          :disabled="working || (Boolean(editing) && !dirty)"
        >
          {{ editing ? '保存修改' : '保存草稿' }}
        </button>
        <button
          v-if="editing && !published && preview"
          type="button"
          class="ui-btn ui-btn--primary"
          :disabled="working || dirty || !preview.total"
          @click="publishCurrent"
        >
          <Send :size="16" aria-hidden="true" />发布
        </button>
      </template>
    </AdminDrawer>
  </PortalShell>
</template>

<style scoped>
.tm {
  --tm-muted: var(--color-muted-foreground);
  color: var(--color-foreground);
}
.tm button,
.tm input,
.tm select,
.tm a {
  touch-action: manipulation;
}
.tm button,
.tm select,
.tm input:not([type='checkbox']),
.tm-row-actions a {
  min-height: 44px !important;
}
.tm :deep(button) {
  min-height: 44px;
  min-width: 44px;
}
.tm button {
  cursor: pointer;
}
.tm button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
.tm :is(button, a, input, select, summary):focus-visible,
.tm [tabindex='-1']:focus-visible {
  outline: 2px solid var(--color-ring);
  outline-offset: 3px;
}
.tm-modules {
  display: flex;
  gap: 6px;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 28px;
}
.tm-modules a {
  min-height: 48px;
  display: inline-flex;
  align-items: center;
  padding: 10px 18px;
  color: var(--tm-muted);
  font-weight: 600;
  text-decoration: none;
  border-bottom: 2px solid transparent;
}
.tm-modules a[aria-current='page'] {
  color: var(--color-foreground);
  border-color: var(--color-primary);
}
.tm-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}
.tm h2,
.tm h3,
.tm h4,
.tm p {
  margin: 0;
}
.tm h2 {
  font-size: 24px;
  letter-spacing: -0.03em;
}
.tm h2 span,
.tm h4 span {
  color: var(--tm-muted);
  margin-left: 8px;
  font-size: 14px;
  font-weight: 500;
}
.tm-heading p,
.tm-task-info p {
  color: var(--tm-muted);
  font-size: 13px;
  margin-top: 8px;
}
.tm-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.tm-search {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  border: 1px solid var(--color-border-strong);
  border-radius: 10px;
  background: var(--color-card);
  color: var(--tm-muted);
  min-width: 0;
}
.tm-toolbar .tm-search {
  width: min(100%, 420px);
}
.tm .tm-search input {
  border: 0 !important;
  background: transparent !important;
  padding: 10px 0;
  box-shadow: none !important;
  min-width: 0;
  width: 100%;
  outline: none !important;
  color: var(--color-foreground);
}
.tm-search:focus-within {
  outline: 2px solid var(--color-ring);
  outline-offset: 2px;
}
.tm-sort {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--tm-muted);
  white-space: nowrap;
  font-size: 13px;
}
.tm-sort select {
  width: auto;
}
.tm-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 20px;
}
.tm-filters button {
  display: flex;
  align-items: center;
  gap: 9px;
  border: 1px solid transparent;
  border-radius: 9px;
  padding: 8px 13px;
  color: var(--tm-muted);
  background: transparent;
  font-weight: 600;
}
.tm-filters button[aria-pressed='true'] {
  background: var(--color-card);
  border-color: var(--color-border);
  color: var(--color-foreground);
}
.tm-filters span {
  font-variant-numeric: tabular-nums;
  font-size: 12px;
}
.tm-list {
  display: grid;
  gap: 12px;
}
.tm-row {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) 210px auto;
  align-items: center;
  gap: 24px;
  padding: 22px 24px;
  border: 1px solid var(--color-border);
  border-radius: 16px;
  background: var(--color-card);
}
.tm-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--tm-muted);
  font-size: 12px;
}
.tm-badge {
  display: inline-flex;
  align-items: center;
  padding: 5px 9px;
  border-radius: 6px;
  font-size: 12px;
  color: var(--tm-muted);
  background: var(--color-surface-soft);
  white-space: nowrap;
}
.tm-badge[data-status='PUBLISHED'] {
  color: var(--color-foreground);
  border: 1px solid var(--color-border);
}
.tm-task-info {
  min-width: 0;
  overflow-wrap: anywhere;
}
.tm-task-info h3 {
  margin-top: 10px;
  font-size: 17px;
}
.tm-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin: 0;
  text-align: center;
}
.tm-stats dt {
  font-size: 12px;
  color: var(--tm-muted);
}
.tm-stats dd {
  margin: 5px 0 0;
  font-size: 22px;
  font-variant-numeric: tabular-nums;
}
.tm-attention dd {
  font-weight: 750;
}
.tm-row-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.tm-row-actions :is(button, a) {
  white-space: nowrap;
}
.tm-icon {
  display: grid;
  place-items: center;
  min-width: 44px;
  padding: 10px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: transparent;
  color: var(--tm-muted);
}
.tm .tm-danger {
  color: var(--color-danger);
}
.tm-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 70px 20px;
  color: var(--tm-muted);
  border: 1px dashed var(--color-border);
  border-radius: 16px;
  text-align: center;
}
.tm-empty h3 {
  color: var(--color-foreground);
}
.tm-back {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--tm-muted);
  font-size: 13px;
}
/* Editor inside the drawer: single-column audience panel, tighter sections. */
.tm--drawer .tm-section {
  padding: 20px;
}
.tm--drawer .tm-audience {
  grid-template-columns: 1fr;
}
.tm--drawer .tm-members {
  border-top: 1px solid var(--color-border);
  border-left: 0;
  padding: 16px 0 0;
}
.tm--drawer .tm-note {
  margin-top: 0;
}
.tm-body {
  padding: 0;
  margin: 0;
  border: 0;
  min-width: 0;
}
.tm-section {
  padding: 26px;
  border: 1px solid var(--color-border);
  border-radius: 16px;
  background: var(--color-card);
  margin-bottom: 20px;
  min-width: 0;
}
.tm-section > header {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 24px;
}
.tm-section > header > span {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  color: var(--tm-muted);
  font-size: 12px;
}
.tm-section h3 {
  font-size: 17px;
}
.tm-section header p,
.tm-help {
  color: var(--tm-muted);
  font-size: 13px;
  line-height: 1.6;
}
.tm-section header p {
  margin-top: 4px;
}
.tm-fields {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
}
.tm-fields label,
.tm-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  min-width: 0;
}
.tm-full {
  grid-column: 1/-1;
}
.tm small {
  color: var(--tm-muted);
  font-size: 12px;
  font-weight: 400;
  line-height: 1.6;
}
.tm-fields input,
.tm-field input,
.tm-sort select {
  border: 1px solid var(--color-border);
  border-radius: 9px;
  background: var(--color-card);
  color: var(--color-foreground);
  padding: 10px 12px;
  max-width: 100%;
  min-width: 0;
}
#task-subtasks {
  margin-top: 24px;
}
.tm-audience {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 24px;
}
.tm-audience:disabled {
  opacity: 0.75;
}
.tm-conditions {
  display: grid;
  gap: 16px;
  align-content: start;
}
.tm-panel-heading {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 44px;
}
.tm-panel-heading > span {
  color: var(--tm-muted);
  font-size: 12px;
}
.tm-panel-heading + .tm-help {
  margin: -8px 0 4px;
}
.tm-condition-row {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  gap: 12px;
  align-items: start;
}
.tm-condition-row > .tm-label {
  display: flex;
  align-items: center;
  min-height: 44px;
  margin: 0;
}
.tm-condition-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 4px;
}
.tm-members {
  border-left: 1px solid var(--color-border);
  padding-left: 24px;
  min-width: 0;
}
.tm-member-tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--tm-muted);
  font-size: 12px;
  margin: 4px 0;
}
.tm-audience h4 {
  font-size: 14px;
  color: var(--color-foreground);
}
.tm-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}
.tm-checks {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.tm-check {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
  flex-direction: row;
  justify-content: flex-start;
}
.tm-check:focus-within,
.tm-member:focus-within {
  outline: 2px solid var(--color-ring);
  outline-offset: 2px;
}
.tm-check:has(input:checked) {
  background: var(--color-surface-soft);
  border-color: var(--color-foreground);
}
.tm input[type='checkbox'] {
  width: 17px;
  height: 17px;
  min-height: 17px;
  flex: 0 0 17px;
  margin: 0;
  accent-color: var(--color-primary);
}
.tm-member-heading {
  display: flex;
  justify-content: flex-start;
  align-items: center;
  min-height: 44px;
}
.tm-member-heading .tm-back {
  margin-left: auto;
}
.tm-selection-count {
  border-radius: 6px;
  padding: 4px 8px;
  background: var(--color-surface-soft);
  font-variant-numeric: tabular-nums;
}
.tm-members > .tm-help {
  margin: 8px 0 20px;
}
.tm-selected {
  border: 0;
  padding: 8px 0;
}
.tm-selected:has(input:checked) {
  background: transparent;
}
.tm-member-list {
  max-height: 232px;
  overflow: auto;
  border: 1px solid var(--color-border);
  border-radius: 10px;
}
.tm-member-list > p {
  padding: 20px;
}
.tm-member {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 56px;
  padding: 10px 14px;
  cursor: pointer;
}
.tm-member + .tm-member {
  border-top: 1px solid var(--color-border);
}
.tm-member:has(input:checked) {
  background: var(--color-surface-soft);
}
.tm-member span {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
  overflow-wrap: anywhere;
}
.tm-member strong {
  font-size: 14px;
}
.tm-preview {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--color-border);
  font-size: 13px;
}
.tm-preview details {
  width: 100%;
}
.tm-preview details:not([open]) {
  width: auto;
  margin-left: auto;
}
.tm-preview summary {
  cursor: pointer;
  min-height: 44px;
  display: flex;
  align-items: center;
  text-decoration: underline;
}
.tm-preview ul {
  max-height: 220px;
  overflow: auto;
  padding-left: 20px;
  line-height: 1.9;
}
.tm-feedback,
.tm-note {
  padding: 14px 18px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  margin-bottom: 20px !important;
  background: var(--color-card);
  font-size: 14px;
  line-height: 1.6;
}
.tm-error {
  border-color: var(--color-danger);
}
.tm-error a {
  color: inherit;
  text-decoration: underline;
}
@media (hover: hover) and (pointer: fine) {
  .tm-icon:hover,
  .tm-back:hover,
  .tm-filters button:hover {
    color: var(--color-foreground);
    background: var(--color-surface-soft);
  }
  .tm-member:hover {
    background: var(--color-surface-soft);
  }
}
@media (max-width: 1200px) {
  .tm-row {
    grid-template-columns: minmax(0, 1fr) 200px;
    gap: 20px;
  }
  .tm-row-actions {
    grid-column: 1/-1;
    border-top: 1px solid var(--color-border);
    padding-top: 16px;
  }
}
@media (max-width: 1024px) {
  .tm-audience {
    grid-template-columns: 1fr;
  }
  .tm-members {
    border-top: 1px solid var(--color-border);
    border-left: 0;
    padding: 16px 0 0;
  }
}
@media (max-width: 640px) {
  .tm-condition-row,
  .tm-condition-fields {
    grid-template-columns: 1fr;
    gap: 8px;
  }
  .tm-condition-row > .tm-label {
    min-height: 0;
  }
  .tm-condition-fields {
    gap: 16px;
  }
  .tm-panel-heading {
    gap: 8px;
  }
  .tm-modules {
    gap: 0;
  }
  .tm-modules a {
    padding: 10px 12px;
    font-size: 14px;
  }
  .tm-heading {
    align-items: flex-start;
  }
  .tm h2 {
    font-size: 21px;
  }
  .tm-toolbar {
    flex-direction: column;
  }
  .tm-sort {
    justify-content: space-between;
  }
  .tm-toolbar .tm-search {
    width: 100%;
  }
  .tm-filters {
    gap: 4px;
  }
  .tm-filters button {
    padding: 8px 10px;
  }
  .tm-row {
    grid-template-columns: 1fr;
    padding: 18px;
    gap: 18px;
  }
  .tm-stats {
    text-align: left;
  }
  .tm-row-actions {
    gap: 6px;
  }
  .tm-fields {
    grid-template-columns: 1fr;
  }
  .tm-section {
    padding: 18px;
  }
  .tm-badge {
    white-space: normal;
  }
}
</style>

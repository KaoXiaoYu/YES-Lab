<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Gift, Pencil, Plus, Send, Square, Trash2 } from '@lucide/vue'
import PortalShell from '../components/PortalShell.vue'
import DiscussionRichTextEditor from '../components/DiscussionRichTextEditor.vue'
import TaskSubtaskEditor from '../components/TaskSubtaskEditor.vue'
import {
  closeBounty,
  createBounty,
  deleteBounty,
  getAdminSubtask,
  getBountyClaims,
  listBounties,
  publishBounty,
  updateBounty,
} from '../services/authApi'

const bounties = ref([])
const loading = ref(true)
const working = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const showForm = ref(false)
const editingId = ref('')
const fieldErrors = ref({})
const listFilter = ref('ALL')
const searchQuery = ref('')
/** 字段名 -> DOM 元素：把焦点移到第一个出错的字段（就近提示 + 可键盘定位）。 */
const fieldRefs = {}

const statusLabels = { DRAFT: '草稿', PUBLISHED: '进行中', CLOSED: '已结束' }
const form = reactive({
  title: '',
  contentHtml: '',
  prizeDescription: '',
  prizeSlots: null,
  points: 0,
  headcountLimit: null,
  unlimitedHeadcount: true,
  startDate: '',
  endDate: '',
  subtasks: [],
  roles: [],
  statuses: [],
  gradesText: '',
  tagsText: '',
})

const roleLabels = { TEACHER: '教师', CORE_STUDENT: '核心学生', MEMBER: '普通成员' }

function splitList(value) {
  return (value || '')
    .split(/[、,，]/)
    .map((item) => item.trim())
    .filter(Boolean)
}

/** 接取资格：同维度取「或」，跨维度取「且」；全空表示全体成员可接。 */
function buildRules() {
  const rules = []
  form.roles.forEach((value) => rules.push({ dimension: 'ROLE', value }))
  form.statuses.forEach((value) => rules.push({ dimension: 'MEMBER_STATUS', value }))
  splitList(form.gradesText).forEach((value) => rules.push({ dimension: 'GRADE', value }))
  splitList(form.tagsText).forEach((value) => rules.push({ dimension: 'SKILL_TAG', value }))
  return rules
}

const published = computed(() => bounties.value.find((item) => item.id === editingId.value)?.status === 'PUBLISHED')
const bountyFilters = [
  { id: 'ALL', label: '全部' },
  { id: 'DRAFT', label: '草稿' },
  { id: 'PUBLISHED', label: '进行中' },
  { id: 'CLOSED', label: '已结束' },
]
const bountyCounts = computed(() => ({
  ALL: bounties.value.length,
  DRAFT: bounties.value.filter((item) => item.status === 'DRAFT').length,
  PUBLISHED: bounties.value.filter((item) => item.status === 'PUBLISHED').length,
  CLOSED: bounties.value.filter((item) => item.status === 'CLOSED').length,
}))
const filteredBounties = computed(() => {
  const query = searchQuery.value.trim().toLocaleLowerCase()
  return bounties.value.filter((item) => {
    if (listFilter.value !== 'ALL' && item.status !== listFilter.value) return false
    return !query || item.title.toLocaleLowerCase().includes(query)
  })
})

/** 数值输入框可能是空串（清空）或非数字字符串，统一按后端约束判断。 */
function asInteger(value) {
  if (value === null || value === undefined || value === '') return null
  const parsed = Number(value)
  return Number.isInteger(parsed) ? parsed : Number.NaN
}

function richTextIsBlank(html) {
  return !String(html || '')
    .replace(/<[^>]*>/g, ' ')
    .replace(/&nbsp;|&#160;/gi, ' ')
    .trim()
}

/**
 * 提交前按后端同一套约束校验，并把每一项落到具体字段上。
 * 只依赖后端时用户只能看到一句「请检查提交内容」，不知道改哪里。
 */
function validateForm() {
  const errors = {}
  if (!form.title.trim()) errors.title = '请输入悬赏标题'
  else if (form.title.trim().length > 160) errors.title = '悬赏标题不能超过 160 字'
  if (richTextIsBlank(form.contentHtml)) errors.contentHtml = '请填写悬赏说明'

  const slots = asInteger(form.prizeSlots)
  const hasSlots = slots !== null && !Number.isNaN(slots)
  if (slots !== null && (!hasSlots || slots < 1)) errors.prizeSlots = '奖金份数需为 1—100 的整数'
  else if (hasSlots && slots > 100) errors.prizeSlots = '奖金份数不能超过 100'
  if (hasSlots && !form.prizeDescription.trim()) errors.prizeDescription = '设置奖金份数时必须填写奖金说明'
  else if (form.prizeDescription.trim().length > 500) errors.prizeDescription = '奖金说明不能超过 500 字'

  const rawPoints = asInteger(form.points)
  const points = rawPoints === null ? 0 : rawPoints
  if (Number.isNaN(points) || points < 0) errors.points = '积分需为 0—100000 的整数'
  else if (points > 100000) errors.points = '积分不能超过 100000'

  if (!form.unlimitedHeadcount) {
    const limit = asInteger(form.headcountLimit)
    if (limit === null || Number.isNaN(limit) || limit < 1) errors.headcountLimit = '接取人数上限需为 1—1000 的整数'
    else if (limit > 1000) errors.headcountLimit = '接取人数上限不能超过 1000'
    else if (hasSlots && slots > limit) errors.prizeSlots = '奖金份数不能多于接取人数上限'
  }
  if (form.startDate && form.endDate && form.endDate < form.startDate) errors.endDate = '截止日期不能早于开始日期'
  form.subtasks.forEach((item, index) => {
    if (!String(item.title || '').trim()) errors[`subtasks.${index}.title`] = `第 ${index + 1} 个子任务还没填标题`
  })
  return errors
}

function setFieldRef(name) {
  return (element) => {
    if (element) fieldRefs[name] = element
  }
}

function summaryOf(errors) {
  const first = Object.values(errors)[0]
  return first ? `请检查提交内容：${first}` : ''
}

function focusFirstError() {
  const target = fieldRefs[Object.keys(fieldErrors.value)[0]]
  if (!target || typeof target.focus !== 'function') return
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  target.scrollIntoView({ block: 'center', behavior: reduceMotion ? 'auto' : 'smooth' })
  target.focus({ preventScroll: true })
}

/** 用户开始改某个字段时立刻撤掉它的错误，顶部汇总同步更新。 */
function clearFieldError(name) {
  if (!fieldErrors.value[name]) return
  const { [name]: _removed, ...rest } = fieldErrors.value
  fieldErrors.value = rest
  errorMessage.value = summaryOf(rest)
}

/** 在「不限人数 / 限制人数」之间切换时，人数上限与奖金份数的联带错误都要重算。 */
function onHeadcountModeChange() {
  const { headcountLimit: _limit, prizeSlots: _slots, ...rest } = fieldErrors.value
  fieldErrors.value = rest
  errorMessage.value = summaryOf(rest)
}

async function load() {
  loading.value = true
  try {
    bounties.value = await listBounties()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

onMounted(load)

function resetForm() {
  form.title = ''
  form.contentHtml = ''
  form.prizeDescription = ''
  form.prizeSlots = null
  form.points = 0
  form.headcountLimit = null
  form.unlimitedHeadcount = true
  form.startDate = ''
  form.endDate = ''
  form.subtasks = []
  form.roles = []
  form.statuses = []
  form.gradesText = ''
  form.tagsText = ''
  fieldErrors.value = {}
}

function openCreate() {
  resetForm()
  editingId.value = ''
  showForm.value = true
  errorMessage.value = ''
  successMessage.value = ''
}

function openEdit(item) {
  resetForm()
  editingId.value = item.id
  form.title = item.title
  // 奖金说明只存在于列表视图（详情用的是通用 TaskView，不含悬赏字段）：不回填就会在保存时被判成空。
  form.prizeDescription = item.prizeDescription || ''
  form.prizeSlots = item.prizeSlots
  form.points = item.points
  form.headcountLimit = item.headcountLimit
  form.unlimitedHeadcount = item.headcountLimit == null
  form.startDate = item.startDate || ''
  form.endDate = item.endDate || ''
  showForm.value = true
  errorMessage.value = ''
  successMessage.value = ''
  // 正文、子任务与接取条件属于详情接口，按需拉取（列表不返回富文本）。
  loadDetail(item.id)
}

async function loadDetail(taskId) {
  try {
    const detail = await getBountyClaims(taskId)
    form.contentHtml = detail.task.contentHtml
    // 正文按需拉取：不在这里塞 contentHtml，编辑器展开时才取，保存前再由 resolveSubtaskContents 补齐。
    form.subtasks = detail.task.subtasks.map((item) => ({
      id: item.id,
      title: item.title,
      hasContent: item.hasContent,
    }))
    const rules = detail.task.rules || []
    form.roles = rules.filter((rule) => rule.dimension === 'ROLE').map((rule) => rule.value)
    form.statuses = rules.filter((rule) => rule.dimension === 'MEMBER_STATUS').map((rule) => rule.value)
    form.gradesText = rules
      .filter((rule) => rule.dimension === 'GRADE')
      .map((rule) => rule.value)
      .join('、')
    form.tagsText = rules
      .filter((rule) => rule.dimension === 'SKILL_TAG')
      .map((rule) => rule.value)
      .join('、')
  } catch (error) {
    errorMessage.value = error.message
  }
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
    title: form.title,
    contentHtml: form.contentHtml,
    prizeDescription: form.prizeSlots ? form.prizeDescription : null,
    prizeSlots: form.prizeSlots || null,
    points: Number(form.points) || 0,
    headcountLimit: form.unlimitedHeadcount ? null : Number(form.headcountLimit) || null,
    startDate: form.startDate || null,
    endDate: form.endDate || null,
    subtasks: items.map((item) => ({
      id: item.id || null,
      title: item.title,
      contentHtml: item.contentHtml ?? null,
    })),
    rules: buildRules(),
  }
}

async function save() {
  errorMessage.value = ''
  successMessage.value = ''
  const errors = validateForm()
  if (Object.keys(errors).length) {
    fieldErrors.value = errors
    errorMessage.value = summaryOf(errors)
    focusFirstError()
    return
  }
  fieldErrors.value = {}
  working.value = true
  try {
    const body = await buildPayload()
    if (editingId.value) {
      await updateBounty(editingId.value, body)
      successMessage.value = '已保存悬赏。'
    } else {
      await createBounty(body)
      successMessage.value = '已创建悬赏草稿。'
    }
    showForm.value = false
    await load()
  } catch (error) {
    // 后端把字段级原因放在 fields 里：逐字段就近显示，没有明细时才退回 message。
    const fields = error.fields || {}
    if (Object.keys(fields).length) {
      fieldErrors.value = fields
      errorMessage.value = summaryOf(fields)
      focusFirstError()
    } else {
      errorMessage.value = error.message
    }
  } finally {
    working.value = false
  }
}

async function runAction(action, item, message) {
  if (message && !window.confirm(message)) return
  working.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await action(item.id)
    await load()
    successMessage.value = '操作已完成。'
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
</script>

<template>
  <PortalShell eyebrow="ADMIN / BOUNTY" title="悬赏管理" description="创建悬赏，管理接取、完成与奖金发放。">
    <section class="task-toolbar" aria-label="悬赏操作">
      <p class="task-toolbar-note">奖金线下发放；系统记录发放与领取状态。</p>
      <div class="task-toolbar-actions">
        <button class="portal-primary" type="button" @click="openCreate">
          <Plus :size="17" aria-hidden="true" />创建悬赏
        </button>
      </div>
    </section>

    <p v-if="errorMessage" class="portal-state inline error" role="alert">{{ errorMessage }}</p>
    <p v-if="successMessage" class="portal-state success" role="status">{{ successMessage }}</p>

    <Transition name="task-reveal">
      <section v-if="showForm" class="admin-form-card" aria-labelledby="bounty-form-title">
        <header>
          <Gift :size="22" aria-hidden="true" />
          <div>
            <p>{{ editingId ? 'EDIT BOUNTY' : 'NEW BOUNTY' }}</p>
            <h3 id="bounty-form-title">{{ editingId ? '编辑悬赏' : '创建悬赏' }}</h3>
          </div>
        </header>
        <p v-if="published" class="task-locked-note" role="status">
          已发布：资格、奖励与积分已锁定；人数上限和奖金份数只可增加，截止日只可延长。延长已结算悬赏会重新待结算。
        </p>

        <div class="admin-form-grid">
          <label class="full"
            >悬赏标题<input
              id="bounty-field-title"
              :ref="setFieldRef('title')"
              v-model.trim="form.title"
              required
              maxlength="160"
              :aria-invalid="Boolean(fieldErrors.title)"
              :aria-describedby="fieldErrors.title ? 'bounty-field-title-error' : undefined"
              @input="clearFieldError('title')"
            /><small v-if="fieldErrors.title" id="bounty-field-title-error" class="field-error">{{
              fieldErrors.title
            }}</small></label
          >
          <div class="full task-editor-field">
            <span class="task-editor-label">悬赏说明</span>
            <DiscussionRichTextEditor v-model="form.contentHtml" label="悬赏说明" :max-length="20000" />
            <small v-if="fieldErrors.contentHtml" id="bounty-field-contentHtml-error" class="field-error">{{
              fieldErrors.contentHtml
            }}</small>
          </div>
          <label
            >开始日期<input
              id="bounty-field-startDate"
              :ref="setFieldRef('startDate')"
              v-model="form.startDate"
              type="date"
              @input="clearFieldError('startDate')"
          /></label>
          <label
            >截止日期<input
              id="bounty-field-endDate"
              :ref="setFieldRef('endDate')"
              v-model="form.endDate"
              type="date"
              :aria-invalid="Boolean(fieldErrors.endDate)"
              :aria-describedby="fieldErrors.endDate ? 'bounty-field-endDate-error' : undefined"
              @input="clearFieldError('endDate')"
            /><small v-if="fieldErrors.endDate" id="bounty-field-endDate-error" class="field-error">{{
              fieldErrors.endDate
            }}</small></label
          >

          <label
            >接取名额
            <span class="bounty-inline-choice">
              <label class="bounty-inline-radio"
                ><input
                  v-model="form.unlimitedHeadcount"
                  type="radio"
                  :value="true"
                  @change="onHeadcountModeChange"
                />不限人数</label
              >
              <label class="bounty-inline-radio"
                ><input
                  v-model="form.unlimitedHeadcount"
                  type="radio"
                  :value="false"
                  @change="onHeadcountModeChange"
                />限制人数</label
              >
              <input
                v-if="!form.unlimitedHeadcount"
                id="bounty-field-headcountLimit"
                :ref="setFieldRef('headcountLimit')"
                v-model.number="form.headcountLimit"
                type="number"
                min="1"
                max="1000"
                :disabled="published"
                aria-label="接取人数上限"
                :aria-invalid="Boolean(fieldErrors.headcountLimit)"
                :aria-describedby="fieldErrors.headcountLimit ? 'bounty-field-headcountLimit-error' : undefined"
                @input="clearFieldError('headcountLimit')"
              />
            </span>
            <small v-if="fieldErrors.headcountLimit" id="bounty-field-headcountLimit-error" class="field-error">{{
              fieldErrors.headcountLimit
            }}</small>
            <small v-if="!fieldErrors.headcountLimit">只控制接取人数，不等同奖金份数。</small></label
          >

          <label
            >奖金份数
            <input
              id="bounty-field-prizeSlots"
              :ref="setFieldRef('prizeSlots')"
              v-model.number="form.prizeSlots"
              type="number"
              min="1"
              max="100"
              :disabled="published"
              :aria-invalid="Boolean(fieldErrors.prizeSlots)"
              :aria-describedby="fieldErrors.prizeSlots ? 'bounty-field-prizeSlots-error' : undefined"
              @input="clearFieldError('prizeSlots')"
            />
            <small v-if="fieldErrors.prizeSlots" id="bounty-field-prizeSlots-error" class="field-error">{{
              fieldErrors.prizeSlots
            }}</small>
            <small v-else>前 N 名完成者获奖；留空表示不设奖金。</small></label
          >
          <label class="full"
            >奖金说明
            <input
              id="bounty-field-prizeDescription"
              :ref="setFieldRef('prizeDescription')"
              v-model.trim="form.prizeDescription"
              maxlength="500"
              :disabled="published && !form.prizeSlots"
              placeholder="例如：500 元现金 + 获奖证书"
              :aria-invalid="Boolean(fieldErrors.prizeDescription)"
              :aria-describedby="fieldErrors.prizeDescription ? 'bounty-field-prizeDescription-error' : undefined"
              @input="clearFieldError('prizeDescription')"
            />
            <small v-if="fieldErrors.prizeDescription" id="bounty-field-prizeDescription-error" class="field-error">{{
              fieldErrors.prizeDescription
            }}</small>
            <small v-else>统一说明，所有获奖者相同。</small></label
          >

          <label
            >完成积分
            <input
              id="bounty-field-points"
              :ref="setFieldRef('points')"
              v-model.number="form.points"
              type="number"
              min="0"
              max="100000"
              :disabled="published"
              :aria-invalid="Boolean(fieldErrors.points)"
              :aria-describedby="fieldErrors.points ? 'bounty-field-points-error' : undefined"
              @input="clearFieldError('points')"
            />
            <small v-if="fieldErrors.points" id="bounty-field-points-error" class="field-error">{{
              fieldErrors.points
            }}</small>
            <small v-else>0 表示不计分；悬赏到期后统一结算。</small></label
          >

          <fieldset class="task-rule-fieldset full" :disabled="published">
            <legend>接取资格（同类条件满足一项、不同类都须满足；留空不限）</legend>
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
              <label>年级<input v-model.trim="form.gradesText" placeholder="用顿号分隔" /></label>
              <label>能力标签<input v-model.trim="form.tagsText" placeholder="用顿号分隔" /></label>
            </div>
          </fieldset>

          <TaskSubtaskEditor
            v-model="form.subtasks"
            class="full"
            label="子任务"
            hint="可选；未设置时直接提交完成说明。"
            :load-content="loadSubtaskContent"
          />
        </div>

        <div class="task-form-actions">
          <button class="portal-primary" type="button" :disabled="working" @click="save">
            {{ working ? '保存中…' : '保存悬赏' }}
          </button>
          <button class="portal-secondary" type="button" @click="showForm = false">取消</button>
        </div>
      </section>
    </Transition>

    <div v-if="loading" class="portal-state">正在读取悬赏…</div>
    <div v-else-if="!bounties.length" class="portal-state project-empty">
      <Gift :size="28" aria-hidden="true" /><strong>还没有悬赏</strong><span>创建悬赏并发布后，成员即可接取。</span>
    </div>

    <section v-else class="bounty-admin-tools" aria-label="搜索和筛选悬赏">
      <label class="bounty-search">
        搜索悬赏
        <input v-model.trim="searchQuery" type="search" placeholder="输入标题" />
      </label>
      <nav class="bounty-filters" aria-label="悬赏状态">
        <button
          v-for="item in bountyFilters"
          :key="item.id"
          type="button"
          :aria-pressed="listFilter === item.id"
          @click="listFilter = item.id"
        >
          {{ item.label }} <span>{{ bountyCounts[item.id] }}</span>
        </button>
      </nav>
    </section>
    <p v-if="bounties.length && !filteredBounties.length" class="empty-note">没有匹配的悬赏，试试其他筛选或搜索词。</p>

    <TransitionGroup
      v-if="filteredBounties.length"
      tag="section"
      name="task-list"
      class="task-card-grid"
      aria-label="悬赏列表"
    >
      <article v-for="item in filteredBounties" :key="item.id" class="task-card bounty-card">
        <header>
          <span :data-status="item.status">{{ statusLabels[item.status] }}</span>
          <b>{{ item.points > 0 ? `+${item.points} 积分` : '不计积分' }}</b>
        </header>
        <h2>{{ item.title }}</h2>
        <p class="task-card-dates">
          {{ item.startDate || '未设置开始' }} — {{ item.endDate || '未设置截止' }}
          <span v-if="item.expired" class="overdue">已截止</span>
        </p>
        <dl class="bounty-metrics">
          <div>
            <dt>接取</dt>
            <dd>
              {{
                item.headcountLimit == null
                  ? `已接 ${item.claimed} 人 · 不限`
                  : `已接 ${item.claimed} / ${item.headcountLimit}`
              }}
            </dd>
          </div>
          <div v-if="item.prizeSlots">
            <dt>奖金</dt>
            <dd>{{ item.prizeIssued }} / {{ item.prizeSlots }} 份已产生</dd>
          </div>
          <div>
            <dt>已完成</dt>
            <dd>{{ item.approved }} 人</dd>
          </div>
        </dl>
        <p v-if="item.points > 0" class="task-card-settle">
          {{ item.pointsSettledAt ? `积分已结算（${item.pointsSettledAt.slice(0, 10)}）` : '积分待结算' }}
        </p>
        <p v-if="item.expired" class="task-skip">已截止：不可接取或驳回；仍可移除接取、延长截止日。</p>

        <div class="task-card-actions">
          <RouterLink class="portal-secondary" :to="`/admin/bounties/${item.id}/claims`">接取名单</RouterLink>
          <button type="button" :disabled="working" @click="openEdit(item)">
            <Pencil :size="15" aria-hidden="true" />编辑
          </button>
          <button
            v-if="item.status === 'DRAFT'"
            type="button"
            :disabled="working"
            @click="runAction(publishBounty, item, `确认发布「${item.title}」？发布后接取条件与奖励口径将锁定。`)"
          >
            <Send :size="15" aria-hidden="true" />发布
          </button>
          <button
            v-if="item.status === 'PUBLISHED'"
            type="button"
            :disabled="working"
            @click="
              runAction(
                closeBounty,
                item,
                `确认结束「${item.title}」？结束后到期即结算，不能再接取或驳回，且不可撤销。`,
              )
            "
          >
            <Square :size="15" aria-hidden="true" />结束
          </button>
          <button
            v-if="item.status === 'DRAFT'"
            type="button"
            class="danger"
            :disabled="working"
            @click="runAction(deleteBounty, item, `确认删除草稿「${item.title}」？`)"
          >
            <Trash2 :size="15" aria-hidden="true" />删除
          </button>
        </div>
      </article>
    </TransitionGroup>
  </PortalShell>
</template>

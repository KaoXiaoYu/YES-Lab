<script setup>
import TaskSubmissionProgress from '../components/TaskSubmissionProgress.vue'
import { ArrowLeft, CalendarClock, CheckCheck, ListChecks, RefreshCw, Save, X } from '@lucide/vue'
import { computed, onMounted, reactive, ref } from 'vue'
import PortalShell from '../components/PortalShell.vue'
import DiscussionRichTextEditor from '../components/DiscussionRichTextEditor.vue'
import TaskSubtaskEditor from '../components/TaskSubtaskEditor.vue'
import SubtaskSubmissionsPanel from '../components/SubtaskSubmissionsPanel.vue'
import {
  backfillOnboardingTasks,
  extendOnboardingDueDate,
  getAdminSubtask,
  getOnboardingOverview,
  getOnboardingTask,
  reviewTaskAssignment,
  saveOnboardingTask,
} from '../services/authApi'

const overview = ref(null)
const loading = ref(true)
const working = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const activeAssignmentId = ref('')
const submissionsAssignmentId = ref('')
const extendAssignmentId = ref('')
const statusFilter = ref('SUBMITTED')
const applicantQuery = ref('')
const extendForm = reactive({ dueDate: '', reason: '' })

function toggleSubmissions(assignmentId) {
  submissionsAssignmentId.value = submissionsAssignmentId.value === assignmentId ? '' : assignmentId
}

const task = reactive({
  taskId: null,
  title: '',
  contentHtml: '<p></p>',
  durationDays: 7,
  subtasks: [],
})
const review = reactive({ decision: 'APPROVED', comment: '', exemptionReason: '' })

const statusLabels = {
  PENDING: '待完成',
  SUBMITTED: '待审核',
  APPROVED: '已转正',
  REJECTED: '待补交',
}
const statusFilters = [
  { id: 'SUBMITTED', label: '待审核' },
  { id: 'ACTIVE', label: '进行中 / 待补交' },
  { id: 'APPROVED', label: '已转正' },
  { id: 'ALL', label: '全部' },
]
const rows = computed(() => overview.value?.rows || [])
const reviewableCount = computed(() => rows.value.filter((row) => row.status === 'SUBMITTED').length)
const statusCounts = computed(() => ({
  ALL: rows.value.length,
  SUBMITTED: reviewableCount.value,
  ACTIVE: rows.value.filter((row) => row.status === 'PENDING' || row.status === 'REJECTED').length,
  APPROVED: rows.value.filter((row) => row.status === 'APPROVED').length,
}))
const filteredRows = computed(() => {
  const query = applicantQuery.value.trim().toLocaleLowerCase()
  return rows.value
    .filter((row) => {
      if (statusFilter.value === 'ACTIVE' && !['PENDING', 'REJECTED'].includes(row.status)) return false
      if (statusFilter.value !== 'ALL' && statusFilter.value !== 'ACTIVE' && row.status !== statusFilter.value)
        return false
      return !query || `${row.applicantName} ${row.applicantUsername}`.toLocaleLowerCase().includes(query)
    })
    .sort((first, second) => {
      const priority = (row) => {
        if (row.status === 'SUBMITTED') return 0
        if (row.status === 'PENDING' && row.totalSubtasks > 0 && row.submittedSubtasks >= row.totalSubtasks) return 1
        return { REJECTED: 2, PENDING: 3, APPROVED: 4 }[row.status] ?? 5
      }
      const ratio = (row) => (row.totalSubtasks > 0 ? row.submittedSubtasks / row.totalSubtasks : 0)
      return priority(first) - priority(second) || ratio(second) - ratio(first)
    })
})
const sharedTask = computed(() => overview.value?.task || task)
const validSubtasks = computed(() => task.subtasks.filter((item) => item.title.trim()))

function loadSubtaskContent(subtaskId) {
  return getAdminSubtask(task.taskId, subtaskId)
}

async function load() {
  loading.value = true
  try {
    const [current, data] = await Promise.all([getOnboardingTask(), getOnboardingOverview()])
    task.taskId = current.taskId
    task.title = current.title
    task.contentHtml = current.contentHtml
    task.durationDays = current.durationDays
    task.subtasks = current.subtasks.map((item) => ({
      id: item.id,
      title: item.title,
      hasContent: item.hasContent,
    }))
    overview.value = data
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

onMounted(load)

/**
 * 保存前把「已存在、有说明但这次没展开」的子任务正文补齐。
 * 不补就会把已有说明当成空写掉；补齐失败时抛错并中止保存，绝不静默写空。
 */
async function resolveSubtaskContents() {
  const resolved = []
  for (const item of validSubtasks.value) {
    if (item.id && item.hasContent && item.contentHtml === undefined) {
      const detail = await loadSubtaskContent(item.id)
      resolved.push({ ...item, contentHtml: detail?.contentHtml ?? null })
    } else {
      resolved.push(item)
    }
  }
  return resolved
}

async function save() {
  working.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const items = await resolveSubtaskContents()
    const result = await saveOnboardingTask({
      title: task.title.trim(),
      contentHtml: task.contentHtml,
      durationDays: Number(task.durationDays) || 7,
      subtasks: items.map((item) => ({
        id: item.id ?? null,
        title: item.title.trim(),
        contentHtml: item.contentHtml ?? null,
      })),
    })
    const notes = []
    if (result.reopenedCount > 0) notes.push(`${result.reopenedCount} 位已提交的报名者被退回「待完成」`)
    if (result.rescheduledCount > 0) notes.push(`${result.rescheduledCount} 位的截止日期已按新时长重算`)
    successMessage.value = notes.length
      ? `新手任务已保存并即时生效：${notes.join('，')}。`
      : '新手任务已保存，对所有处于技能测试阶段的报名者即时生效。'
    await load()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    working.value = false
  }
}

async function backfill() {
  working.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const result = await backfillOnboardingTasks()
    successMessage.value = `已为 ${result.issued} 位技能测试阶段报名者发放新手任务，跳过 ${result.skipped} 位已有任务的记录。`
    await load()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    working.value = false
  }
}

function openReview(row) {
  activeAssignmentId.value = row.assignmentId
  review.decision = 'APPROVED'
  review.comment = ''
  review.exemptionReason = ''
}

/** 默认建议日期：今天 +14 天；后端要求必须晚于今天且晚于原截止日期。 */
function openExtend(row) {
  extendAssignmentId.value = row.assignmentId
  const base = new Date()
  base.setDate(base.getDate() + 14)
  extendForm.dueDate = base.toISOString().slice(0, 10)
  extendForm.reason = ''
}

async function submitExtend(row) {
  if (!extendForm.dueDate) return
  working.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const result = await extendOnboardingDueDate(row.assignmentId, {
      dueDate: extendForm.dueDate,
      reason: extendForm.reason || null,
    })
    extendAssignmentId.value = ''
    successMessage.value = `已将 ${row.applicantName} 的截止日期延长至 ${result.dueDate}。`
    await load()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    working.value = false
  }
}

async function submitReview(row) {
  working.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const payload = { decision: review.decision, comment: review.comment || null }
    if (review.decision === 'APPROVED') {
      payload.exemptionReason = review.exemptionReason || null
    }
    await reviewTaskAssignment(row.taskId, row.assignmentId, payload)
    activeAssignmentId.value = ''
    successMessage.value =
      review.decision === 'APPROVED'
        ? `${row.applicantName} 已转为正式成员。`
        : '已驳回，报名者从打回时起有 24 小时修改并重新提交。'
    await load()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    working.value = false
  }
}
</script>

<template>
  <PortalShell eyebrow="ADMIN / ONBOARDING TASK" title="新手任务" description="维护共享子任务，并审核技能测试结果。">
    <RouterLink class="task-back" to="/admin/tasks"><ArrowLeft :size="16" aria-hidden="true" />返回任务管理</RouterLink>

    <p v-if="errorMessage" class="portal-state error" role="alert">{{ errorMessage }}</p>
    <p v-if="successMessage" class="portal-state success" role="status">{{ successMessage }}</p>

    <div v-if="loading" class="portal-state">正在读取新手任务…</div>

    <template v-else>
      <details class="admin-form-card onboarding-settings">
        <summary>
          <span><ListChecks :size="22" aria-hidden="true" /><strong>新手任务设置</strong></span>
          <small>编辑内容、调整时长或补发</small>
        </summary>
        <div class="onboarding-settings-content" aria-labelledby="onboarding-task-title">
          <header>
            <ListChecks :size="22" aria-hidden="true" />
            <h3 id="onboarding-task-title">新手任务</h3>
          </header>
          <p>技能测试阶段共用此任务；报名者提交全部子任务后，可由管理员审核转正。</p>
          <p class="task-locked-note" role="status">
            保存后立即生效。新增子任务会退回已提交记录；删除子任务会清除对应提交；修改时长会重算截止日期。
          </p>

          <div class="admin-form-grid">
            <label class="full">任务标题<input v-model.trim="task.title" maxlength="160" required /></label>
            <div class="full task-editor-field">
              <span class="task-editor-label">新手任务说明</span>
              <DiscussionRichTextEditor v-model="task.contentHtml" label="新手任务说明" :max-length="20000" />
            </div>
            <label
              >时长（天）
              <input v-model.number="task.durationDays" type="number" min="1" max="365" required />
              <small>截止日按各自发放日计算。</small></label
            >
          </div>

          <TaskSubtaskEditor
            v-model="task.subtasks"
            label="子任务（从属于这个大任务）"
            hint="至少一项。成员需提交全部子任务；展开可编辑说明。改动即时生效，改名保留进度。"
            :load-content="loadSubtaskContent"
          />

          <div class="task-form-actions">
            <button
              class="portal-primary"
              type="button"
              :disabled="working || !task.title || !validSubtasks.length"
              @click="save"
            >
              <Save :size="16" aria-hidden="true" />保存新手任务
            </button>
            <button class="portal-secondary" type="button" :disabled="working" @click="backfill">
              <RefreshCw :size="16" aria-hidden="true" />批量补发新手任务
            </button>
          </div>
          <p v-if="overview && overview.missingTaskCount > 0" class="task-skip" role="status">
            当前有
            {{ overview.missingTaskCount }} 位技能测试阶段报名者还没有新手任务，点击「批量补发新手任务」即可发放。
          </p>
        </div>
      </details>

      <section class="task-rows" aria-labelledby="onboarding-rows-title">
        <header class="onboarding-review-heading">
          <div>
            <h3 id="onboarding-rows-title">新手任务审核</h3>
            <p>{{ reviewableCount }} 位待审核 · 截止后仍可审核</p>
          </div>
          <label class="onboarding-search">
            搜索报名者
            <input v-model.trim="applicantQuery" type="search" placeholder="姓名或邮箱" />
          </label>
        </header>
        <nav class="onboarding-status-filters" aria-label="按新手任务状态筛选">
          <button
            v-for="filter in statusFilters"
            :key="filter.id"
            type="button"
            :aria-pressed="statusFilter === filter.id"
            @click="statusFilter = filter.id"
          >
            {{ filter.label }} <span>{{ statusCounts[filter.id] }}</span>
          </button>
        </nav>
        <p v-if="!rows.length" class="empty-note">当前没有处于技能测试阶段的报名者。</p>
        <p v-else-if="!filteredRows.length" class="empty-note">没有符合此状态和搜索条件的报名者。</p>
        <TransitionGroup v-else tag="div" name="task-list" class="onboarding-review-list">
          <article v-for="row in filteredRows" :key="row.applicationId" class="task-row">
            <header>
              <div>
                <strong>{{ row.applicantName }}</strong>
                <span>{{ row.applicantUsername }} · {{ row.stage === 'PROBATION' ? '旧试用期记录' : '技能测试' }}</span>
              </div>
              <b :data-status="row.status">{{ row.status ? statusLabels[row.status] : '未发放' }}</b>
            </header>
            <TaskSubmissionProgress
              :submitted="row.submittedSubtasks"
              :total="row.totalSubtasks"
              :task-submitted="['SUBMITTED', 'APPROVED'].includes(row.status)"
              :accessible-label="`${row.applicantName}的提交进度`"
            >
              <span v-if="row.startDate || row.endDate"
                >{{ row.startDate || '未设置' }} — {{ row.endDate || '未设置' }}</span
              >
              <span v-if="row.overdue" class="overdue"> · 已过提交截止，仍可审核</span>
            </TaskSubmissionProgress>
            <p v-if="row.resubmissionDeadlineAt && row.status === 'REJECTED'" class="task-row-note" role="status">
              报名者本次补交截止：{{ new Date(row.resubmissionDeadlineAt).toLocaleString('zh-CN') }}
            </p>
            <dl class="qualification-readonly">
              <div>
                <dt>学号 / 内部编号</dt>
                <dd>{{ row.memberCode || '报名者尚未填写' }}</dd>
              </div>
              <div>
                <dt>能力标签</dt>
                <dd>{{ row.skillTags?.join('、') || '报名者尚未填写' }}</dd>
              </div>
            </dl>
            <p v-if="row.dueDateExtendedAt" class="task-row-note">
              最近一次延长：{{ row.dueDateExtendedBy }} 于 {{ row.dueDateExtendedAt.slice(0, 10) }} 延长至
              {{ row.endDate
              }}<template v-if="row.dueDateExtensionReason"> · 理由：{{ row.dueDateExtensionReason }}</template>
            </p>
            <p v-if="row.completionNote" class="task-row-note">完成说明：{{ row.completionNote }}</p>
            <p v-if="row.exemptionReason" class="task-skip">已豁免：{{ row.exemptionReason }}</p>
            <p v-if="row.reviewComment" class="task-row-note">审核意见：{{ row.reviewComment }}</p>

            <div class="task-card-actions">
              <button
                v-if="row.assignmentId && row.status !== 'APPROVED'"
                type="button"
                :disabled="working"
                :aria-expanded="activeAssignmentId === row.assignmentId"
                :aria-controls="`onboarding-review-form-${row.assignmentId}`"
                @click="openReview(row)"
              >
                <CheckCheck :size="15" aria-hidden="true" />{{ row.status === 'SUBMITTED' ? '审核提交' : '豁免转正' }}
              </button>
              <button
                v-if="row.assignmentId && row.status !== 'APPROVED'"
                type="button"
                :aria-expanded="extendAssignmentId === row.assignmentId"
                @click="extendAssignmentId === row.assignmentId ? (extendAssignmentId = '') : openExtend(row)"
              >
                <CalendarClock :size="15" aria-hidden="true" />延长截止日期
              </button>
              <button
                v-if="row.assignmentId"
                type="button"
                :aria-expanded="submissionsAssignmentId === row.assignmentId"
                @click="toggleSubmissions(row.assignmentId)"
              >
                <ListChecks :size="15" aria-hidden="true" />查看提交内容
              </button>
              <span v-if="row.convertedProfileId">已转为正式成员</span>
            </div>

            <Transition name="task-reveal">
              <form
                v-if="extendAssignmentId === row.assignmentId"
                class="task-review-form"
                @submit.prevent="submitExtend(row)"
                aria-label="延长该报名者的截止日期"
              >
                <p class="task-skip full">
                  延长只影响这一位报名者；新的截止日期必须晚于今天、且晚于原截止日期。延长后本人即可继续提交，你也可以审核。
                </p>
                <label>新的截止日期<input v-model="extendForm.dueDate" type="date" required /></label>
                <label class="full">延长理由（可选）<input v-model.trim="extendForm.reason" maxlength="500" /></label>
                <div class="task-form-actions">
                  <button class="portal-primary" type="submit" :disabled="working || !extendForm.dueDate">
                    {{ working ? '提交中…' : '确认延长' }}
                  </button>
                  <button type="button" class="portal-secondary" @click="extendAssignmentId = ''">取消</button>
                </div>
              </form>
            </Transition>

            <Transition name="task-reveal">
              <form
                v-if="activeAssignmentId === row.assignmentId"
                :id="`onboarding-review-form-${row.assignmentId}`"
                class="task-review-form"
                @submit.prevent="submitReview(row)"
              >
                <label v-if="row.status === 'SUBMITTED'"
                  >审核结论<select v-model="review.decision">
                    <option value="APPROVED">通过并转为正式成员</option>
                    <option value="REJECTED">驳回（不转正）</option>
                  </select></label
                >
                <p v-else class="task-locked-note full" role="status">
                  此记录尚无可审核的提交。若确认免修并转正，请填写豁免理由；未提交的记录不能驳回。
                </p>
                <label class="full">审核意见<input v-model.trim="review.comment" maxlength="1000" /></label>
                <template v-if="review.decision === 'APPROVED'">
                  <p class="task-locked-note" role="status">
                    通过后转为正式成员。资料由报名者填写；未填不影响通过，首次登录时补齐。
                    {{
                      row.status === 'SUBMITTED'
                        ? `需提交全部 ${sharedTask.subtasks.length} 项；免修请填写理由。`
                        : '无有效提交，只能填写豁免理由后转正。'
                    }}
                  </p>
                  <dl class="qualification-readonly">
                    <div>
                      <dt>学号 / 内部编号</dt>
                      <dd>{{ row.memberCode || '报名者尚未填写' }}</dd>
                    </div>
                    <div>
                      <dt>能力标签</dt>
                      <dd>{{ row.skillTags?.join('、') || '报名者尚未填写' }}</dd>
                    </div>
                  </dl>
                  <label class="full"
                    >豁免理由{{ row.status === 'SUBMITTED' ? '（可选）' : '（必填）'
                    }}<input
                      v-model.trim="review.exemptionReason"
                      maxlength="500"
                      :placeholder="row.status === 'SUBMITTED' ? '免修时填写理由' : '必填：免修转正理由'"
                  /></label>
                </template>
                <p v-if="review.decision === 'REJECTED'" class="task-skip">
                  必填审核意见；报名者自驳回起 24 小时内补交。
                </p>
                <div class="task-form-actions">
                  <button
                    class="portal-primary"
                    type="submit"
                    :disabled="
                      working ||
                      (review.decision === 'REJECTED' && !review.comment) ||
                      (row.status !== 'SUBMITTED' && !review.exemptionReason)
                    "
                  >
                    {{ working ? '提交中…' : '提交审核结果' }}
                  </button>
                  <button type="button" class="portal-secondary" @click="activeAssignmentId = ''">
                    <X :size="15" aria-hidden="true" />取消
                  </button>
                </div>
              </form>
            </Transition>

            <SubtaskSubmissionsPanel
              v-if="submissionsAssignmentId === row.assignmentId"
              :task-id="row.taskId"
              :assignment-id="row.assignmentId"
            />
          </article>
        </TransitionGroup>
      </section>
    </template>
  </PortalShell>
</template>

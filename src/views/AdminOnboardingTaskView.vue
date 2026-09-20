<script setup>
import { ArrowLeft, CheckCheck, ListChecks, RefreshCw, Save, X } from '@lucide/vue'
import { computed, onMounted, reactive, ref } from 'vue'
import PortalShell from '../components/PortalShell.vue'
import DiscussionRichTextEditor from '../components/DiscussionRichTextEditor.vue'
import TaskSubtaskEditor from '../components/TaskSubtaskEditor.vue'
import SubtaskSubmissionsPanel from '../components/SubtaskSubmissionsPanel.vue'
import {
  backfillOnboardingTasks,
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
const review = reactive({ decision: 'APPROVED', comment: '', memberCode: '', skillTagsText: '', exemptionReason: '' })

const statusLabels = {
  PENDING: '待完成',
  SUBMITTED: '待确认',
  APPROVED: '已通过',
  REJECTED: '已驳回',
}
const rows = computed(() => overview.value?.rows || [])
const sharedTask = computed(() => overview.value?.task || task)
const validSubtasks = computed(() => task.subtasks.filter((item) => item.title.trim()))

function loadSubtaskContent(subtaskId) {
  return getAdminSubtask(task.taskId, subtaskId)
}

function progressPercent(row) {
  if (!row.totalSubtasks) return 0
  return Math.round((row.submittedSubtasks / row.totalSubtasks) * 100)
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
  review.memberCode = ''
  review.skillTagsText = ''
  review.exemptionReason = ''
}

async function submitReview(row) {
  working.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const payload = { decision: review.decision, comment: review.comment || null }
    if (review.decision === 'APPROVED') {
      payload.memberCode = review.memberCode.trim()
      payload.skillTags = review.skillTagsText
        .split(/[、,，]/)
        .map((item) => item.trim())
        .filter(Boolean)
      payload.exemptionReason = review.exemptionReason || null
    }
    await reviewTaskAssignment(row.taskId, row.assignmentId, payload)
    activeAssignmentId.value = ''
    successMessage.value =
      review.decision === 'APPROVED' ? `${row.applicantName} 已转为正式成员。` : '已驳回，报名者可修改后重新提交。'
    await load()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    working.value = false
  }
}
</script>

<template>
  <PortalShell
    eyebrow="ADMIN / ONBOARDING TASK"
    title="新手任务"
    description="全实验室共用一个新手任务大任务：在这里维护它的内容与子任务，改动对技能测试阶段的报名者即时生效；子任务全部完成后管理员审核通过即转为正式成员。"
  >
    <RouterLink class="task-back" to="/admin/tasks"><ArrowLeft :size="16" aria-hidden="true" />返回任务管理</RouterLink>

    <p v-if="errorMessage" class="portal-state error" role="alert">{{ errorMessage }}</p>
    <p v-if="successMessage" class="portal-state success" role="status">{{ successMessage }}</p>

    <div v-if="loading" class="portal-state">正在读取新手任务…</div>

    <template v-else>
      <section class="admin-form-card" aria-labelledby="onboarding-task-title">
        <header>
          <ListChecks :size="22" aria-hidden="true" />
          <h3 id="onboarding-task-title">新手任务</h3>
        </header>
        <p>
          这是一个大任务，所有进入技能测试阶段的报名者共享它：每人在其中看到并提交属于自己的进度，子任务
          <strong>全部完成</strong>后才能提交，管理员审核通过即转为正式成员。
        </p>
        <p class="task-locked-note" role="status">
          保存后立即生效：新增子任务会把「已提交待确认」的报名者退回「待完成」并发送站内消息；删除子任务会同时清掉对应的提交记录；修改时长会按每人各自的发放日期重算截止日期。
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
            <small>每位报名者的截止日期 = 本人被分配到这个大任务当天 + 此处时长。</small></label
          >
        </div>

        <TaskSubtaskEditor
          v-model="task.subtasks"
          label="子任务（从属于这个大任务）"
          hint="至少需要一项：成员必须提交全部子任务的内容才能提交与转正。展开箭头可为每项写富文本说明，成员点子任务进入独立页面阅读并提交内容；改动保存后即时生效，改名不影响已提交的进度。"
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
          当前有 {{ overview.missingTaskCount }} 位技能测试阶段报名者还没有新手任务，点击「批量补发新手任务」即可发放。
        </p>
      </section>

      <section class="task-rows" aria-labelledby="onboarding-rows-title">
        <h3 id="onboarding-rows-title">技能测试阶段完成情况（{{ rows.length }} 人）</h3>
        <p v-if="!rows.length" class="empty-note">当前没有处于技能测试阶段的报名者。</p>
        <article v-for="row in rows" :key="row.applicationId" class="task-row">
          <header>
            <div>
              <strong>{{ row.applicantName }}</strong>
              <span>{{ row.applicantUsername }} · {{ row.stage === 'PROBATION' ? '旧试用期记录' : '技能测试' }}</span>
            </div>
            <b :data-status="row.status">{{ row.status ? statusLabels[row.status] : '未发放' }}</b>
          </header>
          <div class="task-progress">
            <div
              class="task-progress-track"
              role="progressbar"
              aria-valuemin="0"
              :aria-valuenow="row.submittedSubtasks"
              :aria-valuemax="row.totalSubtasks"
              :aria-label="`${row.applicantName} 的子任务完成进度`"
            >
              <span :style="{ width: `${progressPercent(row)}%` }"></span>
            </div>
            <span class="task-progress-label">
              已提交 {{ row.submittedSubtasks }} / {{ row.totalSubtasks }}
              <span v-if="row.startDate || row.endDate">
                · {{ row.startDate || '未设置' }} — {{ row.endDate || '未设置' }}</span
              >
              <span v-if="row.overdue" class="overdue">已逾期</span>
            </span>
          </div>
          <p v-if="row.completionNote" class="task-row-note">完成说明：{{ row.completionNote }}</p>
          <p v-if="row.exemptionReason" class="task-skip">已豁免：{{ row.exemptionReason }}</p>
          <p v-if="row.reviewComment" class="task-row-note">审核意见：{{ row.reviewComment }}</p>

          <div class="task-card-actions">
            <button
              v-if="row.assignmentId && row.status !== 'APPROVED'"
              type="button"
              :disabled="working"
              @click="openReview(row)"
            >
              <CheckCheck :size="15" aria-hidden="true" />审核
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

          <form
            v-if="activeAssignmentId === row.assignmentId"
            class="task-review-form"
            @submit.prevent="submitReview(row)"
          >
            <label
              >审核结论<select v-model="review.decision">
                <option value="APPROVED">通过并转为正式成员</option>
                <option value="REJECTED">驳回（不转正）</option>
              </select></label
            >
            <label class="full">审核意见<input v-model.trim="review.comment" maxlength="1000" /></label>
            <template v-if="review.decision === 'APPROVED'">
              <p class="task-locked-note" role="status">
                通过后将直接转为正式成员（不再经过试用期），需要同时填写学号/内部编号与能力标签；该报名者必须已提交全部
                {{ sharedTask.subtasks.length }} 项子任务，否则会被拒绝。
              </p>
              <label>学号 / 内部编号<input v-model.trim="review.memberCode" maxlength="64" required /></label>
              <label>能力标签（至少一项，用、分隔）<input v-model.trim="review.skillTagsText" required /></label>
              <label class="full"
                >豁免理由（可选）<input
                  v-model.trim="review.exemptionReason"
                  maxlength="500"
                  placeholder="仅在确认免修时填写"
              /></label>
            </template>
            <p v-else class="task-skip">驳回必须填写审核意见。</p>
            <div class="task-form-actions">
              <button
                class="portal-primary"
                type="submit"
                :disabled="
                  working ||
                  (review.decision === 'REJECTED' && !review.comment) ||
                  (review.decision === 'APPROVED' && (!review.memberCode || !review.skillTagsText))
                "
              >
                {{ working ? '提交中…' : '提交审核结果' }}
              </button>
              <button type="button" class="portal-secondary" @click="activeAssignmentId = ''">
                <X :size="15" aria-hidden="true" />取消
              </button>
            </div>
          </form>

          <SubtaskSubmissionsPanel
            v-if="submissionsAssignmentId === row.assignmentId"
            :task-id="row.taskId"
            :assignment-id="row.assignmentId"
          />
        </article>
      </section>
    </template>
  </PortalShell>
</template>

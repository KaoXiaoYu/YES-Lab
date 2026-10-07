<script setup>
import { ArrowLeft, CheckCheck, ListChecks, X } from '@lucide/vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import TaskSubmissionProgress from '../components/TaskSubmissionProgress.vue'
import PortalShell from '../components/PortalShell.vue'
import { confirmAction } from '../services/confirm'
import { useToastFeedback } from '../composables/useToastFeedback'
import SubtaskSubmissionsPanel from '../components/SubtaskSubmissionsPanel.vue'
import TaskSupplementMembers from '../components/TaskSupplementMembers.vue'
import { getTaskProgress, removeTaskAssignment, reviewTaskAssignment } from '../services/authApi'

const route = useRoute()
const progress = ref(null)
const loading = ref(true)
const working = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const activeAssignmentId = ref('')
const submissionsAssignmentId = ref('')

function toggleSubmissions(assignmentId) {
  submissionsAssignmentId.value = submissionsAssignmentId.value === assignmentId ? '' : assignmentId
}
const review = reactive({ decision: 'APPROVED', comment: '' })
const refreshFailed = ref(false)
const refreshing = ref(false)

const statusLabels = {
  PENDING: '进行中',
  SUBMITTED: '待确认',
  APPROVED: '已通过',
  REJECTED: '已驳回',
}
const roleLabels = { TEACHER: '教师', CORE_STUDENT: '核心学生', MEMBER: '普通成员' }

const task = computed(() => progress.value?.task || null)
const rowFilter = ref('ALL')
const statusRank = { SUBMITTED: 0, PENDING: 1, REJECTED: 2, APPROVED: 3 }
const rowFilters = computed(() => {
  const rows = progress.value?.assignments || []
  return [
    { value: 'ALL', label: '全部', count: rows.length },
    ...Object.keys(statusRank).map((value) => ({
      value,
      label: statusLabels[value],
      count: rows.filter((row) => row.status === value).length,
    })),
  ].filter((item) => item.value === 'ALL' || item.count)
})
function submissionsComplete(row) {
  return row.totalSubtasks > 0
    ? row.submittedSubtasks >= row.totalSubtasks
    : ['SUBMITTED', 'APPROVED'].includes(row.status)
}
function submissionPercent(row) {
  if (!row.totalSubtasks) return submissionsComplete(row) ? 100 : 0
  return Math.min(100, Math.max(0, Math.round((row.submittedSubtasks / row.totalSubtasks) * 100)))
}
function reviewPriority(row) {
  if (row.status === 'SUBMITTED') return 0
  if (row.status === 'APPROVED') return 4
  if (row.status === 'PENDING' && submissionsComplete(row)) return 1
  if (row.status === 'REJECTED') return 2
  return 3
}
const visibleRows = computed(() =>
  [...(progress.value?.assignments || [])]
    .filter((row) => rowFilter.value === 'ALL' || row.status === rowFilter.value)
    .sort((a, b) => reviewPriority(a) - reviewPriority(b) || submissionPercent(b) - submissionPercent(a)),
)
useToastFeedback({ success: successMessage, error: errorMessage, keepErrorInline: () => !task.value })

let hasLoaded = false
async function load() {
  refreshing.value = true
  if (!progress.value)
    // Keep current content on screen while refreshing after an action.
    if (!hasLoaded) loading.value = true
  errorMessage.value = ''
  refreshFailed.value = false
  try {
    progress.value = await getTaskProgress(route.params.taskId)
  } catch (error) {
    errorMessage.value = error.message
    refreshFailed.value = true
  } finally {
    hasLoaded = true
    loading.value = false
    refreshing.value = false
  }
}

onMounted(load)

function openReview(assignmentId) {
  activeAssignmentId.value = assignmentId
  review.decision = 'APPROVED'
  review.comment = ''
}

async function submitReview() {
  working.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const payload = { decision: review.decision, comment: review.comment || null }
    await reviewTaskAssignment(route.params.taskId, activeAssignmentId.value, payload)
    activeAssignmentId.value = ''
    successMessage.value = review.decision === 'APPROVED' ? '已确认通过。' : '已驳回。'
    await load()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    working.value = false
  }
}

async function supplementCompleted(result) {
  working.value = true
  try {
    progress.value = { ...progress.value, task: result }
    successMessage.value = `本次新增 ${result.supplementResult.createdCount} 人，已发放跳过 ${result.supplementResult.skippedExistingCount} 人。`
    await load()
    if (refreshFailed.value) errorMessage.value = `补发成功，名单刷新失败：${errorMessage.value}`
  } finally {
    working.value = false
  }
}

async function removeAssignment(row) {
  if (
    !(await confirmAction({
      title: `移除 ${row.name}？`,
      message: '该成员将不再是这项任务的对象，其提交记录也不再计入进度。',
      confirmText: '移除对象',
      tone: 'danger',
    }))
  )
    return
  working.value = true
  try {
    await removeTaskAssignment(route.params.taskId, row.assignmentId)
    await load()
    successMessage.value = '已移除该对象。'
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    working.value = false
  }
}
</script>

<template>
  <PortalShell
    title="任务完成情况"
    description="任务汇总、子任务提交率与逐人明细；审核只记录结论，积分在任务到期后由系统统一结算。"
  >
    <RouterLink class="task-back" to="/admin/tasks"><ArrowLeft :size="16" aria-hidden="true" />返回任务管理</RouterLink>

    <div v-if="loading" class="portal-state">正在读取完成情况…</div>
    <div v-if="errorMessage" class="portal-state error" role="alert">{{ errorMessage }}</div>
    <button v-if="refreshFailed" class="portal-secondary" type="button" :disabled="working || refreshing" @click="load">
      重试刷新名单
    </button>
    <p v-if="successMessage" class="portal-state success" role="status">{{ successMessage }}</p>

    <template v-if="progress && task">
      <section class="task-summary" aria-labelledby="task-summary-title">
        <header>
          <div>
            <p>{{ task.status === 'CLOSED' ? '已结束' : '进行中' }}</p>
            <h2 id="task-summary-title">{{ task.title }}</h2>
            <span>{{ task.startDate || '未设置开始' }} — {{ task.endDate || '未设置截止' }}</span>
          </div>
          <b>{{ task.points > 0 ? `每个通过对象 ${task.points} 分` : '不计分' }}</b>
        </header>
        <dl>
          <div>
            <dt>应完成</dt>
            <dd>{{ progress.assignmentCount }}</dd>
          </div>
          <div>
            <dt>已通过</dt>
            <dd>{{ progress.approvedCount }}</dd>
          </div>
          <div>
            <dt>待确认</dt>
            <dd>{{ progress.submittedCount }}</dd>
          </div>
          <div>
            <dt>进行中</dt>
            <dd>{{ progress.pendingCount }}</dd>
          </div>
          <div>
            <dt>已驳回</dt>
            <dd>{{ progress.rejectedCount }}</dd>
          </div>
          <div>
            <dt>已发放积分</dt>
            <dd>{{ progress.awardedPointsTotal }}</dd>
          </div>
          <div>
            <dt>积分结算</dt>
            <dd>{{ task.pointsSettledAt ? `已结算 ${task.pointsSettledAt.slice(0, 10)}` : '待结算' }}</dd>
          </div>
        </dl>
        <p v-if="task.status === 'CLOSED'" class="task-skip" role="status">
          任务已由管理员结束，不能再提交、审核或驳回。
        </p>
        <p v-else-if="task.expired && progress.submittedCount > 0" class="task-skip" role="status">
          任务已截止，成员不能再提交；已提交内容仍可审核，截止后通过的对象也会按规则计入积分。
        </p>
        <p v-else-if="progress.submittedCount > 0" class="task-skip" role="status">
          还有 {{ progress.submittedCount }} 人待审核；截止后仍可继续审核，驳回后本人有 24 小时补交。
        </p>
      </section>

      <TaskSupplementMembers
        v-if="task.status === 'PUBLISHED'"
        :task-id="task.id"
        :assignments="progress.assignments"
        :busy="working || refreshFailed || refreshing"
        @completed="supplementCompleted"
      />

      <section class="task-subtask-progress" aria-labelledby="subtask-progress-title">
        <h3 id="subtask-progress-title">子任务提交率</h3>
        <ul>
          <li v-for="item in progress.subtaskProgress" :key="item.subtaskId">
            <span>{{ item.title }}</span>
            <b>{{ item.submittedCount }} / {{ item.totalCount }}</b>
          </li>
        </ul>
        <p v-if="!progress.subtaskProgress.length" class="empty-note">该任务没有子任务。</p>
      </section>

      <section class="task-rows" aria-labelledby="task-rows-title">
        <h3 id="task-rows-title">逐人明细</h3>
        <div
          v-if="progress.assignments.length"
          class="admin-chip-filters is-inline"
          role="group"
          aria-label="按状态筛选"
        >
          <button
            v-for="item in rowFilters"
            :key="item.value"
            type="button"
            :class="{ active: rowFilter === item.value }"
            :aria-pressed="rowFilter === item.value"
            @click="rowFilter = item.value"
          >
            {{ item.label }}<span>{{ item.count }}</span>
          </button>
        </div>
        <p v-if="!progress.assignments.length" class="empty-note">还没有发放对象。</p>
        <TransitionGroup tag="div" name="list" class="task-row-list">
          <article v-for="row in visibleRows" :key="row.assignmentId" class="task-row">
            <header>
              <div>
                <strong>{{ row.name }}</strong>
                <span>{{ row.memberCode }} · {{ roleLabels[row.role] }} · {{ row.grade || '年级未填' }}</span>
              </div>
              <b :data-status="row.status">{{ statusLabels[row.status] }}</b>
            </header>
            <TaskSubmissionProgress
              :submitted="row.submittedSubtasks"
              :total="row.totalSubtasks"
              :task-submitted="['SUBMITTED', 'APPROVED'].includes(row.status)"
              :accessible-label="`${row.name}的提交进度`"
            />
            <p>
              <span v-if="row.overdue" class="overdue">已逾期</span>
              <span v-if="row.awardedPoints != null">· 已计 {{ row.awardedPoints }} 分</span>
              <span v-else-if="row.pointsSkippedReason" class="task-skip">· 未计分：{{ row.pointsSkippedReason }}</span>
            </p>
            <p v-if="row.completionNote" class="task-row-note">完成说明：{{ row.completionNote }}</p>
            <p v-if="row.reviewComment" class="task-row-note">
              审核意见：{{ row.reviewComment }}（{{ row.reviewedBy }}，{{ row.reviewedAt?.slice(0, 10) }}）
            </p>

            <div class="task-card-actions">
              <button
                v-if="row.status === 'SUBMITTED'"
                type="button"
                :disabled="working || task.status === 'CLOSED'"
                :title="task.status === 'CLOSED' ? '任务已由管理员结束，不能再审核或驳回' : ''"
                @click="openReview(row.assignmentId)"
              >
                <CheckCheck :size="15" aria-hidden="true" />人工审核
              </button>
              <button
                type="button"
                :aria-expanded="submissionsAssignmentId === row.assignmentId"
                @click="toggleSubmissions(row.assignmentId)"
              >
                <ListChecks :size="15" aria-hidden="true" />查看提交内容
              </button>
              <button
                v-if="task.status === 'PUBLISHED' && row.status === 'PENDING'"
                type="button"
                class="danger"
                :disabled="working"
                @click="removeAssignment(row)"
              >
                <X :size="15" aria-hidden="true" />移除对象
              </button>
            </div>

            <Transition name="reveal">
              <form
                v-if="activeAssignmentId === row.assignmentId"
                class="task-review-form"
                @submit.prevent="submitReview"
              >
                <label
                  >审核结论<select v-model="review.decision">
                    <option value="APPROVED">通过并发放积分</option>
                    <option value="REJECTED">驳回（不发分）</option>
                  </select></label
                >
                <label class="full">审核意见<input v-model.trim="review.comment" maxlength="1000" /></label>
                <p v-if="review.decision === 'APPROVED'" class="task-skip full">
                  通过只记录结论；积分在任务到期后统一结算，凭证使用任务详情站内路径。
                </p>
                <p v-if="review.decision === 'REJECTED'" class="task-skip">驳回必须填写审核意见，且不会发放积分。</p>
                <div class="task-form-actions">
                  <button
                    class="portal-primary"
                    type="submit"
                    :disabled="working || (review.decision === 'REJECTED' && !review.comment)"
                  >
                    {{ working ? '提交中…' : '提交审核结果' }}
                  </button>
                  <button type="button" class="portal-secondary" @click="activeAssignmentId = ''">取消</button>
                </div>
              </form>
            </Transition>

            <Transition name="reveal">
              <SubtaskSubmissionsPanel
                v-if="submissionsAssignmentId === row.assignmentId"
                :task-id="task.id"
                :assignment-id="row.assignmentId"
              />
            </Transition>
          </article>
        </TransitionGroup>
        <p v-if="progress.assignments.length && !visibleRows.length" class="empty-note">该状态下没有成员。</p>
      </section>
    </template>
  </PortalShell>
</template>

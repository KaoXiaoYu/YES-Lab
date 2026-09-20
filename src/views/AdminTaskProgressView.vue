<script setup>
import { ArrowLeft, CheckCheck, ListChecks, UserPlus, X } from '@lucide/vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import PortalShell from '../components/PortalShell.vue'
import SubtaskSubmissionsPanel from '../components/SubtaskSubmissionsPanel.vue'
import {
  getTaskProgress,
  removeTaskAssignment,
  reviewTaskAssignment,
  supplementTaskAssignments,
} from '../services/authApi'

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
const review = reactive({ decision: 'APPROVED', comment: '', evidenceUrl: '', memberCode: '', skillTagsText: '' })
const supplementTags = ref('')

const statusLabels = {
  PENDING: '进行中',
  SUBMITTED: '待确认',
  APPROVED: '已通过',
  REJECTED: '已驳回',
}
const roleLabels = { TEACHER: '教师', CORE_STUDENT: '核心学生', MEMBER: '普通成员' }

const task = computed(() => progress.value?.task || null)

async function load() {
  loading.value = true
  try {
    progress.value = await getTaskProgress(route.params.taskId)
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openReview(assignmentId) {
  activeAssignmentId.value = assignmentId
  review.decision = 'APPROVED'
  review.comment = ''
  review.evidenceUrl = ''
  review.memberCode = ''
  review.skillTagsText = ''
}

async function submitReview() {
  working.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const payload = { decision: review.decision, comment: review.comment || null }
    if (review.decision === 'APPROVED') payload.evidenceUrl = review.evidenceUrl || null
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

async function supplement() {
  const tags = supplementTags.value
    .split(/[、,，]/)
    .map((item) => item.trim())
    .filter(Boolean)
  if (!tags.length) return
  working.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const result = await supplementTaskAssignments(route.params.taskId, {
      rules: tags.map((value) => ({ dimension: 'SKILL_TAG', value })),
      memberProfileIds: [],
    })
    supplementTags.value = ''
    progress.value = { ...progress.value, task: result }
    successMessage.value = '已按条件补充发放。'
    await load()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    working.value = false
  }
}

async function removeAssignment(row) {
  if (!window.confirm(`确认移除 ${row.name} 的任务对象吗？`)) return
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
    eyebrow="ADMIN / TASK PROGRESS"
    title="任务完成情况"
    description="任务汇总、子任务提交率与逐人明细；审核通过后自动发放积分，驳回不发分。"
  >
    <RouterLink class="task-back" to="/admin/tasks"><ArrowLeft :size="16" aria-hidden="true" />返回任务管理</RouterLink>

    <div v-if="loading" class="portal-state">正在读取完成情况…</div>
    <div v-else-if="errorMessage" class="portal-state error" role="alert">{{ errorMessage }}</div>
    <p v-if="successMessage" class="portal-state success" role="status">{{ successMessage }}</p>

    <template v-else-if="progress && task">
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
        </dl>
      </section>

      <section v-if="task.status === 'PUBLISHED'" class="admin-form-card" aria-labelledby="supplement-title">
        <header>
          <UserPlus :size="22" aria-hidden="true" />
          <div>
            <p>SUPPLEMENT</p>
            <h3 id="supplement-title">补充发放</h3>
          </div>
        </header>
        <p>按能力标签补充命中的成员，已存在的对象不会重复创建。</p>
        <div class="admin-form-grid">
          <label class="full"
            >能力标签（用、分隔）<input v-model.trim="supplementTags" placeholder="无人机、视觉"
          /></label>
        </div>
        <button class="portal-primary" type="button" :disabled="working || !supplementTags" @click="supplement">
          <UserPlus :size="16" aria-hidden="true" />补充发放
        </button>
      </section>

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
        <p v-if="!progress.assignments.length" class="empty-note">还没有发放对象。</p>
        <article v-for="row in progress.assignments" :key="row.assignmentId" class="task-row">
          <header>
            <div>
              <strong>{{ row.name }}</strong>
              <span>{{ row.memberCode }} · {{ roleLabels[row.role] }} · {{ row.grade || '年级未填' }}</span>
            </div>
            <b :data-status="row.status">{{ statusLabels[row.status] }}</b>
          </header>
          <p>
            已提交 {{ row.submittedSubtasks }} / {{ row.totalSubtasks }}
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
              :disabled="working"
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

          <form v-if="activeAssignmentId === row.assignmentId" class="task-review-form" @submit.prevent="submitReview">
            <label
              >审核结论<select v-model="review.decision">
                <option value="APPROVED">通过并发放积分</option>
                <option value="REJECTED">驳回（不发分）</option>
              </select></label
            >
            <label class="full">审核意见<input v-model.trim="review.comment" maxlength="1000" /></label>
            <label v-if="review.decision === 'APPROVED'" class="full"
              >积分凭证链接（可选）<input
                v-model.trim="review.evidenceUrl"
                maxlength="1000"
                placeholder="留空时使用任务详情站内路径"
            /></label>
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

          <SubtaskSubmissionsPanel
            v-if="submissionsAssignmentId === row.assignmentId"
            :task-id="task.id"
            :assignment-id="row.assignmentId"
          />
        </article>
      </section>
    </template>
  </PortalShell>
</template>

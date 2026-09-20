<script setup>
import { ArrowLeft, CheckCircle2, ChevronRight, CircleDashed, Clock3, TriangleAlert } from '@lucide/vue'
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import PortalShell from '../components/PortalShell.vue'
import { getMyTask, submitMyTask } from '../services/authApi'

const route = useRoute()
const task = ref(null)
const loading = ref(true)
const working = ref(false)
const errorMessage = ref('')
const actionError = ref('')
const note = ref('')

const statusLabels = {
  PENDING: '待完成',
  SUBMITTED: '等待管理员确认',
  APPROVED: '已通过',
  REJECTED: '已驳回，请修改后重新提交',
}
const taskStatusLabels = { PUBLISHED: '进行中', CLOSED: '已结束' }
const readOnly = computed(
  () => !task.value || task.value.taskStatus !== 'PUBLISHED' || task.value.status === 'APPROVED',
)
const submittedCount = computed(() => task.value?.subtasks?.filter((item) => item.submitted).length ?? 0)
const totalCount = computed(() => task.value?.subtasks?.length ?? 0)
const progressPercent = computed(() =>
  totalCount.value ? Math.round((submittedCount.value / totalCount.value) * 100) : 0,
)

async function load() {
  try {
    task.value = await getMyTask(route.params.assignmentId)
    note.value = task.value.completionNote || ''
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function submit() {
  if (!note.value.trim()) return
  working.value = true
  actionError.value = ''
  try {
    task.value = await submitMyTask(task.value.assignmentId, note.value.trim())
  } catch (error) {
    actionError.value = error.message
  } finally {
    working.value = false
  }
}
</script>

<template>
  <PortalShell
    eyebrow="COLLABORATION / TASK"
    title="任务详情"
    description="查看任务说明，逐个子任务提交完成内容后填写总完成说明，等待管理员人工确认。"
  >
    <RouterLink class="task-back" to="/tasks"><ArrowLeft :size="16" aria-hidden="true" />返回我的任务</RouterLink>

    <div v-if="loading" class="portal-state">正在读取任务…</div>
    <div v-else-if="errorMessage" class="portal-state error" role="alert">{{ errorMessage }}</div>

    <template v-else-if="task">
      <section class="task-panel" aria-labelledby="task-detail-title">
        <header>
          <div>
            <p>{{ taskStatusLabels[task.taskStatus] || task.taskStatus }}</p>
            <h2 id="task-detail-title">{{ task.title }}</h2>
          </div>
        </header>
        <div class="task-panel-status" :data-status="task.status">
          <strong>{{ statusLabels[task.status] || task.status }}</strong>
          <span :class="{ overdue: task.overdue }">
            <TriangleAlert v-if="task.overdue" :size="15" aria-hidden="true" />
            <Clock3 v-else :size="15" aria-hidden="true" />
            {{ task.startDate || '未设置开始' }} — {{ task.endDate || '未设置截止' }}
            <template v-if="task.overdue">（已逾期）</template>
          </span>
          <div v-if="totalCount" class="task-progress">
            <div
              class="task-progress-track"
              role="progressbar"
              aria-valuemin="0"
              :aria-valuenow="submittedCount"
              :aria-valuemax="totalCount"
              aria-label="子任务完成进度"
            >
              <span :style="{ width: `${progressPercent}%` }"></span>
            </div>
            <span class="task-progress-label">已提交 {{ submittedCount }} / {{ totalCount }}</span>
          </div>
          <span v-if="task.points > 0">
            通过后 +{{ task.awardedPoints ?? task.points }} 积分
            <template v-if="task.pointsSkippedReason">（本次未计分：{{ task.pointsSkippedReason }}）</template>
          </span>
        </div>

        <!-- eslint-disable-next-line vue/no-v-html -->
        <div class="task-panel-content" v-html="task.contentHtml"></div>

        <h3 v-if="totalCount" class="task-subtask-entries-title">
          子任务（已提交 {{ submittedCount }} / {{ totalCount }}）
        </h3>
        <ul v-if="totalCount" class="task-subtask-entries">
          <li v-for="subtask in task.subtasks" :key="subtask.id">
            <RouterLink :to="`/tasks/${task.assignmentId}/subtasks/${subtask.id}`">
              <CheckCircle2 v-if="subtask.submitted" :size="18" aria-hidden="true" />
              <CircleDashed v-else :size="18" aria-hidden="true" />
              <span class="task-subtask-entry-title">{{ subtask.title }}</span>
              <span class="task-subtask-entry-meta">
                {{ subtask.submitted ? '已提交' : '未提交' }}
                <template v-if="subtask.hasContent"> · 有说明</template>
              </span>
              <ChevronRight :size="16" aria-hidden="true" />
            </RouterLink>
          </li>
        </ul>
        <p v-else class="empty-note">本任务没有子任务，直接填写完成说明提交即可。</p>

        <p v-if="task.status === 'APPROVED'" class="task-panel-approved" role="status">
          管理员已确认通过<template v-if="task.awardedPoints">，本次获得 {{ task.awardedPoints }} 积分</template>。
        </p>
        <p v-else-if="task.status === 'REJECTED' && task.reviewComment" class="task-panel-reject" role="status">
          管理员意见：{{ task.reviewComment }}
        </p>

        <form v-if="!readOnly" class="task-panel-submit" @submit.prevent="submit">
          <label for="task-note">
            完成说明
            <textarea
              id="task-note"
              v-model="note"
              rows="4"
              maxlength="2000"
              placeholder="说明完成情况、产出或遇到的问题。"
              required
            ></textarea>
            <small>提交后管理员会人工确认；驳回时可以修改说明并重新提交。</small>
          </label>
          <button class="portal-primary" type="submit" :disabled="working || !note.trim()">
            {{ working ? '提交中…' : task.status === 'SUBMITTED' ? '更新完成说明' : '提交完成说明' }}
          </button>
        </form>
        <p v-else class="empty-note">任务已结束或已通过，不能再修改。</p>

        <p v-if="actionError" class="portal-state error inline" role="alert">{{ actionError }}</p>
      </section>
    </template>
  </PortalShell>
</template>

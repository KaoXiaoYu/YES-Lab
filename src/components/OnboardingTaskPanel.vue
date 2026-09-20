<script setup>
import { CheckCircle2, ChevronRight, CircleDashed, Clock3, TriangleAlert } from '@lucide/vue'
import { computed, ref } from 'vue'
import { submitOnboardingTask } from '../services/authApi'

const props = defineProps({
  task: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  errorMessage: { type: String, default: '' },
  editable: { type: Boolean, default: false },
})

const emit = defineEmits(['refresh'])

const working = ref(false)
const actionError = ref('')
const note = ref('')
const statusLabels = {
  PENDING: '待完成',
  SUBMITTED: '等待管理员确认',
  APPROVED: '已通过',
  REJECTED: '已驳回，请修改后重新提交',
}

const submittedCount = computed(() => props.task?.subtasks?.filter((item) => item.submitted).length ?? 0)
const totalCount = computed(() => props.task?.subtasks?.length ?? 0)
const allCompleted = computed(
  () => props.task?.allSubtasksSubmitted ?? (totalCount.value > 0 && submittedCount.value >= totalCount.value),
)
const progressPercent = computed(() =>
  totalCount.value ? Math.round((submittedCount.value / totalCount.value) * 100) : 0,
)
const canSubmit = computed(() => props.editable && props.task && props.task.status !== 'APPROVED')
/** 每个子任务是独立页面；不可编辑（未登录或已通过）时只展示状态，不给入口。 */
const canOpenSubtasks = computed(() => props.editable && props.task && props.task.status !== 'APPROVED')

function daysLabel(task) {
  if (!task.endDate) return '未设置截止日期'
  if (task.status === 'APPROVED') return `截止日期 ${task.endDate}`
  if (task.overdue) return `已逾期（截止 ${task.endDate}）`
  if (task.daysRemaining === 0) return `今天截止（${task.endDate}）`
  return `剩余 ${task.daysRemaining} 天（截止 ${task.endDate}）`
}

async function submit() {
  if (!note.value.trim()) return
  working.value = true
  actionError.value = ''
  try {
    await submitOnboardingTask(note.value.trim())
    note.value = ''
    emit('refresh')
  } catch (error) {
    actionError.value = error.message
  } finally {
    working.value = false
  }
}
</script>

<template>
  <section class="task-panel" aria-labelledby="onboarding-task-title">
    <header>
      <CircleDashed :size="22" aria-hidden="true" />
      <div>
        <p>ONBOARDING TASK</p>
        <h2 id="onboarding-task-title">新手任务</h2>
      </div>
    </header>

    <div v-if="loading" class="empty-note">正在读取新手任务…</div>
    <div v-else-if="errorMessage" class="portal-state error" role="alert">{{ errorMessage }}</div>
    <div v-else-if="!task" class="empty-note">
      目前没有新手任务。通过面试后会在此处收到新手任务，完成后经管理员确认即可转为正式成员。
    </div>

    <template v-else>
      <div class="task-panel-status" :data-status="task.status">
        <strong>{{ task.title }}</strong>
        <span>{{ statusLabels[task.status] || task.status }}</span>
        <span :class="{ overdue: task.overdue }">
          <TriangleAlert v-if="task.overdue" :size="15" aria-hidden="true" />
          <Clock3 v-else :size="15" aria-hidden="true" />
          {{ daysLabel(task) }}
        </span>
        <div class="task-progress">
          <div
            class="task-progress-track"
            role="progressbar"
            aria-valuemin="0"
            :aria-valuenow="submittedCount"
            :aria-valuemax="totalCount"
            aria-label="我的新手任务完成进度"
          >
            <span :style="{ width: `${progressPercent}%` }"></span>
          </div>
          <span class="task-progress-label">我的进度 {{ submittedCount }} / {{ totalCount }} 项子任务已提交</span>
        </div>
      </div>

      <div v-if="task.status === 'REJECTED' && task.reviewComment" class="task-panel-reject" role="status">
        管理员意见：{{ task.reviewComment }}
      </div>

      <!-- eslint-disable-next-line vue/no-v-html -->
      <div class="task-panel-content" v-html="task.contentHtml"></div>

      <h3 class="task-subtask-entries-title">子任务（已提交 {{ submittedCount }} / {{ totalCount }}）</h3>
      <ul class="task-subtask-entries">
        <li v-for="subtask in task.subtasks" :key="subtask.id">
          <RouterLink v-if="canOpenSubtasks" :to="`/application/subtasks/${subtask.id}`">
            <CheckCircle2 v-if="subtask.submitted" :size="18" aria-hidden="true" />
            <CircleDashed v-else :size="18" aria-hidden="true" />
            <span class="task-subtask-entry-title">{{ subtask.title }}</span>
            <span class="task-subtask-entry-meta">
              {{ subtask.submitted ? '已提交' : '未提交' }}
              <template v-if="subtask.hasContent"> · 有说明</template>
            </span>
            <ChevronRight :size="16" aria-hidden="true" />
          </RouterLink>
          <div v-else class="task-subtask-entry-static">
            <CheckCircle2 v-if="subtask.submitted" :size="18" aria-hidden="true" />
            <CircleDashed v-else :size="18" aria-hidden="true" />
            <span class="task-subtask-entry-title">{{ subtask.title }}</span>
            <span class="task-subtask-entry-meta">{{ subtask.submitted ? '已提交' : '未提交' }}</span>
          </div>
        </li>
      </ul>

      <div v-if="task.status === 'APPROVED'" class="task-panel-approved" role="status">
        新手任务已通过，账号已转为正式成员。请重新登录以获取新的权限。
      </div>

      <form v-else-if="canSubmit" class="task-panel-submit" @submit.prevent="submit">
        <p v-if="!allCompleted" class="task-locked-note" role="status">
          还需要提交 {{ totalCount - submittedCount }} 项子任务的内容；全部子任务提交后才能提交完成说明。
        </p>
        <label for="onboarding-note">
          完成说明
          <textarea
            id="onboarding-note"
            v-model="note"
            rows="3"
            maxlength="2000"
            placeholder="简要说明你完成了哪些内容，方便管理员确认。"
            required
          ></textarea>
          <small>提交后管理员会人工确认；驳回时可以修改说明并重新提交。</small>
        </label>
        <button class="portal-primary" type="submit" :disabled="working || !note.trim() || !allCompleted">
          {{ working ? '提交中…' : task.status === 'SUBMITTED' ? '更新完成说明' : '提交完成说明' }}
        </button>
      </form>

      <p v-if="actionError" class="portal-state error inline" role="alert">{{ actionError }}</p>
    </template>
  </section>
</template>

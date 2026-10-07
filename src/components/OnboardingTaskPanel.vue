<script setup>
import TaskSubmissionProgress from './TaskSubmissionProgress.vue'
import LoadingSkeleton from './LoadingSkeleton.vue'
import { CheckCircle2, ChevronRight, CircleDashed, Clock3, TriangleAlert } from '@lucide/vue'
import { computed, ref } from 'vue'
import AdminDrawer from './AdminDrawer.vue'
import SubtaskWorkspace from './SubtaskWorkspace.vue'
import { useDraft } from '../composables/useDraft'
import { getOnboardingSubtask, submitOnboardingSubtask, submitOnboardingTask } from '../services/authApi'
import { celebrate } from '../services/celebrate'

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
const openSubtask = ref(null)
const noteDraft = useDraft('onboarding-note', () => note.value)
noteDraft.restore((value) => (note.value = value), { announce: false })
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
const canSubmit = computed(() => props.editable && props.task?.editable && props.task.status !== 'APPROVED')
/** 每个子任务是独立页面；不可编辑（未登录或已通过）时只展示状态，不给入口。 */
const canOpenSubtasks = computed(() => props.editable && props.task?.editable && props.task.status !== 'APPROVED')
const readOnlyReason = computed(() => {
  if (!props.task) return ''
  if (props.task.status === 'SUBMITTED') return '完成说明已提交，管理员仍可审核。'
  if (props.task.status === 'REJECTED' && props.task.resubmissionDeadlineAt) {
    return `本次驳回后的 24 小时补交期限已过（${new Date(props.task.resubmissionDeadlineAt).toLocaleString('zh-CN')}），如需继续请联系管理员。`
  }
  if (props.task.overdue) return '已过提交截止时间，不能再提交；管理员仍可审核已提交内容。'
  return '当前不能提交，请联系管理员确认。'
})

function daysLabel(task) {
  if (task.status === 'REJECTED' && task.resubmissionDeadlineAt) {
    return `补交截止 ${new Date(task.resubmissionDeadlineAt).toLocaleString('zh-CN')}`
  }
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
    noteDraft.clear()
    celebrate({ title: '新手任务已提交', message: '管理员审核通过后，你会直接成为正式成员。' })
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
        <h2 id="onboarding-task-title">新手任务</h2>
      </div>
    </header>

    <LoadingSkeleton v-if="loading" variant="detail" :rows="3" label="正在读取新手任务" />
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
        <TaskSubmissionProgress
          :submitted="submittedCount"
          :total="totalCount"
          :task-submitted="['SUBMITTED', 'APPROVED'].includes(task.status)"
          accessible-label="我的新手任务提交进度"
        />
      </div>

      <div v-if="task.status === 'REJECTED' && task.reviewComment" class="task-panel-reject" role="status">
        管理员意见：{{ task.reviewComment }}
      </div>

      <!-- eslint-disable-next-line vue/no-v-html -->
      <div class="task-panel-content" v-html="task.contentHtml"></div>

      <h3 class="task-subtask-entries-title">子任务（已提交 {{ submittedCount }} / {{ totalCount }}）</h3>
      <ul class="task-subtask-entries">
        <li v-for="subtask in task.subtasks" :key="subtask.id">
          <a
            v-if="canOpenSubtasks"
            :href="`/application/subtasks/${subtask.id}`"
            @click.exact.prevent="openSubtask = subtask"
          >
            <CheckCircle2 v-if="subtask.submitted" :size="18" aria-hidden="true" />
            <CircleDashed v-else :size="18" aria-hidden="true" />
            <span class="task-subtask-entry-title">{{ subtask.title }}</span>
            <span class="task-subtask-entry-meta">
              {{ subtask.submitted ? '已提交' : '未提交' }}
              <template v-if="subtask.hasContent"> · 有说明</template>
            </span>
            <ChevronRight :size="16" aria-hidden="true" />
          </a>
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
          <small>提交后管理员会人工确认；驳回后从打回时起有 24 小时补交。</small>
        </label>
        <button class="portal-primary" type="submit" :disabled="working || !note.trim() || !allCompleted">
          {{ working ? '提交中…' : task.status === 'SUBMITTED' ? '更新完成说明' : '提交完成说明' }}
        </button>
      </form>

      <p v-else-if="task.status !== 'APPROVED'" class="task-skip" role="status">{{ readOnlyReason }}</p>

      <p v-if="actionError" class="portal-state error inline" role="alert">{{ actionError }}</p>

      <AdminDrawer
        :open="Boolean(openSubtask)"
        :title="openSubtask?.title || '子任务'"
        description="新手任务"
        size="lg"
        hide-footer
        @update:open="(value) => !value && (openSubtask = null)"
      >
        <SubtaskWorkspace
          v-if="openSubtask"
          :key="openSubtask.id"
          :draft-key="`onboarding-subtask:${openSubtask.id}`"
          :load="() => getOnboardingSubtask(openSubtask.id)"
          :submit="(html) => submitOnboardingSubtask(openSubtask.id, html)"
          :show-title="false"
          progress-label="我的进度"
          locked-message="新手任务已通过，不能再修改提交。"
          all-done-message="关闭面板，填写完成说明并提交新手任务。"
          @submitted="emit('refresh')"
        />
      </AdminDrawer>
    </template>
  </section>
</template>

<script setup>
import { CheckCircle2, Clock3, Send, TriangleAlert } from '@lucide/vue'
import { computed, onMounted, ref, watch } from 'vue'
import TaskSubmissionProgress from './TaskSubmissionProgress.vue'
import DiscussionRichTextEditor from './DiscussionRichTextEditor.vue'
import LoadingSkeleton from './LoadingSkeleton.vue'
import { useDraft } from '../composables/useDraft'
import { celebrate } from '../services/celebrate'
import { toast } from '../services/toast'

/**
 * One subtask: its brief, the member's rich-text submission and progress.
 * Used both as a standalone page and inside the task detail side sheet, so members
 * can work through subtasks without leaving the task.
 */
const props = defineProps({
  draftKey: { type: String, required: true },
  load: { type: Function, required: true },
  submit: { type: Function, required: true },
  progressLabel: { type: String, default: '大任务进度' },
  lockedMessage: { type: String, default: '大任务已结束或已通过，不能再修改提交。' },
  allDoneMessage: { type: String, default: '回到大任务填写完成说明并提交。' },
  showTitle: { type: Boolean, default: true },
})
const emit = defineEmits(['submitted', 'loaded', 'dirty-change'])

const subtask = ref(null)
const loading = ref(true)
const working = ref(false)
const errorMessage = ref('')
const actionError = ref('')
const content = ref('<p></p>')
const savedContent = ref('<p></p>')

const hasContent = computed(() => {
  const raw = String(content.value || '')
  const text = raw
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/g, '')
    .trim()
  return text.length > 0 || raw.includes('<img')
})
const dirty = computed(() => Boolean(subtask.value?.editable) && content.value !== savedContent.value)
watch(dirty, (value) => emit('dirty-change', value), { immediate: true })

const draft = useDraft(
  () => props.draftKey,
  () => content.value,
)

async function fetchSubtask({ quiet = false } = {}) {
  if (!quiet) loading.value = true
  try {
    subtask.value = await props.load()
    savedContent.value = subtask.value.submittedContentHtml || '<p></p>'
    if (!quiet) {
      content.value = savedContent.value
      if (subtask.value.editable) draft.restore((value) => (content.value = value))
    }
    emit('loaded', subtask.value)
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

onMounted(fetchSubtask)

async function send() {
  if (working.value || !hasContent.value) return
  working.value = true
  actionError.value = ''
  const wasSubmitted = subtask.value.submitted
  try {
    await props.submit(content.value)
    draft.clear()
    await fetchSubtask({ quiet: true })
    content.value = savedContent.value
    const done = subtask.value.totalSubtasks && subtask.value.submittedSubtasks === subtask.value.totalSubtasks
    if (done && !wasSubmitted) celebrate({ title: '全部子任务都已提交', message: props.allDoneMessage })
    else toast.success(`「${subtask.value.title}」${wasSubmitted ? '已更新' : '已提交'}。`)
    emit('submitted', subtask.value)
  } catch (error) {
    actionError.value = error.message
  } finally {
    working.value = false
  }
}

defineExpose({ dirty })
</script>

<template>
  <LoadingSkeleton v-if="loading" variant="detail" :rows="3" label="正在读取子任务" />
  <div v-else-if="errorMessage" class="portal-state error" role="alert">{{ errorMessage }}</div>
  <section v-else-if="subtask" class="task-panel subtask-workspace" :aria-label="subtask.title">
    <header v-if="showTitle">
      <div>
        <p>{{ subtask.taskTitle }}</p>
        <h2>{{ subtask.title }}</h2>
      </div>
    </header>

    <div class="task-panel-status" :data-status="subtask.submitted ? 'APPROVED' : 'PENDING'">
      <strong>{{ subtask.submitted ? '已提交' : '未提交' }}</strong>
      <span :class="{ overdue: subtask.overdue }">
        <TriangleAlert v-if="subtask.overdue" :size="15" aria-hidden="true" />
        <Clock3 v-else :size="15" aria-hidden="true" />
        {{ subtask.dueDate ? `截止 ${subtask.dueDate}` : '未设置截止日期' }}
        <template v-if="subtask.overdue">（已逾期）</template>
      </span>
      <TaskSubmissionProgress
        :submitted="subtask.submittedSubtasks"
        :total="subtask.totalSubtasks"
        :task-submitted="subtask.submitted"
        :accessible-label="progressLabel"
      />
    </div>

    <!-- eslint-disable-next-line vue/no-v-html -->
    <div v-if="subtask.contentHtml" class="task-panel-content" v-html="subtask.contentHtml"></div>
    <p v-else class="empty-note">本子任务没有额外说明，按要求完成后在下面提交内容即可。</p>

    <section class="task-subtask-submit" :aria-label="subtask.submitted ? '我的提交' : '提交完成内容'">
      <h3>{{ subtask.submitted ? '我的提交（可修改后重新提交）' : '提交完成内容' }}</h3>
      <p v-if="subtask.submitted && subtask.submittedAt" class="task-submit-meta">
        <CheckCircle2 :size="15" aria-hidden="true" />
        已于 {{ new Date(subtask.submittedAt).toLocaleString('zh-CN') }} 提交
      </p>
      <DiscussionRichTextEditor v-model="content" label="本子任务的完成内容" :max-length="5000" />
      <div class="task-panel-actions">
        <button
          class="portal-primary"
          type="button"
          :disabled="working || !subtask.editable || !hasContent"
          @click="send"
        >
          <Send :size="16" aria-hidden="true" />
          {{ working ? '提交中…' : subtask.submitted ? '更新提交' : '提交' }}
        </button>
        <slot name="actions" />
      </div>
      <p v-if="!subtask.editable" class="empty-note">{{ lockedMessage }}</p>
      <p v-else-if="!hasContent" class="empty-note">先填写内容再提交。</p>
    </section>

    <p v-if="actionError" class="portal-state error inline" role="alert">{{ actionError }}</p>
  </section>
</template>

<script setup>
import { ArrowLeft, CheckCircle2, Clock3, Send, TriangleAlert } from '@lucide/vue'
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import PortalShell from '../components/PortalShell.vue'
import DiscussionRichTextEditor from '../components/DiscussionRichTextEditor.vue'
import { getOnboardingSubtask, submitOnboardingSubtask } from '../services/authApi'

const route = useRoute()
const subtask = ref(null)
const loading = ref(true)
const working = ref(false)
const errorMessage = ref('')
const actionError = ref('')
const draft = ref('<p></p>')

const progressPercent = computed(() =>
  subtask.value?.totalSubtasks ? Math.round((subtask.value.submittedSubtasks / subtask.value.totalSubtasks) * 100) : 0,
)

/** 富文本去掉标签后是否还有内容——空提交后端也会拒，这里先给出更快的反馈。 */
const hasDraftContent = computed(() => {
  const raw = String(draft.value || '')
  const text = raw
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/g, '')
    .trim()
  return text.length > 0 || raw.includes('<img')
})

async function load() {
  try {
    subtask.value = await getOnboardingSubtask(route.params.subtaskId)
    // 已提交过就把原内容回填，报名者可以直接改后重新提交
    draft.value = subtask.value.submittedContentHtml || '<p></p>'
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function submit() {
  working.value = true
  actionError.value = ''
  try {
    await submitOnboardingSubtask(route.params.subtaskId, draft.value)
    await load()
  } catch (error) {
    actionError.value = error.message
  } finally {
    working.value = false
  }
}
</script>

<template>
  <PortalShell
    eyebrow="RECRUITMENT / ONBOARDING / SUBTASK"
    title="新手任务子任务"
    description="阅读子任务说明，提交这一项的完成内容（富文本）；全部子任务都提交后才能回到新手任务提交完成说明。"
  >
    <RouterLink class="task-back" to="/application">
      <ArrowLeft :size="16" aria-hidden="true" />返回我的报名
    </RouterLink>

    <div v-if="loading" class="portal-state">正在读取子任务…</div>
    <div v-else-if="errorMessage" class="portal-state error" role="alert">{{ errorMessage }}</div>

    <template v-else-if="subtask">
      <section class="task-panel" aria-labelledby="onboarding-subtask-title">
        <header>
          <div>
            <p>{{ subtask.taskTitle }}</p>
            <h2 id="onboarding-subtask-title">{{ subtask.title }}</h2>
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
          <div class="task-progress">
            <div
              class="task-progress-track"
              role="progressbar"
              aria-valuemin="0"
              :aria-valuenow="subtask.submittedSubtasks"
              :aria-valuemax="subtask.totalSubtasks"
              aria-label="我的新手任务提交进度"
            >
              <span :style="{ width: `${progressPercent}%` }"></span>
            </div>
            <span class="task-progress-label">
              我的进度 {{ subtask.submittedSubtasks }} / {{ subtask.totalSubtasks }} 项子任务已提交
            </span>
          </div>
        </div>

        <!-- eslint-disable-next-line vue/no-v-html -->
        <div v-if="subtask.contentHtml" class="task-panel-content" v-html="subtask.contentHtml"></div>
        <p v-else class="empty-note">本子任务没有额外说明，按要求完成后在下面提交内容即可。</p>

        <section class="task-subtask-submit" aria-labelledby="onboarding-subtask-submit-title">
          <h3 id="onboarding-subtask-submit-title">
            {{ subtask.submitted ? '我的提交（可修改后重新提交）' : '提交完成内容' }}
          </h3>
          <p v-if="subtask.submitted && subtask.submittedAt" class="task-submit-meta">
            <CheckCircle2 :size="15" aria-hidden="true" />
            已于 {{ new Date(subtask.submittedAt).toLocaleString('zh-CN') }} 提交
          </p>
          <DiscussionRichTextEditor v-model="draft" label="本子任务的完成内容" :max-length="5000" />
          <div class="task-panel-actions">
            <button
              class="portal-primary"
              type="button"
              :disabled="working || !subtask.editable || !hasDraftContent"
              @click="submit"
            >
              <Send :size="16" aria-hidden="true" />
              {{ working ? '提交中…' : subtask.submitted ? '更新提交' : '提交' }}
            </button>
            <RouterLink class="portal-secondary" to="/application">返回我的报名提交</RouterLink>
          </div>
          <p v-if="!subtask.editable" class="empty-note">新手任务已通过，不能再修改提交。</p>
          <p v-else-if="!hasDraftContent" class="empty-note">请先填写内容再提交。</p>
        </section>

        <p v-if="actionError" class="portal-state error inline" role="alert">{{ actionError }}</p>
      </section>
    </template>
  </PortalShell>
</template>

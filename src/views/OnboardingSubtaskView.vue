<script setup>
import { ArrowLeft, CheckCircle2, CircleDashed, Clock3, TriangleAlert } from '@lucide/vue'
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import PortalShell from '../components/PortalShell.vue'
import { getOnboardingSubtask, toggleOnboardingSubtask } from '../services/authApi'

const route = useRoute()
const subtask = ref(null)
const loading = ref(true)
const working = ref(false)
const errorMessage = ref('')
const actionError = ref('')

const progressPercent = computed(() =>
  subtask.value?.totalSubtasks ? Math.round((subtask.value.completedSubtasks / subtask.value.totalSubtasks) * 100) : 0,
)

async function load() {
  try {
    subtask.value = await getOnboardingSubtask(route.params.subtaskId)
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function toggle() {
  working.value = true
  actionError.value = ''
  try {
    await toggleOnboardingSubtask(route.params.subtaskId, !subtask.value.completed)
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
    description="阅读该子任务的说明，完成后勾选；全部子任务完成后回到新手任务提交完成说明。"
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

        <div class="task-panel-status" :data-status="subtask.completed ? 'APPROVED' : 'PENDING'">
          <strong>{{ subtask.completed ? '已完成' : '未完成' }}</strong>
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
              :aria-valuenow="subtask.completedSubtasks"
              :aria-valuemax="subtask.totalSubtasks"
              aria-label="我的新手任务完成进度"
            >
              <span :style="{ width: `${progressPercent}%` }"></span>
            </div>
            <span class="task-progress-label">
              我的进度 {{ subtask.completedSubtasks }} / {{ subtask.totalSubtasks }} 项子任务
            </span>
          </div>
        </div>

        <!-- eslint-disable-next-line vue/no-v-html -->
        <div v-if="subtask.contentHtml" class="task-panel-content" v-html="subtask.contentHtml"></div>
        <p v-else class="empty-note">本子任务没有额外说明，完成后勾选即可。</p>

        <div class="task-panel-actions">
          <button
            class="portal-primary"
            type="button"
            :disabled="working || !subtask.editable"
            :aria-pressed="subtask.completed"
            @click="toggle"
          >
            <CheckCircle2 v-if="subtask.completed" :size="16" aria-hidden="true" />
            <CircleDashed v-else :size="16" aria-hidden="true" />
            {{ subtask.completed ? '取消完成标记' : '标记为已完成' }}
          </button>
          <RouterLink class="portal-secondary" to="/application">返回我的报名提交</RouterLink>
        </div>
        <p v-if="!subtask.editable" class="empty-note">新手任务已通过，不能再修改子任务。</p>

        <p v-if="actionError" class="portal-state error inline" role="alert">{{ actionError }}</p>
      </section>
    </template>
  </PortalShell>
</template>

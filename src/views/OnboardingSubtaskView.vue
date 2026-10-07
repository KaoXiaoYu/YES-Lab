<script setup>
import { ArrowLeft } from '@lucide/vue'
import { useRoute } from 'vue-router'
import PortalShell from '../components/PortalShell.vue'
import SubtaskWorkspace from '../components/SubtaskWorkspace.vue'
import { getOnboardingSubtask, submitOnboardingSubtask } from '../services/authApi'

const route = useRoute()
const subtaskId = route.params.subtaskId
</script>

<template>
  <PortalShell
    title="新手任务子任务"
    description="阅读子任务说明并提交这一项的完成内容；全部提交后回到报名页提交新手任务。"
  >
    <RouterLink class="task-back" to="/application"><ArrowLeft :size="16" aria-hidden="true" />返回我的报名</RouterLink>
    <SubtaskWorkspace
      :draft-key="`onboarding-subtask:${subtaskId}`"
      :load="() => getOnboardingSubtask(subtaskId)"
      :submit="(html) => submitOnboardingSubtask(subtaskId, html)"
      progress-label="我的进度"
      locked-message="新手任务已通过，不能再修改提交。"
      all-done-message="回到报名页填写完成说明，提交新手任务。"
    >
      <template #actions>
        <RouterLink class="portal-secondary" to="/application">返回我的报名</RouterLink>
      </template>
    </SubtaskWorkspace>
  </PortalShell>
</template>

<script setup>
import { ArrowLeft } from '@lucide/vue'
import { useRoute } from 'vue-router'
import PortalShell from '../components/PortalShell.vue'
import SubtaskWorkspace from '../components/SubtaskWorkspace.vue'
import { getMySubtask, submitMySubtask } from '../services/authApi'

const route = useRoute()
const assignmentId = route.params.assignmentId
const subtaskId = route.params.subtaskId
</script>

<template>
  <PortalShell title="子任务" description="阅读子任务说明并提交完成内容；大任务确认前可以修改后重新提交。">
    <RouterLink class="task-back" :to="`/tasks/${assignmentId}`">
      <ArrowLeft :size="16" aria-hidden="true" />返回大任务
    </RouterLink>
    <SubtaskWorkspace
      :draft-key="`subtask:${assignmentId}:${subtaskId}`"
      :load="() => getMySubtask(assignmentId, subtaskId)"
      :submit="(html) => submitMySubtask(assignmentId, subtaskId, html)"
    >
      <template #actions>
        <RouterLink class="portal-secondary" :to="`/tasks/${assignmentId}`">返回大任务</RouterLink>
      </template>
    </SubtaskWorkspace>
  </PortalShell>
</template>

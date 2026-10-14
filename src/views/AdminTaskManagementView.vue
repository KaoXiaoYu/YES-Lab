<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import PortalShell from '../components/PortalShell.vue'
import AdminTasksView from './AdminTasksView.vue'
import AdminOnboardingTaskView from './AdminOnboardingTaskView.vue'
import AdminBountiesView from './AdminBountiesView.vue'

const route = useRoute()
const categories = [
  { id: 'standard', label: '普通任务', component: AdminTasksView },
  { id: 'onboarding', label: '新手任务', component: AdminOnboardingTaskView },
  { id: 'bounty', label: '悬赏任务', component: AdminBountiesView },
]
const category = computed(() => categories.find((item) => item.id === route.query.type) || categories[0])
</script>

<template>
  <PortalShell title="任务管理" description="发布任务、查看进度与处理审核。">
    <nav class="tm-modules" aria-label="任务分类">
      <RouterLink
        v-for="item in categories"
        :key="item.id"
        :to="{ path: '/admin/tasks', query: { ...route.query, type: item.id }, hash: route.hash }"
        :aria-current="category.id === item.id ? 'page' : undefined"
        >{{ item.label }}</RouterLink
      >
    </nav>
    <component :is="category.component" :key="category.id" />
  </PortalShell>
</template>

<script setup>
import { RouterView } from 'vue-router'
import SubmissionFeedbackModal from './components/SubmissionFeedbackModal.vue'
import RepositoryFooter from './components/RepositoryFooter.vue'
</script>

<template>
  <RouterView />
  <RepositoryFooter v-if="$route.path !== '/'" />
  <SubmissionFeedbackModal />
</template>

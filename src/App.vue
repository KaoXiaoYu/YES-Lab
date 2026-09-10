<script setup>
import { RouterView } from 'vue-router'
import CompetitionCountdown from './components/CompetitionCountdown.vue'
import SubmissionFeedbackModal from './components/SubmissionFeedbackModal.vue'
</script>

<template>
  <RouterView />
  <CompetitionCountdown />
  <SubmissionFeedbackModal />
</template>

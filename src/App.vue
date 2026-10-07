<script setup>
import { RouterView } from 'vue-router'
import SubmissionFeedbackModal from './components/SubmissionFeedbackModal.vue'
import RepositoryFooter from './components/RepositoryFooter.vue'
import ConfirmDialog from './components/ConfirmDialog.vue'
import ToastHost from './components/ToastHost.vue'
import CelebrationHost from './components/CelebrationHost.vue'
import CommandPalette from './components/CommandPalette.vue'
import { notifyRouteLeft } from './services/routeTransition'
</script>

<template>
  <!-- Pages fade between different views; the same view (e.g. login ⇄ register, the admin shell) is reused. -->
  <RouterView v-slot="{ Component }">
    <Transition name="page" mode="out-in" @after-leave="notifyRouteLeft">
      <component :is="Component" />
    </Transition>
  </RouterView>
  <RepositoryFooter v-if="$route.path !== '/'" />
  <SubmissionFeedbackModal />
  <ConfirmDialog />
  <ToastHost />
  <CelebrationHost />
  <CommandPalette />
</template>

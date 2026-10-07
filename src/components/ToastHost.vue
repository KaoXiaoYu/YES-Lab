<script setup>
import { CircleAlert, CircleCheck, Info, X } from '@lucide/vue'
import { dismissToast, pauseDismiss, scheduleDismiss, toasts } from '../services/toast'

const icons = { success: CircleCheck, error: CircleAlert, info: Info }

function resume(item) {
  if (item.timeout) scheduleDismiss(item.id, 2400)
}
</script>

<template>
  <Teleport to="body">
    <div class="ui-toast-region" aria-live="polite" aria-relevant="additions">
      <TransitionGroup name="ui-toast" tag="ol" class="ui-toast-stack">
        <li
          v-for="item in toasts"
          :key="item.id"
          class="ui-toast"
          :class="`ui-toast--${item.tone}`"
          :role="item.tone === 'error' ? 'alert' : 'status'"
          @mouseenter="pauseDismiss(item.id)"
          @mouseleave="resume(item)"
          @focusin="pauseDismiss(item.id)"
          @focusout="resume(item)"
        >
          <component :is="icons[item.tone] || Info" class="ui-toast-icon" :size="18" aria-hidden="true" />
          <p>
            <strong v-if="item.title">{{ item.title }}</strong>
            <span>{{ item.message }}</span>
          </p>
          <button type="button" class="ui-toast-close" aria-label="关闭提示" @click="dismissToast(item.id)">
            <X :size="16" aria-hidden="true" />
          </button>
        </li>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<script setup>
import { TriangleAlert, CircleQuestionMark } from '@lucide/vue'
import { ref } from 'vue'
import { useModalLayer } from '../composables/useModalLayer'
import { confirmState, settleConfirm } from '../services/confirm'

const panel = ref(null)
const cancelButton = ref(null)
const confirmButton = ref(null)

useModalLayer(panel, {
  active: () => confirmState.open,
  onEscape: () => settleConfirm(false),
  initialFocus: () => (confirmState.tone === 'danger' ? cancelButton.value : confirmButton.value),
})
</script>

<template>
  <Teleport to="body">
    <Transition name="ui-dialog">
      <div v-if="confirmState.open" class="ui-dialog-backdrop" @click.self="settleConfirm(false)">
        <section
          ref="panel"
          class="ui-dialog"
          :class="`ui-dialog--${confirmState.tone}`"
          role="alertdialog"
          aria-modal="true"
          aria-labelledby="ui-confirm-title"
          aria-describedby="ui-confirm-message"
          tabindex="-1"
        >
          <span class="ui-dialog-icon" aria-hidden="true">
            <TriangleAlert v-if="confirmState.tone === 'danger'" :size="20" />
            <CircleQuestionMark v-else :size="20" />
          </span>
          <div class="ui-dialog-body">
            <h2 id="ui-confirm-title">{{ confirmState.title }}</h2>
            <p id="ui-confirm-message">{{ confirmState.message }}</p>
            <ul v-if="confirmState.details?.length">
              <li v-for="item in confirmState.details" :key="item">{{ item }}</li>
            </ul>
          </div>
          <footer class="ui-dialog-actions">
            <button ref="cancelButton" type="button" class="ui-btn ui-btn--secondary" @click="settleConfirm(false)">
              {{ confirmState.cancelText }}
            </button>
            <button
              ref="confirmButton"
              type="button"
              class="ui-btn"
              :class="confirmState.tone === 'danger' ? 'ui-btn--danger' : 'ui-btn--primary'"
              @click="settleConfirm(true)"
            >
              {{ confirmState.confirmText }}
            </button>
          </footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

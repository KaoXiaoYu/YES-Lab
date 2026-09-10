<script setup>
import { ArrowRight, CircleCheck, X } from 'lucide-vue-next'
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { closeSubmissionFeedback, submissionFeedbackState } from '../services/submissionFeedback'

const dialog = ref(null)
const confirmButton = ref(null)
let returnFocus = null

watch(() => submissionFeedbackState.open, async (open) => {
  if (open) {
    returnFocus = document.activeElement
    document.body.classList.add('submission-feedback-open')
    await nextTick()
    confirmButton.value?.focus()
    return
  }

  document.body.classList.remove('submission-feedback-open')
  await nextTick()
  if (returnFocus?.isConnected && returnFocus !== document.body) returnFocus.focus()
  else focusPageHeading()
  returnFocus = null
})

onBeforeUnmount(() => document.body.classList.remove('submission-feedback-open'))

function focusPageHeading() {
  const heading = document.querySelector('main h1, main h2')
  if (!heading) return
  const hadTabindex = heading.hasAttribute('tabindex')
  heading.setAttribute('tabindex', '-1')
  heading.focus({ preventScroll: true })
  if (!hadTabindex) heading.addEventListener('blur', () => heading.removeAttribute('tabindex'), { once: true })
}

function handleKeydown(event) {
  if (event.key === 'Escape') {
    event.preventDefault()
    closeSubmissionFeedback()
    return
  }
  if (event.key !== 'Tab') return

  const controls = [...(dialog.value?.querySelectorAll('button:not(:disabled)') || [])]
  if (!controls.length) return
  const first = controls[0]
  const last = controls[controls.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}
</script>

<template>
  <Teleport to="body">
    <Transition name="submission-feedback">
      <div
        v-if="submissionFeedbackState.open"
        class="submission-feedback-overlay"
        @click.self="closeSubmissionFeedback"
        @keydown="handleKeydown"
      >
        <section
          ref="dialog"
          class="submission-feedback-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="submission-feedback-title"
          aria-describedby="submission-feedback-message"
        >
          <button class="submission-feedback-close" type="button" aria-label="关闭提交结果" @click="closeSubmissionFeedback">
            <X :size="19" aria-hidden="true" />
          </button>
          <div class="submission-feedback-mark" aria-hidden="true"><CircleCheck :size="32" /></div>
          <p>{{ submissionFeedbackState.eyebrow }}</p>
          <h2 id="submission-feedback-title">{{ submissionFeedbackState.title }}</h2>
          <span id="submission-feedback-message">{{ submissionFeedbackState.message }}</span>
          <button ref="confirmButton" class="submission-feedback-confirm" type="button" @click="closeSubmissionFeedback">
            {{ submissionFeedbackState.confirmLabel }}<ArrowRight :size="17" aria-hidden="true" />
          </button>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

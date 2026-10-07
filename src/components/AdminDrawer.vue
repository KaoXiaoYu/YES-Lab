<script setup>
import { X } from '@lucide/vue'
import { inject, ref } from 'vue'
import { useModalLayer } from '../composables/useModalLayer'
import { confirmAction } from '../services/confirm'

const props = defineProps({
  open: { type: Boolean, default: false },
  title: { type: String, required: true },
  eyebrow: { type: String, default: '' },
  description: { type: String, default: '' },
  size: { type: String, default: 'md', validator: (value) => ['md', 'lg'].includes(value) },
  dirty: { type: Boolean, default: false },
  busy: { type: Boolean, default: false },
  submitText: { type: String, default: '保存' },
  busyText: { type: String, default: '保存中…' },
  submitDisabled: { type: Boolean, default: false },
  hideFooter: { type: Boolean, default: false },
})
const emit = defineEmits(['update:open', 'submit', 'closed'])

const panel = ref(null)
const layerTarget = inject('adminLayout', null) ? '#admin-layer' : 'body'
const headingId = `drawer-${Math.random().toString(36).slice(2, 9)}`

async function requestClose() {
  if (props.busy) return
  if (
    props.dirty &&
    !(await confirmAction({
      title: '放弃未保存的修改？',
      message: '关闭后，这次填写的内容不会保留。',
      confirmText: '放弃修改',
      cancelText: '继续编辑',
      tone: 'danger',
    }))
  )
    return
  emit('update:open', false)
}

useModalLayer(panel, {
  active: () => props.open,
  onEscape: requestClose,
  initialFocus: () => panel.value?.querySelector('[autofocus], .ui-drawer-body :is(input, select, textarea)'),
})

defineExpose({ requestClose })
</script>

<template>
  <Teleport :to="layerTarget" defer>
    <Transition name="ui-drawer" @after-leave="emit('closed')">
      <div v-if="open" class="ui-drawer-backdrop" @click.self="requestClose">
        <form
          ref="panel"
          class="ui-drawer"
          :class="`ui-drawer--${size}`"
          role="dialog"
          aria-modal="true"
          :aria-labelledby="headingId"
          :aria-busy="busy"
          tabindex="-1"
          @submit.prevent="emit('submit')"
        >
          <header class="ui-drawer-head">
            <div>
              <p v-if="eyebrow">{{ eyebrow }}</p>
              <h2 :id="headingId">{{ title }}</h2>
              <span v-if="description">{{ description }}</span>
            </div>
            <button type="button" class="ui-icon-button" aria-label="关闭" :disabled="busy" @click="requestClose">
              <X :size="18" aria-hidden="true" />
            </button>
          </header>
          <div class="ui-drawer-body" :inert="busy">
            <slot />
          </div>
          <footer v-if="!hideFooter" class="ui-drawer-foot">
            <p class="ui-drawer-status" :class="{ dirty }">
              <slot name="status">{{ dirty ? '有未保存的修改' : '' }}</slot>
            </p>
            <slot name="footer" :request-close="requestClose">
              <button type="button" class="ui-btn ui-btn--secondary" :disabled="busy" @click="requestClose">
                取消
              </button>
              <button type="submit" class="ui-btn ui-btn--primary" :disabled="busy || submitDisabled">
                {{ busy ? busyText : submitText }}
              </button>
            </slot>
          </footer>
        </form>
      </div>
    </Transition>
  </Teleport>
</template>

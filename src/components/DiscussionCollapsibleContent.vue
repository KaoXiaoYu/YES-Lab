<script setup>
import { ChevronDown, ChevronUp } from 'lucide-vue-next'
import { nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'

const props = defineProps({
  html: { type: String, default: '' },
  maxHeight: { type: Number, default: 280 },
  compact: { type: Boolean, default: false },
  label: { type: String, default: '讨论内容' },
})

const contentId = `discussion-content-${useId()}`
const contentElement = ref(null)
const expanded = ref(false)
const overflowing = ref(false)
let resizeObserver = null

function measure() {
  const element = contentElement.value
  if (!element) return
  overflowing.value = element.scrollHeight > props.maxHeight + 4
}

function toggle() {
  expanded.value = !expanded.value
}

watch(() => props.html, async () => {
  expanded.value = false
  overflowing.value = false
  await nextTick()
  measure()
})

onMounted(async () => {
  await nextTick()
  measure()
  if (window.ResizeObserver) {
    resizeObserver = new ResizeObserver(measure)
    resizeObserver.observe(contentElement.value)
  }
})

onBeforeUnmount(() => resizeObserver?.disconnect())
</script>

<template>
  <div :class="['discussion-collapsible', { compact }]" @load.capture="measure">
    <div
      :id="contentId"
      ref="contentElement"
      :class="['discussion-rich-content', { 'is-collapsed': overflowing && !expanded }]"
      :style="{ '--discussion-collapse-height': `${maxHeight}px` }"
      v-html="html"
    ></div>
    <span v-if="overflowing && !expanded" class="discussion-content-fade" aria-hidden="true"></span>
    <button
      v-if="overflowing"
      class="discussion-content-toggle"
      type="button"
      :aria-controls="contentId"
      :aria-expanded="expanded"
      @click="toggle"
    >
      <ChevronUp v-if="expanded" :size="17" aria-hidden="true" />
      <ChevronDown v-else :size="17" aria-hidden="true" />
      {{ expanded ? '收起内容' : `展开${label}` }}
    </button>
  </div>
</template>

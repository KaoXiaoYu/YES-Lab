<script setup>
defineProps({
  show: { type: Boolean, default: false },
  busy: { type: Boolean, default: false },
  message: { type: String, default: '有未保存的修改' },
  saveText: { type: String, default: '保存修改' },
  busyText: { type: String, default: '保存中…' },
  discardText: { type: String, default: '放弃' },
  form: { type: String, default: '' },
})
const emit = defineEmits(['save', 'discard'])
</script>

<template>
  <Transition name="ui-savebar">
    <div v-if="show || busy" class="ui-savebar" role="region" aria-label="保存修改">
      <p><span class="ui-savebar-dot" aria-hidden="true"></span>{{ message }}</p>
      <div>
        <button type="button" class="ui-btn ui-btn--ghost" :disabled="busy" @click="emit('discard')">
          {{ discardText }}
        </button>
        <button
          :type="form ? 'submit' : 'button'"
          :form="form || undefined"
          class="ui-btn ui-btn--primary"
          :disabled="busy"
          @click="!form && emit('save')"
        >
          {{ busy ? busyText : saveText }}
        </button>
      </div>
    </div>
  </Transition>
</template>

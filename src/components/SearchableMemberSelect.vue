<script setup>
import { Search } from 'lucide-vue-next'
import { computed, ref, useId } from 'vue'

const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  options: { type: Array, default: () => [] },
  label: { type: String, required: true },
  searchLabel: { type: String, default: '按姓名或学号搜索' },
  placeholder: { type: String, default: '输入姓名或学号' },
  emptyLabel: { type: String, default: '请选择成员' },
  valueKey: { type: String, default: 'id' },
  required: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  nullOnEmpty: { type: Boolean, default: false },
})
const emit = defineEmits(['update:modelValue', 'change'])
const search = ref('')
const id = `member-select-${useId()}`

const filteredOptions = computed(() => {
  const keyword = search.value.trim().toLocaleLowerCase()
  if (!keyword) return props.options
  const filtered = props.options.filter((item) => `${item.name || ''} ${item.memberCode || ''}`.toLocaleLowerCase().includes(keyword))
  const selected = props.options.find((item) => String(item[props.valueKey]) === String(props.modelValue))
  return selected && !filtered.some((item) => item[props.valueKey] === selected[props.valueKey]) ? [selected, ...filtered] : filtered
})

function update(event) {
  emit('update:modelValue', props.nullOnEmpty && event.target.value === '' ? null : event.target.value)
  emit('change', event)
}
</script>

<template>
  <div class="searchable-member-select">
    <label :for="`${id}-search`">{{ searchLabel }}
      <span class="member-search-input"><Search :size="16" aria-hidden="true" /><input :id="`${id}-search`" v-model.trim="search" type="search" :placeholder="placeholder" autocomplete="off" /></span>
    </label>
    <label :for="id">{{ label }}
      <select :id="id" :value="modelValue" :required="required" :disabled="disabled" @change="update">
        <option value="">{{ emptyLabel }}</option>
        <option v-for="item in filteredOptions" :key="item[valueKey]" :value="item[valueKey]">{{ item.name }}{{ item.memberCode ? ` · ${item.memberCode}` : '' }}</option>
      </select>
      <small v-if="search && !filteredOptions.length">没有匹配姓名或学号的成员。</small>
    </label>
  </div>
</template>

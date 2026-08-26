<script setup>
import { Check, ChevronDown, Search, X } from 'lucide-vue-next'
import { computed, nextTick, onBeforeUnmount, ref, useId, watch } from 'vue'

const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  options: { type: Array, default: () => [] },
  label: { type: String, required: true },
  placeholder: { type: String, default: '输入姓名或学号搜索' },
  emptyLabel: { type: String, default: '请选择成员' },
  valueKey: { type: String, default: 'id' },
  required: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  nullOnEmpty: { type: Boolean, default: false },
})
const emit = defineEmits(['update:modelValue', 'change'])
const id = `member-combobox-${useId()}`
const input = ref(null)
const query = ref('')
const open = ref(false)
const activeIndex = ref(-1)
let blurTimer

const selectedOption = computed(() => props.options.find((item) => sameValue(item[props.valueKey], props.modelValue)) || null)
const selectedLabel = computed(() => optionLabel(selectedOption.value))
const filteredOptions = computed(() => {
  const keyword = query.value.trim().toLocaleLowerCase()
  if (!keyword || (!open.value && selectedOption.value && keyword === selectedLabel.value.toLocaleLowerCase())) return props.options
  return props.options.filter((item) => `${item.name || ''} ${item.memberCode || ''}`.toLocaleLowerCase().includes(keyword))
})

watch([selectedOption, open], () => {
  if (!open.value) query.value = selectedLabel.value
})
watch(() => props.modelValue, () => nextTick(updateValidity))

onBeforeUnmount(() => window.clearTimeout(blurTimer))

function sameValue(left, right) {
  return left != null && right != null && String(left) === String(right)
}

function optionLabel(item) {
  if (!item) return ''
  return `${item.name || ''}${item.memberCode ? ` · ${item.memberCode}` : ''}`
}

function beginSearch() {
  if (props.disabled) return
  window.clearTimeout(blurTimer)
  if (!open.value) query.value = ''
  open.value = true
  const selectedIndex = filteredOptions.value.findIndex((item) => sameValue(item[props.valueKey], props.modelValue))
  activeIndex.value = selectedIndex >= 0 ? selectedIndex : (filteredOptions.value.length ? 0 : -1)
}

function handleInput() {
  open.value = true
  activeIndex.value = filteredOptions.value.length ? 0 : -1
  updateValidity()
}

function choose(item) {
  const value = item[props.valueKey]
  emit('update:modelValue', value)
  emit('change', item)
  query.value = optionLabel(item)
  open.value = false
  activeIndex.value = -1
  nextTick(updateValidity)
}

function clearSelection() {
  const value = props.nullOnEmpty ? null : ''
  emit('update:modelValue', value)
  emit('change', null)
  query.value = ''
  open.value = true
  activeIndex.value = filteredOptions.value.length ? 0 : -1
  nextTick(() => {
    updateValidity()
    input.value?.focus()
  })
}

function closeLater() {
  window.clearTimeout(blurTimer)
  blurTimer = window.setTimeout(() => {
    open.value = false
    activeIndex.value = -1
    query.value = selectedLabel.value
    updateValidity()
  }, 120)
}

function handleKeydown(event) {
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    if (!open.value) beginSearch()
    else if (filteredOptions.value.length) activeIndex.value = (activeIndex.value + 1) % filteredOptions.value.length
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    if (!open.value) beginSearch()
    else if (filteredOptions.value.length) activeIndex.value = (activeIndex.value - 1 + filteredOptions.value.length) % filteredOptions.value.length
  } else if (event.key === 'Enter' && open.value && activeIndex.value >= 0) {
    event.preventDefault()
    choose(filteredOptions.value[activeIndex.value])
  } else if (event.key === 'Escape') {
    open.value = false
    activeIndex.value = -1
    query.value = selectedLabel.value
  }
}

function updateValidity() {
  input.value?.setCustomValidity(props.required && !props.modelValue ? '请从待选人物中选择一名成员' : '')
}
</script>

<template>
  <div :class="['searchable-member-select', { open, disabled }]">
    <label :for="id">{{ label }}</label>
    <div class="member-combobox">
      <Search :size="17" aria-hidden="true" />
      <input
        :id="id"
        ref="input"
        v-model="query"
        type="search"
        role="combobox"
        autocomplete="off"
        aria-autocomplete="list"
        :aria-expanded="open"
        :aria-controls="`${id}-listbox`"
        :aria-activedescendant="open && activeIndex >= 0 ? `${id}-option-${activeIndex}` : undefined"
        :placeholder="open ? placeholder : (selectedOption ? selectedLabel : emptyLabel)"
        :required="required"
        :disabled="disabled"
        @focus="beginSearch"
        @input="handleInput"
        @keydown="handleKeydown"
        @blur="closeLater"
        @invalid="beginSearch"
      />
      <button v-if="selectedOption && !disabled" type="button" :aria-label="`清除${label}`" @mousedown.prevent="clearSelection"><X :size="16" aria-hidden="true" /></button>
      <ChevronDown v-else :size="17" aria-hidden="true" />
      <ul v-if="open" :id="`${id}-listbox`" class="member-combobox-options" role="listbox" :aria-label="`${label}待选人物`">
        <li
          v-for="(item, index) in filteredOptions"
          :id="`${id}-option-${index}`"
          :key="item[valueKey]"
          role="option"
          :aria-selected="sameValue(item[valueKey], modelValue)"
          :class="{ active: index === activeIndex, selected: sameValue(item[valueKey], modelValue) }"
          @mouseenter="activeIndex = index"
          @mousedown.prevent="choose(item)"
        >
          <span class="member-combobox-avatar"><img v-if="item.avatarUrl" :src="item.avatarUrl" alt="" /><b v-else>{{ (item.name || '?').slice(0, 1) }}</b></span>
          <span><strong>{{ item.name }}</strong><small>{{ item.memberCode || '无内部编号' }}</small></span>
          <Check v-if="sameValue(item[valueKey], modelValue)" :size="17" aria-hidden="true" />
        </li>
        <li v-if="!filteredOptions.length" class="empty" role="option" aria-disabled="true">没有匹配姓名或学号的成员</li>
      </ul>
    </div>
    <small v-if="selectedOption">已选择：{{ selectedLabel }}</small>
  </div>
</template>

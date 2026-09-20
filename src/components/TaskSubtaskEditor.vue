<script setup>
import { ArrowDown, ArrowUp, ChevronDown, ChevronRight, FileText, ListPlus, Plus, Trash2 } from '@lucide/vue'
import { computed, ref } from 'vue'
import DiscussionRichTextEditor from './DiscussionRichTextEditor.vue'

const props = defineProps({
  /** 子任务数组：{ id?: string, title: string, contentHtml?: string, hasContent?: boolean } */
  modelValue: { type: Array, default: () => [] },
  label: { type: String, default: '子任务' },
  hint: { type: String, default: '' },
  max: { type: Number, default: 50 },
  disabled: { type: Boolean, default: false },
  /** 已存在且带正文的子任务，展开时按需拉取正文：(subtaskId) => Promise<{ contentHtml }> */
  loadContent: { type: Function, default: null },
})

const emit = defineEmits(['update:modelValue'])

const items = computed(() => props.modelValue ?? [])
const canAdd = computed(() => !props.disabled && items.value.length < props.max)
const expandedKey = ref('')
const loadingKey = ref('')
const loadError = ref('')

/** 列表里用索引作为展开键：新增行的 id 为空，索引是唯一稳定的局部标识。 */
function rowKey(item, index) {
  return item.id || `new-${index}`
}

function update(next) {
  emit('update:modelValue', next)
}

function addItem() {
  if (!canAdd.value) return
  update([...items.value, { title: '', contentHtml: null }])
  expandedKey.value = `new-${items.value.length}`
}

function removeItem(index) {
  if (props.disabled) return
  if (expandedKey.value === rowKey(items.value[index], index)) expandedKey.value = ''
  update(items.value.filter((_, position) => position !== index))
}

function moveItem(index, offset) {
  if (props.disabled) return
  const target = index + offset
  if (target < 0 || target >= items.value.length) return
  const next = [...items.value]
  const [moved] = next.splice(index, 1)
  next.splice(target, 0, moved)
  update(next)
}

function changeItem(index, patch) {
  const next = [...items.value]
  next[index] = { ...next[index], ...patch }
  update(next)
}

async function toggleExpanded(item, index) {
  const key = rowKey(item, index)
  if (expandedKey.value === key) {
    expandedKey.value = ''
    return
  }
  expandedKey.value = key
  loadError.value = ''
  // 已有正文但尚未加载时才按需拉取，避免一次渲染 50 个编辑器与 50 份正文。
  if (!props.loadContent || !item.id || !item.hasContent || item.contentHtml !== undefined) return
  loadingKey.value = key
  try {
    const detail = await props.loadContent(item.id)
    changeItem(index, { contentHtml: detail.contentHtml ?? null })
  } catch (error) {
    loadError.value = error.message
  } finally {
    loadingKey.value = ''
  }
}
</script>

<template>
  <fieldset class="task-subtask-editor" :disabled="disabled">
    <legend>{{ label }}</legend>
    <p v-if="hint" class="task-subtask-hint">{{ hint }}</p>

    <ol v-if="items.length" class="task-subtask-list">
      <li v-for="(item, index) in items" :key="rowKey(item, index)">
        <div class="task-subtask-row">
          <span class="task-subtask-index" aria-hidden="true">{{ index + 1 }}</span>
          <label class="task-subtask-field">
            <span class="sr-only">第 {{ index + 1 }} 项子任务标题</span>
            <input
              :value="item.title"
              type="text"
              maxlength="200"
              placeholder="例如：配置开发环境"
              :disabled="disabled"
              @input="changeItem(index, { title: $event.target.value })"
            />
          </label>
          <div class="task-subtask-actions">
            <button
              type="button"
              :aria-expanded="expandedKey === rowKey(item, index)"
              :aria-label="`${expandedKey === rowKey(item, index) ? '收起' : '展开'}第 ${index + 1} 项子任务说明`"
              @click="toggleExpanded(item, index)"
            >
              <ChevronDown v-if="expandedKey === rowKey(item, index)" :size="15" aria-hidden="true" />
              <ChevronRight v-else :size="15" aria-hidden="true" />
            </button>
            <button
              type="button"
              :disabled="disabled || index === 0"
              :aria-label="`把第 ${index + 1} 项上移`"
              @click="moveItem(index, -1)"
            >
              <ArrowUp :size="15" aria-hidden="true" />
            </button>
            <button
              type="button"
              :disabled="disabled || index === items.length - 1"
              :aria-label="`把第 ${index + 1} 项下移`"
              @click="moveItem(index, 1)"
            >
              <ArrowDown :size="15" aria-hidden="true" />
            </button>
            <button
              type="button"
              class="danger"
              :disabled="disabled"
              :aria-label="`删除第 ${index + 1} 项子任务`"
              @click="removeItem(index)"
            >
              <Trash2 :size="15" aria-hidden="true" />
            </button>
          </div>
          <span v-if="item.hasContent || (item.contentHtml ?? '').trim()" class="task-subtask-flag">
            <FileText :size="14" aria-hidden="true" />有说明
          </span>
        </div>

        <div v-if="expandedKey === rowKey(item, index)" class="task-subtask-content">
          <p v-if="loadingKey === rowKey(item, index)" class="empty-note">正在读取子任务说明…</p>
          <DiscussionRichTextEditor
            v-else
            :model-value="item.contentHtml ?? '<p></p>'"
            :label="`第 ${index + 1} 项子任务的说明（可留空）`"
            :max-length="5000"
            @update:model-value="changeItem(index, { contentHtml: $event, hasContent: true })"
          />
          <p v-if="loadError" class="portal-state error inline" role="alert">{{ loadError }}</p>
        </div>
      </li>
    </ol>

    <div class="task-subtask-footer">
      <button class="portal-secondary" type="button" :disabled="!canAdd" @click="addItem">
        <Plus v-if="items.length" :size="16" aria-hidden="true" />
        <ListPlus v-else :size="16" aria-hidden="true" />
        添加子任务
      </button>
      <small v-if="items.length">已添加 {{ items.length }} / {{ max }} 项，顺序即成员看到的顺序。</small>
      <small v-else>还没有子任务；每项都可以展开写富文本说明。</small>
    </div>
  </fieldset>
</template>

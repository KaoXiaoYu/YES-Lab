<script setup>
import Image from '@tiptap/extension-image'
import StarterKit from '@tiptap/starter-kit'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import {
  Bold,
  Braces,
  ImagePlus,
  Italic,
  Link,
  List,
  ListOrdered,
  Quote,
  Redo2,
  Strikethrough,
  Undo2,
  Unlink,
} from '@lucide/vue'
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  label: { type: String, default: '讨论内容' },
  maxLength: { type: Number, default: 5000 },
  compact: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'update:length'])
const validationMessage = ref('')

const editor = useEditor({
  extensions: [
    StarterKit.configure({
      link: {
        openOnClick: false,
        HTMLAttributes: { rel: 'noopener noreferrer nofollow', target: '_blank' },
      },
    }),
    Image.configure({ allowBase64: false, inline: false }),
  ],
  content: props.modelValue || '<p></p>',
  editorProps: {
    attributes: {
      'aria-label': props.label,
      class: `discussion-editor-content${props.compact ? ' compact' : ''}`,
    },
  },
  onCreate: ({ editor: instance }) => emitLength(instance),
  onUpdate: ({ editor: instance }) => {
    validationMessage.value = ''
    const html = instance.getHTML()
    emit('update:modelValue', html)
    emitLength(instance)
  },
})

watch(
  () => props.modelValue,
  (value) => {
    if (!editor.value) return
    const next = value || '<p></p>'
    if (editor.value.getHTML() !== next) editor.value.commands.setContent(next, { emitUpdate: false })
    emitLength(editor.value)
  },
)

onBeforeUnmount(() => editor.value?.destroy())

function emitLength(instance) {
  emit('update:length', { text: instance.getText().trim().length, html: instance.getHTML().length })
}

function toggle(command) {
  const chain = editor.value?.chain().focus()
  if (chain) command(chain).run()
}

// Link and image details are entered in a small panel under the toolbar (no browser prompts).
const insertPanel = ref(null)
const panelUrl = ref('')
const panelAlt = ref('')
const panelError = ref('')
const panelUrlInput = ref(null)
let panelTrigger = null

async function openPanel(kind, event) {
  panelTrigger = event?.currentTarget || null
  insertPanel.value = kind
  panelError.value = ''
  panelUrl.value = kind === 'link' ? editor.value?.getAttributes('link').href || '' : ''
  panelAlt.value = ''
  await nextTick()
  panelUrlInput.value?.focus()
  panelUrlInput.value?.select()
}

function closePanel({ restoreFocus = true } = {}) {
  insertPanel.value = null
  panelError.value = ''
  if (restoreFocus) panelTrigger?.focus()
  panelTrigger = null
}

function applyPanel() {
  const url = panelUrl.value.trim()
  if (insertPanel.value === 'link') {
    if (!url) {
      editor.value?.chain().focus().extendMarkRange('link').unsetLink().run()
      closePanel({ restoreFocus: false })
      return
    }
    if (!/^https?:\/\//i.test(url)) {
      panelError.value = '链接需要以 http:// 或 https:// 开头。'
      return
    }
    editor.value?.chain().focus().extendMarkRange('link').setLink({ href: url }).run()
    closePanel({ restoreFocus: false })
    return
  }
  if (!/^https?:\/\//i.test(url)) {
    panelError.value = '图片地址需要以 http:// 或 https:// 开头。'
    return
  }
  const alt = panelAlt.value.trim()
  if (!alt) {
    panelError.value = '写一句图片说明，方便无法查看图片的读者理解内容。'
    return
  }
  editor.value?.chain().focus().setImage({ src: url, alt, title: alt }).run()
  closePanel({ restoreFocus: false })
}
</script>

<template>
  <div class="discussion-editor-shell" :class="{ compact }">
    <div v-if="editor" class="editor-toolbar discussion-editor-toolbar" role="toolbar" :aria-label="`${label}格式工具`">
      <button
        type="button"
        :aria-pressed="editor.isActive('bold')"
        aria-label="粗体"
        title="粗体"
        @click="toggle((chain) => chain.toggleBold())"
      >
        <Bold :size="17" aria-hidden="true" />
      </button>
      <button
        type="button"
        :aria-pressed="editor.isActive('italic')"
        aria-label="斜体"
        title="斜体"
        @click="toggle((chain) => chain.toggleItalic())"
      >
        <Italic :size="17" aria-hidden="true" />
      </button>
      <button
        type="button"
        :aria-pressed="editor.isActive('strike')"
        aria-label="删除线"
        title="删除线"
        @click="toggle((chain) => chain.toggleStrike())"
      >
        <Strikethrough :size="17" aria-hidden="true" />
      </button>
      <span aria-hidden="true"></span>
      <button
        type="button"
        :aria-pressed="editor.isActive('heading', { level: 2 })"
        aria-label="二级标题"
        title="二级标题"
        @click="toggle((chain) => chain.toggleHeading({ level: 2 }))"
      >
        H2
      </button>
      <button
        type="button"
        :aria-pressed="editor.isActive('heading', { level: 3 })"
        aria-label="三级标题"
        title="三级标题"
        @click="toggle((chain) => chain.toggleHeading({ level: 3 }))"
      >
        H3
      </button>
      <button
        type="button"
        :aria-pressed="editor.isActive('bulletList')"
        aria-label="无序列表"
        title="无序列表"
        @click="toggle((chain) => chain.toggleBulletList())"
      >
        <List :size="17" aria-hidden="true" />
      </button>
      <button
        type="button"
        :aria-pressed="editor.isActive('orderedList')"
        aria-label="有序列表"
        title="有序列表"
        @click="toggle((chain) => chain.toggleOrderedList())"
      >
        <ListOrdered :size="17" aria-hidden="true" />
      </button>
      <button
        type="button"
        :aria-pressed="editor.isActive('blockquote')"
        aria-label="引用"
        title="引用"
        @click="toggle((chain) => chain.toggleBlockquote())"
      >
        <Quote :size="17" aria-hidden="true" />
      </button>
      <button
        type="button"
        :aria-pressed="editor.isActive('codeBlock')"
        aria-label="代码块"
        title="代码块"
        @click="toggle((chain) => chain.toggleCodeBlock())"
      >
        <Braces :size="17" aria-hidden="true" />
      </button>
      <span aria-hidden="true"></span>
      <button
        type="button"
        :aria-pressed="editor.isActive('link')"
        aria-label="添加或修改链接"
        title="添加链接"
        :aria-expanded="insertPanel === 'link'"
        @click="(event) => (insertPanel === 'link' ? closePanel() : openPanel('link', event))"
      >
        <Link :size="17" aria-hidden="true" />
      </button>
      <button
        type="button"
        :disabled="!editor.isActive('link')"
        aria-label="移除链接"
        title="移除链接"
        @click="editor.chain().focus().unsetLink().run()"
      >
        <Unlink :size="17" aria-hidden="true" />
      </button>
      <button
        type="button"
        aria-label="插入网络图片"
        title="插入网络图片"
        :aria-expanded="insertPanel === 'image'"
        @click="(event) => (insertPanel === 'image' ? closePanel() : openPanel('image', event))"
      >
        <ImagePlus :size="17" aria-hidden="true" />
      </button>
      <span aria-hidden="true"></span>
      <button
        type="button"
        aria-label="撤销"
        title="撤销"
        :disabled="!editor.can().chain().focus().undo().run()"
        @click="editor.chain().focus().undo().run()"
      >
        <Undo2 :size="17" aria-hidden="true" />
      </button>
      <button
        type="button"
        aria-label="重做"
        title="重做"
        :disabled="!editor.can().chain().focus().redo().run()"
        @click="editor.chain().focus().redo().run()"
      >
        <Redo2 :size="17" aria-hidden="true" />
      </button>
    </div>
    <Transition name="reveal">
      <div
        v-if="insertPanel"
        class="editor-insert-panel"
        role="group"
        :aria-label="insertPanel === 'link' ? '添加链接' : '插入网络图片'"
        @keydown.esc.stop.prevent="closePanel()"
      >
        <label>
          {{ insertPanel === 'link' ? '链接地址' : '图片地址' }}
          <input
            ref="panelUrlInput"
            v-model="panelUrl"
            type="url"
            inputmode="url"
            placeholder="https://"
            @keydown.enter.prevent="applyPanel"
          />
        </label>
        <label v-if="insertPanel === 'image'">
          图片说明
          <input
            v-model="panelAlt"
            type="text"
            maxlength="120"
            placeholder="例如：机器狗在走廊避障"
            @keydown.enter.prevent="applyPanel"
          />
        </label>
        <div class="editor-insert-actions">
          <button type="button" class="portal-primary" @click="applyPanel">
            {{ insertPanel === 'link' ? (panelUrl.trim() ? '应用链接' : '移除链接') : '插入图片' }}
          </button>
          <button type="button" class="portal-secondary" @click="closePanel()">取消</button>
        </div>
        <p v-if="panelError" class="discussion-editor-error" role="alert">{{ panelError }}</p>
      </div>
    </Transition>
    <EditorContent :editor="editor" />
    <p v-if="validationMessage" class="discussion-editor-error" role="alert">{{ validationMessage }}</p>
    <p class="discussion-editor-help">
      支持格式化文字、链接、网络图片和代码块；内容会在服务器端进行安全清洗，最多 {{ maxLength }} 个 HTML 字符。
    </p>
  </div>
</template>

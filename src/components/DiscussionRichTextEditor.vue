<script setup>
import Image from '@tiptap/extension-image'
import StarterKit from '@tiptap/starter-kit'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import {
  Bold, Braces, ImagePlus, Italic, Link, List, ListOrdered, Quote, Redo2,
  Strikethrough, Undo2, Unlink,
} from 'lucide-vue-next'
import { onBeforeUnmount, ref, watch } from 'vue'

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

watch(() => props.modelValue, (value) => {
  if (!editor.value) return
  const next = value || '<p></p>'
  if (editor.value.getHTML() !== next) editor.value.commands.setContent(next, { emitUpdate: false })
  emitLength(editor.value)
})

onBeforeUnmount(() => editor.value?.destroy())

function emitLength(instance) {
  emit('update:length', { text: instance.getText().trim().length, html: instance.getHTML().length })
}

function toggle(command) {
  const chain = editor.value?.chain().focus()
  if (chain) command(chain).run()
}

function editLink() {
  const current = editor.value?.getAttributes('link').href || ''
  const value = window.prompt('请输入链接地址（仅支持 http 或 https）', current)
  if (value === null) return
  const href = value.trim()
  if (!href) {
    editor.value?.chain().focus().extendMarkRange('link').unsetLink().run()
    return
  }
  if (!/^https?:\/\//i.test(href)) {
    validationMessage.value = '链接必须以 http:// 或 https:// 开头。'
    return
  }
  editor.value?.chain().focus().extendMarkRange('link').setLink({ href }).run()
}

function insertImage() {
  const value = window.prompt('请输入图片地址（仅支持 http 或 https）')
  if (value === null) return
  const src = value.trim()
  if (!/^https?:\/\//i.test(src)) {
    validationMessage.value = '图片地址必须以 http:// 或 https:// 开头。'
    return
  }
  const alt = window.prompt('请输入图片说明，方便无法查看图片的用户理解内容')?.trim()
  if (!alt) {
    validationMessage.value = '请为图片填写说明文字。'
    return
  }
  editor.value?.chain().focus().setImage({ src, alt, title: alt }).run()
}
</script>

<template>
  <div class="discussion-editor-shell" :class="{ compact }">
    <div v-if="editor" class="editor-toolbar discussion-editor-toolbar" role="toolbar" :aria-label="`${label}格式工具`">
      <button type="button" :aria-pressed="editor.isActive('bold')" aria-label="粗体" title="粗体" @click="toggle(chain => chain.toggleBold())"><Bold :size="17" aria-hidden="true" /></button>
      <button type="button" :aria-pressed="editor.isActive('italic')" aria-label="斜体" title="斜体" @click="toggle(chain => chain.toggleItalic())"><Italic :size="17" aria-hidden="true" /></button>
      <button type="button" :aria-pressed="editor.isActive('strike')" aria-label="删除线" title="删除线" @click="toggle(chain => chain.toggleStrike())"><Strikethrough :size="17" aria-hidden="true" /></button>
      <span aria-hidden="true"></span>
      <button type="button" :aria-pressed="editor.isActive('heading', { level: 2 })" aria-label="二级标题" title="二级标题" @click="toggle(chain => chain.toggleHeading({ level: 2 }))">H2</button>
      <button type="button" :aria-pressed="editor.isActive('heading', { level: 3 })" aria-label="三级标题" title="三级标题" @click="toggle(chain => chain.toggleHeading({ level: 3 }))">H3</button>
      <button type="button" :aria-pressed="editor.isActive('bulletList')" aria-label="无序列表" title="无序列表" @click="toggle(chain => chain.toggleBulletList())"><List :size="17" aria-hidden="true" /></button>
      <button type="button" :aria-pressed="editor.isActive('orderedList')" aria-label="有序列表" title="有序列表" @click="toggle(chain => chain.toggleOrderedList())"><ListOrdered :size="17" aria-hidden="true" /></button>
      <button type="button" :aria-pressed="editor.isActive('blockquote')" aria-label="引用" title="引用" @click="toggle(chain => chain.toggleBlockquote())"><Quote :size="17" aria-hidden="true" /></button>
      <button type="button" :aria-pressed="editor.isActive('codeBlock')" aria-label="代码块" title="代码块" @click="toggle(chain => chain.toggleCodeBlock())"><Braces :size="17" aria-hidden="true" /></button>
      <span aria-hidden="true"></span>
      <button type="button" :aria-pressed="editor.isActive('link')" aria-label="添加或修改链接" title="添加链接" @click="editLink"><Link :size="17" aria-hidden="true" /></button>
      <button type="button" :disabled="!editor.isActive('link')" aria-label="移除链接" title="移除链接" @click="editor.chain().focus().unsetLink().run()"><Unlink :size="17" aria-hidden="true" /></button>
      <button type="button" aria-label="插入网络图片" title="插入网络图片" @click="insertImage"><ImagePlus :size="17" aria-hidden="true" /></button>
      <span aria-hidden="true"></span>
      <button type="button" aria-label="撤销" title="撤销" :disabled="!editor.can().chain().focus().undo().run()" @click="editor.chain().focus().undo().run()"><Undo2 :size="17" aria-hidden="true" /></button>
      <button type="button" aria-label="重做" title="重做" :disabled="!editor.can().chain().focus().redo().run()" @click="editor.chain().focus().redo().run()"><Redo2 :size="17" aria-hidden="true" /></button>
    </div>
    <EditorContent :editor="editor" />
    <p v-if="validationMessage" class="discussion-editor-error" role="alert">{{ validationMessage }}</p>
    <p class="discussion-editor-help">支持格式化文字、链接、网络图片和代码块；内容会在服务器端进行安全清洗，最多 {{ maxLength }} 个 HTML 字符。</p>
  </div>
</template>

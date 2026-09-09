<script setup>
import { Heart, MessageCircle, Pencil, Send, Trash2, X } from 'lucide-vue-next'
import { nextTick, onMounted, reactive, ref } from 'vue'
import PortalShell from '../components/PortalShell.vue'
import {
  createDiscussion, createDiscussionReply, deleteDiscussion, deleteDiscussionReply,
  listDiscussions, toggleDiscussionLike, toggleDiscussionReplyLike,
  updateDiscussion, updateDiscussionReply,
} from '../services/authApi'

const posts = ref([])
const loading = ref(true)
const working = ref(false)
const errorMessage = ref('')
const form = reactive({ title: '', content: '' })
const replyDrafts = reactive({})
const editingPostId = ref(null)
const editingPost = reactive({ title: '', content: '' })
const editingReplyId = ref(null)
const editingReplyContent = ref('')
const roleLabels = { TEACHER: '指导老师', CORE_STUDENT: '核心成员', MEMBER: '成员' }

onMounted(async () => {
  await refresh()
  await nextTick()
  if (window.location.hash) document.getElementById(window.location.hash.slice(1))?.scrollIntoView({ block: 'start' })
})

async function refresh() {
  loading.value = true
  try { posts.value = await listDiscussions() }
  catch (error) { errorMessage.value = error.message }
  finally { loading.value = false }
}

async function submitPost() {
  await run(async () => {
    const created = await createDiscussion({ title: form.title.trim(), content: form.content.trim() })
    posts.value.unshift(created)
    form.title = ''; form.content = ''
  })
}

function startPostEdit(post) {
  editingPostId.value = post.id
  editingPost.title = post.title
  editingPost.content = post.content
}

async function savePost(post) {
  await run(async () => {
    replacePost(await updateDiscussion(post.id, { title: editingPost.title.trim(), content: editingPost.content.trim() }))
    editingPostId.value = null
  })
}

async function removePost(post) {
  if (!window.confirm(`确认删除《${post.title}》及其全部回复吗？`)) return
  await run(async () => { await deleteDiscussion(post.id); posts.value = posts.value.filter(item => item.id !== post.id) })
}

async function likePost(post) {
  await run(async () => replacePost(await toggleDiscussionLike(post.id)))
}

async function submitReply(post) {
  const content = replyDrafts[post.id]?.trim()
  if (!content) return
  await run(async () => { replacePost(await createDiscussionReply(post.id, { content })); replyDrafts[post.id] = '' })
}

function startReplyEdit(reply) {
  editingReplyId.value = reply.id
  editingReplyContent.value = reply.content
}

async function saveReply(reply) {
  await run(async () => {
    replacePost(await updateDiscussionReply(reply.id, { content: editingReplyContent.value.trim() }))
    editingReplyId.value = null
  })
}

async function removeReply(reply) {
  if (!window.confirm('确认删除这条回复吗？')) return
  await run(async () => replacePost(await deleteDiscussionReply(reply.id)))
}

async function likeReply(reply) {
  await run(async () => replacePost(await toggleDiscussionReplyLike(reply.id)))
}

async function run(action) {
  working.value = true
  errorMessage.value = ''
  try { await action() }
  catch (error) { errorMessage.value = error.message }
  finally { working.value = false }
}

function replacePost(updated) {
  posts.value = posts.value.map(post => post.id === updated.id ? updated : post)
}

function formatTime(value) { return new Date(value).toLocaleString('zh-CN') }
</script>

<template>
  <PortalShell eyebrow="MEMBER / DISCUSSION" title="讨论板" description="分享进展、提出问题，和实验室成员一起交流。">
    <div v-if="errorMessage" class="form-alert" role="alert">{{ errorMessage }}</div>

    <section class="discussion-compose">
      <header><MessageCircle :size="22" aria-hidden="true" /><div><p>NEW TOPIC</p><h2>发起讨论</h2></div></header>
      <form @submit.prevent="submitPost">
        <label>标题<input v-model.trim="form.title" required maxlength="160" placeholder="用一句话说明讨论主题" /></label>
        <label>内容<textarea v-model.trim="form.content" required maxlength="5000" rows="5" placeholder="写下背景、问题或希望大家回应的内容。" /></label>
        <div><small>{{ form.content.length }} / 5000</small><button class="portal-primary" type="submit" :disabled="working"><Send :size="17" />发布讨论</button></div>
      </form>
    </section>

    <div v-if="loading" class="portal-state">正在读取讨论…</div>
    <div v-else-if="!posts.length" class="portal-state">还没有讨论，来发布第一条吧。</div>
    <section v-else class="discussion-list" aria-label="讨论列表">
      <article v-for="post in posts" :id="`post-${post.id}`" :key="post.id" class="discussion-post">
        <header class="discussion-author"><span>{{ post.author.name.slice(0, 1) }}</span><div><strong>{{ post.author.name }}</strong><small>{{ roleLabels[post.author.role] }} · {{ formatTime(post.createdAt) }}</small></div></header>

        <form v-if="editingPostId === post.id" class="discussion-edit-form" @submit.prevent="savePost(post)">
          <label>标题<input v-model.trim="editingPost.title" required maxlength="160" /></label>
          <label>内容<textarea v-model.trim="editingPost.content" required maxlength="5000" rows="5" /></label>
          <div><button type="button" @click="editingPostId = null"><X :size="16" />取消</button><button class="portal-primary" type="submit" :disabled="working">保存修改</button></div>
        </form>
        <template v-else><h2>{{ post.title }}</h2><p class="discussion-content">{{ post.content }}</p></template>

        <div class="discussion-actions">
          <button type="button" :class="{ active: post.likedByMe }" :disabled="working || post.canEdit" :aria-pressed="post.likedByMe" @click="likePost(post)"><Heart :size="17" :fill="post.likedByMe ? 'currentColor' : 'none'" />{{ post.likeCount }}</button>
          <span><MessageCircle :size="17" />{{ post.replies.length }} 条回复</span>
          <button v-if="post.canEdit" type="button" @click="startPostEdit(post)"><Pencil :size="16" />编辑</button>
          <button v-if="post.canDelete" class="danger" type="button" @click="removePost(post)"><Trash2 :size="16" />删除</button>
        </div>

        <section v-if="post.replies.length" class="discussion-replies" :aria-label="`${post.title}的回复`">
          <article v-for="reply in post.replies" :key="reply.id">
            <header><span>{{ reply.author.name.slice(0, 1) }}</span><div><strong>{{ reply.author.name }}</strong><small>{{ roleLabels[reply.author.role] }} · {{ formatTime(reply.createdAt) }}</small></div></header>
            <form v-if="editingReplyId === reply.id" @submit.prevent="saveReply(reply)"><textarea v-model.trim="editingReplyContent" required maxlength="2000" rows="3" /><div><button type="button" @click="editingReplyId = null">取消</button><button type="submit" :disabled="working">保存</button></div></form>
            <p v-else>{{ reply.content }}</p>
            <div class="reply-actions"><button type="button" :class="{ active: reply.likedByMe }" :disabled="working || reply.canEdit" :aria-pressed="reply.likedByMe" @click="likeReply(reply)"><Heart :size="15" :fill="reply.likedByMe ? 'currentColor' : 'none'" />{{ reply.likeCount }}</button><button v-if="reply.canEdit" type="button" @click="startReplyEdit(reply)">编辑</button><button v-if="reply.canDelete" class="danger" type="button" @click="removeReply(reply)">删除</button></div>
          </article>
        </section>

        <form class="discussion-reply-form" @submit.prevent="submitReply(post)"><label :for="`reply-${post.id}`">回复</label><div><textarea :id="`reply-${post.id}`" v-model.trim="replyDrafts[post.id]" required maxlength="2000" rows="2" placeholder="写下你的回复…" /><button type="submit" :disabled="working || !replyDrafts[post.id]?.trim()" aria-label="发送回复"><Send :size="18" /></button></div></form>
      </article>
    </section>
  </PortalShell>
</template>

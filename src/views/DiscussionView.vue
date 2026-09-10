<script setup>
import { ArrowDownWideNarrow, ChevronDown, ChevronUp, Heart, LockKeyhole, Megaphone, MessageCircle, Pencil, Pin, PinOff, Search, Send, Trash2, UserPlus, X } from 'lucide-vue-next'
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DiscussionCollapsibleContent from '../components/DiscussionCollapsibleContent.vue'
import DiscussionRichTextEditor from '../components/DiscussionRichTextEditor.vue'
import PortalShell from '../components/PortalShell.vue'
import {
  authState, createDiscussion, createDiscussionReply, deleteDiscussion, deleteDiscussionReply,
  listDiscussions, toggleDiscussionLike, toggleDiscussionPin, toggleDiscussionReplyLike,
  updateDiscussion, updateDiscussionReply,
} from '../services/authApi'

const router = useRouter()
const route = useRoute()
const posts = ref([])
const loading = ref(true)
const working = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const form = reactive({ title: '', content: '', announcement: false, length: { text: 0, html: 0 } })
const replyDrafts = reactive({})
const replyLengths = reactive({})
const replyingPostId = ref(null)
const editingPostId = ref(null)
const editingPost = reactive({ title: '', content: '', length: { text: 0, html: 0 } })
const editingReplyId = ref(null)
const editingReply = reactive({ content: '', length: { text: 0, html: 0 } })
const gate = reactive({ open: false, guest: false, title: '', message: '' })
const gatePrimary = ref(null)
const pinsExpanded = ref(false)
const searchQuery = ref('')
let gateReturnFocus = null

const participatingRoles = ['TEACHER', 'CORE_STUDENT', 'MEMBER']
const sortOptions = [
  { value: 'NEWEST', label: '最新发布' },
  { value: 'OLDEST', label: '最早发布' },
  { value: 'ID_ASC', label: '编号从小到大' },
  { value: 'ID_DESC', label: '编号从大到小' },
  { value: 'MOST_LIKED', label: '点赞最多' },
  { value: 'MOST_REPLIED', label: '回复最多' },
]
const validSortModes = new Set(sortOptions.map(option => option.value))
const sortMode = ref(validSortModes.has(route.query.sort) ? route.query.sort : 'NEWEST')
const canParticipate = computed(() => participatingRoles.includes(authState.account?.role))
const pinnedPosts = computed(() => posts.value.filter(post => post.pinned)
  .sort((left, right) => new Date(right.pinnedAt) - new Date(left.pinnedAt) || right.contentNumber - left.contentNumber))
const filteredPosts = computed(() => {
  const terms = searchQuery.value.trim().toLocaleLowerCase('zh-CN').split(/\s+/).filter(Boolean)
  if (!terms.length) return posts.value
  return posts.value.filter(post => {
    const number = String(post.contentNumber)
    const paddedNumber = `d${number.padStart(6, '0')}`
    const text = `${post.title} ${plainText(post.content)} ${number} ${paddedNumber} #${paddedNumber}`
      .toLocaleLowerCase('zh-CN')
    return terms.every(term => text.includes(term))
  })
})
const roleLabels = { TEACHER: '指导老师', CORE_STUDENT: '核心成员', MEMBER: '成员' }

onMounted(async () => {
  await refresh()
  await nextTick()
  if (window.location.hash) document.getElementById(window.location.hash.slice(1))?.scrollIntoView({ block: 'start' })
})

async function refresh() {
  loading.value = true
  errorMessage.value = ''
  try { posts.value = await listDiscussions(sortMode.value) }
  catch (error) { errorMessage.value = error.message }
  finally { loading.value = false }
}

async function submitPost() {
  if (!requireParticipation()) return
  if (!validRichContent(form.content, form.length, 5000)) return
  if (form.announcement && !window.confirm('公告发布后不能修改，并会由梅琳娜推送给所有站内账号。确认发布吗？')) return
  await run(async () => {
    const created = await createDiscussion({ title: form.title.trim(), content: form.content, announcement: form.announcement })
    posts.value.push(created)
    sortPosts()
    form.title = ''; form.content = ''; form.announcement = false; form.length = { text: 0, html: 0 }
    successMessage.value = created.announcement ? '公告已发布，梅琳娜正在向站内账号发送通知。' : '讨论已发布。'
  })
}

function startPostEdit(post) {
  if (!requireParticipation()) return
  editingPostId.value = post.id
  editingPost.title = post.title
  editingPost.content = post.content
  editingPost.length = { text: 0, html: post.content.length }
}

async function savePost(post) {
  if (!validRichContent(editingPost.content, editingPost.length, 5000)) return
  await run(async () => {
    replacePost(await updateDiscussion(post.id, { title: editingPost.title.trim(), content: editingPost.content }))
    editingPostId.value = null
    successMessage.value = '讨论内容已保存。'
  })
}

async function removePost(post) {
  if (!requireParticipation()) return
  if (!window.confirm(`确认删除《${post.title}》及其全部回复吗？`)) return
  await run(async () => { await deleteDiscussion(post.id); posts.value = posts.value.filter(item => item.id !== post.id); successMessage.value = '讨论及其回复已删除。' })
}

async function likePost(post) {
  if (!requireParticipation()) return
  await run(async () => replacePost(await toggleDiscussionLike(post.id)))
}

async function pinPost(post) {
  await run(async () => replacePost(await toggleDiscussionPin(post.id)))
}

function openReply(post, event) {
  if (!requireParticipation(event)) return
  replyingPostId.value = replyingPostId.value === post.id ? null : post.id
  if (replyDrafts[post.id] === undefined) replyDrafts[post.id] = ''
}

async function submitReply(post) {
  const content = replyDrafts[post.id] || ''
  if (!validRichContent(content, replyLengths[post.id], 2000)) return
  await run(async () => {
    replacePost(await createDiscussionReply(post.id, { content }))
    replyDrafts[post.id] = ''
    replyLengths[post.id] = { text: 0, html: 0 }
    replyingPostId.value = null
    successMessage.value = '回复已发布。'
  })
}

function startReplyEdit(reply) {
  if (!requireParticipation()) return
  editingReplyId.value = reply.id
  editingReply.content = reply.content
  editingReply.length = { text: 0, html: reply.content.length }
}

async function saveReply(reply) {
  if (!validRichContent(editingReply.content, editingReply.length, 2000)) return
  await run(async () => {
    replacePost(await updateDiscussionReply(reply.id, { content: editingReply.content }))
    editingReplyId.value = null
    successMessage.value = '回复内容已保存。'
  })
}

async function removeReply(reply) {
  if (!requireParticipation()) return
  if (!window.confirm('确认删除这条回复吗？')) return
  await run(async () => { replacePost(await deleteDiscussionReply(reply.id)); successMessage.value = '回复已删除。' })
}

async function likeReply(reply) {
  if (!requireParticipation()) return
  await run(async () => replacePost(await toggleDiscussionReplyLike(reply.id)))
}

async function run(action) {
  working.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try { await action() }
  catch (error) { errorMessage.value = error.message }
  finally { working.value = false }
}

function validRichContent(content, length = {}, maxLength) {
  const hasImage = /<img\b/i.test(content || '')
  if (!length?.text && !hasImage) {
    errorMessage.value = '请输入有效的讨论内容。'
    return false
  }
  if ((length?.html || content?.length || 0) > maxLength) {
    errorMessage.value = `内容不能超过 ${maxLength} 个 HTML 字符。`
    return false
  }
  return true
}

function requireParticipation(event) {
  if (canParticipate.value) return true
  gateReturnFocus = event?.currentTarget || document.activeElement
  gate.guest = !authState.account
  gate.title = gate.guest ? '请登录或注册' : '暂时无权限'
  gate.message = gate.guest
    ? '讨论内容可以公开浏览；发布、回复和点赞需要先注册或登录。'
    : '当前账号尚未成为正式成员，暂时不能发布、回复或点赞。请完成面试及后续招新流程。'
  gate.open = true
  nextTick(() => gatePrimary.value?.focus())
  return false
}

function closeGate(restoreFocus = true) {
  gate.open = false
  if (restoreFocus) nextTick(() => gateReturnFocus?.focus?.())
}

async function goRegister() {
  closeGate(false)
  await router.push({ path: '/register', query: { redirect: '/discussions' } })
}

function replacePost(updated) {
  posts.value = posts.value.map(post => post.id === updated.id ? updated : post)
  sortPosts()
}

async function changeSort() {
  await router.replace({ query: { ...route.query, sort: sortMode.value === 'NEWEST' ? undefined : sortMode.value } })
  await refresh()
}

function sortPosts() {
  const newest = (left, right) => new Date(right.createdAt) - new Date(left.createdAt) || right.contentNumber - left.contentNumber
  const comparators = {
    NEWEST: newest,
    OLDEST: (left, right) => new Date(left.createdAt) - new Date(right.createdAt) || left.contentNumber - right.contentNumber,
    ID_ASC: (left, right) => left.contentNumber - right.contentNumber,
    ID_DESC: (left, right) => right.contentNumber - left.contentNumber,
    MOST_LIKED: (left, right) => right.likeCount - left.likeCount || newest(left, right),
    MOST_REPLIED: (left, right) => right.replies.length - left.replies.length || newest(left, right),
  }
  posts.value = [...posts.value].sort(comparators[sortMode.value] || newest)
}

function authorPath(author) { return author.profileId ? `/members/${author.profileId}` : null }
function contentNumber(value) { return `#D${String(value).padStart(6, '0')}` }
function formatTime(value) { return new Date(value).toLocaleString('zh-CN') }
function plainText(value) { return (value || '').replace(/<[^>]*>/g, ' ').replace(/&nbsp;/gi, ' ').replace(/\s+/g, ' ') }
</script>

<template>
  <PortalShell eyebrow="PUBLIC / DISCUSSION" title="讨论板">
    <div v-if="successMessage" class="save-message" role="status">{{ successMessage }}</div>
    <div v-if="errorMessage" class="form-alert" role="alert">{{ errorMessage }}</div>

    <section class="discussion-compose">
      <header><MessageCircle :size="22" aria-hidden="true" /><div><p>NEW TOPIC</p><h2>发起讨论</h2></div></header>
      <form v-if="canParticipate" @submit.prevent="submitPost">
        <label>标题<input v-model.trim="form.title" required maxlength="160" placeholder="用一句话说明讨论主题" /></label>
        <label>内容</label>
        <DiscussionRichTextEditor v-model="form.content" label="新讨论内容" :max-length="5000" @update:length="form.length = $event" />
        <label class="discussion-announcement-option"><input v-model="form.announcement" type="checkbox" /><span><strong><Megaphone :size="17" aria-hidden="true" />作为公告发布</strong><small>梅琳娜会向所有站内账号推送；公告发布后正文和标题均不可修改。</small></span></label>
        <div><small>{{ form.length.html }} / 5000 HTML 字符</small><button class="portal-primary" type="submit" :disabled="working || !form.title.trim()"><Send :size="17" aria-hidden="true" />发布讨论</button></div>
      </form>
      <button v-else class="discussion-compose-gate" type="button" @click="requireParticipation($event)">
        <LockKeyhole :size="22" aria-hidden="true" /><span><strong>{{ authState.account ? '成员讨论权限暂未开放' : '登录后参与讨论' }}</strong><small>{{ authState.account ? '完成招新流程并成为正式成员后即可发布、回复和点赞。' : '讨论内容公开可见，注册或登录后可参与互动。' }}</small></span>
      </button>
    </section>

    <section v-if="!loading && pinnedPosts.length" class="discussion-pinned" aria-labelledby="discussion-pinned-title">
      <header><span><Pin :size="20" aria-hidden="true" /></span><div><p>PINNED</p><h2 id="discussion-pinned-title">置顶内容</h2></div><b>{{ pinnedPosts.length }}</b></header>
      <button v-if="pinnedPosts.length > 1" class="discussion-pinned-toggle" type="button" aria-controls="discussion-pinned-list" :aria-expanded="pinsExpanded" @click="pinsExpanded = !pinsExpanded"><span><strong>{{ pinnedPosts.length }} 条置顶内容</strong><small>{{ pinsExpanded ? '点击收起置顶列表' : '已折叠，点击查看全部' }}</small></span><ChevronUp v-if="pinsExpanded" :size="20" aria-hidden="true" /><ChevronDown v-else :size="20" aria-hidden="true" /></button>
      <div v-show="pinnedPosts.length === 1 || pinsExpanded" id="discussion-pinned-list" class="discussion-pinned-list">
        <article v-for="post in pinnedPosts" :key="`pinned-${post.id}`">
          <div class="discussion-pinned-meta"><span v-if="post.announcement"><Megaphone :size="15" aria-hidden="true" />公告</span><b>{{ contentNumber(post.contentNumber) }}</b><small>{{ post.author.name }} · {{ formatTime(post.createdAt) }}</small></div>
          <h3><a :href="`#post-${post.id}`">{{ post.title }}</a></h3>
          <DiscussionCollapsibleContent :html="post.content" :max-height="180" label="置顶内容" compact />
          <a class="discussion-pinned-jump" :href="`#post-${post.id}`">查看完整讨论</a>
        </article>
      </div>
    </section>

    <section class="discussion-browser-bar" aria-labelledby="discussion-browser-title">
      <div><h2 id="discussion-browser-title">浏览讨论</h2></div>
      <div class="discussion-browser-controls">
        <label class="discussion-search"><Search :size="18" aria-hidden="true" /><span class="sr-only">搜索讨论</span><input v-model="searchQuery" type="search" placeholder="搜索标题、正文或编号" autocomplete="off" /></label>
        <label class="discussion-sort"><ArrowDownWideNarrow :size="18" aria-hidden="true" /><span>排序方式</span><select v-model="sortMode" :disabled="loading" @change="changeSort"><option v-for="option in sortOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select></label>
      </div>
    </section>

    <div v-if="loading" class="portal-state">正在读取讨论…</div>
    <div v-else-if="!posts.length" class="portal-state">还没有讨论。</div>
    <div v-else-if="!filteredPosts.length" class="portal-state discussion-search-empty">没有找到相关讨论。<button type="button" @click="searchQuery = ''">清除搜索</button></div>
    <section v-else class="discussion-list" aria-label="讨论列表">
      <article v-for="post in filteredPosts" :id="`post-${post.id}`" :key="post.id" :class="['discussion-post', { 'is-announcement': post.announcement, 'is-pinned': post.pinned }]">
        <header class="discussion-author">
          <RouterLink v-if="authorPath(post.author)" class="discussion-author-link" :to="authorPath(post.author)" :aria-label="`查看${post.author.name}的个人主页`">
            <span class="discussion-avatar"><img v-if="post.author.avatarUrl" :src="post.author.avatarUrl" alt="" loading="lazy" /><b v-else>{{ post.author.name.slice(0, 1) }}</b></span>
            <span class="discussion-author-copy"><strong>{{ post.author.name }}</strong><small>{{ roleLabels[post.author.role] || '成员' }} · {{ formatTime(post.createdAt) }}</small></span>
          </RouterLink>
          <span v-else class="discussion-author-link"><span class="discussion-avatar"><b>{{ post.author.name.slice(0, 1) }}</b></span><span class="discussion-author-copy"><strong>{{ post.author.name }}</strong><small>{{ roleLabels[post.author.role] || '成员' }} · {{ formatTime(post.createdAt) }}</small></span></span>
          <a class="discussion-content-number" :href="`#post-${post.id}`" :aria-label="`讨论编号${post.contentNumber}`">{{ contentNumber(post.contentNumber) }}</a>
        </header>

        <div v-if="post.announcement || post.pinned" class="discussion-post-flags"><span v-if="post.announcement"><Megaphone :size="15" aria-hidden="true" />公告</span><span v-if="post.pinned"><Pin :size="15" aria-hidden="true" />已置顶</span></div>

        <form v-if="editingPostId === post.id" class="discussion-edit-form" @submit.prevent="savePost(post)">
          <label>标题<input v-model.trim="editingPost.title" required maxlength="160" /></label>
          <label>内容</label>
          <DiscussionRichTextEditor v-model="editingPost.content" label="编辑讨论内容" :max-length="5000" @update:length="editingPost.length = $event" />
          <div><button type="button" @click="editingPostId = null"><X :size="16" aria-hidden="true" />取消</button><button class="portal-primary" type="submit" :disabled="working">保存修改</button></div>
        </form>
        <template v-else><h2>{{ post.title }}</h2><DiscussionCollapsibleContent :html="post.content" label="全文" /></template>

        <div class="discussion-actions">
          <button type="button" :class="{ active: post.likedByMe }" :disabled="working || post.canEdit" :aria-pressed="post.likedByMe" :title="post.canEdit ? '不能给自己的讨论点赞' : '点赞'" @click="likePost(post)"><Heart :size="17" :fill="post.likedByMe ? 'currentColor' : 'none'" aria-hidden="true" />{{ post.likeCount }}</button>
          <span><MessageCircle :size="17" aria-hidden="true" />{{ post.replies.length }} 条回复</span>
          <button v-if="post.canEdit" type="button" @click="startPostEdit(post)"><Pencil :size="16" aria-hidden="true" />编辑</button>
          <button v-if="post.canPin" type="button" :class="{ active: post.pinned }" :aria-pressed="post.pinned" @click="pinPost(post)"><PinOff v-if="post.pinned" :size="16" aria-hidden="true" /><Pin v-else :size="16" aria-hidden="true" />{{ post.pinned ? '取消置顶' : '置顶' }}</button>
          <button v-if="post.canDelete" class="danger" type="button" @click="removePost(post)"><Trash2 :size="16" aria-hidden="true" />删除</button>
        </div>

        <section v-if="post.replies.length" class="discussion-replies" :aria-label="`${post.title}的回复`">
          <article v-for="reply in post.replies" :id="`content-${reply.contentNumber}`" :key="reply.id">
            <header>
              <RouterLink v-if="authorPath(reply.author)" class="discussion-author-link" :to="authorPath(reply.author)" :aria-label="`查看${reply.author.name}的个人主页`"><span class="discussion-avatar"><img v-if="reply.author.avatarUrl" :src="reply.author.avatarUrl" alt="" loading="lazy" /><b v-else>{{ reply.author.name.slice(0, 1) }}</b></span><span class="discussion-author-copy"><strong>{{ reply.author.name }}</strong><small>{{ roleLabels[reply.author.role] || '成员' }} · {{ formatTime(reply.createdAt) }}</small></span></RouterLink>
              <span v-else class="discussion-author-link"><span class="discussion-avatar"><b>{{ reply.author.name.slice(0, 1) }}</b></span><span class="discussion-author-copy"><strong>{{ reply.author.name }}</strong><small>{{ roleLabels[reply.author.role] || '成员' }} · {{ formatTime(reply.createdAt) }}</small></span></span>
              <a class="discussion-content-number" :href="`#content-${reply.contentNumber}`" :aria-label="`回复编号${reply.contentNumber}`">{{ contentNumber(reply.contentNumber) }}</a>
            </header>
            <form v-if="editingReplyId === reply.id" @submit.prevent="saveReply(reply)">
              <DiscussionRichTextEditor v-model="editingReply.content" label="编辑回复内容" :max-length="2000" compact @update:length="editingReply.length = $event" />
              <div><button type="button" @click="editingReplyId = null">取消</button><button type="submit" :disabled="working">保存</button></div>
            </form>
            <DiscussionCollapsibleContent v-else :html="reply.content" :max-height="180" label="回复" compact />
            <div class="reply-actions"><button type="button" :class="{ active: reply.likedByMe }" :disabled="working || reply.canEdit" :aria-pressed="reply.likedByMe" :title="reply.canEdit ? '不能给自己的回复点赞' : '点赞'" @click="likeReply(reply)"><Heart :size="15" :fill="reply.likedByMe ? 'currentColor' : 'none'" aria-hidden="true" />{{ reply.likeCount }}</button><button v-if="reply.canEdit" type="button" @click="startReplyEdit(reply)">编辑</button><button v-if="reply.canDelete" class="danger" type="button" @click="removeReply(reply)">删除</button></div>
          </article>
        </section>

        <section class="discussion-reply-form" aria-label="回复讨论">
          <button v-if="replyingPostId !== post.id" type="button" class="discussion-reply-open" @click="openReply(post, $event)"><MessageCircle :size="17" aria-hidden="true" />参与回复</button>
          <form v-else @submit.prevent="submitReply(post)">
            <label>回复内容</label>
            <DiscussionRichTextEditor v-model="replyDrafts[post.id]" :label="`回复《${post.title}》`" :max-length="2000" compact @update:length="replyLengths[post.id] = $event" />
            <div><button type="button" @click="replyingPostId = null">取消</button><button class="portal-primary" type="submit" :disabled="working"><Send :size="17" aria-hidden="true" />发送回复</button></div>
          </form>
        </section>
      </article>
    </section>

    <div v-if="gate.open" class="discussion-gate-overlay" @click.self="closeGate()" @keydown.esc="closeGate()">
      <section class="discussion-gate-dialog" role="dialog" aria-modal="true" aria-labelledby="discussion-gate-title" aria-describedby="discussion-gate-message">
        <header><span><UserPlus v-if="gate.guest" :size="21" aria-hidden="true" /><LockKeyhole v-else :size="21" aria-hidden="true" /></span><button type="button" aria-label="关闭提示" @click="closeGate()"><X :size="18" aria-hidden="true" /></button></header>
        <h2 id="discussion-gate-title">{{ gate.title }}</h2>
        <p id="discussion-gate-message">{{ gate.message }}</p>
        <div><button type="button" @click="closeGate()">暂不参与</button><button v-if="gate.guest" ref="gatePrimary" class="portal-primary" type="button" @click="goRegister">前往注册</button><button v-else ref="gatePrimary" class="portal-primary" type="button" @click="closeGate()">我知道了</button></div>
      </section>
    </div>
  </PortalShell>
</template>

<script setup>
import { ArrowUpRight, CheckCheck, Inbox, Mail, MailOpen } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PortalShell from '../components/PortalShell.vue'
import { getNotifications, markAllNotificationsRead, markNotificationRead } from '../services/authApi'

const router = useRouter()
const inbox = ref({ unreadCount: 0, messages: [] })
const loading = ref(true)
const working = ref(false)
const errorMessage = ref('')
const filter = ref('ALL')

const filteredMessages = computed(() => filter.value === 'UNREAD'
  ? inbox.value.messages.filter(message => !message.read)
  : inbox.value.messages)
const readCount = computed(() => Math.max(0, inbox.value.messages.length - inbox.value.unreadCount))

onMounted(async () => {
  await refresh()
  window.addEventListener('yeslab:notifications-updated', refresh)
})

onBeforeUnmount(() => window.removeEventListener('yeslab:notifications-updated', refresh))

async function refresh() {
  try {
    inbox.value = await getNotifications() || { unreadCount: 0, messages: [] }
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

async function openMessage(message) {
  working.value = true
  errorMessage.value = ''
  try {
    if (!message.read) {
      inbox.value = await markNotificationRead(message.id)
      window.dispatchEvent(new CustomEvent('yeslab:notifications-updated'))
    }
    if (message.targetPath) await router.push(message.targetPath)
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    working.value = false
  }
}

async function readAll() {
  working.value = true
  errorMessage.value = ''
  try {
    inbox.value = await markAllNotificationsRead()
    window.dispatchEvent(new CustomEvent('yeslab:notifications-updated'))
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    working.value = false
  }
}

function formatTime(value) {
  return new Date(value).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false,
  })
}
</script>

<template>
  <PortalShell eyebrow="INBOX / MELINA" title="站内信箱" description="查看梅琳娜送达的流程提醒、审核结果与团队通知。未读消息会保留醒目标记，已读历史也可以随时回看。">
    <section class="inbox-summary" aria-label="信箱概览">
      <article><span><Inbox :size="20" aria-hidden="true" /></span><div><small>全部消息</small><strong>{{ inbox.messages.length }}</strong></div></article>
      <article><span><Mail :size="20" aria-hidden="true" /></span><div><small>未读消息</small><strong>{{ inbox.unreadCount }}</strong></div></article>
      <article><span><MailOpen :size="20" aria-hidden="true" /></span><div><small>已读消息</small><strong>{{ readCount }}</strong></div></article>
    </section>

    <section class="inbox-card" aria-labelledby="inbox-list-title">
      <header>
        <div><p>MESSAGE ARCHIVE</p><h2 id="inbox-list-title">收件记录</h2></div>
        <div class="inbox-toolbar">
          <div class="inbox-filters" aria-label="筛选消息">
            <button type="button" :class="{ active: filter === 'ALL' }" :aria-pressed="filter === 'ALL'" @click="filter = 'ALL'">全部</button>
            <button type="button" :class="{ active: filter === 'UNREAD' }" :aria-pressed="filter === 'UNREAD'" @click="filter = 'UNREAD'">未读 {{ inbox.unreadCount }}</button>
          </div>
          <button v-if="inbox.unreadCount" class="inbox-read-all" type="button" :disabled="working" @click="readAll"><CheckCheck :size="17" aria-hidden="true" />全部标为已读</button>
        </div>
      </header>

      <div v-if="errorMessage" class="form-alert" role="alert">{{ errorMessage }}</div>
      <div v-if="loading" class="portal-state">正在整理站内消息…</div>
      <div v-else-if="filteredMessages.length" class="inbox-message-list">
        <article v-for="message in filteredMessages" :key="message.id" :class="{ unread: !message.read }">
          <span class="inbox-message-state" aria-hidden="true"><Mail v-if="!message.read" :size="18" /><MailOpen v-else :size="18" /></span>
          <div class="inbox-message-copy">
            <div><strong>{{ message.title }}</strong><span>{{ message.read ? '已读' : '未读' }}</span></div>
            <p>{{ message.summary }}</p>
            <small>{{ message.senderName }} · {{ formatTime(message.createdAt) }}</small>
          </div>
          <button type="button" :disabled="working" @click="openMessage(message)">
            {{ message.targetPath ? '查看相关内容' : message.read ? '查看消息' : '标为已读' }}
            <ArrowUpRight v-if="message.targetPath" :size="16" aria-hidden="true" />
          </button>
        </article>
      </div>
      <div v-else class="inbox-empty">
        <MailOpen :size="26" aria-hidden="true" />
        <strong>{{ filter === 'UNREAD' ? '没有未读消息' : '信箱还是空的' }}</strong>
        <p>{{ filter === 'UNREAD' ? '新的流程提醒会继续由梅琳娜送到这里。' : '审核结果、面试安排和团队提醒会显示在这里。' }}</p>
      </div>
    </section>
  </PortalShell>
</template>

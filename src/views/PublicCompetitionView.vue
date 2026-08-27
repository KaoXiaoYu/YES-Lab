<script setup>
import { ArrowLeft, CalendarDays, ExternalLink, FolderKanban, ImageOff, LoaderCircle, Medal, ShieldCheck, UsersRound } from 'lucide-vue-next'
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { fetchPublicCompetition } from '../services/publicApi'

const route = useRoute(); const item = ref(null); const loading = ref(true); const errorMessage = ref('')
const certificateState = ref('loading'); const imageStates = reactive({})
const levelLabels = { SCHOOL: '校级', PROVINCIAL: '省级', REGIONAL: '赛区', NATIONAL: '国家级', INTERNATIONAL: '国际级', OTHER: '其他' }
function setImageState(id, state) { imageStates[id] = state }
onMounted(async () => { try { item.value = await fetchPublicCompetition(route.params.competitionId) } catch { errorMessage.value = '该比赛成果尚未公开或服务暂不可用。' } finally { loading.value = false } })
</script>

<template>
  <div class="public-competition-page">
    <header class="public-result-topbar"><RouterLink to="/" class="public-back-link"><ArrowLeft :size="17" aria-hidden="true" />返回 YES Lab 首页</RouterLink><img src="/yes-lab-logo.png" alt="YES Lab" width="900" height="506" /></header>
    <main v-if="item" class="public-result-main">
      <header class="public-result-hero"><p>COMPETITION RESULT / {{ levelLabels[item.level] }}</p><h1>{{ item.name }}</h1><span>{{ item.track || '综合赛道' }}</span><strong><Medal :size="24" aria-hidden="true" />{{ item.awardName }}</strong></header>
      <section v-if="item.certificateUrl" class="public-result-certificate" aria-labelledby="certificate-title"><header><p>VERIFIED CERTIFICATE</p><h2 id="certificate-title">获奖证书</h2><span>{{ item.certificateOriginalName }}</span></header><figure><div v-if="certificateState === 'loading'" class="public-image-status" role="status"><LoaderCircle :size="24" aria-hidden="true" />证书图片加载中…</div><div v-else-if="certificateState === 'error'" class="public-image-status error" role="alert"><ImageOff :size="24" aria-hidden="true" /><span>证书预览加载失败，可<a :href="item.certificateUrl" target="_blank" rel="noopener noreferrer">在新窗口重试</a>。</span></div><img v-if="item.certificateContentType?.startsWith('image/')" :class="{ 'is-ready': certificateState === 'loaded' }" :src="item.certificateUrl" :alt="`${item.name}获奖证书`" loading="eager" decoding="async" fetchpriority="high" @load="certificateState = 'loaded'" @error="certificateState = 'error'" /><object v-else :class="{ 'is-ready': certificateState === 'loaded' }" :data="item.certificateUrl" type="application/pdf" :aria-label="`${item.name}获奖证书 PDF`" @load="certificateState = 'loaded'" @error="certificateState = 'error'"><p>当前浏览器无法直接预览证书 PDF，可<a :href="item.certificateUrl" target="_blank" rel="noopener noreferrer">在新窗口打开</a>。</p></object></figure></section>
      <section v-if="item.images.length" class="public-result-gallery"><figure v-for="image in item.images" :key="image.id"><div v-if="imageStates[image.id] !== 'loaded'" class="public-image-status" :class="{ error: imageStates[image.id] === 'error' }" :role="imageStates[image.id] === 'error' ? 'alert' : 'status'"><ImageOff v-if="imageStates[image.id] === 'error'" :size="22" aria-hidden="true" /><LoaderCircle v-else :size="22" aria-hidden="true" />{{ imageStates[image.id] === 'error' ? '图片加载失败' : '图片加载中…' }}</div><img :class="{ 'is-ready': imageStates[image.id] === 'loaded' }" :src="image.url" :alt="image.description" loading="lazy" decoding="async" @load="setImageState(image.id, 'loaded')" @error="setImageState(image.id, 'error')" /><figcaption>{{ image.description }}</figcaption></figure></section>
      <div class="public-result-layout"><article class="public-result-story"><p>ABOUT THE RESULT</p><h2>比赛记录</h2><div>{{ item.description }}</div></article><aside class="public-result-facts"><div><CalendarDays :size="18" aria-hidden="true" /><span>比赛时间<strong>{{ item.competitionDate }}</strong></span></div><div><UsersRound :size="18" aria-hidden="true" /><span>队长<strong>{{ item.captain.name }}</strong></span></div><div><ShieldCheck :size="18" aria-hidden="true" /><span>指导老师<strong>{{ item.advisorName || '未关联' }}</strong></span></div><div v-if="item.project"><FolderKanban :size="18" aria-hidden="true" /><span>关联项目<strong>{{ item.project.name }}</strong></span></div></aside></div>
      <section class="public-result-team"><header><p>TEAM MEMBERS</p><h2>参赛成员</h2></header><div><component :is="member.linkedProfileId ? 'RouterLink' : 'div'" v-for="member in item.participants" :key="`${member.displayName}-${member.linkedProfileId}`" :to="member.linkedProfileId ? `/members/${member.linkedProfileId}` : undefined" :class="{ unlinked: !member.linkedProfileId }"><span>{{ member.displayName.slice(0, 1) }}</span><div><strong>{{ member.displayName }}</strong><small>{{ member.captain ? '队长' : member.linkedProfileId ? 'YES Lab 成员' : '外部成员' }}</small></div><ExternalLink v-if="member.linkedProfileId" :size="15" aria-hidden="true" /></component></div></section>
    </main>
    <main v-else class="public-result-main"><div v-if="loading" class="portal-state">正在读取比赛详情…</div><div v-else class="portal-state error" role="alert">{{ errorMessage }}<RouterLink to="/">返回首页</RouterLink></div></main>
  </div>
</template>

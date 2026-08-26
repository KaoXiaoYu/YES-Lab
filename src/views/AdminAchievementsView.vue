<script setup>
import { Check, ExternalLink, Eye, FileBadge, Newspaper, Pencil, Save, ShieldCheck, Trophy, X } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import PortalShell from '../components/PortalShell.vue'
import { createNews, getAuthenticatedFile, listCompetitions, listManagedNews, reviewCompetition, updateCompetitionDisplay, updateNews } from '../services/authApi'

const tab = ref('competitions'); const competitions = ref([]); const news = ref([]); const selectedId = ref(null); const loading = ref(true); const saving = ref(false); const errorMessage = ref(''); const message = ref(''); const imageUrls = ref({})
const reviewNote = ref(''); const display = reactive({ featured: false, displayOrder: 100 })
const newsEditingId = ref(null); const newsForm = reactive({ title: '', sourceName: '', sourceUrl: '', summary: '', publishedDate: '', visible: true })
const selected = computed(() => competitions.value.find((item) => item.id === selectedId.value) || null)
const pendingCount = computed(() => competitions.value.filter((item) => item.verificationStatus === 'PENDING').length)
const reviewLabels = { NOT_REQUIRED: '未结束', PENDING: '待审核', APPROVED: '已认证', REJECTED: '已驳回' }

onMounted(load)
watch(selected, async (item) => { reviewNote.value = item?.reviewNote || ''; display.featured = item?.featured || false; display.displayOrder = item?.displayOrder ?? 100; clearImages(); if (!item) return; for (const image of item.images) { try { imageUrls.value[image.id] = await getAuthenticatedFile(image.url) } catch { imageUrls.value[image.id] = '' } } }, { immediate: true })
onBeforeUnmount(clearImages)

async function load() { try { [competitions.value, news.value] = await Promise.all([listCompetitions(), listManagedNews()]); selectedId.value = competitions.value.find((item) => item.verificationStatus === 'PENDING')?.id || competitions.value[0]?.id || null } catch (error) { errorMessage.value = error.message } finally { loading.value = false } }
function clearImages() { Object.values(imageUrls.value).forEach((url) => url && URL.revokeObjectURL(url)); imageUrls.value = {} }
function replaceItem(updated) { const index = competitions.value.findIndex((item) => item.id === updated.id); competitions.value.splice(index, 1, updated) }
async function review(status) { saving.value = true; errorMessage.value = ''; try { replaceItem(await reviewCompetition(selected.value.id, { status, note: reviewNote.value || null })); message.value = status === 'APPROVED' ? '比赛成果已通过认证。' : '比赛记录已驳回并通知队长修改。' } catch (error) { errorMessage.value = error.message } finally { saving.value = false } }
async function saveDisplay() { saving.value = true; errorMessage.value = ''; try { replaceItem(await updateCompetitionDisplay(selected.value.id, display)); message.value = '首页展示和手动排序已保存。' } catch (error) { errorMessage.value = error.message } finally { saving.value = false } }
async function openCertificate() { const page = window.open('', '_blank'); try { const url = await getAuthenticatedFile(`/api/v1/competitions/${selected.value.id}/certificate`); if (page) page.location = url; else window.open(url, '_blank') } catch (error) { page?.close(); errorMessage.value = error.message } }
function editNews(item) { newsEditingId.value = item.id; Object.assign(newsForm, item) }
function resetNews() { newsEditingId.value = null; Object.assign(newsForm, { title: '', sourceName: '', sourceUrl: '', summary: '', publishedDate: '', visible: true }) }
async function saveNews() { saving.value = true; errorMessage.value = ''; try { const saved = newsEditingId.value ? await updateNews(newsEditingId.value, newsForm) : await createNews(newsForm); const index = news.value.findIndex((item) => item.id === saved.id); if (index >= 0) news.value.splice(index, 1, saved); else news.value.unshift(saved); news.value.sort((a, b) => b.publishedDate.localeCompare(a.publishedDate)); message.value = '新闻引用已保存。'; resetNews() } catch (error) { errorMessage.value = error.message } finally { saving.value = false } }
</script>

<template>
  <PortalShell eyebrow="ADMIN / ACHIEVEMENTS" title="成果管理" description="审核比赛证书、维护首页比赛排序，并管理引用自学校官网或公众号的相关新闻。">
    <div class="achievement-admin-tabs"><button :class="{ active: tab === 'competitions' }" type="button" @click="tab = 'competitions'"><Trophy :size="17" aria-hidden="true" />比赛审核 <b>{{ pendingCount }}</b></button><button :class="{ active: tab === 'news' }" type="button" @click="tab = 'news'"><Newspaper :size="17" aria-hidden="true" />新闻引用</button></div>
    <div v-if="loading" class="portal-state">正在读取成果管理数据…</div><div v-if="message" class="save-message" role="status">{{ message }}</div><div v-if="errorMessage" class="form-alert" role="alert">{{ errorMessage }}</div>

    <div v-if="!loading && tab === 'competitions'" class="achievement-admin-layout">
      <aside class="achievement-admin-list"><header><div><p>COMPETITION QUEUE</p><h2>比赛记录</h2></div><span>{{ competitions.length }}</span></header><button v-for="item in competitions" :key="item.id" type="button" :class="{ active: selectedId === item.id }" @click="selectedId = item.id"><span>{{ item.captain.name.slice(0, 1) }}</span><div><strong>{{ item.name }}</strong><small>{{ item.captain.name }} · {{ item.awardName || '未结束' }}</small></div><b :data-review="item.verificationStatus">{{ reviewLabels[item.verificationStatus] }}</b></button><p v-if="!competitions.length" class="empty-note">暂无比赛提交。</p></aside>
      <section v-if="selected" class="achievement-admin-detail"><header><div><p>COMPETITION / {{ selected.lifecycle }}</p><h2>{{ selected.name }}</h2><span>{{ selected.track || '综合赛道' }} · 队长 {{ selected.captain.name }}</span></div><RouterLink :to="`/competitions/${selected.id}/edit`"><Pencil :size="16" aria-hidden="true" />修改完整资料</RouterLink></header>
        <div class="achievement-detail-grid"><article><p>获奖结果</p><strong>{{ selected.awardName || '比赛尚未结束' }}</strong></article><article><p>成员</p><strong>{{ selected.participants.map((item) => item.displayName).join('、') }}</strong></article><article><p>指导老师</p><strong>{{ selected.advisorName || '未填写' }}</strong></article><article><p>关联项目</p><strong>{{ selected.project?.name || '未关联' }}</strong></article><article class="full"><p>文字描述</p><span>{{ selected.description }}</span></article></div>
        <section v-if="selected.images.length" class="achievement-review-gallery"><figure v-for="image in selected.images" :key="image.id"><img v-if="imageUrls[image.id]" :src="imageUrls[image.id]" :alt="image.description" /><figcaption>{{ image.description }}</figcaption></figure></section>
        <button v-if="selected.hasCertificate" class="achievement-secondary" type="button" @click="openCertificate"><FileBadge :size="17" aria-hidden="true" />查看审核证书：{{ selected.certificateOriginalName }}</button>
        <section v-if="selected.lifecycle === 'FINISHED'" class="achievement-review-box"><header><ShieldCheck :size="19" aria-hidden="true" /><h3>管理员认证</h3></header><label>审核意见<textarea v-model.trim="reviewNote" rows="4" maxlength="500" placeholder="通过可留空；驳回时请说明需要修改的内容。"></textarea></label><div><button type="button" class="review-reject" :disabled="saving" @click="review('REJECTED')"><X :size="16" aria-hidden="true" />驳回</button><button type="button" class="review-approve" :disabled="saving || !selected.hasCertificate" @click="review('APPROVED')"><Check :size="16" aria-hidden="true" />审核通过</button></div></section>
        <form v-if="selected.verificationStatus === 'APPROVED'" class="achievement-display-box" @submit.prevent="saveDisplay"><header><Eye :size="19" aria-hidden="true" /><h3>公开首页展示</h3></header><label class="project-switch"><input v-model="display.featured" type="checkbox" /><span><strong>展示在首页比赛栏</strong><small>关闭后仍保留公开详情和成员主页成果记录。</small></span></label><label>手动排序值<input v-model.number="display.displayOrder" type="number" min="0" max="9999" /><small>数字越小越靠前。</small></label><button class="portal-primary" type="submit" :disabled="saving"><Save :size="16" aria-hidden="true" />保存展示设置</button></form>
      </section>
      <section v-else class="portal-state">请选择一条比赛记录。</section>
    </div>

    <div v-if="!loading && tab === 'news'" class="news-admin-layout">
      <section class="news-form-card"><header><p>EXTERNAL COVERAGE</p><h2>{{ newsEditingId ? '编辑新闻引用' : '新增新闻引用' }}</h2><button v-if="newsEditingId" type="button" @click="resetNews">取消编辑</button></header><form class="competition-form-grid" @submit.prevent="saveNews"><label class="full">新闻标题<input v-model.trim="newsForm.title" required maxlength="220" /></label><label>来源网站<input v-model.trim="newsForm.sourceName" required maxlength="120" placeholder="例如：学校官网" /></label><label>发布日期<input v-model="newsForm.publishedDate" type="date" required /></label><label class="full">原文链接<input v-model.trim="newsForm.sourceUrl" type="url" required maxlength="800" placeholder="https://..." /></label><label class="full">引用摘要<textarea v-model.trim="newsForm.summary" required rows="6" maxlength="5000" placeholder="概括外部报道与实验室成果的关系，不复制全文。"></textarea></label><label class="project-switch full"><input v-model="newsForm.visible" type="checkbox" /><span><strong>在首页新闻栏展示</strong><small>公开新闻按发布日期自动倒序。</small></span></label><footer class="full"><button class="portal-primary" type="submit" :disabled="saving"><Save :size="16" aria-hidden="true" />保存新闻</button></footer></form></section>
      <section class="news-admin-list"><header><p>PUBLISHED SOURCES</p><h2>新闻列表</h2></header><article v-for="item in news" :key="item.id"><time>{{ item.publishedDate }}</time><div><small>{{ item.sourceName }} · {{ item.visible ? '公开' : '隐藏' }}</small><h3>{{ item.title }}</h3><p>{{ item.summary }}</p></div><div><a :href="item.sourceUrl" target="_blank" rel="noopener noreferrer" aria-label="打开新闻原文"><ExternalLink :size="17" aria-hidden="true" /></a><button type="button" aria-label="编辑新闻" @click="editNews(item)"><Pencil :size="17" aria-hidden="true" /></button></div></article><p v-if="!news.length" class="empty-note">尚未添加外部新闻引用。</p></section>
    </div>
  </PortalShell>
</template>

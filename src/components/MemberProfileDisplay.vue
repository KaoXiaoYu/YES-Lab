<script setup>
import { FolderGit2, Pencil, Trophy } from 'lucide-vue-next'
import { computed } from 'vue'

const props = defineProps({
  profile: { type: Object, required: true },
  privateView: { type: Boolean, default: false },
  editable: { type: Boolean, default: false },
})

const roleLabels = { TEACHER: '指导教师', CORE_STUDENT: '核心成员', MEMBER: '正式成员' }
const statusLabels = { CANDIDATE: '候选', TRIAL: '试用', OFFICIAL: '正式', PAUSED: '暂停', EXITED: '退出' }
const isTeacher = computed(() => props.profile.role === 'TEACHER')
</script>

<template>
  <div class="member-profile-display">
    <section :class="['profile-overview', { 'teacher-profile': isTeacher }]">
      <div class="profile-identity-card">
        <div class="profile-avatar">
          <img v-if="profile.avatarUrl" :src="profile.avatarUrl" :alt="`${profile.name}的头像`" />
          <span v-else>{{ profile.name.slice(0, 1) }}</span>
        </div>
        <div class="profile-name-block">
          <p>{{ roleLabels[profile.role] }} / {{ statusLabels[profile.status] }}</p>
          <h2>{{ profile.name }}</h2>
          <span>{{ profile.headline || '还没有填写主页标语' }}</span>
        </div>
        <RouterLink v-if="editable" class="profile-edit-link" to="/profile/edit">
          <Pencil :size="17" aria-hidden="true" />编辑个人主页
        </RouterLink>

        <dl>
          <div v-if="privateView"><dt>学号 / 内部编号</dt><dd>{{ profile.memberCode }}</dd></div>
          <template v-if="!isTeacher">
            <div><dt>年级</dt><dd>{{ profile.grade || '暂未填写' }}</dd></div>
            <div><dt>专业</dt><dd>{{ profile.major || '暂未填写' }}</dd></div>
            <div><dt>班级</dt><dd>{{ profile.className || '暂未填写' }}</dd></div>
          </template>
          <div v-if="privateView"><dt>内部联系方式</dt><dd>{{ profile.internalContact || '暂未填写' }}</dd></div>
        </dl>
        <ul aria-label="能力标签"><li v-for="tag in profile.skillTags" :key="tag">{{ tag }}</li></ul>
      </div>

      <div class="growth-grid">
        <template v-if="!isTeacher">
          <article><span>POINTS</span><strong>{{ profile.totalPoints }}</strong><small>积分系统暂未接入</small></article>
          <article><span>RANK</span><strong>{{ profile.currentRank || '—' }}</strong><small>排名将在积分系统启用后更新</small></article>
        </template>
        <article><span>PROJECTS</span><strong>{{ profile.projectRecords?.length || 0 }}</strong><small>项目记录</small></article>
        <article><span>RESULTS</span><strong>{{ profile.achievementRecords?.length || 0 }}</strong><small>比赛与成果</small></article>
      </div>
    </section>

    <section class="profile-story-card">
      <header><div><p>MEMBER PROFILE</p><h2>研究与成长档案</h2></div><span>由成员本人维护的公开介绍</span></header>
      <div class="profile-rich-content" v-html="profile.profileHtml"></div>
    </section>

    <section class="profile-record-grid">
      <article>
        <header><FolderGit2 :size="20" aria-hidden="true" /><h2>项目记录</h2></header>
        <ul v-if="profile.projectRecords?.length"><li v-for="item in profile.projectRecords" :key="item">{{ item }}</li></ul>
        <p v-else>项目管理模块接入后，将在这里展示参与项目。</p>
      </article>
      <article>
        <header><Trophy :size="20" aria-hidden="true" /><h2>比赛与成果</h2></header>
        <ul v-if="profile.achievementRecords?.length"><li v-for="item in profile.achievementRecords" :key="item">{{ item }}</li></ul>
        <p v-else>成果管理模块接入后，将在这里展示比赛与研究成果。</p>
      </article>
    </section>
  </div>
</template>

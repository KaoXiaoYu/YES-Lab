import { createRouter, createWebHistory } from 'vue-router'
import { authState, restoreSession } from '../services/authApi'
import PublicHomeView from '../views/PublicHomeView.vue'

const routes = [
  { path: '/', name: 'home', component: PublicHomeView },
  { path: '/members/:profileId', name: 'public-member', component: () => import('../views/PublicMemberView.vue') },
  { path: '/competition-results/:competitionId', name: 'public-competition', component: () => import('../views/PublicCompetitionView.vue') },
  { path: '/login', name: 'login', component: () => import('../views/AuthView.vue'), meta: { guest: true } },
  { path: '/register', name: 'register', component: () => import('../views/AuthView.vue'), meta: { guest: true } },
  { path: '/profile', name: 'profile', component: () => import('../views/ProfileView.vue'), meta: { roles: ['TEACHER', 'CORE_STUDENT', 'MEMBER'] } },
  { path: '/profile/edit', name: 'profile-edit', component: () => import('../views/ProfileEditView.vue'), meta: { roles: ['TEACHER', 'CORE_STUDENT', 'MEMBER'] } },
  { path: '/projects', name: 'projects', component: () => import('../views/ProjectsView.vue'), meta: { roles: ['TEACHER', 'CORE_STUDENT', 'MEMBER'] } },
  { path: '/projects/new', name: 'project-create', component: () => import('../views/ProjectCreateView.vue'), meta: { roles: ['TEACHER', 'CORE_STUDENT'] } },
  { path: '/projects/:projectId', name: 'project-workspace', component: () => import('../views/ProjectWorkspaceView.vue'), meta: { roles: ['TEACHER', 'CORE_STUDENT', 'MEMBER'] } },
  { path: '/competitions', name: 'competitions', component: () => import('../views/CompetitionsView.vue'), meta: { roles: ['TEACHER', 'CORE_STUDENT', 'MEMBER'] } },
  { path: '/competitions/new', name: 'competition-create', component: () => import('../views/CompetitionFormView.vue'), meta: { roles: ['TEACHER', 'CORE_STUDENT', 'MEMBER'] } },
  { path: '/competitions/:competitionId/edit', name: 'competition-edit', component: () => import('../views/CompetitionFormView.vue'), meta: { roles: ['TEACHER', 'CORE_STUDENT', 'MEMBER'] } },
  { path: '/application', name: 'application', component: () => import('../views/RecruitmentView.vue'), meta: { roles: ['VISITOR'] } },
  { path: '/admin/recruitment', name: 'admin-recruitment', component: () => import('../views/AdminRecruitmentView.vue'), meta: { roles: ['TEACHER', 'CORE_STUDENT'] } },
  { path: '/admin/members', name: 'admin-members', component: () => import('../views/AdminMembersView.vue'), meta: { roles: ['TEACHER', 'CORE_STUDENT'] } },
  { path: '/admin/achievements', name: 'admin-achievements', component: () => import('../views/AdminAchievementsView.vue'), meta: { roles: ['TEACHER', 'CORE_STUDENT'] } },
  { path: '/admin/homepage', name: 'admin-homepage', component: () => import('../views/AdminHomepageView.vue'), meta: { roles: ['TEACHER', 'CORE_STUDENT'] } },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  await restoreSession()
  const role = authState.account?.role
  if (to.meta.guest && authState.account) return role === 'VISITOR' ? '/application' : '/profile'
  if (to.meta.roles && !authState.account) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.meta.roles && !to.meta.roles.includes(role)) return role === 'VISITOR' ? '/application' : '/profile'
  return true
})

export default router

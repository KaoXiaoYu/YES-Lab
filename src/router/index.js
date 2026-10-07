import { createRouter, createWebHistory } from 'vue-router'
import { authState, getOwnProfile, restoreSession } from '../services/authApi'
import PublicHomeView from '../views/PublicHomeView.vue'

const routes = [
  { path: '/', name: 'home', component: PublicHomeView },
  { path: '/members/:profileId', name: 'public-member', component: () => import('../views/PublicMemberView.vue') },
  {
    path: '/competition-results/:competitionId',
    name: 'public-competition',
    component: () => import('../views/PublicCompetitionView.vue'),
  },
  { path: '/login', name: 'login', component: () => import('../views/AuthView.vue'), meta: { guest: true } },
  { path: '/register', name: 'register', component: () => import('../views/AuthView.vue'), meta: { guest: true } },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('../views/ProfileView.vue'),
    meta: { roles: ['TEACHER', 'CORE_STUDENT', 'MEMBER'] },
  },
  {
    path: '/profile/edit',
    name: 'profile-edit',
    component: () => import('../views/ProfileEditView.vue'),
    meta: { roles: ['TEACHER', 'CORE_STUDENT', 'MEMBER'] },
  },
  {
    path: '/complete-profile',
    name: 'complete-profile',
    component: () => import('../views/ProfileCompletionView.vue'),
    meta: { roles: ['MEMBER'], allowIncompleteQualification: true },
  },
  {
    path: '/points',
    name: 'points-leaderboard',
    component: () => import('../views/PointsLeaderboardView.vue'),
    meta: { roles: ['TEACHER', 'CORE_STUDENT', 'MEMBER'] },
  },
  {
    path: '/fund',
    name: 'fund',
    component: () => import('../views/FundView.vue'),
    meta: { roles: ['TEACHER', 'CORE_STUDENT', 'MEMBER'] },
  },
  {
    path: '/projects',
    name: 'projects',
    component: () => import('../views/ProjectsView.vue'),
    meta: { roles: ['TEACHER', 'CORE_STUDENT', 'MEMBER'] },
  },
  {
    path: '/projects/new',
    name: 'project-create',
    component: () => import('../views/ProjectCreateView.vue'),
    meta: { roles: ['TEACHER', 'CORE_STUDENT'] },
  },
  {
    path: '/projects/:projectId',
    name: 'project-workspace',
    component: () => import('../views/ProjectWorkspaceView.vue'),
    meta: { roles: ['TEACHER', 'CORE_STUDENT', 'MEMBER'] },
  },
  {
    path: '/tasks',
    name: 'tasks',
    component: () => import('../views/TasksView.vue'),
    meta: { roles: ['TEACHER', 'CORE_STUDENT', 'MEMBER'] },
  },
  {
    path: '/tasks/:assignmentId/subtasks/:subtaskId',
    name: 'task-subtask',
    component: () => import('../views/TaskSubtaskView.vue'),
    meta: { roles: ['TEACHER', 'CORE_STUDENT', 'MEMBER'] },
  },
  {
    path: '/tasks/:assignmentId',
    name: 'task-detail',
    component: () => import('../views/TaskDetailView.vue'),
    meta: { roles: ['TEACHER', 'CORE_STUDENT', 'MEMBER'] },
  },
  {
    path: '/bounties',
    name: 'bounties',
    component: () => import('../views/BountyBoardView.vue'),
    meta: { roles: ['TEACHER', 'CORE_STUDENT', 'MEMBER'] },
  },
  {
    path: '/bounties/:taskId',
    name: 'bounty-detail',
    component: () => import('../views/BountyDetailView.vue'),
    meta: { roles: ['TEACHER', 'CORE_STUDENT', 'MEMBER'] },
  },
  {
    path: '/admin/bounties',
    name: 'admin-bounties',
    component: () => import('../views/AdminBountiesView.vue'),
    meta: { roles: ['TEACHER', 'CORE_STUDENT'] },
  },
  {
    path: '/admin/bounties/:taskId/claims',
    name: 'admin-bounty-claims',
    component: () => import('../views/AdminBountyClaimsView.vue'),
    meta: { roles: ['TEACHER', 'CORE_STUDENT'] },
  },
  {
    path: '/admin/tasks',
    name: 'admin-tasks',
    component: () => import('../views/AdminTasksView.vue'),
    meta: { roles: ['TEACHER', 'CORE_STUDENT'] },
  },
  {
    path: '/admin/tasks/onboarding',
    name: 'admin-onboarding-task',
    component: () => import('../views/AdminOnboardingTaskView.vue'),
    meta: { roles: ['TEACHER', 'CORE_STUDENT'] },
  },
  {
    path: '/admin/tasks/:taskId/progress',
    name: 'admin-task-progress',
    component: () => import('../views/AdminTaskProgressView.vue'),
    meta: { roles: ['TEACHER', 'CORE_STUDENT'] },
  },
  {
    path: '/competitions',
    name: 'competitions',
    component: () => import('../views/CompetitionsView.vue'),
    meta: { roles: ['TEACHER', 'CORE_STUDENT', 'MEMBER'] },
  },
  { path: '/discussions', name: 'discussions', component: () => import('../views/DiscussionView.vue') },
  {
    path: '/competitions/new',
    name: 'competition-create',
    component: () => import('../views/CompetitionFormView.vue'),
    meta: { roles: ['TEACHER', 'CORE_STUDENT', 'MEMBER'] },
  },
  {
    path: '/competitions/:competitionId/edit',
    name: 'competition-edit',
    component: () => import('../views/CompetitionFormView.vue'),
    meta: { roles: ['TEACHER', 'CORE_STUDENT', 'MEMBER'] },
  },
  {
    path: '/application',
    name: 'application',
    component: () => import('../views/RecruitmentView.vue'),
    meta: { roles: ['VISITOR'] },
  },
  {
    path: '/application/subtasks/:subtaskId',
    name: 'application-subtask',
    component: () => import('../views/OnboardingSubtaskView.vue'),
    meta: { roles: ['VISITOR'] },
  },
  {
    path: '/inbox',
    name: 'inbox',
    component: () => import('../views/InboxView.vue'),
    meta: { roles: ['TEACHER', 'CORE_STUDENT', 'MEMBER', 'VISITOR'] },
  },
  {
    path: '/admin/recruitment',
    name: 'admin-recruitment',
    component: () => import('../views/AdminRecruitmentView.vue'),
    meta: { roles: ['TEACHER', 'CORE_STUDENT'] },
  },
  {
    path: '/admin/members',
    name: 'admin-members',
    component: () => import('../views/AdminMembersView.vue'),
    meta: { roles: ['TEACHER', 'CORE_STUDENT'] },
  },
  {
    path: '/admin/points',
    name: 'admin-points',
    component: () => import('../views/AdminPointsView.vue'),
    meta: { roles: ['TEACHER', 'CORE_STUDENT'] },
  },
  {
    path: '/admin/achievements',
    name: 'admin-achievements',
    component: () => import('../views/AdminAchievementsView.vue'),
    meta: { roles: ['TEACHER', 'CORE_STUDENT'] },
  },
  {
    path: '/admin/homepage',
    name: 'admin-homepage',
    component: () => import('../views/AdminHomepageView.vue'),
    meta: { roles: ['TEACHER', 'CORE_STUDENT'] },
  },
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

  if (role === 'MEMBER' && to.meta.roles?.includes('MEMBER')) {
    if (authState.memberQualificationComplete !== true && to.name !== 'complete-profile') {
      try {
        await getOwnProfile()
      } catch {
        return { name: 'complete-profile', query: { redirect: to.fullPath, retry: '1' } }
      }
      if (!authState.memberQualificationComplete) {
        return { name: 'complete-profile', query: { redirect: to.fullPath } }
      }
    }
    if (to.name === 'complete-profile' && authState.memberQualificationComplete === true) {
      return safeMemberRedirect(to.query.redirect) || '/profile'
    }
  }
  return true
})

function safeMemberRedirect(candidate) {
  if (typeof candidate !== 'string' || !candidate.startsWith('/') || candidate.startsWith('//')) return null
  if (candidate.startsWith('/complete-profile')) return null
  return candidate
}

const chunkRecoveryKey = 'openlims-route-chunk-recovery'
const chunkLoadFailure =
  /Failed to fetch dynamically imported module|Importing a module script failed|error loading dynamically imported module|ChunkLoadError/i

router.onError((error, to) => {
  if (!chunkLoadFailure.test(String(error?.message || error))) return
  const target = to?.fullPath || window.location.pathname + window.location.search + window.location.hash
  if (sessionStorage.getItem(chunkRecoveryKey) === target) {
    sessionStorage.removeItem(chunkRecoveryKey)
    return
  }
  sessionStorage.setItem(chunkRecoveryKey, target)
  window.location.replace(target)
})

router.afterEach((to, _from, failure) => {
  if (!failure && sessionStorage.getItem(chunkRecoveryKey) === to.fullPath) sessionStorage.removeItem(chunkRecoveryKey)
})

export default router

import { reactive } from 'vue'

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')
const legacyTokenKey = 'yeslab_access_token'

export const authState = reactive({
  token: null,
  account: null,
  ready: false,
  memberQualificationComplete: null,
})

let restorePromise
let refreshPromise
sessionStorage.removeItem(legacyTokenKey)

export async function restoreSession() {
  if (authState.ready) return authState.account
  if (restorePromise) return restorePromise
  restorePromise = (async () => {
    try {
      await refreshSession()
    } catch {
      clearSession()
    } finally {
      authState.ready = true
    }
    return authState.account
  })()
  return restorePromise
}

export async function login(credentials) {
  const response = await apiRequest('/api/v1/auth/login', { method: 'POST', body: credentials, authenticated: false })
  setSession(response)
  return response.account
}

export async function register(credentials) {
  const response = await apiRequest('/api/v1/auth/register', {
    method: 'POST',
    body: credentials,
    authenticated: false,
  })
  setSession(response)
  return response.account
}

export async function logout() {
  clearAccountDrafts(authState.account?.id ?? authState.account?.username)
  try {
    await fetch(`${apiBaseUrl}/api/v1/auth/logout`, {
      method: 'POST',
      headers: { Accept: 'application/json' },
      credentials: 'include',
    })
  } finally {
    clearSession()
  }
}

export function changeOwnPassword(payload) {
  return apiRequest('/api/v1/auth/password', { method: 'PUT', body: payload })
}

export async function getOwnProfile() {
  const profile = await apiRequest('/api/v1/member/profile')
  if (authState.account?.role === 'MEMBER') {
    authState.memberQualificationComplete = Boolean(profile?.qualificationComplete)
  }
  return profile
}

export async function completeOwnQualification(payload) {
  const profile = await apiRequest('/api/v1/member/profile/qualification', { method: 'PUT', body: payload })
  authState.memberQualificationComplete = Boolean(profile?.qualificationComplete)
  return profile
}

export function getOwnShowcase() {
  return apiRequest('/api/v1/member/profile/showcase')
}

export function updateOwnShowcase(payload) {
  return apiRequest('/api/v1/member/profile/showcase', { method: 'PUT', body: payload })
}

export async function updateOwnProfile(payload) {
  const profile = await apiRequest('/api/v1/member/profile', { method: 'PUT', body: payload })
  syncAccountAvatar(profile)
  return profile
}

export function getAdminOverview() {
  return apiRequest('/api/v1/admin/overview')
}
export function listMembers() {
  return apiRequest('/api/v1/admin/members')
}

export function updateMember(profileId, payload) {
  return apiRequest(`/api/v1/admin/members/${profileId}`, { method: 'PUT', body: payload })
}

export function resetMemberPassword(profileId) {
  return apiRequest(`/api/v1/admin/members/${profileId}/password`, { method: 'PUT' })
}

export function getPointRules() {
  return apiRequest('/api/v1/points/rules')
}

export function getOwnPoints() {
  return apiRequest('/api/v1/member/points')
}

export function getPointLeaderboard(period = 'TOTAL') {
  return apiRequest(`/api/v1/points/leaderboard?period=${encodeURIComponent(period)}`)
}

export function getPointSources() {
  return apiRequest('/api/v1/admin/points/sources')
}

export function listPointGrants() {
  return apiRequest('/api/v1/admin/points/grants')
}

export function grantPoints(payload) {
  return apiRequest('/api/v1/admin/points/grants', { method: 'POST', body: payload })
}

export function reversePointGrant(grantId, payload) {
  return apiRequest(`/api/v1/admin/points/grants/${grantId}/reversal`, { method: 'POST', body: payload })
}

export function issueBountyPrize(taskId, assignmentId) {
  return apiRequest(`/api/v1/admin/bounties/${taskId}/claims/${assignmentId}/prize-fulfillment/issue`, {
    method: 'POST',
  })
}

export function confirmBountyPrizeReceived(assignmentId) {
  return apiRequest(`/api/v1/tasks/${assignmentId}/bounty-prize/confirm-received`, { method: 'POST' })
}

export function createCoreStudent(payload) {
  return apiRequest('/api/v1/admin/members/core-students', { method: 'POST', body: payload })
}

export async function replaceOwnAvatar(avatar) {
  const form = new FormData()
  form.append('avatar', avatar)
  const profile = await formRequest('/api/v1/member/profile/avatar', { method: 'PUT', body: form })
  syncAccountAvatar(profile)
  return profile
}

export async function deleteOwnAvatar() {
  const profile = await apiRequest('/api/v1/member/profile/avatar', { method: 'DELETE' })
  syncAccountAvatar(profile)
  return profile
}

export function replaceManagedMemberAvatar(profileId, avatar) {
  const form = new FormData()
  form.append('avatar', avatar)
  return formRequest(`/api/v1/admin/members/${profileId}/avatar`, { method: 'PUT', body: form })
}

export function deleteManagedMemberAvatar(profileId) {
  return apiRequest(`/api/v1/admin/members/${profileId}/avatar`, { method: 'DELETE' })
}

export function listProjects() {
  return apiRequest('/api/v1/projects')
}

export function getProject(projectId) {
  return apiRequest(`/api/v1/projects/${projectId}`)
}

export function listProjectMemberOptions() {
  return apiRequest('/api/v1/projects/member-options')
}

export function createProject(payload) {
  return apiRequest('/api/v1/projects', { method: 'POST', body: payload })
}

export function updateProject(projectId, payload) {
  return apiRequest(`/api/v1/projects/${projectId}`, { method: 'PUT', body: payload })
}

export function updateProjectTeam(projectId, payload) {
  return apiRequest(`/api/v1/projects/${projectId}/team`, { method: 'PUT', body: payload })
}

export function getHomepageContent() {
  return apiRequest('/api/v1/admin/homepage')
}

export function updateHomepageContent(payload) {
  return apiRequest('/api/v1/admin/homepage', { method: 'PUT', body: payload })
}

export function replaceProjectCover(projectId, cover) {
  const form = new FormData()
  form.append('cover', cover)
  return formRequest(`/api/v1/projects/${projectId}/cover`, { method: 'PUT', body: form })
}

export function listCompetitions() {
  return apiRequest('/api/v1/competitions')
}
export function getOwnCompetitionCountdown() {
  return apiRequest('/api/v1/competitions/countdown')
}
export function getCompetition(id) {
  return apiRequest(`/api/v1/competitions/${id}`)
}
export function listCompetitionMemberOptions() {
  return apiRequest('/api/v1/competitions/member-options')
}
export function listCompetitionProjectOptions() {
  return apiRequest('/api/v1/competitions/project-options')
}
export function createCompetition(payload, certificate, images = [], registration = null) {
  const form = new FormData()
  form.append('data', new Blob([JSON.stringify(payload)], { type: 'application/json' }))
  if (certificate) form.append('certificate', certificate)
  if (registration) form.append('registration', registration)
  images.forEach((image) => form.append('images', image))
  return formRequest('/api/v1/competitions', { method: 'POST', body: form })
}
export function updateCompetition(id, payload) {
  return apiRequest(`/api/v1/competitions/${id}`, { method: 'PUT', body: payload })
}
export function replaceCompetitionCertificate(id, certificate) {
  const form = new FormData()
  form.append('certificate', certificate)
  return formRequest(`/api/v1/competitions/${id}/certificate`, { method: 'PUT', body: form })
}
export function replaceCompetitionImages(id, images, descriptions = []) {
  const form = new FormData()
  images.forEach((image) => form.append('images', image))
  descriptions.forEach((item) => form.append('descriptions', item))
  return formRequest(`/api/v1/competitions/${id}/images`, { method: 'PUT', body: form })
}
export function deleteCompetitionImage(competitionId, imageId) {
  return apiRequest(`/api/v1/competitions/${competitionId}/images/${imageId}`, { method: 'DELETE' })
}
export function reviewCompetition(id, payload) {
  return apiRequest(`/api/v1/admin/achievements/competitions/${id}/review`, { method: 'PATCH', body: payload })
}
export function updateCompetitionDisplay(id, payload) {
  return apiRequest(`/api/v1/admin/achievements/competitions/${id}/display`, { method: 'PATCH', body: payload })
}
export function listManagedNews() {
  return apiRequest('/api/v1/admin/achievements/news')
}
export function createNews(payload) {
  return apiRequest('/api/v1/admin/achievements/news', { method: 'POST', body: payload })
}
export function updateNews(id, payload) {
  return apiRequest(`/api/v1/admin/achievements/news/${id}`, { method: 'PUT', body: payload })
}
export async function getAuthenticatedFile(path) {
  const response = await fetchWithRefresh(`${apiBaseUrl}${path}`, {
    headers: { Authorization: `Bearer ${authState.token}` },
  })
  if (!response.ok) throw new ApiError(`文件读取失败（${response.status}）`, {}, response.status)
  return URL.createObjectURL(await response.blob())
}

export function getOwnApplication() {
  return apiRequest('/api/v1/recruitment/me')
}

export function getRecruitmentQuestions() {
  return apiRequest('/api/v1/recruitment/me/questions')
}

export function saveOwnApplication(payload) {
  return apiRequest('/api/v1/recruitment/me', { method: 'PUT', body: payload })
}

export function saveOwnQualification(payload) {
  return apiRequest('/api/v1/recruitment/me/qualification', { method: 'PUT', body: payload })
}

export function uploadRecruitmentPortfolioImages(images) {
  const form = new FormData()
  images.forEach((image) => form.append('images', image))
  return formRequest('/api/v1/recruitment/me/portfolio-images', { method: 'POST', body: form })
}

export function deleteRecruitmentPortfolioImage(imageId) {
  return apiRequest(`/api/v1/recruitment/me/portfolio-images/${imageId}`, { method: 'DELETE' })
}

export function listRecruitmentApplications() {
  return apiRequest('/api/v1/admin/recruitment/applications')
}

export function listInterviewers() {
  return apiRequest('/api/v1/admin/recruitment/interviewers')
}

export function changeRecruitmentStage(applicationId, payload) {
  return apiRequest(`/api/v1/admin/recruitment/applications/${applicationId}/stage`, { method: 'PATCH', body: payload })
}

export function saveInterview(applicationId, payload) {
  return apiRequest(`/api/v1/admin/recruitment/applications/${applicationId}/interview`, {
    method: 'PUT',
    body: payload,
  })
}

export function resolveInterviewDecision(applicationId, payload) {
  return apiRequest(`/api/v1/admin/recruitment/applications/${applicationId}/interview-decision`, {
    method: 'PATCH',
    body: payload,
  })
}

export function setInterviewResultPending(applicationId, pending) {
  return apiRequest(`/api/v1/admin/recruitment/applications/${applicationId}/interview-result-pending`, {
    method: 'PATCH',
    body: { pending },
  })
}

export function convertRecruitmentToMember(applicationId, payload) {
  return apiRequest(`/api/v1/admin/recruitment/applications/${applicationId}/convert`, {
    method: 'POST',
    body: payload,
  })
}

export function resetRecruitmentPassword(applicationId) {
  return apiRequest(`/api/v1/admin/recruitment/applications/${applicationId}/password`, { method: 'PUT' })
}

export function getInterviewSchedule() {
  return apiRequest('/api/v1/recruitment/interviews')
}
export function bookInterviewSession(sessionId) {
  return apiRequest(`/api/v1/recruitment/interviews/sessions/${sessionId}/book`, { method: 'POST' })
}
export function cancelInterviewBooking() {
  return apiRequest('/api/v1/recruitment/interviews/booking', { method: 'DELETE' })
}
export function listInterviewSessions() {
  return apiRequest('/api/v1/admin/recruitment/interview-sessions')
}
export function createInterviewSession(payload) {
  return apiRequest('/api/v1/admin/recruitment/interview-sessions', { method: 'POST', body: payload })
}
export function updateInterviewSession(sessionId, payload) {
  return apiRequest(`/api/v1/admin/recruitment/interview-sessions/${sessionId}`, { method: 'PUT', body: payload })
}
export function cancelInterviewSession(sessionId) {
  return apiRequest(`/api/v1/admin/recruitment/interview-sessions/${sessionId}`, { method: 'DELETE' })
}
export function callNextInterview(sessionId) {
  return apiRequest(`/api/v1/admin/recruitment/interview-sessions/${sessionId}/call-next`, { method: 'POST' })
}
export function startScheduledInterview(sessionId, bookingId) {
  return apiRequest(`/api/v1/admin/recruitment/interview-sessions/${sessionId}/bookings/${bookingId}/start`, {
    method: 'POST',
  })
}
export function markInterviewNoShow(sessionId, bookingId) {
  return apiRequest(`/api/v1/admin/recruitment/interview-sessions/${sessionId}/bookings/${bookingId}/no-show`, {
    method: 'POST',
  })
}
export function completeScheduledInterview(sessionId, bookingId, payload) {
  return apiRequest(`/api/v1/admin/recruitment/interview-sessions/${sessionId}/bookings/${bookingId}/complete`, {
    method: 'POST',
    body: payload,
  })
}
export function endInterviewSessionEarly(sessionId) {
  return apiRequest(`/api/v1/admin/recruitment/interview-sessions/${sessionId}/end-early`, { method: 'POST' })
}

export function getNotifications() {
  return apiRequest('/api/v1/notifications')
}
export function getNotificationVisibility() {
  return apiRequest('/api/v1/notifications/visibility')
}
export function getMelinaVisibilitySettings() {
  return apiRequest('/api/v1/admin/notifications/melina-visibility')
}
export function updateMelinaVisibilitySettings(payload) {
  return apiRequest('/api/v1/admin/notifications/melina-visibility', { method: 'PUT', body: payload })
}
export function markNotificationRead(notificationId) {
  return apiRequest(`/api/v1/notifications/${notificationId}/read`, { method: 'PATCH' })
}
export function markAllNotificationsRead() {
  return apiRequest('/api/v1/notifications/read-all', { method: 'PATCH' })
}

export function listDiscussions(sort = 'NEWEST') {
  return apiRequest(`/api/v1/discussions?sort=${encodeURIComponent(sort)}`, { optionalAuthentication: true })
}
export function createDiscussion(payload) {
  return apiRequest('/api/v1/discussions', { method: 'POST', body: payload })
}
export function updateDiscussion(postId, payload) {
  return apiRequest(`/api/v1/discussions/${postId}`, { method: 'PUT', body: payload })
}
export function deleteDiscussion(postId) {
  return apiRequest(`/api/v1/discussions/${postId}`, { method: 'DELETE' })
}
export function listDiscussionPage({ sort = 'NEWEST', page = 0, size = 20, q = '' } = {}) {
  const params = new URLSearchParams({ sort, page, size })
  if (q.trim()) params.set('q', q.trim())
  return apiRequest(`/api/v1/discussions/page?${params}`, { optionalAuthentication: true })
}
export function toggleDiscussionLike(postId) {
  return apiRequest(`/api/v1/discussions/${postId}/like`, { method: 'PATCH' })
}
export function toggleDiscussionPin(postId) {
  return apiRequest(`/api/v1/discussions/${postId}/pin`, { method: 'PATCH' })
}
export function createDiscussionReply(postId, payload) {
  return apiRequest(`/api/v1/discussions/${postId}/replies`, { method: 'POST', body: payload })
}
export function updateDiscussionReply(replyId, payload) {
  return apiRequest(`/api/v1/discussions/replies/${replyId}`, { method: 'PUT', body: payload })
}
export function deleteDiscussionReply(replyId) {
  return apiRequest(`/api/v1/discussions/replies/${replyId}`, { method: 'DELETE' })
}
export function toggleDiscussionReplyLike(replyId) {
  return apiRequest(`/api/v1/discussions/replies/${replyId}/like`, { method: 'PATCH' })
}

async function apiRequest(path, options = {}) {
  const headers = { Accept: 'application/json' }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  const useAuthentication = options.authenticated !== false
  if (useAuthentication && authState.token) headers.Authorization = `Bearer ${authState.token}`
  const refreshOnUnauthorized = useAuthentication && (!options.optionalAuthentication || Boolean(authState.token))

  let response
  try {
    response = await fetchWithRefresh(
      `${apiBaseUrl}${path}`,
      {
        method: options.method || 'GET',
        headers,
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
      },
      refreshOnUnauthorized,
    )
  } catch {
    throw new ApiError('无法连接后端服务，请确认 Spring Boot 已启动。')
  }

  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    if (response.status === 401 && refreshOnUnauthorized) clearSession()
    throw new ApiError(payload?.message || `请求失败（${response.status}）`, payload?.fields || {}, response.status)
  }
  if (options.method && options.method !== 'GET') notifyDomainMutation(path)
  return payload?.data ?? null
}

async function formRequest(path, options) {
  let response
  try {
    response = await fetchWithRefresh(`${apiBaseUrl}${path}`, {
      method: options.method,
      headers: {
        Accept: 'application/json',
        ...(authState.token ? { Authorization: `Bearer ${authState.token}` } : {}),
      },
      body: options.body,
    })
  } catch {
    throw new ApiError('无法连接后端服务，请确认 Spring Boot 已启动。')
  }
  const payload = await response.json().catch(() => null)
  if (!response.ok)
    throw new ApiError(payload?.message || `请求失败（${response.status}）`, payload?.fields || {}, response.status)
  if (options.method && options.method !== 'GET') notifyDomainMutation(path)
  return payload?.data ?? null
}

function setSession(response) {
  if (authState.account?.id !== response.account?.id) authState.memberQualificationComplete = null
  authState.token = response.accessToken
  authState.account = response.account
  authState.ready = true
}

function syncAccountAvatar(profile) {
  if (!authState.account) return
  authState.account.displayName = profile.name
  authState.account.avatarUrl = profile.avatarUrl
}

// Unsent drafts (see useDraft) stay in this browser only while their author is signed in.
function clearAccountDrafts(owner) {
  if (owner == null) return
  try {
    const prefix = `yeslab:draft:${owner}:`
    Object.keys(localStorage)
      .filter((key) => key.startsWith(prefix))
      .forEach((key) => localStorage.removeItem(key))
  } catch {
    // Storage unavailable; nothing to clear.
  }
}

function clearSession() {
  sessionStorage.removeItem(legacyTokenKey)
  authState.token = null
  authState.account = null
  authState.memberQualificationComplete = null
  authState.ready = true
  restorePromise = null
}

async function refreshSession() {
  if (refreshPromise) return refreshPromise
  refreshPromise = (async () => {
    const response = await fetch(`${apiBaseUrl}/api/v1/auth/refresh`, {
      method: 'POST',
      headers: { Accept: 'application/json' },
      credentials: 'include',
    })
    if (!response.ok) throw new Error('登录状态不可恢复')
    const payload = await response.json()
    setSession(payload.data)
    return payload.data
  })().finally(() => {
    refreshPromise = null
  })
  return refreshPromise
}

async function fetchWithRefresh(url, init, authenticated = true) {
  const request = { ...init, credentials: 'include' }
  let response = await fetch(url, request)
  if (response.status !== 401 || !authenticated) return response

  try {
    await refreshSession()
  } catch {
    clearSession()
    return response
  }

  const headers = new Headers(init.headers || {})
  headers.set('Authorization', `Bearer ${authState.token}`)
  response = await fetch(url, { ...request, headers })
  return response
}

export class ApiError extends Error {
  constructor(message, fields = {}, status = 0) {
    super(message)
    this.fields = fields
    this.status = status
  }
}

export function uploadSponsorLogo(logo) {
  const form = new FormData()
  form.append('logo', logo)
  return formRequest('/api/v1/admin/homepage/sponsors/logo', { method: 'POST', body: form })
}

export function uploadHomepageModel(model) {
  const form = new FormData()
  form.append('model', model)
  return formRequest('/api/v1/admin/homepage/models', { method: 'POST', body: form })
}

// ---------- 任务模块 ----------

export function getMyOnboardingTask() {
  return apiRequest('/api/v1/recruitment/me/onboarding-task')
}

export function getOnboardingSubtask(subtaskId) {
  return apiRequest(`/api/v1/recruitment/me/onboarding-task/subtasks/${subtaskId}`)
}

export function submitOnboardingSubtask(subtaskId, contentHtml) {
  return apiRequest(`/api/v1/recruitment/me/onboarding-task/subtasks/${subtaskId}/submission`, {
    method: 'POST',
    body: { contentHtml },
  })
}

export function submitOnboardingTask(completionNote) {
  return apiRequest('/api/v1/recruitment/me/onboarding-task/submission', {
    method: 'POST',
    body: { completionNote },
  })
}

export function getOnboardingTask() {
  return apiRequest('/api/v1/admin/tasks/onboarding')
}

export function saveOnboardingTask(payload) {
  return apiRequest('/api/v1/admin/tasks/onboarding', { method: 'PUT', body: payload })
}

export function getOnboardingOverview() {
  return apiRequest('/api/v1/admin/tasks/onboarding-overview')
}

export function backfillOnboardingTasks() {
  return apiRequest('/api/v1/admin/tasks/onboarding-tasks/backfill', { method: 'POST' })
}

/** 按人延长新手任务截止日期：到期（本人 due_date 已过）后唯一的解锁动作。 */
export function extendOnboardingDueDate(assignmentId, payload) {
  return apiRequest(`/api/v1/admin/tasks/onboarding-assignments/${assignmentId}/due-date`, {
    method: 'PUT',
    body: payload,
  })
}

// ---------- 悬赏任务 ----------

export function getBountyBoard() {
  return apiRequest('/api/v1/bounties')
}

export function getBountyDetail(taskId) {
  return apiRequest(`/api/v1/bounties/${taskId}`)
}

export function claimBounty(taskId) {
  return apiRequest(`/api/v1/bounties/${taskId}/claim`, { method: 'POST' })
}

export function abandonBounty(taskId) {
  return apiRequest(`/api/v1/bounties/${taskId}/abandon`, { method: 'POST' })
}

export function listBounties() {
  return apiRequest('/api/v1/admin/bounties')
}

export function createBounty(payload) {
  return apiRequest('/api/v1/admin/bounties', { method: 'POST', body: payload })
}

export function updateBounty(taskId, payload) {
  return apiRequest(`/api/v1/admin/bounties/${taskId}`, { method: 'PUT', body: payload })
}

export function publishBounty(taskId) {
  return apiRequest(`/api/v1/admin/bounties/${taskId}/publish`, { method: 'POST' })
}

export function closeBounty(taskId) {
  return apiRequest(`/api/v1/admin/bounties/${taskId}/close`, { method: 'POST' })
}

export function deleteBounty(taskId) {
  return apiRequest(`/api/v1/admin/bounties/${taskId}`, { method: 'DELETE' })
}

export function getBountyClaims(taskId) {
  return apiRequest(`/api/v1/admin/bounties/${taskId}/claims`)
}

export function revokeBountyClaim(taskId, assignmentId, payload) {
  return apiRequest(`/api/v1/admin/bounties/${taskId}/claims/${assignmentId}/revoke`, {
    method: 'POST',
    body: payload,
  })
}

export function removeBountyClaim(taskId, assignmentId) {
  return apiRequest(`/api/v1/admin/bounties/${taskId}/claims/${assignmentId}`, { method: 'DELETE' })
}

export function reviewTaskAssignment(taskId, assignmentId, payload) {
  return apiRequest(`/api/v1/admin/tasks/${taskId}/assignments/${assignmentId}/review`, {
    method: 'PUT',
    body: payload,
  })
}

export function listTasks(status, keyword) {
  const params = new URLSearchParams()
  if (status) params.set('status', status)
  if (keyword) params.set('keyword', keyword)
  const query = params.toString()
  return apiRequest(`/api/v1/admin/tasks${query ? `?${query}` : ''}`)
}

export function createTask(payload) {
  return apiRequest('/api/v1/admin/tasks', { method: 'POST', body: payload })
}

export function getTask(taskId) {
  return apiRequest(`/api/v1/admin/tasks/${taskId}`)
}

export function updateTask(taskId, payload) {
  return apiRequest(`/api/v1/admin/tasks/${taskId}`, { method: 'PUT', body: payload })
}

export function previewTaskAudience(payload) {
  return apiRequest('/api/v1/admin/tasks/audience-preview', { method: 'POST', body: payload })
}

export function publishTask(taskId) {
  return apiRequest(`/api/v1/admin/tasks/${taskId}/publish`, { method: 'POST' })
}

export function closeTask(taskId) {
  return apiRequest(`/api/v1/admin/tasks/${taskId}/close`, { method: 'POST' })
}

export function deleteTask(taskId) {
  return apiRequest(`/api/v1/admin/tasks/${taskId}`, { method: 'DELETE' })
}

export function supplementTaskAssignments(taskId, payload) {
  return apiRequest(`/api/v1/admin/tasks/${taskId}/assignments`, { method: 'POST', body: payload })
}

export function removeTaskAssignment(taskId, assignmentId) {
  return apiRequest(`/api/v1/admin/tasks/${taskId}/assignments/${assignmentId}`, { method: 'DELETE' })
}

export function getTaskProgress(taskId) {
  return apiRequest(`/api/v1/admin/tasks/${taskId}/progress`)
}

export function getMyTasks() {
  return apiRequest('/api/v1/tasks')
}

export function getMyTask(assignmentId) {
  return apiRequest(`/api/v1/tasks/${assignmentId}`)
}

export function getMySubtask(assignmentId, subtaskId) {
  return apiRequest(`/api/v1/tasks/${assignmentId}/subtasks/${subtaskId}`)
}

export function getAdminSubtask(taskId, subtaskId) {
  return apiRequest(`/api/v1/admin/tasks/${taskId}/subtasks/${subtaskId}`)
}

export function submitMySubtask(assignmentId, subtaskId, contentHtml) {
  return apiRequest(`/api/v1/tasks/${assignmentId}/subtasks/${subtaskId}/submission`, {
    method: 'POST',
    body: { contentHtml },
  })
}

/** 管理端：展开某人的子任务提交内容（懒加载）。 */
export function getAssignmentSubtaskSubmissions(taskId, assignmentId) {
  return apiRequest(`/api/v1/admin/tasks/${taskId}/assignments/${assignmentId}/subtasks`)
}

export function submitMyTask(assignmentId, completionNote) {
  return apiRequest(`/api/v1/tasks/${assignmentId}/submission`, {
    method: 'POST',
    body: { completionNote },
  })
}

export function listTaskMemberOptions() {
  return apiRequest('/api/v1/admin/tasks/member-options')
}

export function getMemberRankingPage(period = 'TOTAL', page = 0) {
  return apiRequest(`/api/v1/points/preview?period=${encodeURIComponent(period)}&page=${page}`)
}
export function getPublicFundSummary() {
  return apiRequest('/api/v1/public/fund/summary', { authenticated: false })
}
export function getFundSummary() {
  return apiRequest('/api/v1/fund')
}
export function getFundEntries(filters) {
  return apiRequest(`/api/v1/fund/entries?${new URLSearchParams(filters)}`)
}
export function createFundEntry(payload, initialize = false) {
  return apiRequest(initialize ? '/api/v1/fund/initialize' : '/api/v1/fund/entries', { method: 'POST', body: payload })
}
export function reverseFundEntry(id, payload) {
  return apiRequest(`/api/v1/fund/entries/${id}/reverse`, { method: 'POST', body: payload })
}

export function replaceCompetitionRegistration(id, registration) {
  const form = new FormData()
  form.append('registration', registration)
  return formRequest(`/api/v1/competitions/${id}/registration`, { method: 'PUT', body: form })
}

function notifyDomainMutation(path) {
  const kind = /\/fund(?:\/|$)/.test(path)
    ? 'fund'
    : /\/(tasks|bounties|projects|competitions|recruitment)(?:\/|$)/.test(path)
      ? 'deadlines'
      : null
  if (!kind) return
  window.dispatchEvent(new Event(`yeslab:${kind}-changed`))
  try {
    localStorage.setItem(`yeslab-${kind}-changed`, `${Date.now()}-${crypto.randomUUID()}`)
  } catch {
    /* Browser storage may be unavailable. */
  }
}
export function getDeadlines(personal, filters) {
  return apiRequest(`/api/v1/${personal ? 'me' : 'public'}/deadlines?${new URLSearchParams(filters)}`, {
    authenticated: personal,
  })
}

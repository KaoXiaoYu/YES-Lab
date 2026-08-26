import { reactive } from 'vue'

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')
const tokenKey = 'yeslab_access_token'

export const authState = reactive({
  token: sessionStorage.getItem(tokenKey),
  account: null,
  ready: false,
})

let restorePromise

export async function restoreSession() {
  if (authState.ready) return authState.account
  if (restorePromise) return restorePromise
  restorePromise = (async () => {
    if (!authState.token) {
      authState.ready = true
      return null
    }
    try {
      authState.account = await apiRequest('/api/v1/auth/me')
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
  const response = await apiRequest('/api/v1/auth/register', { method: 'POST', body: credentials, authenticated: false })
  setSession(response)
  return response.account
}

export function logout() {
  clearSession()
}

export function getOwnProfile() {
  return apiRequest('/api/v1/member/profile')
}

export async function updateOwnProfile(payload) {
  const profile = await apiRequest('/api/v1/member/profile', { method: 'PUT', body: payload })
  if (authState.account) {
    authState.account.displayName = profile.name
    authState.account.avatarUrl = profile.avatarUrl
  }
  return profile
}

export function listMembers() {
  return apiRequest('/api/v1/admin/members')
}

export function updateMember(profileId, payload) {
  return apiRequest(`/api/v1/admin/members/${profileId}`, { method: 'PUT', body: payload })
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

export function listCompetitions() { return apiRequest('/api/v1/competitions') }
export function getCompetition(id) { return apiRequest(`/api/v1/competitions/${id}`) }
export function listCompetitionMemberOptions() { return apiRequest('/api/v1/competitions/member-options') }
export function listCompetitionProjectOptions() { return apiRequest('/api/v1/competitions/project-options') }
export function createCompetition(payload, certificate, images = []) {
  const form = new FormData()
  form.append('data', new Blob([JSON.stringify(payload)], { type: 'application/json' }))
  if (certificate) form.append('certificate', certificate)
  images.forEach((image) => form.append('images', image))
  return formRequest('/api/v1/competitions', { method: 'POST', body: form })
}
export function updateCompetition(id, payload) { return apiRequest(`/api/v1/competitions/${id}`, { method: 'PUT', body: payload }) }
export function replaceCompetitionCertificate(id, certificate) {
  const form = new FormData(); form.append('certificate', certificate)
  return formRequest(`/api/v1/competitions/${id}/certificate`, { method: 'PUT', body: form })
}
export function replaceCompetitionImages(id, images, descriptions = []) {
  const form = new FormData(); images.forEach((image) => form.append('images', image)); descriptions.forEach((item) => form.append('descriptions', item))
  return formRequest(`/api/v1/competitions/${id}/images`, { method: 'PUT', body: form })
}
export function reviewCompetition(id, payload) { return apiRequest(`/api/v1/admin/achievements/competitions/${id}/review`, { method: 'PATCH', body: payload }) }
export function updateCompetitionDisplay(id, payload) { return apiRequest(`/api/v1/admin/achievements/competitions/${id}/display`, { method: 'PATCH', body: payload }) }
export function listManagedNews() { return apiRequest('/api/v1/admin/achievements/news') }
export function createNews(payload) { return apiRequest('/api/v1/admin/achievements/news', { method: 'POST', body: payload }) }
export function updateNews(id, payload) { return apiRequest(`/api/v1/admin/achievements/news/${id}`, { method: 'PUT', body: payload }) }
export async function getAuthenticatedFile(path) {
  const response = await fetch(`${apiBaseUrl}${path}`, { headers: { Authorization: `Bearer ${authState.token}` } })
  if (!response.ok) throw new ApiError(`文件读取失败（${response.status}）`, {}, response.status)
  return URL.createObjectURL(await response.blob())
}

export function getOwnApplication() {
  return apiRequest('/api/v1/recruitment/me')
}

export function saveOwnApplication(payload) {
  return apiRequest('/api/v1/recruitment/me', { method: 'PUT', body: payload })
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
  return apiRequest(`/api/v1/admin/recruitment/applications/${applicationId}/interview`, { method: 'PUT', body: payload })
}

export function convertRecruitmentToMember(applicationId, payload) {
  return apiRequest(`/api/v1/admin/recruitment/applications/${applicationId}/convert`, { method: 'POST', body: payload })
}

async function apiRequest(path, options = {}) {
  const headers = { Accept: 'application/json' }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (options.authenticated !== false && authState.token) headers.Authorization = `Bearer ${authState.token}`

  let response
  try {
    response = await fetch(`${apiBaseUrl}${path}`, {
      method: options.method || 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    })
  } catch {
    throw new ApiError('无法连接后端服务，请确认 Spring Boot 已启动。')
  }

  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    if (response.status === 401 && options.authenticated !== false) clearSession()
    throw new ApiError(payload?.message || `请求失败（${response.status}）`, payload?.fields || {}, response.status)
  }
  return payload?.data ?? null
}

async function formRequest(path, options) {
  let response
  try {
    response = await fetch(`${apiBaseUrl}${path}`, {
      method: options.method,
      headers: { Accept: 'application/json', ...(authState.token ? { Authorization: `Bearer ${authState.token}` } : {}) },
      body: options.body,
    })
  } catch { throw new ApiError('无法连接后端服务，请确认 Spring Boot 已启动。') }
  const payload = await response.json().catch(() => null)
  if (!response.ok) throw new ApiError(payload?.message || `请求失败（${response.status}）`, payload?.fields || {}, response.status)
  return payload?.data ?? null
}

function setSession(response) {
  authState.token = response.accessToken
  authState.account = response.account
  authState.ready = true
  sessionStorage.setItem(tokenKey, response.accessToken)
}

function clearSession() {
  sessionStorage.removeItem(tokenKey)
  authState.token = null
  authState.account = null
  authState.ready = true
  restorePromise = null
}

export class ApiError extends Error {
  constructor(message, fields = {}, status = 0) {
    super(message)
    this.fields = fields
    this.status = status
  }
}

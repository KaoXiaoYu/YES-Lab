const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

export async function fetchPublicHome(onCoreLoaded) {
  try {
    const home = await fetchPublicData('/api/v1/public/home')
    if (onCoreLoaded) onCoreLoaded(normalizeHome(home))
    const [publicProfiles, publicProjects, competitions, news] = await Promise.all([
      fetchPublicData('/api/v1/public/member-profiles').catch(() => []),
      fetchPublicData('/api/v1/public/project-teams').catch(() => []),
      fetchPublicData('/api/v1/public/competitions').catch(() => []),
      fetchPublicData('/api/v1/public/news').catch(() => []),
    ])
    return normalizeHome(home, publicProfiles, publicProjects, competitions, news)
  } catch (error) {
    console.warn('YES Lab API 暂不可用，继续使用内置展示数据。', error)
    return null
  }
}

export async function fetchPublicCompetition(id) {
  const item = await fetchPublicData(`/api/v1/public/competitions/${encodeURIComponent(id)}`)
  return normalizeCompetition(item)
}

export async function fetchPublicMemberProfile(profileId) {
  try {
    return await fetchPublicData(`/api/v1/public/member-profiles/${encodeURIComponent(profileId)}`)
  } catch (error) {
    try {
      const legacy = await fetchPublicData(`/api/v1/public/members/${encodeURIComponent(profileId)}`)
      const [grade, major] = (legacy.gradeAndMajor || '').split('·').map((item) => item.trim())
      return {
        id: legacy.slug,
        role: legacy.core ? 'CORE_STUDENT' : 'MEMBER',
        name: legacy.name,
        grade,
        major,
        className: null,
        avatarUrl: null,
        status: 'OFFICIAL',
        skillTags: legacy.tags,
        headline: 'YES Lab 成员',
        profileHtml: '<p>成员详细主页内容正在完善中。</p>',
        totalPoints: legacy.points,
        currentRank: legacy.rank,
        projectRecords: [],
        achievementRecords: [],
      }
    } catch {
      console.warn('成员公开主页暂不可用。', error)
      return null
    }
  }
}

async function fetchPublicData(path) {
  const response = await fetch(`${apiBaseUrl}${path}`, { headers: { Accept: 'application/json' } })
  if (!response.ok) throw new Error(`Public API responded with ${response.status}`)
  const payload = await response.json()
  return payload.data
}

function normalizeHome(home, publicProfiles = [], publicProjects = [], competitions = [], news = []) {
  const homepageContent = home.homepageContent || null
  const selectedAdvisorId = homepageContent?.advisorProfileId
  const teacher = publicProfiles.find((member) => member.id === selectedAdvisorId)
    || publicProfiles.find((member) => member.role === 'TEACHER')
  const featuredMemberIds = homepageContent?.featuredMemberProfileIds || []
  const featuredProjectIds = homepageContent?.featuredProjectIds || []
  const managedMembers = publicProfiles.filter((member) => member.role !== 'TEACHER').map(toShowcaseMember)
  const orderedMembers = orderBySelection(managedMembers, featuredMemberIds, (member) => member.profileId)
    .map((member) => ({ ...member, core: featuredMemberIds.length ? featuredMemberIds.includes(member.profileId) : member.core }))
  const managedProjects = publicProjects.length
    ? publicProjects.map(toShowcaseProject)
    : home.projects
  const orderedProjects = orderBySelection(managedProjects, featuredProjectIds, (project) => project.slug)
  const visibleProjects = featuredProjectIds.length
    ? orderedProjects.filter((project) => featuredProjectIds.includes(project.slug))
    : orderedProjects
  return {
    profile: home.profile,
    homepageContent,
    advisor: teacher ? {
      profileId: teacher.id,
      initials: initialsFor(teacher.name),
      name: teacher.name,
      role: 'YES Lab 指导老师',
      description: teacher.headline || '负责实验室研究方向、项目实践与人才培养指导。',
      tags: teacher.skillTags,
      avatarUrl: teacher.avatarUrl,
    } : home.advisor || null,
    statistics: {
      ...home.statistics,
      ...(publicProfiles.length ? { members: publicProfiles.length } : {}),
      ...(publicProjects.length ? { activeProjects: publicProjects.filter((project) => !['COMPLETED', 'ARCHIVED'].includes(project.status)).length } : {}),
    },
    projects: visibleProjects.map((project, index) => ({
      ...project,
      number: String(index + 1).padStart(2, '0'),
      members: project.memberCount > 0 ? `${project.memberCount} 人` : '待补充',
    })),
    members: (orderedMembers.length ? orderedMembers : home.members).map((member) => ({
      ...member,
      role: member.role || member.gradeAndMajor,
    })),
    rankingData: Object.fromEntries(
      Object.entries(home.rankings).map(([board, entries]) => [board, entries.map((entry) => entry.points)]),
    ),
    updates: home.updates.map((item) => ({
      ...item,
      date: item.publishedAt || '最新',
    })),
    awards: home.awards || [],
    competitionResults: competitions.map(normalizeCompetition),
    news: news.map((item) => ({ ...item, date: item.publishedDate, type: item.sourceName, url: item.sourceUrl })),
    sponsors: home.sponsors || [],
  }
}

function orderBySelection(items, selectedIds, getId) {
  if (!selectedIds?.length) return items
  const order = new Map(selectedIds.map((id, index) => [id, index]))
  return [...items].sort((left, right) => {
    const leftOrder = order.has(getId(left)) ? order.get(getId(left)) : Number.MAX_SAFE_INTEGER
    const rightOrder = order.has(getId(right)) ? order.get(getId(right)) : Number.MAX_SAFE_INTEGER
    return leftOrder - rightOrder
  })
}

function normalizeCompetition(item) {
  return {
    ...item,
    images: (item.images || []).map((image) => ({ ...image, url: `${apiBaseUrl}${image.url}` })),
    certificateUrl: item.certificateUrl ? `${apiBaseUrl}${item.certificateUrl}` : null,
  }
}

function toShowcaseProject(project, index) {
  const typeLabels = { COMPETITION: '竞赛', RESEARCH: '科研', INTERNAL: '内部项目', OPEN_SOURCE: '开源' }
  const statusLabels = { PLANNING: '筹备中', ACTIVE: '进行中', PAUSED: '已暂停', COMPLETED: '已完成', ARCHIVED: '已归档' }
  return {
    slug: project.id,
    number: String(index + 1).padStart(2, '0'),
    category: typeLabels[project.type] || project.type,
    title: project.projectName,
    summary: project.description,
    status: statusLabels[project.status] || project.status,
    lead: project.leader?.name || '待指定',
    advisor: project.advisor?.name || '暂未关联',
    memberCount: project.members?.length || 0,
    tech: project.requiredSkillTags || [],
    result: project.outcomes || project.progressDescription || '项目团队正在持续推进阶段目标。',
    repositoryUrl: project.gitRepositoryUrl || '',
    documentUrl: project.documentUrl || '',
    coverImageUrl: project.coverImageUrl ? `${apiBaseUrl}${project.coverImageUrl}` : '',
  }
}

function toShowcaseMember(member, index) {
  const education = [member.grade, member.major].filter(Boolean).join(' · ')
  return {
    profileId: member.id,
    slug: member.id,
    initials: initialsFor(member.name),
    name: member.name,
    role: education || (member.role === 'CORE_STUDENT' ? '核心成员' : '实验室成员'),
    gradeAndMajor: education,
    tags: member.skillTags,
    points: member.totalPoints || 0,
    rank: member.currentRank || index + 1,
    core: member.role === 'CORE_STUDENT',
    avatarUrl: member.avatarUrl,
  }
}

function initialsFor(name = '') {
  const compact = name.trim().replace(/\s+/g, '')
  return compact.slice(0, 2).toUpperCase() || 'YL'
}

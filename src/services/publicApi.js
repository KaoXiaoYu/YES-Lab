const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')

export async function fetchPublicHome() {
  if (!apiBaseUrl) return null

  try {
    const response = await fetch(`${apiBaseUrl}/api/v1/public/home`, {
      headers: { Accept: 'application/json' },
    })

    if (!response.ok) throw new Error(`Public API responded with ${response.status}`)
    const payload = await response.json()
    return normalizeHome(payload.data)
  } catch (error) {
    console.warn('YES Lab API 暂不可用，继续使用内置展示数据。', error)
    return null
  }
}

function normalizeHome(home) {
  return {
    profile: home.profile,
    statistics: home.statistics,
    projects: home.projects.map((project) => ({
      ...project,
      members: project.memberCount > 0 ? `${project.memberCount} 人` : '待补充',
    })),
    members: home.members.map((member) => ({
      ...member,
      role: member.gradeAndMajor,
    })),
    rankingData: Object.fromEntries(
      Object.entries(home.rankings).map(([board, entries]) => [board, entries.map((entry) => entry.points)]),
    ),
    updates: home.updates.map((item) => ({
      ...item,
      date: item.publishedAt || '最新',
    })),
    sponsors: home.sponsors || [],
  }
}

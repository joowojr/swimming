export interface RecentMusicEntry {
  url: string
  playedAt: string
}

const HISTORY_LIMIT = 5
const HISTORY_RETENTION_MS = 7 * 24 * 60 * 60 * 1000
const STORAGE_KEY_PREFIX = 'swimming:youtube-history:v1:user'
const IDENTIFIER_PATTERN = /^[A-Za-z0-9_-]+$/

function validIdentifier(value: string | null) {
  return value !== null
    && value.length > 0
    && value.length <= 256
    && IDENTIFIER_PATTERN.test(value)
}

export function normalizeYouTubeUrl(value: string): string | null {
  try {
    const source = new URL(value.trim())
    if ((source.protocol !== 'http:' && source.protocol !== 'https:')
      || source.username
      || source.password) {
      return null
    }

    const hostname = source.hostname.toLowerCase()
    let videoId: string | null = null
    let playlistId: string | null = null

    if (hostname === 'youtu.be') {
      videoId = source.pathname.split('/').filter(Boolean)[0] ?? null
      playlistId = source.searchParams.get('list')
    } else {
      const youtubeHosts = new Set([
        'youtube.com',
        'www.youtube.com',
        'm.youtube.com',
        'music.youtube.com',
        'youtube-nocookie.com',
        'www.youtube-nocookie.com',
      ])
      if (!youtubeHosts.has(hostname)) return null

      const pathParts = source.pathname.split('/').filter(Boolean)
      const isNoCookieHost = hostname === 'youtube-nocookie.com'
        || hostname === 'www.youtube-nocookie.com'
      if (!isNoCookieHost && source.pathname === '/watch') {
        videoId = source.searchParams.get('v')
        playlistId = source.searchParams.get('list')
      } else if (!isNoCookieHost && source.pathname === '/playlist') {
        playlistId = source.searchParams.get('list')
      } else if (pathParts[0] === 'embed' || pathParts[0] === 'shorts') {
        videoId = pathParts[1] ?? null
        playlistId = source.searchParams.get('list')
      } else {
        return null
      }
    }

    if (videoId !== null && !validIdentifier(videoId)) return null
    if (playlistId !== null && !validIdentifier(playlistId)) return null
    if (!videoId && !playlistId) return null

    const normalized = new URL(videoId
      ? 'https://www.youtube.com/watch'
      : 'https://www.youtube.com/playlist')
    if (videoId) normalized.searchParams.set('v', videoId)
    if (playlistId) normalized.searchParams.set('list', playlistId)
    return normalized.toString()
  } catch {
    return null
  }
}

function storageKey(historyOwnerId: number) {
  return `${STORAGE_KEY_PREFIX}:${historyOwnerId}`
}

function isRecentMusicEntry(value: unknown): value is RecentMusicEntry {
  if (typeof value !== 'object' || value === null) return false
  const entry = value as Partial<RecentMusicEntry>
  return typeof entry.url === 'string' && typeof entry.playedAt === 'string'
}

export function readRecentMusicHistory(
  historyOwnerId: number | null,
  now = Date.now(),
): RecentMusicEntry[] {
  if (historyOwnerId === null || !Number.isSafeInteger(historyOwnerId) || historyOwnerId <= 0) {
    return []
  }

  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(storageKey(historyOwnerId)) ?? '[]')
    if (!Array.isArray(parsed)) return []

    const cutoff = now - HISTORY_RETENTION_MS
    const entries = new Map<string, RecentMusicEntry>()
    parsed.filter(isRecentMusicEntry).forEach((entry) => {
      const url = normalizeYouTubeUrl(entry.url)
      const playedAtTime = Date.parse(entry.playedAt)
      if (!url || !Number.isFinite(playedAtTime) || playedAtTime < cutoff || playedAtTime > now) return

      const current = entries.get(url)
      if (!current || Date.parse(current.playedAt) < playedAtTime) {
        entries.set(url, { url, playedAt: new Date(playedAtTime).toISOString() })
      }
    })

    const recentEntries = [...entries.values()]
      .sort((left, right) => Date.parse(right.playedAt) - Date.parse(left.playedAt))
      .slice(0, HISTORY_LIMIT)
    if (recentEntries.length > 0) {
      localStorage.setItem(storageKey(historyOwnerId), JSON.stringify(recentEntries))
    } else {
      localStorage.removeItem(storageKey(historyOwnerId))
    }
    return recentEntries
  } catch {
    return []
  }
}

export function rememberRecentMusic(
  historyOwnerId: number | null,
  source: string,
  now = new Date(),
): RecentMusicEntry[] {
  const url = normalizeYouTubeUrl(source)
  if (historyOwnerId === null || !Number.isSafeInteger(historyOwnerId) || historyOwnerId <= 0 || !url) {
    return readRecentMusicHistory(historyOwnerId, now.getTime())
  }

  const next = [
    { url, playedAt: now.toISOString() },
    ...readRecentMusicHistory(historyOwnerId, now.getTime()).filter((entry) => entry.url !== url),
  ].slice(0, HISTORY_LIMIT)

  try {
    localStorage.setItem(storageKey(historyOwnerId), JSON.stringify(next))
  } catch {
    // 저장소가 차단되거나 가득 차도 현재 재생은 계속한다.
  }
  return next
}

export function clearRecentMusicHistory(historyOwnerId: number | null) {
  if (historyOwnerId === null || !Number.isSafeInteger(historyOwnerId) || historyOwnerId <= 0) return
  try {
    localStorage.removeItem(storageKey(historyOwnerId))
  } catch {
    // 저장소를 사용할 수 없는 환경에서는 지울 기록도 표시되지 않는다.
  }
}

/**
 * YouTube oEmbed로 영상·재생목록의 제목과 채널명을 읽는다.
 *
 * `api/client`를 쓰지 않는다. 그쪽은 우리 서버용이라 Authorization 헤더를 붙이는데,
 * 그 토큰이 YouTube로 나가면 안 된다. 그래서 인증을 붙이지 않는 fetch를 여기서만 쓴다.
 * 키도 서버도 필요 없고, 실패하면 null이라 호출부는 이름 없이 그대로 동작한다.
 */
const OEMBED_ENDPOINT = 'https://www.youtube.com/oembed'
const TEXT_LIMIT = 200

export interface YouTubeMeta {
  title: string
  author?: string
}

function text(value: unknown) {
  return typeof value === 'string' && value.trim()
    ? value.trim().slice(0, TEXT_LIMIT)
    : undefined
}

export async function fetchYouTubeMeta(
  url: string,
  signal?: AbortSignal,
): Promise<YouTubeMeta | null> {
  try {
    const response = await fetch(
      `${OEMBED_ENDPOINT}?url=${encodeURIComponent(url)}&format=json`,
      { signal, credentials: 'omit', referrerPolicy: 'no-referrer' },
    )
    if (!response.ok) return null

    const payload: unknown = await response.json()
    if (typeof payload !== 'object' || payload === null) return null

    const title = text((payload as Record<string, unknown>).title)
    if (!title) return null
    return { title, author: text((payload as Record<string, unknown>).author_name) }
  } catch {
    // 비공개 영상, 오프라인, 요청 취소. 이름 없이 주소만으로도 재생에는 지장이 없다.
    return null
  }
}

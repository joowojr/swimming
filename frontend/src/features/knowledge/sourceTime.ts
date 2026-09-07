const relativeFormatter = new Intl.RelativeTimeFormat('ko', { numeric: 'auto' })

const savedDateFormatter = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
})

const MINUTE = 60_000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

/**
 * 저장한 시각을 사람이 읽는 말로 바꾼다.
 *
 * 일주일이 넘으면 "8일 전"보다 날짜가 쓸모 있어 날짜로 적는다.
 */
export function formatSavedAt(createdAt: string | null) {
  if (!createdAt) return null

  const saved = new Date(createdAt)
  if (Number.isNaN(saved.getTime())) return null

  const elapsed = Date.now() - saved.getTime()
  if (elapsed < MINUTE) return '방금 전'
  if (elapsed < HOUR) return relativeFormatter.format(-Math.floor(elapsed / MINUTE), 'minute')
  if (elapsed < DAY) return relativeFormatter.format(-Math.floor(elapsed / HOUR), 'hour')
  if (elapsed < 7 * DAY) return relativeFormatter.format(-Math.floor(elapsed / DAY), 'day')

  return savedDateFormatter.format(saved)
}

/**
 * 화면에서 다루는 날짜 문자열(YYYY-MM-DD)의 형식과 해석을 한 쌍으로 정의한다.
 * 사용자가 보는 "오늘"을 기준으로 하므로 UTC로 변환하지 않는다. toISOString()을 쓰면 하루가 밀린다.
 */
export function formatLocalDate(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function parseLocalDate(value: string) {
  return new Date(`${value}T00:00:00`)
}

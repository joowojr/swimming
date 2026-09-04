import { formatLocalDate } from '../../lib/date'

/** 계획 조회는 달 단위다. 여기 있는 것은 그 달 경계를 다루는 계획 전용 헬퍼다. */
export function monthKeyOf(date: string) {
  return date.slice(0, 7)
}

export function monthRange(month: Date) {
  const first = new Date(month.getFullYear(), month.getMonth(), 1)
  const last = new Date(month.getFullYear(), month.getMonth() + 1, 0)
  return { fromDate: formatLocalDate(first), toDate: formatLocalDate(last) }
}

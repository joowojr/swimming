import { client } from '../../api/client'
import { displayNamesOf } from './holidayNames'
import type { HolidayMonth } from './holidayTypes'

export async function getHolidayMonth(year: number, month: number): Promise<HolidayMonth> {
  const response = await client.get<HolidayMonth>('/calendar/holiday', {
    params: { year, month },
  })

  // 법정 명칭을 일상에서 쓰는 이름으로 바꾼 뒤 넘긴다. 아래 단계는 표시 이름만 본다.
  return {
    ...response.data,
    holidays: response.data.holidays.map((holiday) => ({
      ...holiday,
      names: displayNamesOf(holiday.date, holiday.names),
    })),
  }
}

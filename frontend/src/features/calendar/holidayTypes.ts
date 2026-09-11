/** 같은 날짜에 겹친 공휴일은 names에 모여 온다. */
export interface Holiday {
  date: string
  names: string[]
}

export interface HolidayMonth {
  year: number
  month: number
  holidays: Holiday[]
}

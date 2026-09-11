/**
 * 양력 고정일 공휴일의 표시 이름.
 *
 * 백엔드는 한국천문연구원이 내려준 법정 명칭을 그대로 전달한다. 그 표기가 일상에서 쓰는 말과
 * 다른 경우가 있다(12월 25일이 `기독탄신일`로 온다). 데이터의 단일 출처는 그대로 두고 보이는
 * 이름만 여기서 정한다.
 *
 * 키는 `MM-DD`다. 여기 담는 것은 해마다 같은 양력 날짜에 오는 날뿐이므로 날짜가 이름보다
 * 안정적이다. 공급자가 표기를 바꿔도 영향받지 않는다. 음력으로 날짜가 움직이는 설날·추석은
 * 대상이 아니다.
 */
const DISPLAY_NAMES: Record<string, string> = {
  '01-01': '새해',
  '12-25': '크리스마스',
}

/** @param date `YYYY-MM-DD` */
export function displayNamesOf(date: string, officialNames: string[]): string[] {
  const override = DISPLAY_NAMES[date.slice(5)]
  return override ? [override] : officialNames
}

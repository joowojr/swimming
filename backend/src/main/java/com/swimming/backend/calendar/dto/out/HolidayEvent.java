package com.swimming.backend.calendar.dto.out;

import java.time.LocalDate;

/** 외부 공휴일 공급자가 반환하는 공급자 중립적인 날짜 이벤트. */
public record HolidayEvent(LocalDate date, String name) {
}

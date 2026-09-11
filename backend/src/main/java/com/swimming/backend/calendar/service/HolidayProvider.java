package com.swimming.backend.calendar.service;

import com.swimming.backend.calendar.dto.out.HolidayEvent;

import java.time.YearMonth;
import java.util.List;

/**
 * 공휴일 원본의 교체 경계. 구현체는 외부 API의 DTO 대신 날짜와 이름만 반환한다.
 */
public interface HolidayProvider {

    List<HolidayEvent> getHolidays(YearMonth month);
}

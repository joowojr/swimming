package com.swimming.backend.calendar.domain;

import java.time.LocalDate;
import java.util.List;

/** 같은 날짜의 공휴일 명칭을 합친 애플리케이션 내부 값. */
public record PublicHoliday(LocalDate date, List<String> names) {

    public PublicHoliday {
        names = List.copyOf(names);
    }
}

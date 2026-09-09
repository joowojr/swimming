package com.swimming.backend.calendar.dto.in;

import com.swimming.backend.calendar.domain.PublicHoliday;

import java.time.LocalDate;
import java.util.List;

public record HolidayResponse(LocalDate date, List<String> names) {

    public HolidayResponse {
        names = List.copyOf(names);
    }

    public static HolidayResponse from(PublicHoliday holiday) {
        return new HolidayResponse(holiday.date(), holiday.names());
    }
}

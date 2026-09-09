package com.swimming.backend.calendar.dto.in;

import com.swimming.backend.calendar.domain.PublicHoliday;

import java.time.YearMonth;
import java.util.List;

public record HolidayMonthResponse(int year, int month, List<HolidayResponse> holidays) {

    public HolidayMonthResponse {
        holidays = List.copyOf(holidays);
    }

    public static HolidayMonthResponse from(YearMonth month, List<PublicHoliday> holidays) {
        return new HolidayMonthResponse(
                month.getYear(),
                month.getMonthValue(),
                holidays.stream().map(HolidayResponse::from).toList()
        );
    }
}

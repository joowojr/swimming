package com.swimming.backend.calendar.controller;

import com.swimming.backend.calendar.dto.in.HolidayMonthResponse;
import com.swimming.backend.calendar.usecase.HolidayUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "공휴일", description = "Pinboard 캘린더의 월별 공휴일.")
@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final HolidayUseCase holidayUseCase;

    @GetMapping("/holiday")
    public ResponseEntity<HolidayMonthResponse> getMonth(
            @RequestParam int year,
            @RequestParam int month
    ) {
        return ResponseEntity.ok(holidayUseCase.getMonth(year, month));
    }
}

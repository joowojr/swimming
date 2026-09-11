package com.swimming.backend.calendar.controller;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.common.exception.GlobalExceptionHandler;
import com.swimming.backend.calendar.dto.in.HolidayMonthResponse;
import com.swimming.backend.calendar.dto.in.HolidayResponse;
import com.swimming.backend.calendar.usecase.HolidayUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CalendarControllerTest {

    private HolidayUseCase useCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        useCase = mock(HolidayUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CalendarController(useCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("연도와 월로 공휴일을 조회한다")
    void getsMonth() throws Exception {
        when(useCase.getMonth(2026, 9)).thenReturn(new HolidayMonthResponse(
                2026,
                9,
                List.of(new HolidayResponse(LocalDate.of(2026, 9, 1), List.of("공휴일")))
        ));

        mockMvc.perform(get("/api/calendar/holiday").queryParam("year", "2026").queryParam("month", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.month").value(9))
                .andExpect(jsonPath("$.holidays[0].date").value("2026-09-01"))
                .andExpect(jsonPath("$.holidays[0].names[0]").value("공휴일"));
    }

    @Test
    @DisplayName("공휴일 공급자가 연결되지 않았으면 503 ProblemDetail을 반환한다")
    void returnsServiceUnavailableWithoutProvider() throws Exception {
        when(useCase.getMonth(2026, 9))
                .thenThrow(new BusinessException(ErrorCode.HOLIDAY_PROVIDER_UNAVAILABLE));

        mockMvc.perform(get("/api/calendar/holiday").queryParam("year", "2026").queryParam("month", "9"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("HOLIDAY_PROVIDER_UNAVAILABLE"));
    }
}

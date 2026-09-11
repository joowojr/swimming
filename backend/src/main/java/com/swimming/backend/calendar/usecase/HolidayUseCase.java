package com.swimming.backend.calendar.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.calendar.dto.in.HolidayMonthResponse;
import com.swimming.backend.calendar.service.HolidayService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
public class HolidayUseCase {

    private final HolidayService holidayService;

    public HolidayMonthResponse getMonth(int year, int month) {
        if (year < 1000 || year > 9999) {
            throw new BusinessException(ErrorCode.INVALID_HOLIDAY_MONTH);
        }

        try {
            YearMonth requestedMonth = YearMonth.of(year, month);
            return HolidayMonthResponse.from(requestedMonth, holidayService.getHolidays(requestedMonth));
        } catch (DateTimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_HOLIDAY_MONTH, exception);
        }
    }
}

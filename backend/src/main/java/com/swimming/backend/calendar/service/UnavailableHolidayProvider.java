package com.swimming.backend.calendar.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.calendar.dto.out.HolidayEvent;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.List;

/** 외부 공급자를 선택하기 전까지 빈 공휴일을 정상 데이터로 가장하지 않는다. */
@Component
public class UnavailableHolidayProvider implements HolidayProvider {

    @Override
    public List<HolidayEvent> getHolidays(YearMonth month) {
        throw new BusinessException(ErrorCode.HOLIDAY_PROVIDER_UNAVAILABLE);
    }
}

package com.swimming.backend.calendar.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.calendar.dto.out.HolidayEvent;
import com.swimming.backend.calendar.domain.PublicHoliday;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HolidayServiceTest {

    @Test
    @DisplayName("공급자가 반환한 공휴일을 날짜별로 합치고 월별로 캐시한다")
    void normalizesAndCachesMonth() {
        HolidayProvider provider = mock(HolidayProvider.class);
        YearMonth month = YearMonth.of(2026, 5);
        when(provider.getHolidays(month)).thenReturn(List.of(
                new HolidayEvent(LocalDate.of(2026, 5, 5), " 어린이날 "),
                new HolidayEvent(LocalDate.of(2026, 5, 1), "근로자의 날"),
                new HolidayEvent(LocalDate.of(2026, 5, 5), "부처님 오신 날"),
                new HolidayEvent(LocalDate.of(2026, 5, 5), "어린이날")
        ));
        HolidayService service = new HolidayService(provider);

        List<PublicHoliday> first = service.getHolidays(month);
        List<PublicHoliday> second = service.getHolidays(month);

        assertThat(first).containsExactly(
                new PublicHoliday(LocalDate.of(2026, 5, 1), List.of("근로자의 날")),
                new PublicHoliday(
                        LocalDate.of(2026, 5, 5),
                        List.of("어린이날", "부처님 오신 날")
                )
        );
        assertThat(second).isSameAs(first);
        verify(provider, times(1)).getHolidays(month);
    }

    @Test
    @DisplayName("공급자가 요청 월 밖의 데이터를 반환하면 사용할 수 없는 데이터로 처리한다")
    void rejectsEventOutsideRequestedMonth() {
        HolidayProvider provider = mock(HolidayProvider.class);
        YearMonth month = YearMonth.of(2026, 5);
        when(provider.getHolidays(month)).thenReturn(List.of(
                new HolidayEvent(LocalDate.of(2026, 6, 1), "잘못된 날짜")
        ));
        HolidayService service = new HolidayService(provider);

        assertThatThrownBy(() -> service.getHolidays(month))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.HOLIDAY_PROVIDER_UNAVAILABLE));
    }

    @Test
    @DisplayName("공급자 오류를 성공한 빈 공휴일 목록으로 바꾸지 않는다")
    void preservesUnavailableProviderError() {
        HolidayProvider unavailableProvider = month -> {
            throw new BusinessException(ErrorCode.HOLIDAY_PROVIDER_UNAVAILABLE);
        };
        HolidayService service = new HolidayService(unavailableProvider);

        assertThatThrownBy(() -> service.getHolidays(YearMonth.of(2026, 9)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.HOLIDAY_PROVIDER_UNAVAILABLE));
    }
}

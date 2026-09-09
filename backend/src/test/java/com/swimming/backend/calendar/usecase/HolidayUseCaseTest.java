package com.swimming.backend.calendar.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.calendar.domain.PublicHoliday;
import com.swimming.backend.calendar.dto.in.HolidayMonthResponse;
import com.swimming.backend.calendar.service.HolidayService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HolidayUseCaseTest {

    @Test
    @DisplayName("월별 공휴일을 API 응답으로 변환한다")
    void getsMonth() {
        HolidayService service = mock(HolidayService.class);
        YearMonth month = YearMonth.of(2026, 9);
        when(service.getHolidays(month)).thenReturn(List.of(
                new PublicHoliday(LocalDate.of(2026, 9, 1), List.of("공휴일"))
        ));
        HolidayUseCase useCase = new HolidayUseCase(service);

        HolidayMonthResponse response = useCase.getMonth(2026, 9);

        assertThat(response.year()).isEqualTo(2026);
        assertThat(response.month()).isEqualTo(9);
        assertThat(response.holidays()).hasSize(1);
        assertThat(response.holidays().getFirst().names()).containsExactly("공휴일");
    }

    @Test
    @DisplayName("네 자리 연도가 아니면 공급자를 조회하지 않는다")
    void rejectsInvalidYear() {
        HolidayService service = mock(HolidayService.class);
        HolidayUseCase useCase = new HolidayUseCase(service);

        assertThatThrownBy(() -> useCase.getMonth(999, 1))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_HOLIDAY_MONTH));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("존재하지 않는 월이면 공급자를 조회하지 않는다")
    void rejectsInvalidMonth() {
        HolidayService service = mock(HolidayService.class);
        HolidayUseCase useCase = new HolidayUseCase(service);

        assertThatThrownBy(() -> useCase.getMonth(2026, 13))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_HOLIDAY_MONTH));
        verifyNoInteractions(service);
    }
}

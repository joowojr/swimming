package com.swimming.backend.plan.service;

import com.swimming.backend.plan.domain.DailyPlan;
import com.swimming.backend.plan.repository.DailyPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyPlanServiceTest {

    private DailyPlanRepository dailyPlanRepository;
    private DailyPlanService dailyPlanService;

    @BeforeEach
    void setUp() {
        dailyPlanRepository = mock(DailyPlanRepository.class);
        dailyPlanService = new DailyPlanService(dailyPlanRepository);
    }

    @Test
    @DisplayName("사용자와 날짜로 계획을 조회한다")
    void getsPlanByUserAndDate() {
        LocalDate date = LocalDate.of(2026, 8, 20);
        DailyPlan dailyPlan = DailyPlan.create(1L, date, List.of(10L));
        when(dailyPlanRepository.findByUserIdAndPlanDate(1L, date))
                .thenReturn(Optional.of(dailyPlan));

        assertThat(dailyPlanService.get(1L, date)).contains(dailyPlan);
    }

    @Test
    @DisplayName("사용자의 날짜 범위 계획을 날짜순으로 조회한다")
    void getsPlansForDateRange() {
        LocalDate fromDate = LocalDate.of(2026, 8, 20);
        LocalDate toDate = LocalDate.of(2026, 8, 26);
        List<DailyPlan> expected = List.of(
                DailyPlan.create(1L, fromDate, List.of(1L)),
                DailyPlan.create(1L, toDate, List.of(2L))
        );
        when(dailyPlanRepository
                .findAllByUserIdAndPlanDateBetweenOrderByPlanDateAsc(
                        1L,
                        fromDate,
                        toDate
                )).thenReturn(expected);

        assertThat(dailyPlanService.getRange(1L, fromDate, toDate))
                .isSameAs(expected);
    }

    @Test
    @DisplayName("날짜별 계획과 항목을 생성해 저장한다")
    void createsPlan() {
        LocalDate date = LocalDate.of(2026, 8, 20);
        when(dailyPlanRepository.save(any(DailyPlan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DailyPlan dailyPlan = dailyPlanService.create(1L, date, List.of(2L, 1L));

        assertThat(dailyPlan.getUserId()).isEqualTo(1L);
        assertThat(dailyPlan.getPlanDate()).isEqualTo(date);
        assertThat(dailyPlan.getItems())
                .extracting(item -> item.getTaskId())
                .containsExactly(2L, 1L);
        verify(dailyPlanRepository).save(dailyPlan);
    }

    @Test
    @DisplayName("저장된 계획의 현재 구성을 갱신한다")
    void updatesPlanItems() {
        DailyPlan dailyPlan = DailyPlan.create(
                1L,
                LocalDate.of(2026, 8, 20),
                List.of(1L, 2L)
        );

        DailyPlan result = dailyPlanService.update(dailyPlan, List.of(2L, 3L));

        assertThat(result).isSameAs(dailyPlan);
        assertThat(result.getItems())
                .extracting(item -> item.getTaskId())
                .containsExactly(2L, 3L);
    }

    @Test
    @DisplayName("사용자의 날짜별 계획에 Task가 포함됐는지 확인한다")
    void checksTaskInPlan() {
        LocalDate date = LocalDate.of(2026, 8, 20);
        when(dailyPlanRepository.containsTask(1L, date, 10L)).thenReturn(true);

        assertThat(dailyPlanService.containsTask(1L, date, 10L)).isTrue();
        verify(dailyPlanRepository).containsTask(1L, date, 10L);
    }
}

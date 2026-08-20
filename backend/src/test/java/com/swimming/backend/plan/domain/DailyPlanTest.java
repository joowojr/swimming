package com.swimming.backend.plan.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DailyPlanTest {

    @Test
    @DisplayName("Task ID 순서대로 날짜별 계획 항목을 생성한다")
    void createsItemsInRequestedOrder() {
        DailyPlan dailyPlan = DailyPlan.create(
                1L,
                LocalDate.of(2026, 8, 20),
                List.of(3L, 1L, 2L)
        );

        assertThat(dailyPlan.getItems())
                .extracting(DailyPlanItem::getTaskId)
                .containsExactly(3L, 1L, 2L);
        assertThat(dailyPlan.getItems())
                .extracting(DailyPlanItem::getOrderIdx)
                .containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("기존 항목을 재사용하며 Task 추가 제거와 순서를 갱신한다")
    void replacesItemsAndKeepsRequestedOrder() {
        DailyPlan dailyPlan = DailyPlan.create(
                1L,
                LocalDate.of(2026, 8, 20),
                List.of(1L, 2L, 3L)
        );
        DailyPlanItem retainedItem = dailyPlan.getItems().get(1);

        dailyPlan.replaceItems(List.of(3L, 2L, 4L));

        assertThat(dailyPlan.getItems())
                .extracting(DailyPlanItem::getTaskId)
                .containsExactly(3L, 2L, 4L);
        assertThat(dailyPlan.getItems())
                .extracting(DailyPlanItem::getOrderIdx)
                .containsExactly(0, 1, 2);
        assertThat(dailyPlan.getItems()).contains(retainedItem);
    }
}

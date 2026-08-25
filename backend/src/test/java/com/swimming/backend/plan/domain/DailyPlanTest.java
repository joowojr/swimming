package com.swimming.backend.plan.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DailyPlanTest {

    @Test
    @DisplayName("프로젝트 연결 여부와 관계없이 Task를 같은 계획에 순서대로 추가한다")
    void addsTasks() {
        DailyPlan plan = DailyPlan.create(1L, LocalDate.of(2026, 8, 21));

        plan.addItem(DailyPlanItem.createTask(10L));
        plan.addItem(DailyPlanItem.createTask(20L));

        assertThat(plan.getItems()).extracting(DailyPlanItem::getTaskId)
                .containsExactly(10L, 20L);
        assertThat(plan.getItems()).extracting(DailyPlanItem::getOrderIdx)
                .containsExactly(0, 1);
    }

    @Test
    @DisplayName("항목 ID 목록에 따라 순서를 변경한다")
    void reordersItems() {
        DailyPlan plan = DailyPlan.create(1L, LocalDate.of(2026, 8, 21));
        DailyPlanItem first = DailyPlanItem.restore(1L, 10L, 0, null, null);
        DailyPlanItem second = DailyPlanItem.restore(2L, 20L, 1, null, null);
        plan.addItem(first);
        plan.addItem(second);

        plan.reorder(List.of(2L, 1L));

        assertThat(plan.getItems()).extracting(DailyPlanItem::getId)
                .containsExactly(2L, 1L);
        assertThat(plan.getItems()).extracting(DailyPlanItem::getOrderIdx)
                .containsExactly(0, 1);
    }

    @Test
    @DisplayName("항목을 제거하면 뒤 항목의 순서를 당긴다")
    void removesItem() {
        DailyPlan plan = DailyPlan.create(1L, LocalDate.of(2026, 8, 21));
        DailyPlanItem first = DailyPlanItem.restore(1L, 20L, 0, null, null);
        DailyPlanItem second = DailyPlanItem.restore(2L, 10L, 1, null, null);
        plan.addItem(first);
        plan.addItem(second);

        plan.removeItem(1L);

        assertThat(plan.getItems()).containsExactly(second);
        assertThat(second.getOrderIdx()).isZero();
    }
}

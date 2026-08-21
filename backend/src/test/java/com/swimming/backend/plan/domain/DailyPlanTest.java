package com.swimming.backend.plan.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyPlanTest {

    @Test
    @DisplayName("Task와 독립 할 일을 같은 계획에 순서대로 추가한다")
    void addsTaskAndAdHocItem() {
        DailyPlan plan = DailyPlan.create(1L, LocalDate.of(2026, 8, 21));

        plan.addItem(DailyPlanItem.createTask(10L));
        plan.addItem(DailyPlanItem.createAdHoc("장보기"));

        assertThat(plan.getItems()).extracting(DailyPlanItem::getTaskId)
                .containsExactly(10L, null);
        assertThat(plan.getItems()).extracting(DailyPlanItem::getTitle)
                .containsExactly(null, "장보기");
        assertThat(plan.getItems()).extracting(DailyPlanItem::getOrderIdx)
                .containsExactly(0, 1);
    }

    @Test
    @DisplayName("항목 ID 목록에 따라 순서를 변경한다")
    void reordersItems() {
        DailyPlan plan = DailyPlan.create(1L, LocalDate.of(2026, 8, 21));
        DailyPlanItem first = DailyPlanItem.restore(1L, 10L, null, 0, null, null);
        DailyPlanItem second = DailyPlanItem.restore(2L, null, "장보기", 1, null, null);
        plan.addItem(first);
        plan.addItem(second);

        plan.reorder(List.of(2L, 1L));

        assertThat(plan.getItems()).extracting(DailyPlanItem::getId)
                .containsExactly(2L, 1L);
        assertThat(plan.getItems()).extracting(DailyPlanItem::getOrderIdx)
                .containsExactly(0, 1);
    }

    @Test
    @DisplayName("독립 할 일 제목을 바꾸고 항목을 제거하면 순서를 당긴다")
    void updatesAndRemovesItem() {
        DailyPlan plan = DailyPlan.create(1L, LocalDate.of(2026, 8, 21));
        DailyPlanItem first = DailyPlanItem.restore(1L, null, "장보기", 0, null, null);
        DailyPlanItem second = DailyPlanItem.restore(2L, 10L, null, 1, null, null);
        plan.addItem(first);
        plan.addItem(second);

        first.changeAdHocTitle("책 반납");
        plan.removeItem(1L);

        assertThat(first.getTitle()).isEqualTo("책 반납");
        assertThat(plan.getItems()).containsExactly(second);
        assertThat(second.getOrderIdx()).isZero();
    }

    @Test
    @DisplayName("Task 기반 항목의 제목 변경을 거부한다")
    void rejectsChangingTaskItemTitle() {
        DailyPlan plan = DailyPlan.create(1L, LocalDate.of(2026, 8, 21));
        DailyPlanItem taskItem = DailyPlanItem.createTask(10L);
        plan.addItem(taskItem);

        assertThatThrownBy(() -> taskItem.changeAdHocTitle("제목 변경"))
                .isInstanceOf(IllegalStateException.class);
    }
}

package com.swimming.backend.plan.repository.entity;

import com.swimming.backend.plan.domain.DailyPlan;
import com.swimming.backend.plan.domain.DailyPlanItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DailyPlanEntityTest {

    @Test
    @DisplayName("Domain의 신규·수정·삭제 항목을 영속 Entity 컬렉션에 반영한다")
    void appliesDomainItems() {
        LocalDate date = LocalDate.of(2026, 8, 21);
        DailyPlan initial = DailyPlan.create(1L, date);
        initial.addItem(DailyPlanItem.createTask(10L));
        initial.addItem(DailyPlanItem.createAdHoc("장보기"));
        DailyPlanEntity entity = DailyPlanEntity.from(initial);
        ReflectionTestUtils.setField(entity, "id", 1L);
        ReflectionTestUtils.setField(entity.getItems().get(0), "id", 1L);
        ReflectionTestUtils.setField(entity.getItems().get(1), "id", 2L);

        DailyPlan changed = DailyPlan.restore(
                1L,
                1L,
                date,
                null,
                null,
                List.of(
                        DailyPlanItem.restore(2L, null, "책 반납", 0, null, null),
                        DailyPlanItem.createTask(20L)
                )
        );

        entity.apply(changed);

        assertThat(entity.getItems()).extracting(DailyPlanItemEntity::getId)
                .containsExactly(2L, null);
        assertThat(entity.toDomain().getItems()).extracting(DailyPlanItem::getTitle)
                .containsExactly("책 반납", null);
        assertThat(entity.toDomain().getItems()).extracting(DailyPlanItem::getTaskId)
                .containsExactly(null, 20L);
    }
}

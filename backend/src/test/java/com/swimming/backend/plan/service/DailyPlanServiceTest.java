package com.swimming.backend.plan.service;

import com.swimming.backend.plan.domain.DailyPlan;
import com.swimming.backend.plan.domain.DailyPlanItem;
import com.swimming.backend.plan.repository.DailyPlanRepository;
import com.swimming.backend.plan.repository.entity.DailyPlanEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyPlanServiceTest {

    private DailyPlanRepository repository;
    private DailyPlanService service;

    @BeforeEach
    void setUp() {
        repository = mock(DailyPlanRepository.class);
        service = new DailyPlanService(repository);
        when(repository.saveAndFlush(any(DailyPlanEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("사용자와 날짜로 계획을 순수 도메인으로 조회한다")
    void getsPlan() {
        LocalDate date = LocalDate.of(2026, 8, 21);
        DailyPlanEntity entity = entity(1L, date);
        when(repository.findByUserIdAndPlanDate(1L, date)).thenReturn(Optional.of(entity));

        assertThat(service.get(1L, date)).get().satisfies(plan -> {
            assertThat(plan.getId()).isEqualTo(1L);
            assertThat(plan.getPlanDate()).isEqualTo(date);
        });
    }

    @Test
    @DisplayName("UseCase에서 전달한 새 계획을 Entity로 변환해 저장한다")
    void savesNewPlan() {
        LocalDate date = LocalDate.of(2026, 8, 21);
        DailyPlan plan = DailyPlan.create(1L, date);
        plan.addItem(DailyPlanItem.createTask(20L));
        when(repository.saveAndFlush(any(DailyPlanEntity.class))).thenAnswer(invocation -> {
            DailyPlanEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 1L);
            return entity;
        });

        DailyPlan saved = service.save(plan);

        assertThat(saved.getId()).isEqualTo(1L);
        assertThat(saved.getItems()).singleElement()
                .extracting(DailyPlanItem::getTaskId)
                .isEqualTo(20L);
        verify(repository).saveAndFlush(any(DailyPlanEntity.class));
    }

    @Test
    @DisplayName("기존 계획의 변경 사항을 Entity에 반영해 저장한다")
    void updatesExistingPlan() {
        LocalDate date = LocalDate.of(2026, 8, 21);
        DailyPlanEntity entity = entity(1L, date);
        when(repository.findByUserIdAndPlanDate(1L, date)).thenReturn(Optional.of(entity));
        DailyPlan plan = entity.toDomain();
        plan.addItem(DailyPlanItem.createTask(10L));

        DailyPlan saved = service.save(plan);

        assertThat(saved.getItems()).singleElement()
                .extracting(DailyPlanItem::getTaskId)
                .isEqualTo(10L);
        verify(repository).saveAndFlush(entity);
    }

    @Test
    @DisplayName("프로젝트 연결 여부와 관계없이 계획에 포함된 Task를 확인한다")
    void checksLinkedTasks() {
        LocalDate date = LocalDate.of(2026, 8, 21);
        DailyPlan plan = DailyPlan.create(1L, date);
        plan.addItem(DailyPlanItem.createTask(10L));
        plan.addItem(DailyPlanItem.createTask(20L));
        DailyPlanEntity entity = DailyPlanEntity.from(plan);
        ReflectionTestUtils.setField(entity, "id", 1L);
        when(repository.findByUserIdAndPlanDate(1L, date)).thenReturn(Optional.of(entity));

        assertThat(service.containsAllTasks(1L, date, List.of(10L, 20L))).isTrue();
    }

    private DailyPlanEntity entity(Long id, LocalDate date) {
        DailyPlanEntity entity = DailyPlanEntity.from(DailyPlan.create(1L, date));
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}

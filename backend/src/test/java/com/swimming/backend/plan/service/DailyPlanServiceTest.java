package com.swimming.backend.plan.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.plan.domain.DailyPlanItem;
import com.swimming.backend.plan.repository.DailyPlanItemRepository;
import com.swimming.backend.plan.repository.entity.DailyPlanItemEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyPlanServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 21);

    private DailyPlanItemRepository repository;
    private DailyPlanService service;

    @BeforeEach
    void setUp() {
        repository = mock(DailyPlanItemRepository.class);
        service = new DailyPlanService(repository);
    }

    @Test
    @DisplayName("사용자와 날짜로 조회한 항목을 순수 도메인으로 변환한다")
    void getsItemsAsDomain() {
        when(repository.findAllByUserIdAndPlanDateOrderByOrderIdxAsc(1L, DATE))
                .thenReturn(List.of(entity(1L, 10L, 0), entity(2L, 20L, 1)));

        List<DailyPlanItem> items = service.getItems(1L, DATE);

        assertThat(items).extracting(DailyPlanItem::getId).containsExactly(1L, 2L);
        assertThat(items).extracting(DailyPlanItem::getTaskId).containsExactly(10L, 20L);
        assertThat(items).extracting(DailyPlanItem::getOrderIdx).containsExactly(0, 1);
    }

    @Test
    @DisplayName("UseCase에서 전달한 항목을 사용자·날짜와 함께 Entity로 저장한다")
    void savesItemWithUserAndDate() {
        when(repository.save(any(DailyPlanItemEntity.class))).thenAnswer(invocation -> {
            DailyPlanItemEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 7L);
            return entity;
        });

        DailyPlanItem saved = service.save(1L, DATE, DailyPlanItem.restore(null, 20L, 3, null, null));

        assertThat(saved.getId()).isEqualTo(7L);
        assertThat(saved.getTaskId()).isEqualTo(20L);
        assertThat(saved.getOrderIdx()).isEqualTo(3);
    }

    @Test
    @DisplayName("전달받은 순서 맵에 포함된 항목만 순서를 바꿔 저장한다")
    void reordersOnlyMappedItems() {
        DailyPlanItemEntity first = entity(1L, 10L, 0);
        DailyPlanItemEntity second = entity(2L, 20L, 1);
        DailyPlanItemEntity untouched = entity(3L, 30L, 2);
        when(repository.findAllByUserIdAndPlanDateOrderByOrderIdxAsc(1L, DATE))
                .thenReturn(List.of(first, second, untouched));

        service.reorder(1L, DATE, Map.of(2L, 0, 1L, 1));

        assertThat(second.getOrderIdx()).isZero();
        assertThat(first.getOrderIdx()).isEqualTo(1);
        assertThat(untouched.getOrderIdx()).isEqualTo(2);
        verify(repository).saveAll(List.of(first, second, untouched));
    }

    @Test
    @DisplayName("사용자와 날짜가 일치하는 항목을 삭제한다")
    void deletesOwnedItem() {
        DailyPlanItemEntity entity = entity(1L, 10L, 0);
        when(repository.findByIdAndUserIdAndPlanDate(1L, 1L, DATE)).thenReturn(Optional.of(entity));

        service.delete(1L, DATE, 1L);

        verify(repository).delete(entity);
    }

    @Test
    @DisplayName("사용자나 날짜가 다른 항목을 삭제하려 하면 거부한다")
    void rejectsDeletingUnknownItem() {
        when(repository.findByIdAndUserIdAndPlanDate(99L, 1L, DATE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L, DATE, 99L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DAILY_PLAN_ITEM_NOT_FOUND));

        verify(repository, never()).delete(any(DailyPlanItemEntity.class));
    }

    @Test
    @DisplayName("요청한 Task가 모두 그날 계획에 있어야 참을 반환한다")
    void checksLinkedTasks() {
        when(repository.containsTask(1L, DATE, 10L)).thenReturn(true);
        when(repository.containsTask(1L, DATE, 20L)).thenReturn(true);
        when(repository.containsTask(1L, DATE, 30L)).thenReturn(false);

        assertThat(service.containsAllTasks(1L, DATE, List.of(10L, 20L))).isTrue();
        assertThat(service.containsAllTasks(1L, DATE, List.of(10L, 30L))).isFalse();
    }

    private DailyPlanItemEntity entity(Long id, Long taskId, int orderIdx) {
        DailyPlanItemEntity entity = DailyPlanItemEntity.from(
                1L, DATE, DailyPlanItem.restore(null, taskId, orderIdx, null, null));
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}

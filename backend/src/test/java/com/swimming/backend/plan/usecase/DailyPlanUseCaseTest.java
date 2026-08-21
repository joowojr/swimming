package com.swimming.backend.plan.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.plan.domain.DailyPlan;
import com.swimming.backend.plan.dto.DailyPlanResponse;
import com.swimming.backend.plan.dto.UpdateDailyPlanRequest;
import com.swimming.backend.plan.service.DailyPlanService;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.dto.projection.TaskReference;
import com.swimming.backend.task.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyPlanUseCaseTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 20);

    private DailyPlanService dailyPlanService;
    private TaskService taskService;
    private DailyPlanUseCase dailyPlanUseCase;

    @BeforeEach
    void setUp() {
        dailyPlanService = mock(DailyPlanService.class);
        taskService = mock(TaskService.class);
        dailyPlanUseCase = new DailyPlanUseCase(dailyPlanService, taskService);
    }

    @Test
    @DisplayName("조회 기간에 저장된 계획이 없으면 날짜별 빈 계획을 반환한다")
    void returnsEmptyPlansForRange() {
        LocalDate toDate = DATE.plusDays(2);
        when(dailyPlanService.getRange(1L, DATE, toDate)).thenReturn(List.of());

        List<DailyPlanResponse> responses = dailyPlanUseCase.getRange(1L, DATE, toDate);

        assertThat(responses).extracting(DailyPlanResponse::date)
                .containsExactly(DATE, DATE.plusDays(1), toDate);
        assertThat(responses).allSatisfy(response ->
                assertThat(response.items()).isEmpty());
        verify(taskService, never()).getAllByIds(1L, List.of());
    }

    @Test
    @DisplayName("최대 7일의 계획을 빈 날짜와 Task 순서를 유지해 반환한다")
    void returnsPlansAndEmptyDatesInRange() {
        LocalDate toDate = DATE.plusDays(6);
        DailyPlan firstPlan = DailyPlan.create(1L, DATE, List.of(2L, 1L));
        DailyPlan lastPlan = DailyPlan.create(1L, toDate, List.of(1L));
        when(dailyPlanService.getRange(1L, DATE, toDate))
                .thenReturn(List.of(firstPlan, lastPlan));
        when(taskService.getAllByIds(1L, List.of(2L, 1L))).thenReturn(List.of(
                task(1L, 10L, "프로젝트", "둘째", TaskStatus.TODO, 0),
                task(2L, 10L, "프로젝트", "첫째", TaskStatus.DOING, 40)
        ));

        List<DailyPlanResponse> responses = dailyPlanUseCase.getRange(
                1L,
                DATE,
                toDate
        );

        assertThat(responses).hasSize(7);
        assertThat(responses).extracting(DailyPlanResponse::date)
                .containsExactly(
                        DATE,
                        DATE.plusDays(1),
                        DATE.plusDays(2),
                        DATE.plusDays(3),
                        DATE.plusDays(4),
                        DATE.plusDays(5),
                        toDate
                );
        assertThat(responses.getFirst().items()).extracting(item -> item.taskId())
                .containsExactly(2L, 1L);
        assertThat(responses.getFirst().items().getFirst().projectName())
                .isEqualTo("프로젝트");
        assertThat(responses.get(1).items()).isEmpty();
        assertThat(responses.getLast().items()).extracting(item -> item.taskId())
                .containsExactly(1L);
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦은 조회 범위를 거부한다")
    void rejectsReversedDateRange() {
        assertThatThrownBy(() ->
                dailyPlanUseCase.getRange(1L, DATE.plusDays(1), DATE))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_DAILY_PLAN_DATE_RANGE));
        verify(dailyPlanService, never()).getRange(
                1L,
                DATE.plusDays(1),
                DATE
        );
    }

    @Test
    @DisplayName("양 끝을 포함해 8일인 조회 범위를 거부한다")
    void rejectsRangeLongerThanSevenDays() {
        LocalDate toDate = DATE.plusDays(7);

        assertThatThrownBy(() -> dailyPlanUseCase.getRange(1L, DATE, toDate))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_DAILY_PLAN_DATE_RANGE));
        verify(dailyPlanService, never()).getRange(1L, DATE, toDate);
    }

    @Test
    @DisplayName("계획이 없으면 검증된 Task 순서로 새 계획을 생성한다")
    void createsNewPlan() {
        UpdateDailyPlanRequest request = new UpdateDailyPlanRequest(DATE, List.of(2L, 1L));
        stubOwnedTasks(List.of(2L, 1L));
        when(dailyPlanService.get(1L, DATE)).thenReturn(Optional.empty());

        dailyPlanUseCase.update(1L, request);

        verify(dailyPlanService).create(1L, DATE, List.of(2L, 1L));
    }

    @Test
    @DisplayName("같은 날짜의 계획이 있으면 현재 구성으로 갱신한다")
    void updatesExistingPlan() {
        DailyPlan dailyPlan = DailyPlan.create(1L, DATE, List.of(1L));
        UpdateDailyPlanRequest request = new UpdateDailyPlanRequest(DATE, List.of(2L, 1L));
        stubOwnedTasks(List.of(2L, 1L));
        when(dailyPlanService.get(1L, DATE)).thenReturn(Optional.of(dailyPlan));

        dailyPlanUseCase.update(1L, request);

        verify(dailyPlanService).update(dailyPlan, List.of(2L, 1L));
        verify(dailyPlanService, never()).create(1L, DATE, List.of(2L, 1L));
    }

    @Test
    @DisplayName("빈 목록으로 날짜별 계획을 생성할 수 있다")
    void createsEmptyPlan() {
        UpdateDailyPlanRequest request = new UpdateDailyPlanRequest(DATE, List.of());
        when(dailyPlanService.get(1L, DATE)).thenReturn(Optional.empty());

        dailyPlanUseCase.update(1L, request);

        verify(taskService, never()).getAllByIds(1L, List.of());
        verify(dailyPlanService).create(1L, DATE, List.of());
    }

    @Test
    @DisplayName("같은 Task가 중복된 계획을 거부하고 기존 구성을 변경하지 않는다")
    void rejectsDuplicateTasks() {
        UpdateDailyPlanRequest request = new UpdateDailyPlanRequest(DATE, List.of(1L, 1L));

        assertThatThrownBy(() -> dailyPlanUseCase.update(1L, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_DAILY_PLAN_TASKS));
        verify(dailyPlanService, never()).get(1L, DATE);
    }

    @Test
    @DisplayName("존재하지 않는 Task가 포함되면 계획을 변경하지 않는다")
    void rejectsMissingTask() {
        UpdateDailyPlanRequest request = new UpdateDailyPlanRequest(DATE, List.of(1L, 99L));
        when(taskService.getAllByIds(1L, List.of(1L, 99L)))
                .thenReturn(List.of(task(
                        1L, 10L, "프로젝트", "Task", TaskStatus.TODO, 0
                )));

        assertThatThrownBy(() -> dailyPlanUseCase.update(1L, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND));
        verify(dailyPlanService, never()).get(1L, DATE);
    }

    @Test
    @DisplayName("다른 사용자의 프로젝트 Task가 포함되면 존재 여부를 숨긴다")
    void rejectsAnotherUsersTask() {
        UpdateDailyPlanRequest request = new UpdateDailyPlanRequest(DATE, List.of(1L));
        when(taskService.getAllByIds(2L, List.of(1L))).thenReturn(List.of());

        assertThatThrownBy(() -> dailyPlanUseCase.update(2L, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND));
        verify(dailyPlanService, never()).get(2L, DATE);
    }

    private void stubOwnedTasks(List<Long> taskIds) {
        when(taskService.getAllByIds(1L, taskIds)).thenReturn(List.of(
                task(1L, 10L, "프로젝트", "첫째", TaskStatus.TODO, 0),
                task(2L, 10L, "프로젝트", "둘째", TaskStatus.DOING, 40)
        ));
    }

    private TaskReference task(
            Long id,
            Long projectId,
            String projectName,
            String title,
            TaskStatus status,
            int completionPct
    ) {
        return new TaskReference(id, projectId, projectName, title, status, completionPct);
    }
}

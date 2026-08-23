package com.swimming.backend.plan.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.plan.domain.DailyPlan;
import com.swimming.backend.plan.domain.DailyPlanItem;
import com.swimming.backend.plan.dto.CreateDailyPlanItemRequest;
import com.swimming.backend.plan.dto.DailyPlanResponse;
import com.swimming.backend.plan.dto.ReorderDailyPlanItemsRequest;
import com.swimming.backend.plan.dto.UpdateDailyPlanItemRequest;
import com.swimming.backend.plan.service.DailyPlanService;
import com.swimming.backend.project.dto.ProjectReference;
import com.swimming.backend.project.service.ProjectService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyPlanUseCaseTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 21);
    private DailyPlanService dailyPlanService;
    private TaskService taskService;
    private ProjectService projectService;
    private DailyPlanUseCase useCase;

    @BeforeEach
    void setUp() {
        dailyPlanService = mock(DailyPlanService.class);
        taskService = mock(TaskService.class);
        projectService = mock(ProjectService.class);
        useCase = new DailyPlanUseCase(dailyPlanService, taskService, projectService);
        when(dailyPlanService.save(any(DailyPlan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("조회 기간에 빈 날짜와 Task·독립 할 일을 함께 반환한다")
    void returnsMixedItemsAndEmptyDates() {
        DailyPlan plan = planWithIds();
        when(dailyPlanService.getRange(1L, DATE, DATE.plusDays(1))).thenReturn(List.of(plan));
        when(taskService.getReferences(1L, List.of(10L))).thenReturn(List.of(task(10L)));

        List<DailyPlanResponse> responses = useCase.getRange(1L, DATE, DATE.plusDays(1));

        assertThat(responses).hasSize(2);
        assertThat(responses.getFirst().items()).extracting(item -> item.title())
                .containsExactly("API 구현", "장보기");
        assertThat(responses.getFirst().items().get(1).taskId()).isNull();
        assertThat(responses.getLast().items()).isEmpty();
    }

    @Test
    @DisplayName("계획이 없는 날짜에 독립 할 일을 추가하면 계획도 생성한다")
    void createsPlanWithAdHocItem() {
        when(dailyPlanService.get(1L, DATE)).thenReturn(Optional.empty());

        DailyPlanResponse response = useCase.addItem(1L, DATE, new CreateDailyPlanItemRequest(null, null, "  장보기  "));

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.taskId()).isNull();
            assertThat(item.title()).isEqualTo("장보기");
        });
        verify(dailyPlanService).save(any(DailyPlan.class));
    }

    @Test
    @DisplayName("소유한 Task를 날짜별 계획에 연결한다")
    void addsOwnedTask() {
        DailyPlan plan = DailyPlan.create(1L, DATE);
        when(dailyPlanService.get(1L, DATE)).thenReturn(Optional.of(plan));
        when(taskService.getReferences(1L, List.of(10L))).thenReturn(List.of(task(10L)));

        DailyPlanResponse response = useCase.addItem(1L, DATE, new CreateDailyPlanItemRequest(10L, null, null));

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.taskId()).isEqualTo(10L);
            assertThat(item.projectName()).isEqualTo("프로젝트");
        });
    }

    @Test
    @DisplayName("Task와 제목을 동시에 입력한 항목을 거부한다")
    void rejectsAmbiguousItem() {
        assertThatThrownBy(() -> useCase.addItem(
                1L, DATE, new CreateDailyPlanItemRequest(10L, null, "장보기")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_DAILY_PLAN_ITEM));
        verify(dailyPlanService, never()).get(1L, DATE);
    }

    @Test
    @DisplayName("다른 사용자의 Task는 계획에 추가하지 않는다")
    void rejectsAnotherUsersTask() {
        DailyPlan plan = DailyPlan.create(2L, DATE);
        when(dailyPlanService.get(2L, DATE)).thenReturn(Optional.of(plan));
        when(taskService.getReferences(2L, List.of(10L))).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.addItem(2L, DATE, new CreateDailyPlanItemRequest(10L, null, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND));
    }

    @Test
    @DisplayName("프로젝트를 선택해 새 Task를 만들고 계획에 연결한다")
    void createsProjectTaskAndAddsIt() {
        DailyPlan plan = DailyPlan.create(1L, DATE);
        when(dailyPlanService.get(1L, DATE)).thenReturn(Optional.of(plan));
        when(projectService.getReference(1L, 100L))
                .thenReturn(new ProjectReference(100L));
        when(taskService.createAndGetId(100L, "API 문서 작성")).thenReturn(20L);
        when(taskService.getReferences(1L, List.of(20L))).thenReturn(List.of(
                new TaskReference(20L, 100L, "프로젝트", "API 문서 작성", TaskStatus.TODO, 0)
        ));

        DailyPlanResponse response = useCase.addItem(
                1L, DATE, new CreateDailyPlanItemRequest(null, 100L, "API 문서 작성"));

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.taskId()).isEqualTo(20L);
            assertThat(item.projectId()).isEqualTo(100L);
            assertThat(item.title()).isEqualTo("API 문서 작성");
        });
        verify(taskService).createAndGetId(100L, "API 문서 작성");
        verify(dailyPlanService).save(plan);
    }

    @Test
    @DisplayName("날짜별 계획의 전체 항목 ID로 순서를 변경한다")
    void reordersByItemIds() {
        DailyPlan plan = planWithIds();
        when(dailyPlanService.get(1L, DATE)).thenReturn(Optional.of(plan));
        when(taskService.getReferences(1L, List.of(10L))).thenReturn(List.of(task(10L)));

        DailyPlanResponse response = useCase.reorder(1L, DATE, new ReorderDailyPlanItemsRequest(List.of(2L, 1L)));

        assertThat(response.items()).extracting(item -> item.id()).containsExactly(2L, 1L);
    }

    @Test
    @DisplayName("일부 항목만 보낸 순서 변경을 거부한다")
    void rejectsIncompleteOrder() {
        DailyPlan plan = planWithIds();
        when(dailyPlanService.get(1L, DATE)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> useCase.reorder(1L, DATE, new ReorderDailyPlanItemsRequest(List.of(1L))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_DAILY_PLAN_ITEM_ORDER));
    }

    @Test
    @DisplayName("독립 할 일 제목을 수정하고 항목을 삭제한다")
    void updatesAndDeletesAdHocItem() {
        DailyPlan plan = planWithIds();
        when(dailyPlanService.get(1L, DATE)).thenReturn(Optional.of(plan));
        when(taskService.getReferences(1L, List.of(10L))).thenReturn(List.of(task(10L)));

        DailyPlanResponse response = useCase.updateItem(1L, DATE, 2L, new UpdateDailyPlanItemRequest("책 반납"));
        useCase.deleteItem(1L, DATE, 2L);

        assertThat(response.items().get(1).title()).isEqualTo("책 반납");
        assertThat(plan.getItems()).extracting(DailyPlanItem::getId).containsExactly(1L);
    }

    @Test
    @DisplayName("Task 기반 항목의 제목 수정을 거부한다")
    void rejectsUpdatingTaskItem() {
        DailyPlan plan = planWithIds();
        when(dailyPlanService.get(1L, DATE)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> useCase.updateItem(
                1L, DATE, 1L, new UpdateDailyPlanItemRequest("제목 변경")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_DAILY_PLAN_ITEM));
    }

    private DailyPlan planWithIds() {
        return DailyPlan.restore(
                1L,
                1L,
                DATE,
                null,
                null,
                List.of(
                        DailyPlanItem.restore(1L, 10L, null, 0, null, null),
                        DailyPlanItem.restore(2L, null, "장보기", 1, null, null)
                )
        );
    }

    private TaskReference task(Long id) {
        return new TaskReference(id, 100L, "프로젝트", "API 구현", TaskStatus.DOING, 40);
    }
}

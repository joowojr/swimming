package com.swimming.backend.calendar.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.calendar.dto.in.CreateDailyPlanItemsRequest;
import com.swimming.backend.calendar.dto.in.NewDailyPlanTask;
import com.swimming.backend.calendar.dto.in.DailyPlanItemResponse;
import com.swimming.backend.calendar.dto.in.DailyPlanItemType;
import com.swimming.backend.calendar.dto.in.DailyPlanResponse;
import com.swimming.backend.folder.dto.FolderReference;
import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.dto.projection.PlannedTaskRow;
import com.swimming.backend.task.service.TaskService;
import com.swimming.backend.task.service.TaskOrderingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyPlanUseCaseTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 21);

    private TaskService taskService;
    private TaskOrderingService taskOrderingService;
    private FolderService folderService;
    private DailyPlanUseCase useCase;

    @BeforeEach
    void setUp() {
        taskService = mock(TaskService.class);
        taskOrderingService = mock(TaskOrderingService.class);
        folderService = mock(FolderService.class);
        useCase = new DailyPlanUseCase(taskService, taskOrderingService, folderService);
    }

    @Test
    @DisplayName("조회 기간에 폴더 Task와 폴더 없는 Task의 UI 타입을 함께 반환한다")
    void returnsMixedItemsAndEmptyDates() {
        when(taskService.getPlannedRows(1L, DATE, DATE.plusDays(1))).thenReturn(List.of(
                folderRow(10L),
                adHocRow(20L, "장보기")
        ));

        List<DailyPlanResponse> responses = useCase.getRange(1L, DATE, DATE.plusDays(1));

        assertThat(responses).hasSize(2);
        assertThat(responses.getFirst().items()).extracting(DailyPlanItemResponse::title)
                .containsExactly("API 구현", "장보기");
        assertThat(responses.getFirst().items()).extracting(DailyPlanItemResponse::itemType)
                .containsExactly(DailyPlanItemType.TASK, DailyPlanItemType.AD_HOC);
        assertThat(responses.getFirst().items().get(1).taskId()).isEqualTo(20L);
        assertThat(responses.getFirst().items().get(1).status()).isEqualTo(TaskStatus.TODO);
        assertThat(responses.getLast().items()).isEmpty();
    }

    @Test
    @DisplayName("조회 기간에 계획이 하나도 없어도 날짜마다 빈 목록을 채워 반환한다")
    void fillsEveryDateInRange() {
        when(taskService.getPlannedRows(1L, DATE, DATE.plusDays(2))).thenReturn(List.of());

        List<DailyPlanResponse> responses = useCase.getRange(1L, DATE, DATE.plusDays(2));

        assertThat(responses).extracting(DailyPlanResponse::date)
                .containsExactly(DATE, DATE.plusDays(1), DATE.plusDays(2));
        assertThat(responses).allSatisfy(response -> assertThat(response.items()).isEmpty());
    }

    @Test
    @DisplayName("폴더 없는 Task를 만들어 그날 계획에 추가한다")
    void createsAdHocTaskAndAddsIt() {
        when(taskOrderingService.nextRanks(eq(1L), anyList())).thenReturn(List.of(1024L));
        when(taskService.createAll(eq(1L), anyList()))
                .thenReturn(List.of(Task.restore(20L, 1L, null, null, "장보기", TaskStatus.TODO,
                        false, false, 0, 1024L, null, null)));
        when(taskService.getPlannedRows(1L, DATE, DATE))
                .thenReturn(List.of(adHocRow(20L, "장보기")));

        DailyPlanResponse response = useCase.addItems(1L, DATE,
                CreateDailyPlanItemsRequest.ofNewTasks(List.of(new NewDailyPlanTask("  장보기  ", null))));

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.taskId()).isEqualTo(20L);
            assertThat(item.itemType()).isEqualTo(DailyPlanItemType.AD_HOC);
            assertThat(item.title()).isEqualTo("장보기");
            assertThat(item.status()).isEqualTo(TaskStatus.TODO);
        });
        verify(taskService).plan(1L, List.of(20L), DATE);
    }

    @Test
    @DisplayName("소유한 여러 Task를 그날 캘린더에 담는다")
    void plansOwnedTasks() {
        when(taskService.countPlannedOn(1L, DATE, List.of(10L, 20L))).thenReturn(0L);
        when(taskService.getPlannedRows(1L, DATE, DATE)).thenReturn(List.of(
                folderRow(20L), folderRow(10L), adHocRow(30L, "기존")));

        DailyPlanResponse response = useCase.addItems(
                1L, DATE, CreateDailyPlanItemsRequest.ofTaskIds(List.of(10L, 20L)));

        assertThat(response.items()).extracting(DailyPlanItemResponse::taskId)
                .containsExactly(20L, 10L, 30L);
        verify(taskService).plan(1L, List.of(10L, 20L), DATE);
    }

    @Test
    @DisplayName("일괄 요청 안에 중복된 Task가 있으면 항목을 저장하지 않는다")
    void rejectsDuplicatedTasksInBatch() {
        assertThatThrownBy(() -> useCase.addItems(
                1L, DATE, CreateDailyPlanItemsRequest.ofTaskIds(List.of(10L, 10L))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_DAILY_PLAN_TASKS));

        verify(taskService, never()).plan(any(), anyList(), any());
    }

    @Test
    @DisplayName("이미 그날 계획에 있는 Task가 일괄 요청에 포함되면 항목을 저장하지 않는다")
    void rejectsAlreadyPlannedTaskInBatch() {
        when(taskService.countPlannedOn(1L, DATE, List.of(10L, 20L))).thenReturn(1L);

        assertThatThrownBy(() -> useCase.addItems(
                1L, DATE, CreateDailyPlanItemsRequest.ofTaskIds(List.of(10L, 20L))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_DAILY_PLAN_TASKS));

        verify(taskService, never()).plan(any(), anyList(), any());
    }

    @Test
    @DisplayName("이미 있는 Task와 새 Task를 동시에 보낸 요청을 거부한다")
    void rejectsAmbiguousItem() {
        assertThatThrownBy(() -> useCase.addItems(1L, DATE, new CreateDailyPlanItemsRequest(
                List.of(10L), List.of(new NewDailyPlanTask("장보기", null)))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_DAILY_PLAN_ITEM));

        verify(taskService, never()).createAll(any(), anyList());
        verify(taskService, never()).plan(any(), anyList(), any());
    }

    @Test
    @DisplayName("둘 다 비어 있는 요청을 거부한다")
    void rejectsEmptyItem() {
        assertThatThrownBy(() -> useCase.addItems(1L, DATE,
                new CreateDailyPlanItemsRequest(null, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_DAILY_PLAN_ITEM));
    }

    @Test
    @DisplayName("다른 사용자의 Task는 계획에 추가하지 않는다")
    void rejectsAnotherUsersTask() {
        when(taskService.countPlannedOn(2L, DATE, List.of(10L))).thenReturn(0L);
        doThrow(new BusinessException(ErrorCode.TASK_NOT_FOUND))
                .when(taskService).plan(2L, List.of(10L), DATE);

        assertThatThrownBy(() -> useCase.addItems(
                2L, DATE, CreateDailyPlanItemsRequest.ofTaskIds(List.of(10L))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND));
    }

    @Test
    @DisplayName("폴더를 선택해 새 Task를 만들고 계획에 연결한다")
    void createsFolderTaskAndAddsIt() {
        when(taskOrderingService.nextRanks(eq(1L), anyList())).thenReturn(List.of(1024L));
        when(taskService.createAll(eq(1L), anyList()))
                .thenReturn(List.of(Task.restore(20L, 1L, 100L, null, "API 문서 작성", TaskStatus.TODO,
                        false, false, 0, 1024L, null, null)));
        when(taskService.getPlannedRows(1L, DATE, DATE)).thenReturn(List.of(new PlannedTaskRow(
                20L, DATE, 100L, "폴더", false, "API 문서 작성", TaskStatus.TODO, false, false)));

        DailyPlanResponse response = useCase.addItems(1L, DATE,
                CreateDailyPlanItemsRequest.ofNewTasks(
                        List.of(new NewDailyPlanTask("API 문서 작성", 100L))));

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.taskId()).isEqualTo(20L);
            assertThat(item.itemType()).isEqualTo(DailyPlanItemType.TASK);
            assertThat(item.folderId()).isEqualTo(100L);
            assertThat(item.title()).isEqualTo("API 문서 작성");
        });
        verify(folderService).validateOwnerships(1L, List.of(100L));
    }

    @Test
    @DisplayName("그날 캘린더에서 할 일을 뺀다")
    void removesTaskFromPlan() {
        useCase.removeTask(1L, DATE, 20L);

        verify(taskService).unplan(1L, 20L, DATE);
    }

    private PlannedTaskRow folderRow(Long taskId) {
        return new PlannedTaskRow(taskId, DATE, 100L, "폴더", false, "API 구현", TaskStatus.DOING, false, false);
    }

    private PlannedTaskRow adHocRow(Long taskId, String title) {
        return new PlannedTaskRow(taskId, DATE, null, null, null, title, TaskStatus.TODO, false, false);
    }
}

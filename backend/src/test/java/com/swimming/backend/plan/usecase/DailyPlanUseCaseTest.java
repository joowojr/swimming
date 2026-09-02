package com.swimming.backend.plan.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.plan.domain.DailyPlanItem;
import com.swimming.backend.plan.dto.CreateDailyPlanItemsRequest;
import com.swimming.backend.plan.dto.DailyPlanItemResponse;
import com.swimming.backend.plan.dto.DailyPlanItemType;
import com.swimming.backend.plan.dto.DailyPlanResponse;
import com.swimming.backend.plan.dto.ReorderDailyPlanItemsRequest;
import com.swimming.backend.plan.dto.projection.DailyPlanItemQueryRow;
import com.swimming.backend.plan.service.DailyPlanService;
import com.swimming.backend.folder.dto.FolderReference;
import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.dto.projection.TaskReference;
import com.swimming.backend.task.service.TaskService;
import com.swimming.backend.task.service.TaskOrderingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyPlanUseCaseTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 21);

    private DailyPlanService dailyPlanService;
    private TaskService taskService;
    private TaskOrderingService taskOrderingService;
    private FolderService folderService;
    private DailyPlanUseCase useCase;

    @BeforeEach
    void setUp() {
        dailyPlanService = mock(DailyPlanService.class);
        taskService = mock(TaskService.class);
        taskOrderingService = mock(TaskOrderingService.class);
        folderService = mock(FolderService.class);
        useCase = new DailyPlanUseCase(dailyPlanService, taskService, taskOrderingService, folderService);
    }

    @Test
    @DisplayName("조회 기간에 폴더 Task와 폴더 없는 Task의 UI 타입을 함께 반환한다")
    void returnsMixedItemsAndEmptyDates() {
        when(dailyPlanService.getRows(1L, DATE, DATE.plusDays(1))).thenReturn(List.of(
                projectRow(1L, 10L, 0),
                adHocRow(2L, 20L, "장보기", 1)
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
        when(dailyPlanService.getRows(1L, DATE, DATE.plusDays(2))).thenReturn(List.of());

        List<DailyPlanResponse> responses = useCase.getRange(1L, DATE, DATE.plusDays(2));

        assertThat(responses).extracting(DailyPlanResponse::date)
                .containsExactly(DATE, DATE.plusDays(1), DATE.plusDays(2));
        assertThat(responses).allSatisfy(response -> assertThat(response.items()).isEmpty());
    }

    @Test
    @DisplayName("폴더 없는 Task를 만들어 그날 계획에 추가한다")
    void createsAdHocTaskAndAddsIt() {
        when(dailyPlanService.getItems(1L, DATE)).thenReturn(List.of());
        when(taskOrderingService.nextRank(1L, false, false)).thenReturn(1024L);
        when(taskService.create(1L, null, "장보기", false, false, 1024L))
                .thenReturn(Task.restore(20L, 1L, null, null, "장보기", TaskStatus.TODO,
                        false, false, 0, 1024L, null, null));
        when(dailyPlanService.getRows(1L, DATE, DATE))
                .thenReturn(List.of(adHocRow(1L, 20L, "장보기", 0)));

        DailyPlanResponse response = useCase.addItems(
                1L, DATE, new CreateDailyPlanItemsRequest(null, null, "  장보기  "));

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.taskId()).isEqualTo(20L);
            assertThat(item.itemType()).isEqualTo(DailyPlanItemType.AD_HOC);
            assertThat(item.title()).isEqualTo("장보기");
            assertThat(item.status()).isEqualTo(TaskStatus.TODO);
        });
        verify(folderService, never()).getReference(any(), any());
        verify(dailyPlanService).save(eq(1L), eq(DATE), any(DailyPlanItem.class));
    }

    @Test
    @DisplayName("소유한 여러 Task를 기존 항목 뒤 순서로 이어 붙인다")
    void addsOwnedTasksAfterExistingItems() {
        when(dailyPlanService.getItems(1L, DATE))
                .thenReturn(List.of(DailyPlanItem.restore(1L, 30L, 0, null, null)));
        when(dailyPlanService.containsAnyTasks(1L, DATE, List.of(10L, 20L))).thenReturn(false);
        when(taskService.getReferences(1L, List.of(10L, 20L)))
                .thenReturn(List.of(taskReference(10L), taskReference(20L)));
        when(dailyPlanService.getRows(1L, DATE, DATE)).thenReturn(List.of(
                adHocRow(1L, 30L, "기존", 0), projectRow(2L, 10L, 1), projectRow(3L, 20L, 2)));

        DailyPlanResponse response = useCase.addItems(
                1L, DATE, new CreateDailyPlanItemsRequest(List.of(10L, 20L), null, null));

        assertThat(response.items()).extracting(DailyPlanItemResponse::taskId)
                .containsExactly(30L, 10L, 20L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DailyPlanItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(dailyPlanService).saveAll(eq(1L), eq(DATE), captor.capture());
        assertThat(captor.getValue()).extracting(DailyPlanItem::getTaskId).containsExactly(10L, 20L);
        assertThat(captor.getValue()).extracting(DailyPlanItem::getOrderIdx).containsExactly(1, 2);
    }

    @Test
    @DisplayName("일괄 요청 안에 중복된 Task가 있으면 항목을 저장하지 않는다")
    void rejectsDuplicatedTasksInBatch() {
        when(dailyPlanService.getItems(1L, DATE)).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.addItems(
                1L, DATE, new CreateDailyPlanItemsRequest(List.of(10L, 10L), null, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_DAILY_PLAN_TASKS));

        verify(dailyPlanService, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("이미 그날 계획에 있는 Task가 일괄 요청에 포함되면 항목을 저장하지 않는다")
    void rejectsAlreadyPlannedTaskInBatch() {
        when(dailyPlanService.getItems(1L, DATE))
                .thenReturn(List.of(DailyPlanItem.restore(1L, 10L, 0, null, null)));
        when(dailyPlanService.containsAnyTasks(1L, DATE, List.of(10L, 20L))).thenReturn(true);

        assertThatThrownBy(() -> useCase.addItems(
                1L, DATE, new CreateDailyPlanItemsRequest(List.of(10L, 20L), null, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_DAILY_PLAN_TASKS));

        verify(dailyPlanService, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("Task와 제목을 동시에 입력한 항목을 거부한다")
    void rejectsAmbiguousItem() {
        assertThatThrownBy(() -> useCase.addItems(
                1L, DATE, new CreateDailyPlanItemsRequest(List.of(10L), null, "장보기")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_DAILY_PLAN_ITEM));

        verify(dailyPlanService, never()).getItems(1L, DATE);
    }

    @Test
    @DisplayName("다른 사용자의 Task는 계획에 추가하지 않는다")
    void rejectsAnotherUsersTask() {
        when(dailyPlanService.getItems(2L, DATE)).thenReturn(List.of());
        when(dailyPlanService.containsAnyTasks(2L, DATE, List.of(10L))).thenReturn(false);
        when(taskService.getReferences(2L, List.of(10L))).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.addItems(
                2L, DATE, new CreateDailyPlanItemsRequest(List.of(10L), null, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND));

        verify(dailyPlanService, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("폴더를 선택해 새 Task를 만들고 계획에 연결한다")
    void createsProjectTaskAndAddsIt() {
        when(dailyPlanService.getItems(1L, DATE)).thenReturn(List.of());
        when(folderService.getReference(1L, 100L))
                .thenReturn(new FolderReference(100L, "폴더", null));
        when(taskOrderingService.nextRank(1L, false, false)).thenReturn(1024L);
        when(taskService.create(1L, 100L, "API 문서 작성", false, false, 1024L))
                .thenReturn(Task.restore(20L, 1L, 100L, null, "API 문서 작성", TaskStatus.TODO,
                        false, false, 0, 1024L, null, null));
        when(dailyPlanService.getRows(1L, DATE, DATE)).thenReturn(List.of(new DailyPlanItemQueryRow(
                1L, DATE, 20L, 100L, "폴더", false, "API 문서 작성", TaskStatus.TODO, 0)));

        DailyPlanResponse response = useCase.addItems(
                1L, DATE, new CreateDailyPlanItemsRequest(null, 100L, "API 문서 작성"));

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.taskId()).isEqualTo(20L);
            assertThat(item.itemType()).isEqualTo(DailyPlanItemType.TASK);
            assertThat(item.folderId()).isEqualTo(100L);
            assertThat(item.title()).isEqualTo("API 문서 작성");
        });
        verify(taskService).create(1L, 100L, "API 문서 작성", false, false, 1024L);
    }

    @Test
    @DisplayName("날짜별 계획의 전체 항목 ID로 순서를 다시 부여한다")
    void reordersByItemIds() {
        when(dailyPlanService.getItems(1L, DATE)).thenReturn(List.of(
                DailyPlanItem.restore(1L, 10L, 0, null, null),
                DailyPlanItem.restore(2L, 20L, 1, null, null)));
        when(dailyPlanService.getRows(1L, DATE, DATE)).thenReturn(List.of(
                adHocRow(2L, 20L, "장보기", 0), projectRow(1L, 10L, 1)));

        DailyPlanResponse response = useCase.reorder(
                1L, DATE, new ReorderDailyPlanItemsRequest(List.of(2L, 1L)));

        assertThat(response.items()).extracting(DailyPlanItemResponse::id).containsExactly(2L, 1L);
        verify(dailyPlanService).reorder(1L, DATE, Map.of(2L, 0, 1L, 1));
    }

    @Test
    @DisplayName("일부 항목만 보낸 순서 변경을 거부한다")
    void rejectsIncompleteOrder() {
        when(dailyPlanService.getItems(1L, DATE)).thenReturn(List.of(
                DailyPlanItem.restore(1L, 10L, 0, null, null),
                DailyPlanItem.restore(2L, 20L, 1, null, null)));

        assertThatThrownBy(() -> useCase.reorder(
                1L, DATE, new ReorderDailyPlanItemsRequest(List.of(1L))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_DAILY_PLAN_ITEM_ORDER));

        verify(dailyPlanService, never()).reorder(any(), any(), anyMap());
    }

    @Test
    @DisplayName("항목 삭제를 사용자·날짜와 함께 위임한다")
    void deletesItem() {
        useCase.deleteItem(1L, DATE, 2L);

        verify(dailyPlanService).delete(1L, DATE, 2L);
    }

    private DailyPlanItemQueryRow projectRow(Long id, Long taskId, int orderIdx) {
        return new DailyPlanItemQueryRow(id, DATE, taskId, 100L, "폴더", false, "API 구현", TaskStatus.DOING, orderIdx);
    }

    private DailyPlanItemQueryRow adHocRow(Long id, Long taskId, String title, int orderIdx) {
        return new DailyPlanItemQueryRow(id, DATE, taskId, null, null, null, title, TaskStatus.TODO, orderIdx);
    }

    private TaskReference taskReference(Long id) {
        return new TaskReference(id, 100L, "폴더", "API 구현", TaskStatus.DOING);
    }
}

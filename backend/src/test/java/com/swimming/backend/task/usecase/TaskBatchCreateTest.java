package com.swimming.backend.task.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.domain.TaskMatrixSection;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.dto.in.CreateTaskWithPlanRequest;
import com.swimming.backend.task.dto.in.CreateTasksBatchRequest;
import com.swimming.backend.task.dto.in.NewTaskSpec;
import com.swimming.backend.task.dto.in.TaskResponse;
import com.swimming.backend.task.service.TaskOrderingService;
import com.swimming.backend.task.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

/** POST /api/tasks/batch — 모달에서 담은 할 일을 한 번에 만든다. */
class TaskBatchCreateTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 15);

    private TaskService taskService;
    private TaskOrderingService taskOrderingService;
    private FolderService folderService;
    private TaskUseCase taskUseCase;

    @BeforeEach
    void setUp() {
        taskService = mock(TaskService.class);
        taskOrderingService = mock(TaskOrderingService.class);
        folderService = mock(FolderService.class);
        taskUseCase = new TaskUseCase(taskService, taskOrderingService, folderService);
    }

    @Test
    @DisplayName("담은 할 일을 모두 만들고 담은 순서대로 돌려준다")
    void createsEveryDraft() {
        when(taskOrderingService.nextRanks(eq(1L), anyList())).thenAnswer(ranks());
        when(taskService.createAll(eq(1L), anyList()))
                .thenReturn(List.of(task(101L, "알고리즘"), task(102L, "CS 정리")));

        List<TaskResponse> created = taskUseCase.createBatch(1L, new CreateTasksBatchRequest(List.of(
                draft("알고리즘", null, null),
                draft("CS 정리", null, null)
        )));

        assertThat(created).extracting(TaskResponse::title).containsExactly("알고리즘", "CS 정리");
    }

    @Test
    @DisplayName("폴더 소유권은 중복을 제거해 한 번에 확인한다")
    void validatesFoldersOnce() {
        when(taskOrderingService.nextRanks(eq(1L), anyList())).thenAnswer(ranks());
        when(taskService.createAll(eq(1L), anyList()))
                .thenReturn(List.of(task(101L, "가"), task(102L, "나"), task(103L, "다")));

        taskUseCase.createBatch(1L, new CreateTasksBatchRequest(List.of(
                draft("가", 10L, null),
                draft("나", 10L, null),
                draft("다", null, null)
        )));

        verify(folderService).validateOwnerships(1L, List.of(10L));
    }

    @Test
    @DisplayName("남의 폴더가 하나라도 섞여 있으면 아무것도 만들지 않는다")
    void createsNothingWhenAnyFolderIsNotOwned() {
        doThrow(new BusinessException(ErrorCode.FOLDER_NOT_FOUND))
                .when(folderService).validateOwnerships(eq(1L), anyList());

        assertThatThrownBy(() -> taskUseCase.createBatch(1L, new CreateTasksBatchRequest(List.of(
                draft("내 것", 10L, null),
                draft("남의 것", 99L, null)
        ))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FOLDER_NOT_FOUND));

        verify(taskService, never()).createAll(any(), anyList());
        verify(taskService, never()).plan(any(), anyList(), any());
    }

    @Test
    @DisplayName("담은 순서대로 매트릭스 영역을 넘겨 순위를 한 번에 받는다")
    void delegatesRankAllocation() {
        when(taskOrderingService.nextRanks(eq(1L), anyList())).thenReturn(List.of(1024L, 2048L, 4096L));
        when(taskService.createAll(eq(1L), anyList()))
                .thenReturn(List.of(task(101L, "가"), task(102L, "나"), task(103L, "중요")));

        taskUseCase.createBatch(1L, new CreateTasksBatchRequest(List.of(
                draft("가", null, null),
                draft("나", null, null),
                priorityDraft("중요")
        )));

        verify(taskOrderingService).nextRanks(1L, List.of(
                TaskMatrixSection.STANDARD, TaskMatrixSection.STANDARD, TaskMatrixSection.PRIORITY));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NewTaskSpec>> captor = ArgumentCaptor.forClass(List.class);
        verify(taskService).createAll(eq(1L), captor.capture());
        assertThat(captor.getValue()).extracting(NewTaskSpec::matrixRank)
                .containsExactly(1024L, 2048L, 4096L);
    }

    @Test
    @DisplayName("같은 날짜에 담은 할 일은 한 번에 캘린더에 넣는다")
    void addsPlanItemsPerDate() {
        when(taskOrderingService.nextRanks(eq(1L), anyList())).thenAnswer(ranks());
        when(taskService.createAll(eq(1L), anyList()))
                .thenReturn(List.of(task(101L, "가"), task(102L, "나"), task(103L, "날짜 없음")));
        when(taskService.getAllByIds(1L, List.of(101L, 102L, 103L)))
                .thenReturn(List.of(task(101L, "가"), task(102L, "나"), task(103L, "날짜 없음")));

        taskUseCase.createBatch(1L, new CreateTasksBatchRequest(List.of(
                draft("가", null, DATE),
                draft("나", null, DATE),
                draft("날짜 없음", null, null)
        )));

        verify(taskService).plan(1L, List.of(101L, 102L), DATE);
    }

    @Test
    @DisplayName("날짜를 고르지 않았으면 캘린더를 건드리지 않는다")
    void skipsCalendarWithoutDate() {
        when(taskOrderingService.nextRanks(eq(1L), anyList())).thenAnswer(ranks());
        when(taskService.createAll(eq(1L), anyList())).thenReturn(List.of(task(101L, "가")));

        taskUseCase.createBatch(1L, new CreateTasksBatchRequest(List.of(draft("가", null, null))));

        verify(taskService, never()).plan(any(), anyList(), any());
    }

    @Test
    @DisplayName("제목의 앞뒤 공백은 저장 전에 다듬는다")
    void trimsTitle() {
        when(taskOrderingService.nextRanks(eq(1L), anyList())).thenAnswer(ranks());
        when(taskService.createAll(eq(1L), anyList())).thenReturn(List.of(task(101L, "알고리즘")));

        taskUseCase.createBatch(1L, new CreateTasksBatchRequest(List.of(draft("  알고리즘  ", null, null))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NewTaskSpec>> captor = ArgumentCaptor.forClass(List.class);
        verify(taskService).createAll(eq(1L), captor.capture());
        assertThat(captor.getValue()).singleElement()
                .extracting(NewTaskSpec::title).isEqualTo("알고리즘");
    }

    /** 넘긴 영역 수만큼 서로 다른 순위를 돌려준다. 실제 채번은 TaskOrderingBatchRankTest가 본다. */
    private org.mockito.stubbing.Answer<List<Long>> ranks() {
        return invocation -> {
            List<?> sections = invocation.getArgument(1);
            List<Long> allocated = new java.util.ArrayList<>();
            for (int index = 0; index < sections.size(); index++) {
                allocated.add(1024L * (index + 1));
            }
            return allocated;
        };
    }

    private CreateTaskWithPlanRequest draft(String title, Long folderId, LocalDate planDate) {
        return new CreateTaskWithPlanRequest(title, folderId, false, false, planDate);
    }

    private CreateTaskWithPlanRequest priorityDraft(String title) {
        return new CreateTaskWithPlanRequest(title, null, true, false, null);
    }

    private Task task(Long id, String title) {
        return Task.restore(id, 1L, null, null, title, TaskStatus.TODO, 0, null, null);
    }
}

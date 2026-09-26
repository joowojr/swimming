package com.swimming.backend.task.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.folder.dto.FolderReference;
import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.common.dto.CursorPage;
import com.swimming.backend.task.dto.in.TaskSummaryResponse;
import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.dto.in.CreateTaskWithPlanRequest;
import com.swimming.backend.task.dto.in.DeleteTasksRequest;
import com.swimming.backend.task.dto.in.TaskResponse;
import com.swimming.backend.task.dto.in.TaskSort;
import com.swimming.backend.task.dto.in.UpdateTaskInfoRequest;
import com.swimming.backend.task.dto.in.UpdateTaskStatusRequest;
import com.swimming.backend.task.dto.in.UpdateTaskTitleRequest;
import com.swimming.backend.task.service.TaskService;
import com.swimming.backend.task.service.TaskOrderingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskUseCaseTest {

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
    @DisplayName("소유한 폴더에 Task를 생성한다")
    void createsTaskInOwnedFolder() {
        when(folderService.getReference(1L, 10L))
                .thenReturn(new FolderReference(10L, "폴더", null));
        when(taskOrderingService.nextRank(1L, false, false)).thenReturn(1024L);
        when(taskService.create(1L, 10L, "Task", false, false, 1024L))
                .thenReturn(task(1L, 10L, "Task", 0));

        TaskResponse response = taskUseCase.createWithOptionalPlan(
                1L,
                new CreateTaskWithPlanRequest("Task", 10L, false, false, null)
        );

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.folderId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("전체 모드는 사용자가 소유한 모든 Task를 반환한다")
    void returnsAllOwnedTasks() {
        when(taskService.getAll(1L, TaskSort.DESC.toSort())).thenReturn(List.of(
                task(2L, null, "최근 Task", 0),
                task(1L, 10L, "이전 Task", 0)
        ));

        List<TaskResponse> responses = taskUseCase.getList(1L, TaskSort.DESC);

        assertThat(responses).extracting(TaskResponse::id)
                .containsExactly(2L, 1L);
        verify(taskService).getAll(1L, TaskSort.DESC.toSort());
    }

    @Test
    @DisplayName("오래된순은 생성 시각 오름차순으로 조회한다")
    void returnsTasksInAscendingOrder() {
        when(taskService.getAll(1L, TaskSort.ASC.toSort())).thenReturn(List.of(
                task(1L, 10L, "이전 Task", 0)
        ));

        taskUseCase.getList(1L, TaskSort.ASC);

        verify(taskService).getAll(1L, TaskSort.ASC.toSort());
    }

    @Test
    @DisplayName("제목 수정은 상태를 건드리지 않는다")
    void updatesOnlyTitleAfterOwnershipCheck() {
        Task task = task(1L, 10L, "기존", 0);
        task.changeStatus(TaskStatus.DOING);
        task.changeTitle("수정");
        when(taskService.updateTitle(1L, 1L, "  수정  ")).thenReturn(task);

        TaskResponse response = taskUseCase.updateTitle(
                1L, 1L, new UpdateTaskTitleRequest("  수정  "));

        assertThat(response.title()).isEqualTo("수정");
        assertThat(response.status()).isEqualTo(TaskStatus.DOING);
        verify(taskService).updateTitle(1L, 1L, "  수정  ");
    }

    @Test
    @DisplayName("상태 수정은 제목을 건드리지 않는다")
    void updatesOnlyStatusAfterOwnershipCheck() {
        Task task = task(1L, 10L, "기존", 0);
        task.changeStatus(TaskStatus.DONE);
        when(taskService.updateStatus(1L, 1L, TaskStatus.DONE)).thenReturn(task);

        TaskResponse response = taskUseCase.updateStatus(
                1L, 1L, new UpdateTaskStatusRequest(TaskStatus.DONE));

        assertThat(response.title()).isEqualTo("기존");
        assertThat(response.status()).isEqualTo(TaskStatus.DONE);
        verify(taskService).updateStatus(1L, 1L, TaskStatus.DONE);
    }

    @Test
    @DisplayName("다른 사용자의 Task는 찾을 수 없음으로 처리한다")
    void hidesAnotherUsersTask() {
        when(taskService.updateTitle(2L, 1L, "수정"))
                .thenThrow(new BusinessException(ErrorCode.TASK_NOT_FOUND));

        assertThatThrownBy(() -> taskUseCase.updateTitle(
                2L, 1L, new UpdateTaskTitleRequest("수정")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND));
        verify(taskService).updateTitle(2L, 1L, "수정");
    }

    @Test
    @DisplayName("사용자가 소유한 여러 Task를 한 번에 삭제한다")
    void deletesOwnedTasksAtOnce() {
        taskUseCase.deleteTasks(
                1L,
                new DeleteTasksRequest(List.of(1L, 2L, 2L))
        );

        verify(taskService).deleteAll(1L, List.of(1L, 2L));
    }

    @Test
    @DisplayName("삭제 대상 중 찾을 수 없는 Task가 있으면 아무것도 삭제하지 않는다")
    void rejectsDeletionWhenAnyTaskIsMissing() {
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.TASK_NOT_FOUND))
                .when(taskService).deleteAll(1L, List.of(1L, 2L));

        assertThatThrownBy(() -> taskUseCase.deleteTasks(
                1L,
                new DeleteTasksRequest(List.of(1L, 2L))
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND));
        verify(taskService).deleteAll(1L, List.of(1L, 2L));
    }

    @Test
    @DisplayName("수정하기는 제목·폴더·중요·즉시·캘린더 날짜를 한 번에 반영한다")
    void updatesTaskInfo() {
        LocalDate planDate = LocalDate.of(2026, 9, 5);
        when(folderService.getReference(1L, 10L))
                .thenReturn(new FolderReference(10L, "폴더", null));
        when(taskService.getOne(1L, 41L)).thenReturn(task(41L, 10L, "Task", 0));
        when(taskOrderingService.nextRank(1L, true, true)).thenReturn(2048L);
        when(taskService.updateInfo(1L, 41L, "Task", true, 10L, true, true, 2048L, planDate))
                .thenReturn(task(41L, 10L, "Task", 0));

        TaskResponse response = taskUseCase.updateInfo(
                1L,
                41L,
                new UpdateTaskInfoRequest("Task", Optional.of(10L), true, true, planDate)
        );

        verify(taskService).updateInfo(1L, 41L, "Task", true, 10L, true, true, 2048L, planDate);
        assertThat(response.id()).isEqualTo(41L);
    }

    @Test
    @DisplayName("미분류로 옮기면 폴더 소유권을 확인하지 않고 폴더를 비운다")
    void updatesTaskInfoToUnclassified() {
        when(taskService.getOne(1L, 41L)).thenReturn(task(41L, null, "Task", 0));
        when(taskService.updateInfo(1L, 41L, "Task", true, null, false, false, null, null))
                .thenReturn(task(41L, null, "Task", 0));

        taskUseCase.updateInfo(1L, 41L, new UpdateTaskInfoRequest("Task", Optional.empty(), false, false, null));

        verify(folderService, never()).getReference(any(), any());
        // 중요·즉시가 그대로면 새 rank를 계산하지 않는다.
        verify(taskOrderingService, never()).nextRank(any(), anyBoolean(), anyBoolean());
        verify(taskService).updateInfo(1L, 41L, "Task", true, null, false, false, null, null);
    }

    @Test
    @DisplayName("folderId를 보내지 않으면 폴더 소유권을 확인하지 않고 폴더를 그대로 둔다")
    void keepsFolderWhenFolderIdMissing() {
        LocalDate planDate = LocalDate.of(2026, 9, 5);
        when(taskService.getOne(1L, 41L)).thenReturn(task(41L, 10L, "Task", 0));
        when(taskOrderingService.nextRank(1L, true, true)).thenReturn(2048L);
        when(taskService.updateInfo(1L, 41L, "Task", false, null, true, true, 2048L, planDate))
                .thenReturn(task(41L, 10L, "Task", 0));

        taskUseCase.updateInfo(1L, 41L, new UpdateTaskInfoRequest("Task", null, true, true, planDate));

        verify(folderService, never()).getReference(any(), any());
        verify(taskService).updateInfo(1L, 41L, "Task", false, null, true, true, 2048L, planDate);
    }

    @Test
    @DisplayName("할 일을 만들며 날짜를 고르면 그 날짜의 캘린더에 담는다")
    void plansCreatedTaskWhenPlanDateGiven() {
        LocalDate planDate = LocalDate.of(2026, 9, 5);
        when(taskOrderingService.nextRank(1L, false, false)).thenReturn(1024L);
        when(taskService.create(1L, null, "Task", false, false, 1024L))
                .thenReturn(task(41L, null, "Task", 0));
        when(taskService.getOne(1L, 41L)).thenReturn(task(41L, null, "Task", 0));

        taskUseCase.createWithOptionalPlan(
                1L, new CreateTaskWithPlanRequest("Task", null, false, false, planDate));

        verify(taskService).plan(1L, List.of(41L), planDate);
    }

    @Test
    @DisplayName("폴더의 할 일을 최근 순으로 한 페이지 준다")
    void returnsFolderTaskPage() {
        when(folderService.getReference(1L, 10L))
                .thenReturn(new FolderReference(10L, "폴더", null));
        when(taskService.getPageByFolder(10L, null, 21)).thenReturn(List.of(
                taskAt(2L, "둘째", 1),
                taskAt(1L, "첫째", 0)
        ));

        CursorPage<TaskSummaryResponse> page = taskUseCase.getPageByFolder(1L, 10L, 20, null);

        assertThat(page.items()).extracting(TaskSummaryResponse::title)
                .containsExactly("둘째", "첫째");
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @DisplayName("한 페이지를 넘으면 마지막 항목까지만 주고 다음 커서를 남긴다")
    void givesNextCursorWhenFolderHasMoreTasks() {
        when(folderService.getReference(1L, 10L))
                .thenReturn(new FolderReference(10L, "폴더", null));
        when(taskService.getPageByFolder(10L, null, 3)).thenReturn(List.of(
                taskAt(3L, "셋째", 2),
                taskAt(2L, "둘째", 1),
                taskAt(1L, "첫째", 0)
        ));

        CursorPage<TaskSummaryResponse> page = taskUseCase.getPageByFolder(1L, 10L, 2, null);

        assertThat(page.items()).extracting(TaskSummaryResponse::title)
                .containsExactly("셋째", "둘째");
        assertThat(page.hasNext()).isTrue();
        assertThat(page.nextCursor()).isNotNull();
    }

    @Test
    @DisplayName("남의 폴더의 할 일은 조회할 수 없다")
    void rejectsOtherUsersFolder() {
        when(folderService.getReference(1L, 10L))
                .thenThrow(new BusinessException(ErrorCode.FOLDER_NOT_FOUND));

        assertThatThrownBy(() -> taskUseCase.getPageByFolder(1L, 10L, 20, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FOLDER_NOT_FOUND);
    }

    /** 커서를 만들려면 생성 시각이 있어야 한다. */
    private Task taskAt(Long id, String title, int orderIdx) {
        return Task.restore(
                id, 1L, 10L, null, title, TaskStatus.TODO, orderIdx,
                Instant.parse("2026-03-01T00:00:00Z").plusSeconds(id),
                Instant.parse("2026-03-01T00:00:00Z").plusSeconds(id)
        );
    }

    private Task task(Long id, Long folderId, String title, int orderIdx) {
        return Task.restore(
                id,
                1L,
                folderId,
                null,
                title,
                TaskStatus.TODO,
                orderIdx,
                null,
                null
        );
    }

}

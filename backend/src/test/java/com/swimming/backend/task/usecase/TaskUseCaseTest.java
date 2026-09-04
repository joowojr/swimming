package com.swimming.backend.task.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.folder.dto.FolderReference;
import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.plan.service.DailyPlanService;
import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.dto.in.CreateTaskRequest;
import com.swimming.backend.task.dto.in.DeleteTasksRequest;
import com.swimming.backend.task.dto.in.TaskResponse;
import com.swimming.backend.task.dto.in.TaskSort;
import com.swimming.backend.plan.dto.projection.DailyPlanItemQueryRow;
import com.swimming.backend.task.dto.in.UpdateTaskInfoRequest;
import com.swimming.backend.task.dto.in.UpdateTaskInfoResponse;
import com.swimming.backend.task.dto.in.UpdateTaskStatusRequest;
import com.swimming.backend.task.dto.in.UpdateTaskTitleRequest;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskUseCaseTest {

    private TaskService taskService;
    private TaskOrderingService taskOrderingService;
    private FolderService folderService;
    private DailyPlanService dailyPlanService;
    private TaskUseCase taskUseCase;

    @BeforeEach
    void setUp() {
        taskService = mock(TaskService.class);
        taskOrderingService = mock(TaskOrderingService.class);
        folderService = mock(FolderService.class);
        dailyPlanService = mock(DailyPlanService.class);
        taskUseCase = new TaskUseCase(taskService, taskOrderingService, folderService, dailyPlanService);
    }

    @Test
    @DisplayName("소유한 폴더에 Task를 생성한다")
    void createsTaskInOwnedFolder() {
        when(folderService.getReference(1L, 10L))
                .thenReturn(new FolderReference(10L, "폴더", null));
        when(taskOrderingService.nextRank(1L, false, false)).thenReturn(1024L);
        when(taskService.create(1L, 10L, "Task", false, false, 1024L))
                .thenReturn(task(1L, 10L, "Task", 0));

        TaskResponse response = taskUseCase.create(
                1L,
                10L,
                new CreateTaskRequest("Task")
        );

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.folderId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("소유한 폴더의 Task를 저장된 순서대로 반환한다")
    void returnsTasksFromOwnedFolder() {
        when(folderService.getReference(1L, 10L))
                .thenReturn(new FolderReference(10L, "폴더", null));
        when(taskService.getByFolder(10L)).thenReturn(List.of(
                task(1L, 10L, "첫째", 0),
                task(2L, 10L, "둘째", 1)
        ));

        List<TaskResponse> responses = taskUseCase.getByFolder(1L, 10L);

        assertThat(responses).extracting(TaskResponse::title)
                .containsExactly("첫째", "둘째");
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
    @DisplayName("수정하기는 폴더·중요·즉시를 한 번에 반영하고 계획을 옮기지 않으면 빈 계획 목록을 돌려준다")
    void updatesTaskInfoWithoutPlanMove() {
        when(folderService.getReference(1L, 10L))
                .thenReturn(new FolderReference(10L, "폴더", null));
        when(taskService.getOne(1L, 41L)).thenReturn(task(41L, 10L, "Task", 0));
        when(taskOrderingService.nextRank(1L, true, true)).thenReturn(2048L);
        when(taskService.updateInfo(1L, 41L, "Task", 10L, true, true, 2048L)).thenReturn(task(41L, 10L, "Task", 0));

        UpdateTaskInfoResponse response = taskUseCase.updateInfo(
                1L,
                41L,
                new UpdateTaskInfoRequest("Task", 10L, true, true, null)
        );

        verify(taskService).updateInfo(1L, 41L, "Task", 10L, true, true, 2048L);
        assertThat(response.task().id()).isEqualTo(41L);
        assertThat(response.plans()).isEmpty();
    }

    @Test
    @DisplayName("미분류로 옮기면 폴더 소유권을 확인하지 않고 폴더를 비운다")
    void updatesTaskInfoToUnclassified() {
        when(taskService.getOne(1L, 41L)).thenReturn(task(41L, null, "Task", 0));
        when(taskService.updateInfo(1L, 41L, "Task", null, false, false, null)).thenReturn(task(41L, null, "Task", 0));

        taskUseCase.updateInfo(1L, 41L, new UpdateTaskInfoRequest("Task", null, false, false, null));

        verify(folderService, never()).getReference(any(), any());
        // 중요·즉시가 그대로면 새 rank를 계산하지 않는다.
        verify(taskOrderingService, never()).nextRank(any(), anyBoolean(), anyBoolean());
        verify(taskService).updateInfo(1L, 41L, "Task", null, false, false, null);
    }

    @Test
    @DisplayName("계획 날짜를 옮기면 원본과 대상 두 날짜의 계획을 함께 돌려준다")
    void updatesTaskInfoWithPlanMove() {
        LocalDate fromDate = LocalDate.of(2026, 9, 1);
        LocalDate toDate = LocalDate.of(2026, 9, 5);
        when(taskOrderingService.nextRank(1L, false, false)).thenReturn(1024L);
        when(taskService.getOne(1L, 41L)).thenReturn(task(41L, null, "Task", 0));
        when(taskService.updateInfo(1L, 41L, "Task", null, false, false, null)).thenReturn(task(41L, null, "Task", 0));
        when(dailyPlanService.moveItemDate(1L, 7L, 41L, toDate)).thenReturn(fromDate);
        when(dailyPlanService.getRows(1L, fromDate, fromDate)).thenReturn(List.of());
        when(dailyPlanService.getRows(1L, toDate, toDate)).thenReturn(List.of(row(7L, toDate, 41L)));

        UpdateTaskInfoResponse response = taskUseCase.updateInfo(
                1L,
                41L,
                new UpdateTaskInfoRequest("Task", null, false, false, new UpdateTaskInfoRequest.PlanMove(7L, toDate))
        );

        assertThat(response.plans()).hasSize(2);
        assertThat(response.plans().get(0).date()).isEqualTo(fromDate);
        assertThat(response.plans().get(0).items()).isEmpty();
        assertThat(response.plans().get(1).date()).isEqualTo(toDate);
        assertThat(response.plans().get(1).items()).hasSize(1);
    }

    @Test
    @DisplayName("같은 날짜로 옮기면 그 날짜의 계획만 돌려준다")
    void keepsSingleDateWhenPlanDateUnchanged() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        when(taskOrderingService.nextRank(1L, false, false)).thenReturn(1024L);
        when(taskService.getOne(1L, 41L)).thenReturn(task(41L, null, "Task", 0));
        when(taskService.updateInfo(1L, 41L, "Task", null, false, false, null)).thenReturn(task(41L, null, "Task", 0));
        when(dailyPlanService.moveItemDate(1L, 7L, 41L, date)).thenReturn(date);
        when(dailyPlanService.getRows(1L, date, date)).thenReturn(List.of(row(7L, date, 41L)));

        UpdateTaskInfoResponse response = taskUseCase.updateInfo(
                1L,
                41L,
                new UpdateTaskInfoRequest("Task", null, false, false, new UpdateTaskInfoRequest.PlanMove(7L, date))
        );

        assertThat(response.plans()).hasSize(1);
        assertThat(response.plans().get(0).date()).isEqualTo(date);
    }

    @Test
    @DisplayName("계획 항목 id 없이 날짜만 고르면 그 날짜의 계획에 담는다")
    void addsToPlanWhenItemIdIsMissing() {
        LocalDate date = LocalDate.of(2026, 9, 5);
        when(taskService.getOne(1L, 41L)).thenReturn(task(41L, null, "Task", 0));
        when(taskService.updateInfo(1L, 41L, "Task", null, false, false, null))
                .thenReturn(task(41L, null, "Task", 0));
        when(dailyPlanService.getRows(1L, date, date)).thenReturn(List.of(row(7L, date, 41L)));

        UpdateTaskInfoResponse response = taskUseCase.updateInfo(
                1L,
                41L,
                new UpdateTaskInfoRequest("Task", null, false, false,
                        new UpdateTaskInfoRequest.PlanMove(null, date))
        );

        verify(dailyPlanService).addTaskIfAbsent(1L, date, 41L);
        assertThat(response.plans()).hasSize(1);
        assertThat(response.plans().get(0).date()).isEqualTo(date);
    }

    private DailyPlanItemQueryRow row(Long itemId, LocalDate planDate, Long taskId) {
        return new DailyPlanItemQueryRow(
                itemId, planDate, taskId, null, null, null, "Task", TaskStatus.TODO, 0);
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

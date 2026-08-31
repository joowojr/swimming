package com.swimming.backend.task.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.project.dto.ProjectReference;
import com.swimming.backend.project.service.ProjectService;
import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.dto.in.CreateTaskRequest;
import com.swimming.backend.task.dto.in.DeleteTasksRequest;
import com.swimming.backend.task.dto.in.TaskResponse;
import com.swimming.backend.task.dto.in.TaskListMode;
import com.swimming.backend.task.dto.in.UpdateTaskStatusRequest;
import com.swimming.backend.task.dto.in.UpdateTaskTitleRequest;
import com.swimming.backend.task.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskUseCaseTest {

    private TaskService taskService;
    private ProjectService projectService;
    private TaskUseCase taskUseCase;

    @BeforeEach
    void setUp() {
        taskService = mock(TaskService.class);
        projectService = mock(ProjectService.class);
        taskUseCase = new TaskUseCase(taskService, projectService);
    }

    @Test
    @DisplayName("소유한 폴더에 Task를 생성한다")
    void createsTaskInOwnedProject() {
        when(projectService.getReference(1L, 10L))
                .thenReturn(new ProjectReference(10L, "폴더", null));
        when(taskService.create(1L, 10L, "Task", false, false))
                .thenReturn(task(1L, 10L, "Task", 0));

        TaskResponse response = taskUseCase.create(
                1L,
                10L,
                new CreateTaskRequest("Task")
        );

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.projectId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("소유한 폴더의 Task를 저장된 순서대로 반환한다")
    void returnsTasksFromOwnedProject() {
        when(projectService.getReference(1L, 10L))
                .thenReturn(new ProjectReference(10L, "폴더", null));
        when(taskService.getByProject(10L)).thenReturn(List.of(
                task(1L, 10L, "첫째", 0),
                task(2L, 10L, "둘째", 1)
        ));

        List<TaskResponse> responses = taskUseCase.getByProject(1L, 10L);

        assertThat(responses).extracting(TaskResponse::title)
                .containsExactly("첫째", "둘째");
    }

    @Test
    @DisplayName("전체 모드는 사용자가 소유한 모든 Task를 반환한다")
    void returnsAllOwnedTasks() {
        when(taskService.getAll(1L)).thenReturn(List.of(
                task(2L, null, "최근 Task", 0),
                task(1L, 10L, "이전 Task", 0)
        ));

        List<TaskResponse> responses = taskUseCase.getList(1L, TaskListMode.ALL);

        assertThat(responses).extracting(TaskResponse::id)
                .containsExactly(2L, 1L);
        verify(taskService).getAll(1L);
    }

    @Test
    @DisplayName("미분류 모드는 폴더 없는 Task만 반환한다")
    void returnsUnclassifiedTasks() {
        when(taskService.getUnclassified(1L)).thenReturn(List.of(
                task(2L, null, "미분류 Task", 0)
        ));

        List<TaskResponse> responses = taskUseCase.getList(1L, TaskListMode.UNCLASSIFIED);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().projectId()).isNull();
        verify(taskService).getUnclassified(1L);
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

    private Task task(Long id, Long projectId, String title, int orderIdx) {
        return Task.restore(
                id,
                1L,
                projectId,
                null,
                title,
                TaskStatus.TODO,
                orderIdx,
                null,
                null
        );
    }
}

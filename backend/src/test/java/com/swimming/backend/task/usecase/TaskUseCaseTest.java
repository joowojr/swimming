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
    @DisplayName("소유한 프로젝트에 Task를 생성한다")
    void createsTaskInOwnedProject() {
        when(projectService.getReference(1L, 10L))
                .thenReturn(new ProjectReference(10L));
        when(taskService.create(1L, 10L, "Task")).thenReturn(task(1L, 10L, "Task", 0));

        TaskResponse response = taskUseCase.create(
                1L,
                10L,
                new CreateTaskRequest("Task")
        );

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.projectId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("소유한 프로젝트의 Task를 저장된 순서대로 반환한다")
    void returnsTasksFromOwnedProject() {
        when(projectService.getReference(1L, 10L))
                .thenReturn(new ProjectReference(10L));
        when(taskService.getAll(10L)).thenReturn(List.of(
                task(1L, 10L, "첫째", 0),
                task(2L, 10L, "둘째", 1)
        ));

        List<TaskResponse> responses = taskUseCase.getAll(1L, 10L);

        assertThat(responses).extracting(TaskResponse::title)
                .containsExactly("첫째", "둘째");
    }

    @Test
    @DisplayName("제목 수정은 상태를 건드리지 않는다")
    void updatesOnlyTitleAfterOwnershipCheck() {
        Task task = task(1L, 10L, "기존", 0);
        task.changeStatus(TaskStatus.DOING);
        when(taskService.getOne(1L, 1L)).thenReturn(task);
        when(taskService.update(1L, task)).thenReturn(task);

        TaskResponse response = taskUseCase.updateTitle(
                1L, 1L, new UpdateTaskTitleRequest("  수정  "));

        assertThat(response.title()).isEqualTo("수정");
        assertThat(response.status()).isEqualTo(TaskStatus.DOING);
    }

    @Test
    @DisplayName("상태 수정은 제목을 건드리지 않는다")
    void updatesOnlyStatusAfterOwnershipCheck() {
        Task task = task(1L, 10L, "기존", 0);
        when(taskService.getOne(1L, 1L)).thenReturn(task);
        when(taskService.update(1L, task)).thenReturn(task);

        TaskResponse response = taskUseCase.updateStatus(
                1L, 1L, new UpdateTaskStatusRequest(TaskStatus.DONE));

        assertThat(response.title()).isEqualTo("기존");
        assertThat(response.status()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    @DisplayName("다른 사용자의 Task는 찾을 수 없음으로 처리한다")
    void hidesAnotherUsersTask() {
        when(taskService.getOne(2L, 1L))
                .thenThrow(new BusinessException(ErrorCode.TASK_NOT_FOUND));

        assertThatThrownBy(() -> taskUseCase.updateTitle(
                2L, 1L, new UpdateTaskTitleRequest("수정")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND));
        verify(taskService).getOne(2L, 1L);
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

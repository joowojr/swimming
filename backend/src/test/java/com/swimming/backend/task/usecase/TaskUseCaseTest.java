package com.swimming.backend.task.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.project.dto.ProjectReference;
import com.swimming.backend.project.service.ProjectService;
import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.dto.web.CreateTaskRequest;
import com.swimming.backend.task.dto.web.DeleteTasksRequest;
import com.swimming.backend.task.dto.web.ReorderTasksRequest;
import com.swimming.backend.task.dto.web.TaskResponse;
import com.swimming.backend.task.dto.web.UpdateTaskRequest;
import com.swimming.backend.task.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        Task task = task(1L, 10L, "Task", 0);
        when(projectService.getReference(1L, 10L))
                .thenReturn(new ProjectReference(10L));
        when(taskService.create(10L, "Task")).thenReturn(task);

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
    @DisplayName("Task를 수정하기 전에 프로젝트 소유권을 확인한다")
    void updatesTaskAfterOwnershipCheck() {
        Task task = task(1L, 10L, "기존", 0);
        UpdateTaskRequest request = new UpdateTaskRequest(
                "수정",
                TaskStatus.DOING,
                40
        );
        when(taskService.getOne(1L)).thenReturn(task);
        when(projectService.getReference(1L, 10L))
                .thenReturn(new ProjectReference(10L));
        task.update("수정", TaskStatus.DOING, 40);
        when(taskService.update(task, "수정", TaskStatus.DOING, 40))
                .thenReturn(task);

        TaskResponse response = taskUseCase.update(1L, 1L, request);

        assertThat(response.status()).isEqualTo(TaskStatus.DOING);
        assertThat(response.completionPct()).isEqualTo(40);
    }

    @Test
    @DisplayName("다른 사용자의 Task는 찾을 수 없음으로 처리한다")
    void hidesAnotherUsersTask() {
        Task task = task(1L, 10L, "Task", 0);
        UpdateTaskRequest request = new UpdateTaskRequest(
                "수정",
                TaskStatus.DOING,
                40
        );
        when(taskService.getOne(1L)).thenReturn(task);
        when(projectService.getReference(2L, 10L))
                .thenThrow(new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        assertThatThrownBy(() -> taskUseCase.update(2L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(ErrorCode.TASK_NOT_FOUND));
        verify(taskService, never()).update(
                task,
                "수정",
                TaskStatus.DOING,
                40
        );
    }

    @Test
    @DisplayName("소유권을 확인한 여러 Task를 한 번에 삭제한다")
    void deletesOwnedTasksAtOnce() {
        Task first = task(1L, 10L, "첫째", 0);
        Task second = task(2L, 10L, "둘째", 1);
        when(taskService.getAllEntitiesByIds(List.of(1L, 2L)))
                .thenReturn(List.of(first, second));
        when(projectService.getReference(1L, 10L))
                .thenReturn(new ProjectReference(10L));

        taskUseCase.deleteTasks(
                1L,
                new DeleteTasksRequest(List.of(1L, 2L, 2L))
        );

        verify(taskService).deleteAll(List.of(first, second));
        verify(projectService).getReference(1L, 10L);
    }

    @Test
    @DisplayName("삭제 대상 중 찾을 수 없는 Task가 있으면 아무것도 삭제하지 않는다")
    void rejectsDeletionWhenAnyTaskIsMissing() {
        Task first = task(1L, 10L, "첫째", 0);
        when(taskService.getAllEntitiesByIds(List.of(1L, 2L)))
                .thenReturn(List.of(first));

        assertThatThrownBy(() -> taskUseCase.deleteTasks(
                1L,
                new DeleteTasksRequest(List.of(1L, 2L))
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(ErrorCode.TASK_NOT_FOUND));
        verify(taskService, never()).deleteAll(List.of(first));
    }

    @Test
    @DisplayName("삭제 대상 중 다른 사용자의 Task가 있으면 아무것도 삭제하지 않는다")
    void rejectsDeletionWhenAnyTaskIsNotOwned() {
        Task owned = task(1L, 10L, "내 Task", 0);
        Task notOwned = task(2L, 20L, "다른 Task", 0);
        when(taskService.getAllEntitiesByIds(List.of(1L, 2L)))
                .thenReturn(List.of(owned, notOwned));
        when(projectService.getReference(1L, 10L))
                .thenReturn(new ProjectReference(10L));
        when(projectService.getReference(1L, 20L))
                .thenThrow(new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        assertThatThrownBy(() -> taskUseCase.deleteTasks(
                1L,
                new DeleteTasksRequest(List.of(1L, 2L))
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(ErrorCode.TASK_NOT_FOUND));
        verify(taskService, never()).deleteAll(List.of(owned, notOwned));
    }

    @Test
    @DisplayName("프로젝트 소유권을 확인한 뒤 Task 순서를 저장한다")
    void reordersTasksAfterOwnershipCheck() {
        ReorderTasksRequest request = new ReorderTasksRequest(List.of(2L, 1L));
        when(projectService.getReference(1L, 10L))
                .thenReturn(new ProjectReference(10L));

        taskUseCase.reorder(1L, 10L, request);

        verify(taskService).updateOrder(10L, List.of(2L, 1L));
    }

    private Task task(Long id, Long projectId, String title, int orderIdx) {
        Task task = Task.builder()
                .projectId(projectId)
                .title(title)
                .orderIdx(orderIdx)
                .build();
        ReflectionTestUtils.setField(task, "id", id);
        return task;
    }
}

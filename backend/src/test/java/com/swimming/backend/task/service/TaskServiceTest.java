package com.swimming.backend.task.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.dto.TaskSummaryResponse;
import com.swimming.backend.task.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskServiceTest {

    private TaskRepository taskRepository;
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        taskRepository = mock(TaskRepository.class);
        taskService = new TaskService(taskRepository);
    }

    @Test
    @DisplayName("프로젝트의 첫 Task를 기본 상태와 순서로 생성한다")
    void createsFirstTaskWithDefaults() {
        when(taskRepository.findTopByProjectIdOrderByOrderIdxDescIdDesc(10L))
                .thenReturn(Optional.empty());
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            ReflectionTestUtils.setField(task, "id", 1L);
            return task;
        });

        Task task = taskService.create(10L, " API 명세 작성 ");

        assertThat(task.getId()).isEqualTo(1L);
        assertThat(task.getProjectId()).isEqualTo(10L);
        assertThat(task.getTitle()).isEqualTo("API 명세 작성");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(task.getCompletionPct()).isZero();
        assertThat(task.getOrderIdx()).isZero();
    }

    @Test
    @DisplayName("기존 마지막 Task 다음 순서로 생성한다")
    void createsTaskAfterCurrentLastOrder() {
        Task lastTask = task(3L, 10L, "기존 Task", 4);
        when(taskRepository.findTopByProjectIdOrderByOrderIdxDescIdDesc(10L))
                .thenReturn(Optional.of(lastTask));
        when(taskRepository.save(any(Task.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Task task = taskService.create(10L, "새 Task");

        assertThat(task.getOrderIdx()).isEqualTo(5);
    }

    @Test
    @DisplayName("Task 제목과 상태와 완료도를 수정한다")
    void updatesTaskFields() {
        Task task = task(1L, 10L, "기존 Task", 0);

        Task result = taskService.update(task, " 수정 Task ", TaskStatus.HOLD, 65);

        assertThat(result.getTitle()).isEqualTo("수정 Task");
        assertThat(result.getStatus()).isEqualTo(TaskStatus.HOLD);
        assertThat(result.getCompletionPct()).isEqualTo(65);
    }

    @Test
    @DisplayName("프로젝트 상세용 Task 요약을 저장된 순서대로 반환한다")
    void returnsTaskSummariesInStoredOrder() {
        Task first = task(2L, 10L, "첫째", 0);
        first.update("첫째", TaskStatus.DOING, 40);
        Task second = task(1L, 10L, "둘째", 1);
        when(taskRepository.findAllByProjectIdOrderByOrderIdxAscIdAsc(10L))
                .thenReturn(List.of(first, second));

        List<TaskSummaryResponse> responses = taskService.getSummaries(10L);

        assertThat(responses).extracting(TaskSummaryResponse::id)
                .containsExactly(2L, 1L);
        assertThat(responses.getFirst().status()).isEqualTo(TaskStatus.DOING);
        assertThat(responses.getFirst().completionPct()).isEqualTo(40);
        assertThat(responses.getFirst().orderIdx()).isZero();
    }

    @Test
    @DisplayName("Task를 프로젝트 목록에서 삭제한다")
    void deletesTask() {
        Task task = task(1L, 10L, "삭제 Task", 0);

        taskService.delete(task);

        verify(taskRepository).delete(task);
    }

    @Test
    @DisplayName("전달받은 전체 Task ID 순서대로 순서를 다시 부여한다")
    void reordersEveryTaskInProject() {
        Task first = task(1L, 10L, "첫째", 0);
        Task second = task(2L, 10L, "둘째", 1);
        Task third = task(3L, 10L, "셋째", 2);
        when(taskRepository.findAllByProjectIdOrderByOrderIdxAscIdAsc(10L))
                .thenReturn(List.of(first, second, third));

        taskService.updateOrder(10L, List.of(3L, 1L, 2L));

        assertThat(third.getOrderIdx()).isZero();
        assertThat(first.getOrderIdx()).isEqualTo(1);
        assertThat(second.getOrderIdx()).isEqualTo(2);
    }

    @Test
    @DisplayName("Task ID가 누락되거나 중복된 순서 요청을 거부한다")
    void rejectsIncompleteOrDuplicateOrder() {
        Task first = task(1L, 10L, "첫째", 0);
        Task second = task(2L, 10L, "둘째", 1);
        when(taskRepository.findAllByProjectIdOrderByOrderIdxAscIdAsc(10L))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> taskService.updateOrder(10L, List.of(1L)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(ErrorCode.INVALID_TASK_ORDER));

        assertThatThrownBy(() -> taskService.updateOrder(10L, List.of(1L, 1L)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(ErrorCode.INVALID_TASK_ORDER));
    }

    @Test
    @DisplayName("다른 프로젝트의 Task가 포함된 순서 요청을 거부한다")
    void rejectsTaskFromAnotherProjectInOrder() {
        Task first = task(1L, 10L, "첫째", 0);
        Task second = task(2L, 10L, "둘째", 1);
        when(taskRepository.findAllByProjectIdOrderByOrderIdxAscIdAsc(10L))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> taskService.updateOrder(10L, List.of(1L, 99L)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(ErrorCode.INVALID_TASK_ORDER));
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

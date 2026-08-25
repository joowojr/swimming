package com.swimming.backend.task.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.note.repository.entity.NoteEntity;
import com.swimming.backend.project.domain.Project;
import com.swimming.backend.project.repository.entity.ProjectEntity;
import com.swimming.backend.project.domain.ProjectStatus;
import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.dto.projection.TaskReference;
import com.swimming.backend.task.dto.projection.TaskOrganizerContextRow;
import com.swimming.backend.task.dto.in.TaskSummaryResponse;
import com.swimming.backend.task.repository.TaskRepository;
import com.swimming.backend.task.repository.entity.TaskEntity;
import com.swimming.backend.user.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskServiceTest {

    private TaskRepository taskRepository;
    private EntityManager entityManager;
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        taskRepository = mock(TaskRepository.class);
        entityManager = mock(EntityManager.class);
        taskService = new TaskService(taskRepository, entityManager);
        when(entityManager.getReference(eq(User.class), anyLong()))
                .thenAnswer(invocation -> user(invocation.getArgument(1)));
        when(entityManager.getReference(eq(ProjectEntity.class), anyLong()))
                .thenAnswer(invocation -> project(invocation.getArgument(1)));
        when(entityManager.getReference(eq(NoteEntity.class), anyLong()))
                .thenAnswer(invocation -> {
                    NoteEntity note = mock(NoteEntity.class);
                    when(note.getId()).thenReturn(invocation.getArgument(1));
                    return note;
                });
        when(taskRepository.saveAndFlush(any(TaskEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("프로젝트의 첫 Task를 기본 상태와 순서로 생성해 순수 도메인으로 반환한다")
    void createsFirstTaskWithDefaults() {
        when(taskRepository.findTopByProject_IdOrderByOrderIdxDescIdDesc(10L))
                .thenReturn(Optional.empty());
        when(taskRepository.saveAndFlush(any(TaskEntity.class))).thenAnswer(invocation -> {
            TaskEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 1L);
            return entity;
        });

        Task task = taskService.create(1L, 10L, " API 명세 작성 ");

        assertThat(task.getId()).isEqualTo(1L);
        assertThat(task.getProjectId()).isEqualTo(10L);
        assertThat(task.getTitle()).isEqualTo("API 명세 작성");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(task.getOrderIdx()).isZero();
    }

    @Test
    @DisplayName("기존 마지막 Task 다음 순서로 생성한다")
    void createsTaskAfterCurrentLastOrder() {
        when(taskRepository.findTopByProject_IdOrderByOrderIdxDescIdDesc(10L))
                .thenReturn(Optional.of(taskEntity(3L, 10L, "기존 Task", 4)));

        Task task = taskService.create(1L, 10L, "새 Task");

        assertThat(task.getOrderIdx()).isEqualTo(5);
    }

    @Test
    @DisplayName("프로젝트 없는 Task를 사용자 기준 다음 순서로 생성한다")
    void createsProjectlessTaskForUser() {
        when(taskRepository.findTopByUser_IdAndProjectIsNullOrderByOrderIdxDescIdDesc(1L))
                .thenReturn(Optional.empty());
        when(taskRepository.saveAndFlush(any(TaskEntity.class))).thenAnswer(invocation -> {
            TaskEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 2L);
            return entity;
        });

        Task task = taskService.create(1L, null, " 자격증 접수 ");

        assertThat(task.getId()).isEqualTo(2L);
        assertThat(task.getUserId()).isEqualTo(1L);
        assertThat(task.getProjectId()).isNull();
        assertThat(task.getTitle()).isEqualTo("자격증 접수");
        verify(entityManager).getReference(User.class, 1L);
    }

    @Test
    @DisplayName("Note에서 생성한 Task에 원문 Note ID를 저장한다")
    void createsTaskWithSourceNote() {
        when(taskRepository.findTopByProject_IdOrderByOrderIdxDescIdDesc(10L))
                .thenReturn(Optional.empty());

        Task task = taskService.createFromNote(1L, 10L, 7L, "새 Task");

        assertThat(task.getSourceNoteId()).isEqualTo(7L);
        verify(entityManager).getReference(NoteEntity.class, 7L);
    }

    @Test
    @DisplayName("사용자가 소유한 Task 상세를 순수 도메인으로 조회한다")
    void returnsOwnedTaskDetail() {
        when(taskRepository.findByIdAndUser_Id(1L, 1L))
                .thenReturn(Optional.of(taskEntity(1L, 10L, "Task", 0)));

        Task task = taskService.getOne(1L, 1L);

        assertThat(task.getId()).isEqualTo(1L);
        assertThat(task.getProjectId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("존재하지 않는 Task를 조회하면 찾을 수 없음으로 처리한다")
    void rejectsMissingTask() {
        when(taskRepository.findByIdAndUser_Id(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getOne(1L, 1L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND));
    }

    @Test
    @DisplayName("Task 제목과 상태를 수정한다")
    void updatesTaskFields() {
        TaskEntity entity = taskEntity(1L, 10L, "기존 Task", 0);
        when(taskRepository.findByIdAndUser_Id(1L, 1L)).thenReturn(Optional.of(entity));

        Task task = entity.toDomain();
        task.update(" 수정 Task ", TaskStatus.HOLD);

        Task result = taskService.update(1L, task);

        assertThat(result.getTitle()).isEqualTo("수정 Task");
        assertThat(result.getStatus()).isEqualTo(TaskStatus.HOLD);
        verify(taskRepository).saveAndFlush(entity);
    }

    @Test
    @DisplayName("사용자가 소유한 여러 Task를 프로젝트 정보가 포함된 조회 DTO로 반환한다")
    void returnsTaskReferencesByIds() {
        List<TaskReference> expected = List.of(
                new TaskReference(1L, 10L, "첫 프로젝트", "첫째", TaskStatus.TODO),
                new TaskReference(2L, 20L, "둘 프로젝트", "둘째", TaskStatus.DOING)
        );
        when(taskRepository.findAllOwnedByIds(1L, List.of(2L, 1L)))
                .thenReturn(expected);

        List<TaskReference> references = taskService.getReferences(1L, List.of(2L, 1L));

        assertThat(references).isSameAs(expected);
        assertThat(references.get(1).projectName()).isEqualTo("둘 프로젝트");
    }

    @Test
    @DisplayName("Task Organizer용 프로젝트와 Task 컨텍스트를 단일 조회 결과로 반환한다")
    void returnsTaskOrganizerContextFromSingleQuery() {
        List<TaskOrganizerContextRow> expected = List.of(
                new TaskOrganizerContextRow(
                        10L,
                        "Swimming",
                        "생산성 서비스",
                        1L,
                        "Note API 연결",
                        TaskStatus.DOING
                ),
                new TaskOrganizerContextRow(
                        20L,
                        "빈 프로젝트",
                        null,
                        null,
                        null,
                        null
                )
        );
        when(taskRepository.findTaskOrganizerContext(1L, ProjectStatus.ARCHIVED))
                .thenReturn(expected);

        List<TaskOrganizerContextRow> result = taskService.getTaskOrganizerContext(1L);

        assertThat(result).isSameAs(expected);
        verify(taskRepository).findTaskOrganizerContext(1L, ProjectStatus.ARCHIVED);
    }

    @Test
    @DisplayName("프로젝트 상세용 Task 요약을 저장된 순서대로 반환한다")
    void returnsTaskSummariesInStoredOrder() {
        TaskEntity first = taskEntity(2L, 10L, "첫째", 0, TaskStatus.DOING);
        TaskEntity second = taskEntity(1L, 10L, "둘째", 1);
        when(taskRepository.findAllByProject_IdOrderByOrderIdxAscIdAsc(10L))
                .thenReturn(List.of(first, second));

        List<TaskSummaryResponse> responses = taskService.getSummaries(10L);

        assertThat(responses).extracting(TaskSummaryResponse::id)
                .containsExactly(2L, 1L);
        assertThat(responses.getFirst().status()).isEqualTo(TaskStatus.DOING);
        assertThat(responses.getFirst().orderIdx()).isZero();
    }

    @Test
    @DisplayName("여러 Task를 ID 기준으로 배치 삭제한다")
    void deletesTasksInBatch() {
        List<TaskEntity> tasks = List.of(
                taskEntity(1L, 10L, "첫째", 0),
                taskEntity(2L, 10L, "둘째", 1)
        );
        when(taskRepository.findAllOwnedEntitiesByIds(1L, List.of(1L, 2L)))
                .thenReturn(tasks);

        taskService.deleteAll(1L, List.of(1L, 2L));

        verify(taskRepository).deleteAllInBatch(tasks);
    }

    @Test
    @DisplayName("삭제 대상에 다른 사용자의 Task가 있으면 삭제하지 않는다")
    void rejectsDeletingTasksNotOwnedByUser() {
        when(taskRepository.findAllOwnedEntitiesByIds(1L, List.of(1L, 2L)))
                .thenReturn(List.of(taskEntity(1L, 10L, "내 Task", 0)));

        assertThatThrownBy(() -> taskService.deleteAll(1L, List.of(1L, 2L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND));

        verify(taskRepository, org.mockito.Mockito.never()).deleteAllInBatch(any());
    }

    @Test
    @DisplayName("사용자가 소유한 여러 Task의 상태를 한 번에 변경한다")
    void updatesStatusesOfOwnedTasks() {
        TaskEntity first = taskEntity(1L, 10L, "첫째", 0);
        TaskEntity second = taskEntity(2L, 10L, "둘째", 1);
        Map<Long, TaskStatus> statusByTaskId = Map.of(
                1L, TaskStatus.DONE,
                2L, TaskStatus.DOING
        );
        when(taskRepository.findAllOwnedEntitiesByIds(any(), any()))
                .thenReturn(List.of(first, second));

        taskService.updateStatuses(1L, statusByTaskId);

        assertThat(first.getStatus()).isEqualTo(TaskStatus.DONE);
        assertThat(second.getStatus()).isEqualTo(TaskStatus.DOING);
    }

    @Test
    @DisplayName("상태를 변경할 Task 중 소유하지 않은 Task가 있으면 아무것도 변경하지 않는다")
    void rejectsStatusUpdateWhenAnyTaskIsNotOwned() {
        TaskEntity owned = taskEntity(1L, 10L, "내 Task", 0);
        Map<Long, TaskStatus> statusByTaskId = Map.of(
                1L, TaskStatus.DONE,
                2L, TaskStatus.DOING
        );
        when(taskRepository.findAllOwnedEntitiesByIds(any(), any()))
                .thenReturn(List.of(owned));

        assertThatThrownBy(() -> taskService.updateStatuses(1L, statusByTaskId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND));
        assertThat(owned.getStatus()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    @DisplayName("전달받은 전체 Task ID 순서대로 순서를 다시 부여한다")
    void reordersEveryTaskInProject() {
        TaskEntity first = taskEntity(1L, 10L, "첫째", 0);
        TaskEntity second = taskEntity(2L, 10L, "둘째", 1);
        TaskEntity third = taskEntity(3L, 10L, "셋째", 2);
        when(taskRepository.findAllByProject_IdOrderByOrderIdxAscIdAsc(10L))
                .thenReturn(List.of(first, second, third));

        taskService.updateOrder(10L, List.of(3L, 1L, 2L));

        assertThat(third.getOrderIdx()).isZero();
        assertThat(first.getOrderIdx()).isEqualTo(1);
        assertThat(second.getOrderIdx()).isEqualTo(2);
    }

    @Test
    @DisplayName("Task ID가 누락되거나 중복된 순서 요청을 거부한다")
    void rejectsIncompleteOrDuplicateOrder() {
        TaskEntity first = taskEntity(1L, 10L, "첫째", 0);
        TaskEntity second = taskEntity(2L, 10L, "둘째", 1);
        when(taskRepository.findAllByProject_IdOrderByOrderIdxAscIdAsc(10L))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> taskService.updateOrder(10L, List.of(1L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TASK_ORDER));

        assertThatThrownBy(() -> taskService.updateOrder(10L, List.of(1L, 1L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TASK_ORDER));
    }

    @Test
    @DisplayName("다른 프로젝트의 Task가 포함된 순서 요청을 거부한다")
    void rejectsTaskFromAnotherProjectInOrder() {
        TaskEntity first = taskEntity(1L, 10L, "첫째", 0);
        TaskEntity second = taskEntity(2L, 10L, "둘째", 1);
        when(taskRepository.findAllByProject_IdOrderByOrderIdxAscIdAsc(10L))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> taskService.updateOrder(10L, List.of(1L, 99L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TASK_ORDER));
    }

    private TaskEntity taskEntity(Long id, Long projectId, String title, int orderIdx) {
        return taskEntity(id, projectId, title, orderIdx, TaskStatus.TODO);
    }

    private TaskEntity taskEntity(
            Long id,
            Long projectId,
            String title,
            int orderIdx,
            TaskStatus status
    ) {
        Task task = Task.create(1L, projectId, title, orderIdx);
        task.update(title, status);
        TaskEntity entity = TaskEntity.from(task, user(1L), project(projectId), null);
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private ProjectEntity project(Long projectId) {
        User user = user(1L);
        ProjectEntity project = ProjectEntity.from(
                Project.create(1L, null, "프로젝트", "설명", null),
                user,
                null
        );
        ReflectionTestUtils.setField(project, "id", projectId);
        return project;
    }

    private User user(Long userId) {
        User user = User.builder()
                .email("user@example.com")
                .passwordHash("password")
                .nickname("사용자")
                .timezone("Asia/Seoul")
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }
}

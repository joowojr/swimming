package com.swimming.backend.task.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.note.repository.entity.NoteEntity;
import com.swimming.backend.folder.domain.Folder;
import com.swimming.backend.folder.repository.entity.FolderEntity;
import com.swimming.backend.folder.domain.FolderStatus;
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
        when(entityManager.getReference(eq(FolderEntity.class), anyLong()))
                .thenAnswer(invocation -> folder(invocation.getArgument(1)));
        when(entityManager.getReference(eq(NoteEntity.class), anyLong()))
                .thenAnswer(invocation -> {
                    NoteEntity note = mock(NoteEntity.class);
                    when(note.getId()).thenReturn(invocation.getArgument(1));
                    return note;
                });
        when(taskRepository.saveAndFlush(any(TaskEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.softDeleteAllOwnedByIds(any(), any())).thenAnswer(invocation ->
                ((List<?>) invocation.getArgument(1)).size());
        when(taskRepository.updateOwnedStatuses(any(), any(), any())).thenAnswer(invocation ->
                ((List<?>) invocation.getArgument(1)).size());
    }

    @Test
    @DisplayName("폴더의 첫 Task를 기본 상태와 순서로 생성해 순수 도메인으로 반환한다")
    void createsFirstTaskWithDefaults() {
        when(taskRepository.findTopByFolder_IdAndDeletedFalseOrderByIdDesc(10L))
                .thenReturn(Optional.empty());
        when(taskRepository.saveAndFlush(any(TaskEntity.class))).thenAnswer(invocation -> {
            TaskEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 1L);
            return entity;
        });

        Task task = taskService.create(1L, 10L, " API 명세 작성 ");

        assertThat(task.getId()).isEqualTo(1L);
        assertThat(task.getFolderId()).isEqualTo(10L);
        assertThat(task.getTitle()).isEqualTo("API 명세 작성");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(task.getOrderIdx()).isZero();
        assertThat(task.getMatrixRank()).isZero();
    }

    @Test
    @DisplayName("기존 마지막 Task 다음 순서로 생성한다")
    void createsTaskAfterCurrentLastOrder() {
        when(taskRepository.findTopByFolder_IdAndDeletedFalseOrderByIdDesc(10L))
                .thenReturn(Optional.of(taskEntity(3L, 10L, "기존 Task", 4)));

        Task task = taskService.create(1L, 10L, "새 Task");

        assertThat(task.getOrderIdx()).isEqualTo(5);
    }

    @Test
    @DisplayName("폴더 없는 Task를 사용자 기준 다음 순서로 생성한다")
    void createsFolderlessTaskForUser() {
        when(taskRepository.findTopByUser_IdAndFolderIsNullAndDeletedFalseOrderByOrderIdxDescIdDesc(1L))
                .thenReturn(Optional.empty());
        when(taskRepository.saveAndFlush(any(TaskEntity.class))).thenAnswer(invocation -> {
            TaskEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 2L);
            return entity;
        });

        Task task = taskService.create(1L, null, " 자격증 접수 ");

        assertThat(task.getId()).isEqualTo(2L);
        assertThat(task.getUserId()).isEqualTo(1L);
        assertThat(task.getFolderId()).isNull();
        assertThat(task.getTitle()).isEqualTo("자격증 접수");
        verify(entityManager).getReference(User.class, 1L);
    }

    @Test
    @DisplayName("Note에서 생성한 Task에 원문 Note ID를 저장한다")
    void createsTaskWithSourceNote() {
        when(taskRepository.findTopByFolder_IdAndDeletedFalseOrderByIdDesc(10L))
                .thenReturn(Optional.empty());

        Task task = taskService.createFromNote(1L, 10L, 7L, "새 Task");

        assertThat(task.getSourceNoteId()).isEqualTo(7L);
        verify(entityManager).getReference(NoteEntity.class, 7L);
    }

    @Test
    @DisplayName("사용자가 소유한 Task 상세를 순수 도메인으로 조회한다")
    void returnsOwnedTaskDetail() {
        when(taskRepository.findByIdAndUser_IdAndDeletedFalse(1L, 1L))
                .thenReturn(Optional.of(taskEntity(1L, 10L, "Task", 0)));

        Task task = taskService.getOne(1L, 1L);

        assertThat(task.getId()).isEqualTo(1L);
        assertThat(task.getFolderId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("사용자의 모든 Task를 최신순 조회 결과대로 반환한다")
    void returnsAllOwnedTasksInLatestOrder() {
        TaskEntity recent = taskEntity(2L, null, "최근 Task", 0);
        TaskEntity previous = taskEntity(1L, 10L, "이전 Task", 0);
        when(taskRepository.findAllByUser_IdAndDeletedFalseOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(recent, previous));

        List<Task> tasks = taskService.getAll(1L);

        assertThat(tasks).extracting(Task::getId).containsExactly(2L, 1L);
        verify(taskRepository).findAllByUser_IdAndDeletedFalseOrderByCreatedAtDesc(1L);
    }

    @Test
    @DisplayName("사용자의 폴더 없는 Task를 최신순 조회 결과대로 반환한다")
    void returnsUnclassifiedTasksInLatestOrder() {
        TaskEntity recent = taskEntity(2L, null, "최근 미분류", 0);
        TaskEntity previous = taskEntity(1L, null, "이전 미분류", 0);
        when(taskRepository.findAllByUser_IdAndFolderIsNullAndDeletedFalseOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(recent, previous));

        List<Task> tasks = taskService.getUnclassified(1L);

        assertThat(tasks).extracting(Task::getId).containsExactly(2L, 1L);
        assertThat(tasks).allMatch(task -> task.getFolderId() == null);
        verify(taskRepository).findAllByUser_IdAndFolderIsNullAndDeletedFalseOrderByCreatedAtDesc(1L);
    }

    @Test
    @DisplayName("UseCase가 계산한 Matrix rank로 Task를 생성한다")
    void createsTaskWithSuppliedMatrixRank() {
        Task task = taskService.create(1L, null, "새 중요 Task", true, false, 5120L);

        assertThat(task.isPriority()).isTrue();
        assertThat(task.isUrgent()).isFalse();
        assertThat(task.getMatrixRank()).isEqualTo(5120L);
    }

    @Test
    @DisplayName("존재하지 않는 Task를 조회하면 찾을 수 없음으로 처리한다")
    void rejectsMissingTask() {
        when(taskRepository.findByIdAndUser_IdAndDeletedFalse(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getOne(1L, 1L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND));
    }

    @Test
    @DisplayName("제목 수정 API는 소유 Entity의 제목만 변경 감지로 저장한다")
    void updatesOnlyTaskTitle() {
        TaskEntity entity = taskEntity(1L, 10L, "기존 Task", 0);
        when(taskRepository.findByIdAndUser_IdAndDeletedFalse(1L, 1L)).thenReturn(Optional.of(entity));

        Task result = taskService.updateTitle(1L, 1L, " 수정 Task ");

        assertThat(result.getTitle()).isEqualTo("수정 Task");
        assertThat(result.getStatus()).isEqualTo(TaskStatus.TODO);
        verify(taskRepository).findByIdAndUser_IdAndDeletedFalse(1L, 1L);
        verify(taskRepository).flush();
    }

    @Test
    @DisplayName("상태 수정 API는 소유 Entity의 상태만 변경 감지로 저장한다")
    void updatesOnlyTaskStatus() {
        TaskEntity entity = taskEntity(1L, 10L, "기존 Task", 0);
        when(taskRepository.findByIdAndUser_IdAndDeletedFalse(1L, 1L)).thenReturn(Optional.of(entity));

        Task result = taskService.updateStatus(1L, 1L, TaskStatus.HOLD);

        assertThat(result.getTitle()).isEqualTo("기존 Task");
        assertThat(result.getStatus()).isEqualTo(TaskStatus.HOLD);
        verify(taskRepository).findByIdAndUser_IdAndDeletedFalse(1L, 1L);
        verify(taskRepository).flush();
    }

    @Test
    @DisplayName("제목 수정 대상이 없으면 찾을 수 없음으로 처리한다")
    void rejectsMissingTaskWhenUpdatingTitle() {
        when(taskRepository.findByIdAndUser_IdAndDeletedFalse(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.updateTitle(1L, 1L, "수정 Task"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND));
        verify(taskRepository, org.mockito.Mockito.never()).flush();
    }

    @Test
    @DisplayName("상태 수정 대상이 없으면 찾을 수 없음으로 처리한다")
    void rejectsMissingTaskWhenUpdatingStatus() {
        when(taskRepository.findByIdAndUser_IdAndDeletedFalse(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.updateStatus(1L, 1L, TaskStatus.DONE))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND));
        verify(taskRepository, org.mockito.Mockito.never()).flush();
    }

    @Test
    @DisplayName("사용자가 소유한 여러 Task를 폴더 정보가 포함된 조회 DTO로 반환한다")
    void returnsTaskReferencesByIds() {
        List<TaskReference> expected = List.of(
                new TaskReference(1L, 10L, "첫 폴더", "첫째", TaskStatus.TODO),
                new TaskReference(2L, 20L, "둘 폴더", "둘째", TaskStatus.DOING)
        );
        when(taskRepository.findAllOwnedByIdsIncludingDeleted(1L, List.of(2L, 1L)))
                .thenReturn(expected);

        List<TaskReference> references = taskService.getReferences(1L, List.of(2L, 1L));

        assertThat(references).isSameAs(expected);
        assertThat(references.get(1).folderName()).isEqualTo("둘 폴더");
    }

    @Test
    @DisplayName("활성 Task 검증은 soft delete되지 않은 Task만 조회한다")
    void returnsOnlyActiveTaskReferencesForValidation() {
        List<TaskReference> expected = List.of(
                new TaskReference(1L, 10L, "폴더", "활성 Task", TaskStatus.TODO)
        );
        when(taskRepository.findAllOwnedActiveByIds(1L, List.of(1L, 2L)))
                .thenReturn(expected);

        assertThat(taskService.getActiveReferences(1L, List.of(1L, 2L)))
                .isSameAs(expected);
    }

    @Test
    @DisplayName("Task Organizer용 폴더와 Task 컨텍스트를 단일 조회 결과로 반환한다")
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
                        "빈 폴더",
                        null,
                        null,
                        null,
                        null
                )
        );
        when(taskRepository.findTaskOrganizerContext(1L, FolderStatus.ARCHIVED))
                .thenReturn(expected);

        List<TaskOrganizerContextRow> result = taskService.getTaskOrganizerContext(1L);

        assertThat(result).isSameAs(expected);
        verify(taskRepository).findTaskOrganizerContext(1L, FolderStatus.ARCHIVED);
    }

    @Test
    @DisplayName("폴더 상세용 Task 요약을 저장된 순서대로 반환한다")
    void returnsTaskSummariesInStoredOrder() {
        TaskEntity first = taskEntity(2L, 10L, "첫째", 0, TaskStatus.DOING);
        TaskEntity second = taskEntity(1L, 10L, "둘째", 1);
        when(taskRepository.findAllByFolderIdWithFolder(10L))
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
        taskService.deleteAll(1L, List.of(1L, 2L));

        verify(taskRepository).softDeleteAllOwnedByIds(1L, List.of(1L, 2L));
    }

    @Test
    @DisplayName("삭제 대상에 다른 사용자의 Task가 있으면 삭제하지 않는다")
    void rejectsDeletingTasksNotOwnedByUser() {
        when(taskRepository.softDeleteAllOwnedByIds(1L, List.of(1L, 2L))).thenReturn(1);

        assertThatThrownBy(() -> taskService.deleteAll(1L, List.of(1L, 2L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND));

        verify(taskRepository).softDeleteAllOwnedByIds(1L, List.of(1L, 2L));
    }

    @Test
    @DisplayName("사용자가 소유한 여러 Task의 상태를 한 번에 변경한다")
    void updatesStatusesOfOwnedTasks() {
        Map<Long, TaskStatus> statusByTaskId = Map.of(
                1L, TaskStatus.DONE,
                2L, TaskStatus.DOING
        );
        taskService.updateStatuses(1L, statusByTaskId);

        verify(taskRepository).updateOwnedStatuses(1L, List.of(1L), TaskStatus.DONE);
        verify(taskRepository).updateOwnedStatuses(1L, List.of(2L), TaskStatus.DOING);
    }

    @Test
    @DisplayName("상태를 변경할 Task 중 소유하지 않은 Task가 있으면 아무것도 변경하지 않는다")
    void rejectsStatusUpdateWhenAnyTaskIsNotOwned() {
        Map<Long, TaskStatus> statusByTaskId = Map.of(
                1L, TaskStatus.DONE,
                2L, TaskStatus.DOING
        );
        org.mockito.Mockito.doReturn(0)
                .when(taskRepository)
                .updateOwnedStatuses(1L, List.of(2L), TaskStatus.DOING);

        assertThatThrownBy(() -> taskService.updateStatuses(1L, statusByTaskId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND));
    }

    private TaskEntity taskEntity(Long id, Long folderId, String title, int orderIdx) {
        return taskEntity(id, folderId, title, orderIdx, TaskStatus.TODO);
    }

    private TaskEntity taskEntity(
            Long id,
            Long folderId,
            String title,
            int orderIdx,
            TaskStatus status
    ) {
        Task task = Task.create(1L, folderId, title, orderIdx);
        task.changeStatus(status);
        FolderEntity folder = folderId == null ? null : folder(folderId);
        TaskEntity entity = TaskEntity.from(task, user(1L), folder, null);
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private FolderEntity folder(Long folderId) {
        User user = user(1L);
        FolderEntity folder = FolderEntity.from(
                Folder.create(1L, null, "폴더", "설명", null),
                user,
                null
        );
        ReflectionTestUtils.setField(folder, "id", folderId);
        return folder;
    }

    private User user(Long userId) {
        User user = User.builder()
                .email("user@example.com")
                .googleSubject("task-service-google-subject")
                .nickname("사용자")
                .timezone("Asia/Seoul")
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }
}

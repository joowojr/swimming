package com.swimming.backend.task.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.note.repository.entity.NoteEntity;
import com.swimming.backend.folder.domain.FolderStatus;
import com.swimming.backend.folder.repository.entity.FolderEntity;
import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.dto.projection.TaskOrganizerContextRow;
import com.swimming.backend.task.dto.projection.TaskReference;
import com.swimming.backend.task.dto.in.TaskSummaryResponse;
import com.swimming.backend.task.repository.TaskRepository;
import com.swimming.backend.task.repository.entity.TaskEntity;
import com.swimming.backend.user.domain.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRED)
    public Task create(Long userId, Long folderId, String title) {
        return create(userId, folderId, null, title, false, false, 0L);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Task create(Long userId, Long folderId, String title, boolean priority, boolean urgent) {
        return create(userId, folderId, null, title, priority, urgent, 0L);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Task create(
            Long userId,
            Long folderId,
            String title,
            boolean priority,
            boolean urgent,
            long matrixRank
    ) {
        return create(userId, folderId, null, title, priority, urgent, matrixRank);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Task createFromNote(
            Long userId,
            Long folderId,
            Long sourceNoteId,
            String title
    ) {
        return create(userId, folderId, sourceNoteId, title, false, false, 0L);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Task createFromNote(
            Long userId,
            Long folderId,
            Long sourceNoteId,
            String title,
            boolean priority,
            boolean urgent,
            long matrixRank
    ) {
        return create(userId, folderId, sourceNoteId, title, priority, urgent, matrixRank);
    }

    private Task create(
            Long userId,
            Long folderId,
            Long sourceNoteId,
            String title,
            boolean priority,
            boolean urgent,
            long matrixRank
    ) {
        int nextOrder = (folderId == null
                ? taskRepository.findTopByUser_IdAndProjectIsNullAndDeletedFalseOrderByOrderIdxDescIdDesc(userId)
                : taskRepository.findTopByProject_IdAndDeletedFalseOrderByIdDesc(folderId))
                .map(TaskEntity::getOrderIdx)
                .map(orderIdx -> orderIdx + 1)
                .orElse(0);
        Task task = sourceNoteId == null
                ? Task.create(userId, folderId, title, nextOrder, priority, urgent, matrixRank)
                : Task.createFromNote(userId, folderId, sourceNoteId, title, nextOrder, priority, urgent, matrixRank);
        User user = entityManager.getReference(User.class, userId);
        FolderEntity project = folderId == null
                ? null
                : entityManager.getReference(FolderEntity.class, folderId);
        NoteEntity sourceNote = sourceNoteId == null
                ? null
                : entityManager.getReference(NoteEntity.class, sourceNoteId);
        return taskRepository.saveAndFlush(
                TaskEntity.from(task, user, project, sourceNote)
        ).toDomain();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<Task> getByProject(Long folderId) {
        return taskRepository.findAllByProjectIdWithProject(folderId)
                .stream()
                .map(TaskEntity::toDomain)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<Task> getAll(Long userId) {
        return taskRepository.findAllByUser_IdAndDeletedFalseOrderByCreatedAtDesc(userId)
                .stream()
                .map(TaskEntity::toDomain)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<Task> getUnclassified(Long userId) {
        return taskRepository.findAllByUser_IdAndProjectIsNullAndDeletedFalseOrderByCreatedAtDesc(userId)
                .stream()
                .map(TaskEntity::toDomain)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<TaskReference> getReferences(Long userId, List<Long> taskIds) {
        return taskRepository.findAllOwnedByIdsIncludingDeleted(userId, taskIds);
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<TaskReference> getActiveReferences(Long userId, List<Long> taskIds) {
        return taskRepository.findAllOwnedActiveByIds(userId, taskIds);
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<TaskOrganizerContextRow> getTaskOrganizerContext(Long userId) {
        return taskRepository.findTaskOrganizerContext(
                userId,
                FolderStatus.ARCHIVED
        );
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<TaskSummaryResponse> getSummaries(Long folderId) {
        return getByProject(folderId)
                .stream()
                .map(TaskSummaryResponse::from)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Task getOne(Long userId, Long taskId) {
        return getOwnedEntity(userId, taskId).toDomain();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Task updateTitle(Long userId, Long taskId, String title) {
        TaskEntity entity = getOwnedEntity(userId, taskId);
        entity.updateTitle(title.trim());
        taskRepository.flush();
        return entity.toDomain();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Task updateStatus(Long userId, Long taskId, TaskStatus status) {
        TaskEntity entity = getOwnedEntity(userId, taskId);
        entity.updateStatus(status);
        taskRepository.flush();
        return entity.toDomain();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteAll(Long userId, List<Long> taskIds) {
        if (taskIds.isEmpty()) {
            return;
        }
        if (taskRepository.softDeleteAllOwnedByIds(userId, taskIds) != taskIds.size()) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updateStatuses(Long userId, Map<Long, TaskStatus> statusByTaskId) {
        if (statusByTaskId.isEmpty()) {
            return;
        }
        int updatedCount = statusByTaskId.entrySet().stream()
                .collect(Collectors.groupingBy(
                        Map.Entry::getValue,
                        Collectors.mapping(Map.Entry::getKey, Collectors.toList())
                ))
                .entrySet()
                .stream()
                .mapToInt(entry -> taskRepository.updateOwnedStatuses(
                        userId,
                        entry.getValue(),
                        entry.getKey()
                ))
                .sum();
        if (updatedCount != statusByTaskId.size()) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
    }

    private TaskEntity getOwnedEntity(Long userId, Long taskId) {
        return taskRepository.findByIdAndUser_IdAndDeletedFalse(taskId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
    }

}

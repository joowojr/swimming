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
import org.springframework.data.domain.Sort;
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
                ? taskRepository.findTopByUser_IdAndFolderIsNullAndDeletedFalseOrderByOrderIdxDescIdDesc(userId)
                : taskRepository.findTopByFolder_IdAndDeletedFalseOrderByIdDesc(folderId))
                .map(TaskEntity::getOrderIdx)
                .map(orderIdx -> orderIdx + 1)
                .orElse(0);
        Task task = sourceNoteId == null
                ? Task.create(userId, folderId, title, nextOrder, priority, urgent, matrixRank)
                : Task.createFromNote(userId, folderId, sourceNoteId, title, nextOrder, priority, urgent, matrixRank);
        User user = entityManager.getReference(User.class, userId);
        FolderEntity folder = folderId == null
                ? null
                : entityManager.getReference(FolderEntity.class, folderId);
        NoteEntity sourceNote = sourceNoteId == null
                ? null
                : entityManager.getReference(NoteEntity.class, sourceNoteId);
        return taskRepository.saveAndFlush(
                TaskEntity.from(task, user, folder, sourceNote)
        ).toDomain();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<Task> getByFolder(Long folderId) {
        return taskRepository.findAllByFolderIdWithFolder(folderId)
                .stream()
                .map(TaskEntity::toDomain)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<Task> getAll(Long userId, Sort sort) {
        return taskRepository.findAllByUser_IdAndDeletedFalse(userId, sort)
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

    /** 지정된 폴더만 대상으로 하는 컨텍스트. ARCHIVED 도 포함한다. */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<TaskOrganizerContextRow> getTaskOrganizerContext(Long userId, List<Long> folderIds) {
        if (folderIds.isEmpty()) {
            return List.of();
        }

        return taskRepository.findTaskOrganizerContextByFolderIds(userId, folderIds);
    }

    /** task 들이 속한 폴더 id. 폴더가 없는 task 는 빠진다. */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<Long> getFolderIds(Long userId, List<Long> taskIds) {
        if (taskIds.isEmpty()) {
            return List.of();
        }

        return taskRepository.findFolderIdsByTaskIds(userId, taskIds);
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<TaskSummaryResponse> getSummaries(Long folderId) {
        return getByFolder(folderId)
                .stream()
                .map(TaskSummaryResponse::from)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Task getOne(Long userId, Long taskId) {
        return getOwnedEntity(userId, taskId).toDomain();
    }

    /**
     * 제목·폴더·중요·즉시를 한 번에 바꾼다. 엔티티를 한 번만 읽고 변경 감지로 반영한다.
     * matrixRank는 중요·즉시가 바뀔 때만 필요하고 그 판단과 계산은 유스케이스가 한다.
     * null이면 순서를 건드리지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Task updateInfo(
            Long userId,
            Long taskId,
            String title,
            Long folderId,
            boolean priority,
            boolean urgent,
            Long matrixRank
    ) {
        TaskEntity entity = getOwnedEntity(userId, taskId);
        entity.updateTitle(title.trim());
        entity.updateFolder(folderId == null
                ? null
                : entityManager.getReference(FolderEntity.class, folderId));
        entity.updatePriority(priority);
        entity.updateUrgent(urgent);
        if (matrixRank != null) {
            entity.updateMatrixRank(matrixRank);
        }
        taskRepository.flush();
        return entity.toDomain();
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

package com.swimming.backend.task.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.note.repository.entity.NoteEntity;
import com.swimming.backend.project.domain.ProjectStatus;
import com.swimming.backend.project.repository.entity.ProjectEntity;
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

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRED)
    public Task create(Long userId, Long projectId, String title) {
        return create(userId, projectId, null, title);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Task createFromNote(
            Long userId,
            Long projectId,
            Long sourceNoteId,
            String title
    ) {
        return create(userId, projectId, sourceNoteId, title);
    }

    private Task create(
            Long userId,
            Long projectId,
            Long sourceNoteId,
            String title
    ) {
        int nextOrder = (projectId == null
                ? taskRepository.findTopByUser_IdAndProjectIsNullOrderByOrderIdxDescIdDesc(userId)
                : taskRepository.findTopByProject_IdOrderByIdDesc(projectId))
                .map(TaskEntity::getOrderIdx)
                .map(orderIdx -> orderIdx + 1)
                .orElse(0);
        Task task = sourceNoteId == null
                ? Task.create(userId, projectId, title, nextOrder)
                : Task.createFromNote(userId, projectId, sourceNoteId, title, nextOrder);
        User user = entityManager.getReference(User.class, userId);
        ProjectEntity project = projectId == null
                ? null
                : entityManager.getReference(ProjectEntity.class, projectId);
        NoteEntity sourceNote = sourceNoteId == null
                ? null
                : entityManager.getReference(NoteEntity.class, sourceNoteId);
        return taskRepository.saveAndFlush(
                TaskEntity.from(task, user, project, sourceNote)
        ).toDomain();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Long createAndGetId(Long userId, Long projectId, String title) {
        return create(userId, projectId, title).getId();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<Task> getByProject(Long projectId) {
        return taskRepository.findAllByProject_IdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(TaskEntity::toDomain)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<Task> getAll(Long userId) {
        return taskRepository.findAllByUser_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(TaskEntity::toDomain)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<Task> getUnclassified(Long userId) {
        return taskRepository.findAllByUser_IdAndProjectIsNullOrderByCreatedAtDesc(userId)
                .stream()
                .map(TaskEntity::toDomain)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<TaskReference> getReferences(Long userId, List<Long> taskIds) {
        return taskRepository.findAllOwnedByIds(userId, taskIds);
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<TaskOrganizerContextRow> getTaskOrganizerContext(Long userId) {
        return taskRepository.findTaskOrganizerContext(
                userId,
                ProjectStatus.ARCHIVED
        );
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<TaskSummaryResponse> getSummaries(Long projectId) {
        return getByProject(projectId)
                .stream()
                .map(TaskSummaryResponse::from)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Task getOne(Long userId, Long taskId) {
        return getOwnedEntity(userId, taskId).toDomain();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Task update(Long userId, Task task) {
        TaskEntity taskEntity = getOwnedEntity(userId, task.getId());
        taskEntity.apply(task);
        return taskRepository.saveAndFlush(taskEntity).toDomain();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteAll(Long userId, List<Long> taskIds) {
        List<TaskEntity> taskEntities = taskRepository.findAllOwnedEntitiesByIds(userId, taskIds);
        if (taskEntities.size() != taskIds.size()) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        taskRepository.deleteAllInBatch(taskEntities);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updateStatuses(Long userId, Map<Long, TaskStatus> statusByTaskId) {
        List<TaskEntity> taskEntities = taskRepository
                .findAllOwnedEntitiesByIds(userId, List.copyOf(statusByTaskId.keySet()));

        if (taskEntities.size() != statusByTaskId.size()) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }

        taskEntities.forEach(task -> task.changeStatus(statusByTaskId.get(task.getId())));
    }

    private TaskEntity getOwnedEntity(Long userId, Long taskId) {
        return taskRepository.findByIdAndUser_Id(taskId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
    }
}

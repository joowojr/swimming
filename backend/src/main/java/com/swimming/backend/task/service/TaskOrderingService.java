package com.swimming.backend.task.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.domain.TaskMatrixSection;
import com.swimming.backend.task.domain.TaskPlacement;
import com.swimming.backend.task.domain.TaskPlacementChange;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.dto.TaskPlacementResult;
import com.swimming.backend.task.repository.TaskRepository;
import com.swimming.backend.task.repository.entity.TaskEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskOrderingService {

    private final TaskRepository taskRepository;

    @Transactional(propagation = Propagation.REQUIRED)
    public Task updatePriority(Long userId, Long taskId, boolean priority) {
        TaskEntity entity = getOwnedEntity(userId, taskId);
        if (entity.isPriority() != priority) {
            long rank = nextRank(userId, priority, entity.isUrgent());
            entity.updatePriority(priority);
            entity.updateMatrixRank(rank);
        }
        taskRepository.flush();
        return entity.toDomain();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Task updateUrgent(Long userId, Long taskId, boolean urgent) {
        TaskEntity entity = getOwnedEntity(userId, taskId);
        if (entity.isUrgent() != urgent) {
            long rank = nextRank(userId, entity.isPriority(), urgent);
            entity.updateUrgent(urgent);
            entity.updateMatrixRank(rank);
        }
        taskRepository.flush();
        return entity.toDomain();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<Task> getMatrixPage(
            Long userId,
            TaskMatrixSection section,
            Long cursorRank,
            Long cursorTaskId,
            TaskStatus status,
            int limit
    ) {
        List<TaskEntity> entities = cursorRank == null
                ? taskRepository.findMatrixFirstPage(
                        userId,
                        section.isPriority(),
                        section.isUrgent(),
                        status,
                        PageRequest.of(0, limit)
                )
                : taskRepository.findMatrixNextPage(
                        userId,
                        section.isPriority(),
                        section.isUrgent(),
                        status,
                        cursorRank,
                        cursorTaskId,
                        PageRequest.of(0, limit)
                );
        return entities.stream().map(TaskEntity::toDomain).toList();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public TaskPlacement preparePlacement(
            Long userId,
            Long taskId,
            TaskMatrixSection targetSection,
            Long previousTaskId,
            Long nextTaskId
    ) {
        Task movingTask = getOwnedEntity(userId, taskId).toDomain();
        validateOwnedAnchor(userId, previousTaskId);
        validateOwnedAnchor(userId, nextTaskId);
        List<Task> targetTasks = taskRepository
                .findAllByUser_IdAndDeletedFalseAndPriorityAndUrgentOrderByMatrixRankDescIdDesc(
                        userId,
                        targetSection.isPriority(),
                        targetSection.isUrgent()
                )
                .stream()
                .map(TaskEntity::toDomain)
                .toList();
        return TaskPlacement.prepare(
                movingTask,
                targetSection,
                targetTasks,
                previousTaskId,
                nextTaskId
        );
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public TaskPlacementResult applyPlacement(Long userId, TaskPlacementChange change) {
        List<Task> changedTasks = change.changedTasks();
        Map<Long, TaskEntity> entitiesById = taskRepository
                .findAllByUser_IdAndDeletedFalseAndIdIn(
                        userId,
                        changedTasks.stream().map(Task::getId).toList()
                )
                .stream()
                .collect(Collectors.toMap(TaskEntity::getId, Function.identity()));
        if (entitiesById.size() != changedTasks.size()) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        changedTasks.forEach(task -> entitiesById.get(task.getId()).applyPlacement(task));
        taskRepository.flush();
        return new TaskPlacementResult(
                entitiesById.get(change.movedTask().getId()).toDomain(),
                change.sourceSection(),
                change.targetSection(),
                change.rebalanced()
        );
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public long nextRank(Long userId, boolean priority, boolean urgent) {
        return taskRepository
                .findTopByUser_IdAndDeletedFalseAndPriorityAndUrgentOrderByMatrixRankDescIdDesc(
                        userId,
                        priority,
                        urgent
                )
                .map(TaskEntity::getMatrixRank)
                .map(TaskPlacement::rankBefore)
                .orElseGet(TaskPlacement::firstRank);
    }

    private TaskEntity getOwnedEntity(Long userId, Long taskId) {
        return taskRepository.findByIdAndUser_IdAndDeletedFalse(taskId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
    }

    private void validateOwnedAnchor(Long userId, Long anchorId) {
        if (anchorId != null) {
            getOwnedEntity(userId, anchorId);
        }
    }
}

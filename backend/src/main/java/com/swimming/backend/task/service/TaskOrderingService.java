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

import java.util.ArrayList;
import java.util.EnumMap;
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
            Long nextTaskId,
            TaskStatus status
    ) {
        Task movingTask = getOwnedEntity(userId, taskId).toDomain();
        validateOwnedAnchor(userId, previousTaskId);
        validateOwnedAnchor(userId, nextTaskId);
        List<Task> targetTasks = taskRepository
                .findAllByUser_IdAndDeletedFalseAndPriorityAndUrgentAndStatusOrderByMatrixRankDescIdDesc(
                        userId,
                        targetSection.isPriority(),
                        targetSection.isUrgent(),
                        status
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

    /**
     * 여러 건을 한 번에 만들 때 쓸 순위를 넘겨받은 순서대로 매긴다.
     *
     * <p>영역마다 현재 순위를 한 번만 읽고 메모리에서 올린다. 건마다 읽으면 쿼리가 건수만큼
     * 늘고, 값이 "직전에 넣은 것이 이미 보이는가"라는 flush 시점에 의존하게 된다.
     * 새 할 일은 영역 맨 위로 가므로 마지막 건이 맨 위에 온다 — 하나씩 만들었을 때와 같다.
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<Long> nextRanks(Long userId, List<TaskMatrixSection> sections) {
        Map<TaskMatrixSection, Long> lastBySection = new EnumMap<>(TaskMatrixSection.class);
        List<Long> ranks = new ArrayList<>();
        for (TaskMatrixSection section : sections) {
            Long previous = lastBySection.get(section);
            long rank = previous == null
                    ? nextRank(userId, section.isPriority(), section.isUrgent())
                    : TaskPlacement.rankBefore(previous);
            lastBySection.put(section, rank);
            ranks.add(rank);
        }
        return ranks;
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

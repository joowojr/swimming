package com.swimming.backend.task.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.dto.projection.TaskReference;
import com.swimming.backend.task.dto.in.TaskSummaryResponse;
import com.swimming.backend.task.repository.TaskRepository;
import com.swimming.backend.task.repository.entity.TaskEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;

    @Transactional(propagation = Propagation.REQUIRED)
    public Task create(Long projectId, String title) {
        int nextOrder = taskRepository
                .findTopByProjectIdOrderByOrderIdxDescIdDesc(projectId)
                .map(TaskEntity::getOrderIdx)
                .map(orderIdx -> orderIdx + 1)
                .orElse(0);
        Task task = Task.create(projectId, title, nextOrder);
        return taskRepository.saveAndFlush(TaskEntity.from(task)).toDomain();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Long createAndGetId(Long projectId, String title) {
        return create(projectId, title).getId();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<Task> getAll(Long projectId) {
        return taskRepository.findAllByProjectIdOrderByOrderIdxAscIdAsc(projectId)
                .stream()
                .map(TaskEntity::toDomain)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<TaskReference> getReferences(Long userId, List<Long> taskIds) {
        return taskRepository.findAllOwnedByIds(userId, taskIds);
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<TaskSummaryResponse> getSummaries(Long projectId) {
        return getAll(projectId)
                .stream()
                .map(TaskSummaryResponse::from)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Task getOne(Long taskId) {
        return getOwnedEntity(taskId).toDomain();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<Task> getAllByIds(List<Long> taskIds) {
        return taskRepository.findAllById(taskIds)
                .stream()
                .map(TaskEntity::toDomain)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Task update(Task task) {
        TaskEntity taskEntity = getOwnedEntity(task.getId());
        taskEntity.apply(task);
        return taskRepository.saveAndFlush(taskEntity).toDomain();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteAll(List<Long> taskIds) {
        taskRepository.deleteAllByIdInBatch(taskIds);
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

    @Transactional(propagation = Propagation.REQUIRED)
    public void updateOrder(Long projectId, List<Long> taskIds) {
        List<TaskEntity> taskEntities = taskRepository
                .findAllByProjectIdOrderByOrderIdxAscIdAsc(projectId);

        if (taskEntities.size() != taskIds.size()
                || new HashSet<>(taskIds).size() != taskIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_TASK_ORDER);
        }

        Map<Long, TaskEntity> tasksById = new HashMap<>();
        for (TaskEntity taskEntity : taskEntities) {
            tasksById.put(taskEntity.getId(), taskEntity);
        }

        for (int orderIdx = 0; orderIdx < taskIds.size(); orderIdx++) {
            TaskEntity taskEntity = tasksById.get(taskIds.get(orderIdx));
            if (taskEntity == null) {
                throw new BusinessException(ErrorCode.INVALID_TASK_ORDER);
            }
            taskEntity.changeOrder(orderIdx);
        }
    }

    private TaskEntity getOwnedEntity(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
    }
}

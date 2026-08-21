package com.swimming.backend.task.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.dto.projection.TaskReference;
import com.swimming.backend.task.dto.web.TaskSummaryResponse;
import com.swimming.backend.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;

    public Task create(Long projectId, String title) {
        int nextOrder = taskRepository
                .findTopByProjectIdOrderByOrderIdxDescIdDesc(projectId)
                .map(task -> task.getOrderIdx() + 1)
                .orElse(0);
        Task task = Task.builder()
                .projectId(projectId)
                .title(title.trim())
                .orderIdx(nextOrder)
                .build();
        return taskRepository.save(task);
    }

    public Long createAndGetId(Long projectId, String title) {
        return create(projectId, title).getId();
    }

    public List<Task> getAll(Long projectId) {
        return taskRepository.findAllByProjectIdOrderByOrderIdxAscIdAsc(projectId);
    }

    public List<TaskReference> getAllByIds(Long userId, List<Long> taskIds) {
        return taskRepository.findAllOwnedByIds(userId, taskIds);
    }

    public List<TaskSummaryResponse> getSummaries(Long projectId) {
        return getAll(projectId)
                .stream()
                .map(TaskSummaryResponse::from)
                .toList();
    }

    public Task getOne(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
    }

    public List<Task> getAllEntitiesByIds(List<Long> taskIds) {
        return taskRepository.findAllById(taskIds);
    }

    public Task update(
            Task task,
            String title,
            TaskStatus status,
            int completionPct
    ) {
        task.update(title.trim(), status, completionPct);
        return task;
    }

    public void deleteAll(List<Task> tasks) {
        taskRepository.deleteAllInBatch(tasks);
    }

    public void updateOrder(Long projectId, List<Long> taskIds) {
        List<Task> tasks = taskRepository
                .findAllByProjectIdOrderByOrderIdxAscIdAsc(projectId);

        if (tasks.size() != taskIds.size()
                || new HashSet<>(taskIds).size() != taskIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_TASK_ORDER);
        }

        Map<Long, Task> tasksById = new HashMap<>();
        for (Task task : tasks) {
            tasksById.put(task.getId(), task);
        }

        for (int orderIdx = 0; orderIdx < taskIds.size(); orderIdx++) {
            Task task = tasksById.get(taskIds.get(orderIdx));
            if (task == null) {
                throw new BusinessException(ErrorCode.INVALID_TASK_ORDER);
            }
            task.changeOrder(orderIdx);
        }
    }
}

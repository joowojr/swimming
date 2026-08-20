package com.swimming.backend.task.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.project.dto.ProjectReference;
import com.swimming.backend.project.service.ProjectService;
import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.dto.CreateTaskRequest;
import com.swimming.backend.task.dto.ReorderTasksRequest;
import com.swimming.backend.task.dto.TaskResponse;
import com.swimming.backend.task.dto.UpdateTaskRequest;
import com.swimming.backend.task.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskUseCase {

    private final TaskService taskService;
    private final ProjectService projectService;

    @Transactional(propagation = Propagation.REQUIRED)
    public TaskResponse create(
            Long userId,
            Long projectId,
            CreateTaskRequest request
    ) {
        ProjectReference project = projectService.getReference(userId, projectId);
        return TaskResponse.from(taskService.create(project.id(), request.title()));
    }

    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public List<TaskResponse> getAll(Long userId, Long projectId) {
        ProjectReference project = projectService.getReference(userId, projectId);
        return taskService.getAll(project.id())
                .stream()
                .map(TaskResponse::from)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public TaskResponse update(
            Long userId,
            Long taskId,
            UpdateTaskRequest request
    ) {
        Task task = getOwnedTask(userId, taskId);
        return TaskResponse.from(taskService.update(
                task,
                request.title(),
                request.status(),
                request.completionPct()
        ));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void delete(Long userId, Long taskId) {
        taskService.delete(getOwnedTask(userId, taskId));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void reorder(
            Long userId,
            Long projectId,
            ReorderTasksRequest request
    ) {
        ProjectReference project = projectService.getReference(userId, projectId);
        taskService.updateOrder(project.id(), request.taskIds());
    }

    private Task getOwnedTask(Long userId, Long taskId) {
        Task task = taskService.getOne(taskId);
        try {
            projectService.getReference(userId, task.getProjectId());
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.PROJECT_NOT_FOUND) {
                throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
            }
            throw exception;
        }
        return task;
    }
}

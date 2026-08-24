package com.swimming.backend.task.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.project.dto.ProjectReference;
import com.swimming.backend.project.service.ProjectService;
import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.dto.web.CreateTaskRequest;
import com.swimming.backend.task.dto.web.DeleteTasksRequest;
import com.swimming.backend.task.dto.web.ReorderTasksRequest;
import com.swimming.backend.task.dto.web.TaskResponse;
import com.swimming.backend.task.dto.web.UpdateTaskRequest;
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

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
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
        Task task = taskService.getOne(taskId);
        verifyProjectOwnership(userId, task.getProjectId());
        task.update(request.title(), request.status(), request.completionPct());
        return TaskResponse.from(taskService.update(task));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteTasks(Long userId, DeleteTasksRequest request) {
        List<Long> taskIds = request.taskIds().stream().distinct().toList();
        List<Task> tasks = taskService.getAllByIds(taskIds);

        if (tasks.size() != taskIds.size()) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }

        tasks.stream()
                .map(Task::getProjectId)
                .distinct()
                .forEach(projectId -> verifyProjectOwnership(userId, projectId));
        taskService.deleteAll(taskIds);
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

    private void verifyProjectOwnership(Long userId, Long projectId) {
        try {
            projectService.getReference(userId, projectId);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.PROJECT_NOT_FOUND) {
                throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
            }
            throw exception;
        }
    }
}

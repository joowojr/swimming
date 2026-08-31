package com.swimming.backend.task.usecase;

import com.swimming.backend.project.dto.ProjectReference;
import com.swimming.backend.project.service.ProjectService;
import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.dto.in.CreateTaskRequest;
import com.swimming.backend.task.dto.in.DeleteTasksRequest;
import com.swimming.backend.task.dto.in.TaskResponse;
import com.swimming.backend.task.dto.in.TaskListMode;
import com.swimming.backend.task.dto.in.UpdateTaskStatusRequest;
import com.swimming.backend.task.dto.in.UpdateTaskTitleRequest;
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
        return TaskResponse.from(taskService.create(
                userId, project.id(), request.title(), request.priority(), request.urgent()));
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<TaskResponse> getByProject(Long userId, Long projectId) {
        ProjectReference project = projectService.getReference(userId, projectId);
        return taskService.getByProject(project.id())
                .stream()
                .map(TaskResponse::from)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<TaskResponse> getList(Long userId, TaskListMode mode) {
        List<Task> tasks = switch (mode) {
            case ALL -> taskService.getAll(userId);
            case UNCLASSIFIED -> taskService.getUnclassified(userId);
        };
        return tasks.stream()
                .map(TaskResponse::from)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public TaskResponse updateTitle(
            Long userId,
            Long taskId,
            UpdateTaskTitleRequest request
    ) {
        return TaskResponse.from(taskService.updateTitle(userId, taskId, request.title()));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public TaskResponse updateStatus(
            Long userId,
            Long taskId,
            UpdateTaskStatusRequest request
    ) {
        return TaskResponse.from(taskService.updateStatus(userId, taskId, request.status()));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteTasks(Long userId, DeleteTasksRequest request) {
        List<Long> taskIds = request.taskIds().stream().distinct().toList();
        taskService.deleteAll(userId, taskIds);
    }

}

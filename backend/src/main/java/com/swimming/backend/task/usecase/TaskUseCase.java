package com.swimming.backend.task.usecase;

import com.swimming.backend.folder.dto.FolderReference;
import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.dto.in.CreateTaskRequest;
import com.swimming.backend.task.dto.in.CreateTaskWithPlanRequest;
import com.swimming.backend.plan.domain.DailyPlanItem;
import com.swimming.backend.plan.service.DailyPlanService;
import com.swimming.backend.task.dto.in.DeleteTasksRequest;
import com.swimming.backend.task.dto.in.TaskResponse;
import com.swimming.backend.task.dto.in.TaskListMode;
import com.swimming.backend.task.dto.in.UpdateTaskStatusRequest;
import com.swimming.backend.task.dto.in.UpdateTaskTitleRequest;
import com.swimming.backend.task.dto.in.UpdateTaskPriorityRequest;
import com.swimming.backend.task.dto.in.UpdateTaskUrgentRequest;
import com.swimming.backend.task.service.TaskService;
import com.swimming.backend.task.service.TaskOrderingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskUseCase {

    private final TaskService taskService;
    private final TaskOrderingService taskOrderingService;
    private final FolderService folderService;
    private final DailyPlanService dailyPlanService;

    @Transactional(propagation = Propagation.REQUIRED)
    public TaskResponse createWithOptionalPlan(Long userId, CreateTaskWithPlanRequest request) {
        Long folderId = request.folderId() == null
                ? null
                : folderService.getReference(userId, request.folderId()).id();
        long matrixRank = taskOrderingService.nextRank(userId, request.priority(), request.urgent());
        Task task = taskService.create(
                userId, folderId, request.title().trim(), request.priority(), request.urgent(), matrixRank);
        if (request.planDate() != null) {
            int orderIdx = dailyPlanService.getItems(userId, request.planDate()).size();
            dailyPlanService.save(userId, request.planDate(), DailyPlanItem.restore(null, task.getId(), orderIdx, null, null));
        }
        return TaskResponse.from(task);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    @Deprecated(since = "2026-09-01", forRemoval = true)
    public TaskResponse create(
            Long userId,
            Long folderId,
            CreateTaskRequest request
    ) {
        FolderReference project = folderService.getReference(userId, folderId);
        long matrixRank = taskOrderingService.nextRank(userId, request.priority(), request.urgent());
        return TaskResponse.from(taskService.create(
                userId, project.id(), request.title(), request.priority(), request.urgent(), matrixRank));
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<TaskResponse> getByProject(Long userId, Long folderId) {
        FolderReference project = folderService.getReference(userId, folderId);
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
    public TaskResponse updatePriority(Long userId, Long taskId, UpdateTaskPriorityRequest request) {
        return TaskResponse.from(taskOrderingService.updatePriority(userId, taskId, request.priority()));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public TaskResponse updateUrgent(Long userId, Long taskId, UpdateTaskUrgentRequest request) {
        return TaskResponse.from(taskOrderingService.updateUrgent(userId, taskId, request.urgent()));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteTasks(Long userId, DeleteTasksRequest request) {
        List<Long> taskIds = request.taskIds().stream().distinct().toList();
        taskService.deleteAll(userId, taskIds);
    }

}

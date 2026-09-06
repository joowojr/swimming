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
import com.swimming.backend.task.dto.in.TaskSort;
import com.swimming.backend.plan.dto.DailyPlanItemResponse;
import com.swimming.backend.plan.dto.DailyPlanResponse;
import com.swimming.backend.task.dto.in.UpdateTaskInfoRequest;
import com.swimming.backend.task.dto.in.UpdateTaskInfoResponse;
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

import java.time.LocalDate;
import java.util.ArrayList;
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
        FolderReference folder = folderService.getReference(userId, folderId);
        long matrixRank = taskOrderingService.nextRank(userId, request.priority(), request.urgent());
        return TaskResponse.from(taskService.create(
                userId, folder.id(), request.title(), request.priority(), request.urgent(), matrixRank));
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<TaskResponse> getList(Long userId, TaskSort sort) {
        return taskService.getAll(userId, sort.toSort()).stream()
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

    /**
     * 수정 모달의 저장 하나를 처리한다. 폴더·중요·즉시는 Task의 속성이고 계획 날짜는 별도 테이블이지만
     * 사용자에게는 한 번의 저장이므로 한 트랜잭션에서 끝낸다.
     * TODO(task-owns-plan-date): date가 Task 테이블의 컬럼이 되면 plan 분기와 응답의 plans가 사라진다.
     *   docs/backlog/task-owns-plan-date.md
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public UpdateTaskInfoResponse updateInfo(Long userId, Long taskId, UpdateTaskInfoRequest request) {
        Long folderId = request.folderId() == null
                ? null
                : folderService.getReference(userId, request.folderId()).id();

        Task current = taskService.getOne(userId, taskId);
        Long matrixRank = current.isPriority() != request.priority() || current.isUrgent() != request.urgent()
                ? taskOrderingService.nextRank(userId, request.priority(), request.urgent())
                : null;
        Task task = taskService.updateInfo(
                userId, taskId, request.title(), folderId, request.priority(), request.urgent(), matrixRank);

        List<DailyPlanResponse> plans = new ArrayList<>();
        if (request.plan() != null) {
            LocalDate toDate = request.plan().date();
            if (request.plan().itemId() == null) {
                // 계획 항목 id를 모르는 화면에서 날짜를 고른 것이라 새로 담는다.
                dailyPlanService.addTaskIfAbsent(userId, toDate, taskId);
                plans.add(loadPlanResponse(userId, toDate));
            } else {
                LocalDate fromDate = dailyPlanService.moveItemDate(userId, request.plan().itemId(), taskId, toDate);
                plans.add(loadPlanResponse(userId, fromDate));
                if (!fromDate.equals(toDate)) {
                    plans.add(loadPlanResponse(userId, toDate));
                }
            }
        }
        return new UpdateTaskInfoResponse(TaskResponse.from(task), plans);
    }

    private DailyPlanResponse loadPlanResponse(Long userId, LocalDate date) {
        return new DailyPlanResponse(date, dailyPlanService.getRows(userId, date, date)
                .stream()
                .map(DailyPlanItemResponse::from)
                .toList());
    }

    /** @deprecated updateInfo의 priority를 쓴다. */
    @Deprecated
    @Transactional(propagation = Propagation.REQUIRED)
    public TaskResponse updatePriority(Long userId, Long taskId, UpdateTaskPriorityRequest request) {
        return TaskResponse.from(taskOrderingService.updatePriority(userId, taskId, request.priority()));
    }

    /** @deprecated updateInfo의 urgent를 쓴다. */
    @Deprecated
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

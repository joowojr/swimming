package com.swimming.backend.plan.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.plan.domain.DailyPlan;
import com.swimming.backend.plan.domain.DailyPlanItem;
import com.swimming.backend.plan.dto.CreateDailyPlanItemsRequest;
import com.swimming.backend.plan.dto.DailyPlanItemResponse;
import com.swimming.backend.plan.dto.DailyPlanItemType;
import com.swimming.backend.plan.dto.DailyPlanResponse;
import com.swimming.backend.plan.dto.ReorderDailyPlanItemsRequest;
import com.swimming.backend.plan.service.DailyPlanService;
import com.swimming.backend.project.service.ProjectService;
import com.swimming.backend.task.dto.projection.TaskReference;
import com.swimming.backend.task.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DailyPlanUseCase {

    private final DailyPlanService dailyPlanService;
    private final TaskService taskService;
    private final ProjectService projectService;

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<DailyPlanResponse> getRange(Long userId, LocalDate fromDate, LocalDate toDate) {
        validateDateRange(fromDate, toDate);
        List<DailyPlan> dailyPlans = dailyPlanService.getRange(userId, fromDate, toDate);
        Map<Long, TaskReference> tasksById = getOwnedTasksById(userId, taskIdsOf(dailyPlans));
        Map<LocalDate, DailyPlan> plansByDate = dailyPlans.stream()
                .collect(Collectors.toMap(DailyPlan::getPlanDate, Function.identity()));

        List<DailyPlanResponse> responses = new ArrayList<>();
        for (LocalDate date = fromDate; !date.isAfter(toDate); date = date.plusDays(1)) {
            DailyPlan dailyPlan = plansByDate.get(date);
            responses.add(dailyPlan == null
                    ? new DailyPlanResponse(date, List.of())
                    : toResponse(dailyPlan, tasksById));
        }
        return responses;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public DailyPlanResponse addItems(Long userId, LocalDate date, CreateDailyPlanItemsRequest request) {
        String title = request.title() == null ? null : request.title().trim();
        List<Long> taskIds = request.taskIds();
        boolean hasTasks = taskIds != null && !taskIds.isEmpty();
        boolean hasProject = request.projectId() != null;
        boolean hasTitle = title != null && !title.isEmpty();

        boolean linksExistingTasks = hasTasks && !hasProject && !hasTitle;
        boolean createsTask = taskIds == null && hasTitle;
        if (!linksExistingTasks && !createsTask) {
            throw new BusinessException(ErrorCode.INVALID_DAILY_PLAN_ITEM);
        }

        DailyPlan dailyPlan = dailyPlanService.get(userId, date)
                .orElseGet(() -> DailyPlan.create(userId, date));

        if (linksExistingTasks) {
            if (new HashSet<>(taskIds).size() != taskIds.size()
                    || taskIds.stream().anyMatch(dailyPlan::containsTask)) {
                throw new BusinessException(ErrorCode.INVALID_DAILY_PLAN_TASKS);
            }
            getOwnedTasksById(userId, taskIds);
            taskIds.forEach(taskId -> dailyPlan.addItem(DailyPlanItem.createTask(taskId)));
        } else {
            Long projectId = hasProject
                    ? projectService.getReference(userId, request.projectId()).id()
                    : null;
            Long taskId = taskService.createAndGetId(userId, projectId, title);
            dailyPlan.addItem(DailyPlanItem.createTask(taskId));
        }

        DailyPlan saved = dailyPlanService.save(dailyPlan);
        return toResponse(saved, getOwnedTasksById(userId, taskIdsOf(List.of(saved))));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public DailyPlanResponse reorder(Long userId, LocalDate date, ReorderDailyPlanItemsRequest request) {
        DailyPlan dailyPlan = getPlan(userId, date);
        List<Long> currentIds = dailyPlan.getItems().stream().map(DailyPlanItem::getId).toList();
        if (request.itemIds().size() != currentIds.size()
                || new HashSet<>(request.itemIds()).size() != request.itemIds().size()
                || !new HashSet<>(currentIds).equals(new HashSet<>(request.itemIds()))) {
            throw new BusinessException(ErrorCode.INVALID_DAILY_PLAN_ITEM_ORDER);
        }
        dailyPlan.reorder(request.itemIds());
        DailyPlan saved = dailyPlanService.save(dailyPlan);
        return toResponse(saved, getOwnedTasksById(userId, taskIdsOf(List.of(saved))));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteItem(Long userId, LocalDate date, Long itemId) {
        DailyPlan dailyPlan = getPlan(userId, date);
        findItem(dailyPlan, itemId);
        dailyPlan.removeItem(itemId);
        dailyPlanService.save(dailyPlan);
    }

    private DailyPlan getPlan(Long userId, LocalDate date) {
        return dailyPlanService.get(userId, date)
                .orElseThrow(() -> new BusinessException(ErrorCode.DAILY_PLAN_NOT_FOUND));
    }

    private DailyPlanItem findItem(DailyPlan dailyPlan, Long itemId) {
        return dailyPlan.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.DAILY_PLAN_ITEM_NOT_FOUND));
    }

    private DailyPlanResponse toResponse(DailyPlan dailyPlan, Map<Long, TaskReference> tasksById) {
        List<DailyPlanItemResponse> items = dailyPlan.getItems().stream()
                .map(item -> {
                    TaskReference task = tasksById.get(item.getTaskId());
                    return new DailyPlanItemResponse(
                            item.getId(), item.getTaskId(),
                            task.projectId() == null ? DailyPlanItemType.AD_HOC : DailyPlanItemType.TASK,
                            task.projectId(),
                            task.projectName(),
                            task.title(),
                            task.status(),
                            item.getOrderIdx()
                    );
                })
                .toList();
        return new DailyPlanResponse(dailyPlan.getPlanDate(), items);
    }

    private List<Long> taskIdsOf(List<DailyPlan> dailyPlans) {
        return dailyPlans.stream()
                .flatMap(plan -> plan.getItems().stream())
                .map(DailyPlanItem::getTaskId)
                .distinct()
                .toList();
    }

    private Map<Long, TaskReference> getOwnedTasksById(Long userId, List<Long> taskIds) {
        if (taskIds.isEmpty()) return Map.of();
        List<TaskReference> tasks = taskService.getReferences(userId, taskIds);
        if (tasks.size() != new HashSet<>(taskIds).size()) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        Map<Long, TaskReference> tasksById = new HashMap<>();
        for (TaskReference task : tasks) tasksById.put(task.id(), task);
        return tasksById;
    }

    private void validateDateRange(LocalDate fromDate, LocalDate toDate) {
        if (toDate.isBefore(fromDate) || ChronoUnit.DAYS.between(fromDate, toDate) >= 7) {
            throw new BusinessException(ErrorCode.INVALID_DAILY_PLAN_DATE_RANGE);
        }
    }
}

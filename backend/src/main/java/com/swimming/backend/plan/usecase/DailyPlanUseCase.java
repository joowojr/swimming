package com.swimming.backend.plan.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.plan.domain.DailyPlan;
import com.swimming.backend.plan.dto.DailyPlanItemResponse;
import com.swimming.backend.plan.dto.DailyPlanResponse;
import com.swimming.backend.plan.dto.UpdateDailyPlanRequest;
import com.swimming.backend.plan.service.DailyPlanService;
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

    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public List<DailyPlanResponse> getRange(
            Long userId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        validateDateRange(fromDate, toDate);

        List<DailyPlan> dailyPlans = dailyPlanService.getRange(
                userId,
                fromDate,
                toDate
        );
        List<Long> taskIds = dailyPlans.stream()
                .flatMap(dailyPlan -> dailyPlan.getItems().stream())
                .map(item -> item.getTaskId())
                .distinct()
                .toList();
        Map<Long, TaskReference> tasksById = getOwnedTasksById(userId, taskIds);
        Map<LocalDate, DailyPlan> dailyPlansByDate = dailyPlans.stream()
                .collect(Collectors.toMap(
                        DailyPlan::getPlanDate,
                        Function.identity()
                ));

        List<DailyPlanResponse> responses = new ArrayList<>();
        for (LocalDate date = fromDate; !date.isAfter(toDate); date = date.plusDays(1)) {
            DailyPlan dailyPlan = dailyPlansByDate.get(date);
            responses.add(dailyPlan == null
                    ? new DailyPlanResponse(date, List.of())
                    : toResponse(dailyPlan, tasksById));
        }
        return responses;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void update(Long userId, UpdateDailyPlanRequest request) {
        validateNoDuplicateTasks(request.taskIds());
        validateOwnedTasks(userId, request.taskIds());

        dailyPlanService.get(userId, request.date())
                .ifPresentOrElse(
                        dailyPlan -> dailyPlanService.update(dailyPlan, request.taskIds()),
                        () -> dailyPlanService.create(userId, request.date(), request.taskIds())
                );
    }

    private DailyPlanResponse toResponse(
            DailyPlan dailyPlan,
            Map<Long, TaskReference> tasksById
    ) {
        List<DailyPlanItemResponse> items = dailyPlan.getItems()
                .stream()
                .map(item -> {
                    TaskReference task = tasksById.get(item.getTaskId());
                    return new DailyPlanItemResponse(
                            task.id(),
                            task.projectId(),
                            task.projectName(),
                            task.title(),
                            task.status(),
                            task.completionPct(),
                            item.getOrderIdx()
                    );
                })
                .toList();
        return new DailyPlanResponse(dailyPlan.getPlanDate(), items);
    }

    private void validateDateRange(LocalDate fromDate, LocalDate toDate) {
        if (toDate.isBefore(fromDate)
                || ChronoUnit.DAYS.between(fromDate, toDate) >= 7) {
            throw new BusinessException(ErrorCode.INVALID_DAILY_PLAN_DATE_RANGE);
        }
    }

    private void validateNoDuplicateTasks(List<Long> taskIds) {
        if (new HashSet<>(taskIds).size() != taskIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_DAILY_PLAN_TASKS);
        }
    }

    private void validateOwnedTasks(Long userId, List<Long> taskIds) {
        getOwnedTasksById(userId, taskIds);
    }

    private Map<Long, TaskReference> getOwnedTasksById(Long userId, List<Long> taskIds) {
        if (taskIds.isEmpty()) {
            return Map.of();
        }

        List<TaskReference> tasks = taskService.getAllByIds(userId, taskIds);
        if (tasks.size() != new HashSet<>(taskIds).size()) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }

        Map<Long, TaskReference> tasksById = new HashMap<>();
        for (TaskReference task : tasks) {
            tasksById.put(task.id(), task);
        }
        return tasksById;
    }
}

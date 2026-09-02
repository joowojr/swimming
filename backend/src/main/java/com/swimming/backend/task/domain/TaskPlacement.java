package com.swimming.backend.task.domain;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class TaskPlacement {

    private static final long ORDER_RANK_GAP = 1024L;

    private final Task movingTask;
    private final TaskMatrixSection sourceSection;
    private final TaskMatrixSection targetSection;
    private final List<Task> targetTasks;
    private final Task previousTask;
    private final Task nextTask;

    private TaskPlacement(
            Task movingTask,
            TaskMatrixSection targetSection,
            List<Task> targetTasks,
            Long previousTaskId,
            Long nextTaskId
    ) {
        validateAnchorIds(movingTask.getId(), previousTaskId, nextTaskId);
        this.movingTask = movingTask;
        this.sourceSection = TaskMatrixSection.from(movingTask.isPriority(), movingTask.isUrgent());
        this.targetSection = targetSection;
        this.targetTasks = targetTasks.stream()
                .filter(task -> !Objects.equals(task.getId(), movingTask.getId()))
                .sorted(Comparator.comparingLong(Task::getMatrixRank)
                        .reversed()
                        .thenComparing(Task::getId, Comparator.reverseOrder()))
                .toList();
        validateTargetSections();
        this.previousTask = findAnchor(previousTaskId);
        this.nextTask = findAnchor(nextTaskId);
        validateAdjacent();
    }

    public static TaskPlacement prepare(
            Task movingTask,
            TaskMatrixSection targetSection,
            List<Task> targetTasks,
            Long previousTaskId,
            Long nextTaskId
    ) {
        return new TaskPlacement(
                movingTask,
                targetSection,
                targetTasks,
                previousTaskId,
                nextTaskId
        );
    }

    public static long firstRank() {
        return ORDER_RANK_GAP;
    }

    public static long rankBefore(long currentFirstRank) {
        return Math.addExact(currentFirstRank, ORDER_RANK_GAP);
    }

    public TaskPlacementChange move() {
        Long rank = calculateRank();
        boolean rebalanced = rank == null;
        if (rebalanced) {
            rebalance();
            rank = calculateRank();
        }
        if (rank == null) {
            throw new BusinessException(ErrorCode.TASK_PLACEMENT_CONFLICT);
        }
        movingTask.moveTo(targetSection, rank);
        return new TaskPlacementChange(
                movingTask,
                sourceSection,
                targetSection,
                rebalanced ? targetTasks : List.of()
        );
    }

    private Task findAnchor(Long taskId) {
        if (taskId == null) {
            return null;
        }
        return targetTasks.stream()
                .filter(task -> Objects.equals(task.getId(), taskId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_PLACEMENT_CONFLICT));
    }

    private void validateTargetSections() {
        boolean invalidSection = targetTasks.stream().anyMatch(task ->
                task.isPriority() != targetSection.isPriority()
                        || task.isUrgent() != targetSection.isUrgent());
        if (invalidSection) {
            throw new BusinessException(ErrorCode.TASK_PLACEMENT_CONFLICT);
        }
    }

    private void validateAnchorIds(Long movingTaskId, Long previousTaskId, Long nextTaskId) {
        if (Objects.equals(movingTaskId, previousTaskId)
                || Objects.equals(movingTaskId, nextTaskId)
                || (previousTaskId != null && Objects.equals(previousTaskId, nextTaskId))) {
            throw new BusinessException(ErrorCode.INVALID_TASK_PLACEMENT);
        }
    }

    private void validateAdjacent() {
        if (previousTask == null && nextTask == null) {
            if (!targetTasks.isEmpty()) {
                throw new BusinessException(ErrorCode.TASK_PLACEMENT_CONFLICT);
            }
            return;
        }
        if (previousTask == null) {
            if (!Objects.equals(targetTasks.getFirst().getId(), nextTask.getId())) {
                throw new BusinessException(ErrorCode.TASK_PLACEMENT_CONFLICT);
            }
            return;
        }
        if (nextTask == null) {
            if (!Objects.equals(targetTasks.getLast().getId(), previousTask.getId())) {
                throw new BusinessException(ErrorCode.TASK_PLACEMENT_CONFLICT);
            }
            return;
        }
        int previousIndex = targetTasks.indexOf(previousTask);
        int nextIndex = targetTasks.indexOf(nextTask);
        if (nextIndex != previousIndex + 1) {
            throw new BusinessException(ErrorCode.TASK_PLACEMENT_CONFLICT);
        }
    }

    private Long calculateRank() {
        try {
            if (previousTask == null && nextTask == null) {
                return ORDER_RANK_GAP;
            }
            if (previousTask == null) {
                return Math.addExact(nextTask.getMatrixRank(), ORDER_RANK_GAP);
            }
            if (nextTask == null) {
                return Math.subtractExact(previousTask.getMatrixRank(), ORDER_RANK_GAP);
            }
            long difference = Math.subtractExact(
                    previousTask.getMatrixRank(),
                    nextTask.getMatrixRank()
            );
            if (difference <= 1L) {
                return null;
            }
            return Math.addExact(nextTask.getMatrixRank(), difference / 2L);
        } catch (ArithmeticException exception) {
            return null;
        }
    }

    private void rebalance() {
        try {
            long rank = Math.multiplyExact((long) targetTasks.size(), ORDER_RANK_GAP);
            for (Task task : targetTasks) {
                task.changeMatrixRank(rank);
                rank = Math.subtractExact(rank, ORDER_RANK_GAP);
            }
        } catch (ArithmeticException exception) {
            throw new BusinessException(ErrorCode.TASK_PLACEMENT_CONFLICT, exception);
        }
    }
}

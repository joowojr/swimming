package com.swimming.backend.task.domain;

import java.util.List;
import java.util.stream.Stream;

public record TaskPlacementChange(
        Task movedTask,
        TaskMatrixSection sourceSection,
        TaskMatrixSection targetSection,
        List<Task> rebalancedTasks
) {
    public TaskPlacementChange {
        rebalancedTasks = List.copyOf(rebalancedTasks);
    }

    public boolean rebalanced() {
        return !rebalancedTasks.isEmpty();
    }

    public List<Task> changedTasks() {
        if (rebalancedTasks.isEmpty()) {
            return List.of(movedTask);
        }
        return Stream.concat(
                        rebalancedTasks.stream(),
                        Stream.of(movedTask)
                )
                .distinct()
                .toList();
    }
}

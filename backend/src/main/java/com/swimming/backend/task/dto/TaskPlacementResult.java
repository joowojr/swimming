package com.swimming.backend.task.dto;

import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.domain.TaskMatrixSection;

public record TaskPlacementResult(
        Task task,
        TaskMatrixSection sourceSection,
        TaskMatrixSection targetSection,
        boolean rebalanced
) {
}

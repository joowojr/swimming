package com.swimming.backend.task.dto.out;

import com.swimming.backend.task.domain.TaskMatrixSection;
import com.swimming.backend.task.domain.TaskOrderingScope;

import java.util.List;

public record TaskPlacementResponse(
        TaskOrderingScope scope,
        TaskMatrixItemResponse task,
        TaskMatrixSection sourceSection,
        TaskMatrixSection targetSection,
        List<TaskMatrixSection> rebalancedSections
) {
}

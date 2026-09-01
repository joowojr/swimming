package com.swimming.backend.task.dto.out;

import com.swimming.backend.task.domain.TaskMatrixSection;

import java.util.List;

public record TaskMatrixPageResponse(
        TaskMatrixSection section,
        List<TaskMatrixItemResponse> items,
        String nextCursor,
        boolean hasNext
) {
}

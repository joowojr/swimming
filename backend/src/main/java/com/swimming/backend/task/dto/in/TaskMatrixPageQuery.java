package com.swimming.backend.task.dto.in;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.task.domain.TaskMatrixSection;

public record TaskMatrixPageQuery(
        TaskMatrixSection section,
        int size,
        String cursor
) {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;

    public static TaskMatrixPageQuery from(String section, int size, String cursor) {
        if (size < 1 || size > MAX_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_PAGE_SIZE);
        }
        if (cursor != null && cursor.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_MATRIX_CURSOR);
        }
        return new TaskMatrixPageQuery(TaskMatrixSection.fromQuery(section), size, cursor);
    }
}

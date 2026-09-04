package com.swimming.backend.task.dto.in;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.task.domain.TaskMatrixSection;
import com.swimming.backend.task.domain.TaskStatus;

/** status가 null이면 상태로 좁히지 않는다. */
public record TaskMatrixPageQuery(
        TaskMatrixSection section,
        int size,
        String cursor,
        TaskStatus status
) {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;

    public static TaskMatrixPageQuery from(String section, int size, String cursor, String status) {
        if (size < 1 || size > MAX_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_PAGE_SIZE);
        }
        if (cursor != null && cursor.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_MATRIX_CURSOR);
        }
        return new TaskMatrixPageQuery(
                TaskMatrixSection.fromQuery(section), size, cursor, parseStatus(status));
    }

    private static TaskStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return TaskStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_TASK_STATUS);
        }
    }
}

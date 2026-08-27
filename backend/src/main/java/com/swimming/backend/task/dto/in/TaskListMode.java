package com.swimming.backend.task.dto.in;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;

import java.util.Locale;

public enum TaskListMode {
    ALL,
    UNCLASSIFIED;

    public static TaskListMode fromQuery(String value) {
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_TASK_LIST_MODE);
        }

        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_TASK_LIST_MODE);
        }
    }
}

package com.swimming.backend.task.domain;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;

import java.util.Locale;

public enum TaskOrderingScope {
    MATRIX;

    public static TaskOrderingScope fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_TASK_PLACEMENT);
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_TASK_PLACEMENT);
        }
    }
}

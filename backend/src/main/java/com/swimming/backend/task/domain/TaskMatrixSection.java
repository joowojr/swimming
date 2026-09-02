package com.swimming.backend.task.domain;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;

import java.util.Locale;

public enum TaskMatrixSection {
    PRIORITY_URGENT(true, true),
    URGENT(false, true),
    PRIORITY(true, false),
    STANDARD(false, false);

    private final boolean priority;
    private final boolean urgent;

    TaskMatrixSection(boolean priority, boolean urgent) {
        this.priority = priority;
        this.urgent = urgent;
    }

    public boolean isPriority() {
        return priority;
    }

    public boolean isUrgent() {
        return urgent;
    }

    public static TaskMatrixSection from(boolean priority, boolean urgent) {
        for (TaskMatrixSection section : values()) {
            if (section.priority == priority && section.urgent == urgent) {
                return section;
            }
        }
        throw new IllegalStateException("Task Matrix 영역을 결정할 수 없습니다");
    }

    public static TaskMatrixSection fromQuery(String value) {
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_MATRIX_SECTION);
        }

        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_MATRIX_SECTION, exception);
        }
    }
}

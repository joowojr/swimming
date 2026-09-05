package com.swimming.backend.task.dto.in;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import org.springframework.data.domain.Sort;

/** 할 일 목록 정렬. 기준은 생성 시각이고 기본은 최신순이다. */
public enum TaskSort {
    DESC,
    ASC;

    public static TaskSort fromQuery(String value) {
        if (value == null || value.isBlank()) {
            return DESC;
        }
        try {
            return TaskSort.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_TASK_SORT);
        }
    }

    public Sort toSort() {
        return this == ASC
                ? Sort.by(Sort.Direction.ASC, "createdAt")
                : Sort.by(Sort.Direction.DESC, "createdAt");
    }
}

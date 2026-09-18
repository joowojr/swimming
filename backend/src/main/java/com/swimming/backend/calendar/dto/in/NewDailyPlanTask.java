package com.swimming.backend.calendar.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 캘린더에 새로 만들어 담을 할 일 하나. folderId가 없으면 미분류다. */
public record NewDailyPlanTask(
        @NotBlank(message = "할 일을 입력해 주세요")
        @Size(max = 255, message = "할 일은 255자 이내로 입력해 주세요")
        String title,
        Long folderId,
        Boolean priority,
        Boolean urgent
) {
    public NewDailyPlanTask {
        priority = Boolean.TRUE.equals(priority);
        urgent = Boolean.TRUE.equals(urgent);
    }

    public NewDailyPlanTask(String title, Long folderId) {
        this(title, folderId, Boolean.FALSE, Boolean.FALSE);
    }
}

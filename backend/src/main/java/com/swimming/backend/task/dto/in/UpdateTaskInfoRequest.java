package com.swimming.backend.task.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 할 일의 정보를 한 번에 바꾼다. 사용자에게는 수정 모달 하나의 저장이라 한 트랜잭션으로 처리한다.
 * folderId와 planDate는 null이 "미분류"·"캘린더에 없음"을 뜻해 생략과 구분되지 않으므로 항상 보낸다.
 */
public record UpdateTaskInfoRequest(
        @NotBlank(message = "Task 제목을 입력해 주세요")
        @Size(max = 255, message = "Task 제목은 255자 이하여야 합니다")
        String title,

        Long folderId,

        @NotNull(message = "중요 여부를 입력해 주세요")
        Boolean priority,

        @NotNull(message = "즉시 여부를 입력해 주세요")
        Boolean urgent,

        LocalDate planDate
) {
}

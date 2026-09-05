package com.swimming.backend.task.dto.in;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 할 일의 분류 정보를 한 번에 바꾼다. 사용자에게는 수정 모달 하나의 저장이라 한 트랜잭션으로 처리한다.
 * folderId는 null이 "미분류"를 뜻하므로 세 값 모두 항상 보낸다. 계획 항목이 없는 화면에서는 plan이 없다.
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

        @Valid
        PlanMove plan
) {

    /** itemId가 있으면 그 계획 항목을 옮기고, 없으면 그 날짜의 계획에 새로 담는다. */
    public record PlanMove(
            Long itemId,

            @NotNull(message = "계획 날짜를 입력해 주세요")
            LocalDate date
    ) {
    }
}

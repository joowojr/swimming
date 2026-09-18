package com.swimming.backend.task.dto.in;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 모달 하나에서 담은 할 일을 한 번에 만든다.
 *
 * <p>사용자에게는 "모두 추가" 한 번이라 한 트랜잭션에서 끝낸다. 하나라도 실패하면
 * 아무것도 만들어지지 않으므로, 일부만 생긴 중간 상태를 화면이 다룰 일이 없다.
 * 항목의 검증 규칙은 단건 생성과 같아 {@link CreateTaskWithPlanRequest}를 그대로 쓴다.
 */
public record CreateTasksBatchRequest(
        @NotEmpty(message = "추가할 Task를 하나 이상 담아 주세요")
        @Size(max = MAX_SIZE, message = "한 번에 추가할 수 있는 Task는 " + MAX_SIZE + "개까지입니다")
        List<@Valid CreateTaskWithPlanRequest> tasks
) {
    /** 한 요청이 트랜잭션을 오래 잡지 않게 막는 상한. 모달에서 담을 현실적인 양을 크게 웃돈다. */
    public static final int MAX_SIZE = 50;
}

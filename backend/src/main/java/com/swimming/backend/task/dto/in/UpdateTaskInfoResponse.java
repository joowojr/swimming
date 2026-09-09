package com.swimming.backend.task.dto.in;

import com.swimming.backend.task.dto.in.DailyPlanResponse;

import java.util.List;

/**
 * plans에는 이동으로 바뀐 날짜들의 계획이 담긴다. 계획을 옮기지 않았으면 비어 있고,
 * 옮겼으면 원본과 대상 두 날짜가 모두 들어간다.
 */
public record UpdateTaskInfoResponse(
        TaskResponse task,
        List<DailyPlanResponse> plans
) {
}

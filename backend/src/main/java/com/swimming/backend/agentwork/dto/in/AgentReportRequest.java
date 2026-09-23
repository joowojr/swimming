package com.swimming.backend.agentwork.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** 진행·대기·완료·실패 보고가 함께 쓴다. summary는 카드에 보이는 한 줄이고 details는 보관만 한다. */
public record AgentReportRequest(
        @NotBlank @Size(max = 200) String summary,
        Map<String, Object> details
) {
}

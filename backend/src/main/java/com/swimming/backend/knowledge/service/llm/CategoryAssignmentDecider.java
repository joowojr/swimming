package com.swimming.backend.knowledge.service.llm;

import com.swimming.backend.knowledge.dto.out.CategoryAssignmentInput;
import com.swimming.backend.knowledge.dto.out.CategoryAssignmentDecision;

/** 구현체 하나만 주입한다. A/C 전환 시 소화·저장 경로는 유지한다. */
public interface CategoryAssignmentDecider {
    CategoryAssignmentDecision decide(CategoryAssignmentInput input);
}

package com.swimming.backend.note.service;

import com.swimming.backend.note.dto.out.TaskOrganizerInput;

import java.util.List;

/**
 * 폴더가 이미 정해진 상태의 추출 시나리오.
 *
 * <p>결정 B(관련성 유지)를 따른다. 폴더가 주어져도 모델은 주제 적합성을 판단하고,
 * 폴더 밖의 행동은 {@code unclassified} 로 간다. 이것이 정상 동작이다.
 *
 * @param mustBeTask         이 폴더의 행동. {@code tasks} 여야 한다
 * @param mustBeUnclassified 행동이 아니거나 이 폴더 밖의 행동. {@code unclassified} 여야 한다
 */
record TaskExtractTestScenario(
        String id,
        String name,
        String evaluationCriteria,
        TaskOrganizerInput input,
        List<String> mustBeTask,
        List<String> mustBeUnclassified
) {
}

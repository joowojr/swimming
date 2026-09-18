package com.swimming.backend.knowledge.benchmark;

import java.util.List;

import com.swimming.backend.knowledge.benchmark.NodeResolutionEvalScenario.Domain;
import com.swimming.backend.knowledge.benchmark.NodeResolutionEvalScenario.Priority;

record CategoryAssignmentEvalScenario(
        String id,
        Domain domain,
        String folderName,
        List<CategoryFixture> existingCategories,
        SourceFixture source,
        Expected expected,
        Priority priority,
        String rationale
) {
    record CategoryFixture(String id, String title) {}

    record SourceFixture(
            String id,
            String title,
            String summary,
            String topic,
            List<String> subjects,
            String proposedCategoryTitle // B/C가 공유하는 고정 제안 이름. 생성 출처는 snapshot에 기록한다.
    ) {}

    enum Action { REUSE, CREATE }

    // CREATE의 categoryId는 null이다. 새 이름의 정확한 문자열 일치는 요구하지 않는다.
    record Expected(Action action, String categoryId) {}
}

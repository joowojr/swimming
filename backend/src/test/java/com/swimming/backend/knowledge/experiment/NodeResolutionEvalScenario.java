package com.swimming.backend.knowledge.experiment;

import java.util.List;

record NodeResolutionEvalScenario(
        String id,
        String name,
        Domain domain,
        String currentSourceSummary,
        List<String> extractedSubjects,
        List<SimilarSourceFixture> sourceCorpus,
        List<ExpectedResolution> expected
) {
    enum Domain {
        TECHNOLOGY,
        JOB_POSTING,
        KYOTO_TRAVEL,
        AI_PRODUCTIVITY
    }

    enum Action {
        REUSE,
        CREATE
    }

    record SimilarSourceFixture(String id, String summary, List<String> subjects) {
    }

    record ExpectedResolution(
            String candidate,
            Action action,
            String canonicalSubject,
            Priority priority
    ) {
    }

    enum Priority {
        STANDARD,
        REGRESSION_CRITICAL
    }
}

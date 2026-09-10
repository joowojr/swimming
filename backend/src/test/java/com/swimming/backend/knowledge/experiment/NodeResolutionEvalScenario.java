package com.swimming.backend.knowledge.experiment;

import com.swimming.backend.knowledge.dto.out.NodeResolutionResult;

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

    record SimilarSourceFixture(String id, String summary, List<String> subjects) {
    }

    record ExpectedResolution(
            String candidate,
            NodeResolutionResult.Action action,
            String canonicalSubject,
            Priority priority
    ) {
    }

    enum Priority {
        STANDARD,
        REGRESSION_CRITICAL
    }
}

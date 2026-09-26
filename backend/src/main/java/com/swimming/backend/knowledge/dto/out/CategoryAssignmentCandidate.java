package com.swimming.backend.knowledge.dto.out;

import java.util.List;
import java.util.UUID;

/** JEV가 비교할 기존 Category와 그 안에 포함된 Source의 Topic 예시. */
public record CategoryAssignmentCandidate(UUID nodeId, String title, List<String> topics) {

    public CategoryAssignmentCandidate {
        topics = List.copyOf(topics);
    }
}

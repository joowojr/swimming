package com.swimming.backend.knowledge.experiment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NodeResolutionCandidateUnionTest {

    @Test
    @DisplayName("Hybrid 후보는 Subject 임베딩 순서를 유지하며 Source 후보와 합집합으로 결합한다")
    void combinesCandidatesFromBothRetrievalPaths() {
        List<String> candidates = NodeResolutionPipelineComparisonTest.unionCandidates(
                List.of("OpenID Connect", "JSON Web Token", "Access Token"),
                List.of("JSON Web Token", "OAuth Refresh Token", "OpenID Connect")
        );

        assertThat(candidates).containsExactly(
                "JSON Web Token", "OAuth Refresh Token", "OpenID Connect", "Access Token");
    }
}

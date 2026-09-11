package com.swimming.backend.knowledge.experiment;

import com.swimming.backend.knowledge.dto.out.NodeResolutionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class NodeResolutionEvalTestDataTest {

    @Test
    @DisplayName("Golden dataset은 도메인별 시나리오와 모든 후보의 정답을 빠짐없이 가진다")
    void validatesGoldenDatasetContract() {
        List<NodeResolutionEvalScenario> scenarios = NodeResolutionEvalTestData.scenarios();

        assertThat(NodeResolutionEvalTestData.VERSION).isNotBlank();
        assertThat(scenarios).hasSize(12);
        assertThat(scenarios).extracting(NodeResolutionEvalScenario::id).doesNotHaveDuplicates();
        for (NodeResolutionEvalScenario.Domain domain : NodeResolutionEvalScenario.Domain.values()) {
            assertThat(scenarios).filteredOn(scenario -> scenario.domain() == domain).hasSize(3);
        }
        assertThat(scenarios.stream()
                .flatMap(scenario -> scenario.expected().stream())
                .filter(expected -> expected.priority()
                        == NodeResolutionEvalScenario.Priority.REGRESSION_CRITICAL)
                .map(NodeResolutionEvalScenario.ExpectedResolution::candidate))
                .containsExactlyInAnyOrder("스톡옵션", "ICOCA", "간사이 광역 교통권");
        assertThat(scenarios.stream()
                .flatMap(scenario -> scenario.expected().stream())
                .map(NodeResolutionEvalScenario.ExpectedResolution::candidate))
                .doesNotContain("회의 요약");

        assertThat(scenarios).allSatisfy(scenario -> {
            assertThat(scenario.extractedSubjects())
                    .containsExactlyElementsOf(scenario.expected().stream()
                            .map(NodeResolutionEvalScenario.ExpectedResolution::candidate)
                            .toList());
            assertThat(scenario.sourceCorpus()).hasSize(12);
            assertThat(scenario.sourceCorpus())
                    .extracting(NodeResolutionEvalScenario.SimilarSourceFixture::id)
                    .doesNotHaveDuplicates();

            List<String> corpusSubjects = scenario.sourceCorpus().stream()
                    .flatMap(source -> source.subjects().stream())
                    .toList();
            Stream<String> reuseSubjects = scenario.expected().stream()
                    .filter(expected -> expected.action() == NodeResolutionResult.Action.REUSE)
                    .map(NodeResolutionEvalScenario.ExpectedResolution::canonicalSubject);
            assertThat(reuseSubjects).allMatch(corpusSubjects::contains);
        });
    }
}

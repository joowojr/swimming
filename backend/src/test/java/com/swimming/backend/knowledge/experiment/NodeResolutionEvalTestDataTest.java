package com.swimming.backend.knowledge.experiment;

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
                    .filter(expected -> expected.action() == NodeResolutionEvalScenario.Action.REUSE)
                    .map(NodeResolutionEvalScenario.ExpectedResolution::canonicalSubject);
            assertThat(reuseSubjects).allMatch(corpusSubjects::contains);
        });
    }

    @Test
    @DisplayName("판정 전용 평가의 고정 재사용 후보는 정답을 모두 담고 신규 판정의 답은 담지 않는다")
    void 판정_전용_고정_후보의_전제를_지킨다() {
        for (NodeResolutionEvalScenario scenario : NodeResolutionEvalTestData.scenarios()) {
            List<String> reusable = NodeResolutionEvalTestData.reusableSubjects(scenario);

            assertThat(reusable).as("%s 고정 후보", scenario.id()).doesNotHaveDuplicates();

            // 재사용이 정답이면 대상이 컨텍스트에 있어야 한다. 없으면 검색 실패와 구분되지 않는다.
            List<String> reuseTargets = scenario.expected().stream()
                    .filter(expected -> expected.action() == NodeResolutionEvalScenario.Action.REUSE)
                    .map(NodeResolutionEvalScenario.ExpectedResolution::canonicalSubject)
                    .toList();
            assertThat(reusable).as("%s 재사용 정답", scenario.id()).containsAll(reuseTargets);

            // 신규가 정답이면 같은 이름이 컨텍스트에 없어야 한다. 있으면 재사용이 맞는 답이 된다.
            List<String> createTargets = scenario.expected().stream()
                    .filter(expected -> expected.action() == NodeResolutionEvalScenario.Action.CREATE)
                    .map(NodeResolutionEvalScenario.ExpectedResolution::canonicalSubject)
                    .toList();
            if (!createTargets.isEmpty()) {
                assertThat(reusable).as("%s 신규 정답", scenario.id())
                        .doesNotContainAnyElementsOf(createTargets);
            }
        }
    }
}

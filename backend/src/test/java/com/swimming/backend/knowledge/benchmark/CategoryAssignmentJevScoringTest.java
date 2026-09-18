package com.swimming.backend.knowledge.benchmark;

import com.swimming.backend.common.client.dto.SystemOneResponse;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CategoryAssignmentJevScoringTest {
    @Test
    void 제안_이름과_summary를_전달하고_후보_ID는_일_기반_인덱스다() {
        var scenario = CategoryAssignmentEvalTestData.scenarios().getFirst();
        var request = CategoryAssignmentJevEvalTest.request(scenario);
        assertThat(request.state()).isEqualTo(Map.of("proposedCategoryTitle", scenario.source().proposedCategoryTitle(),
                "summary", scenario.source().summary()));
        var criteria = ((com.swimming.backend.common.client.dto.SystemOneRequest.Choice)
                request.questions().get("assignment")).criteria();
        assertThat(criteria).hasSize(scenario.existingCategories().size() + 1);
        for (int i = 0; i < scenario.existingCategories().size(); i++) {
            assertThat(criteria.get(Integer.toString(i + 1))).isEqualTo(scenario.existingCategories().get(i).title());
        }
        assertThat(criteria.keySet()).contains("NEW").doesNotContainAnyElementsOf(
                scenario.existingCategories().stream().map(c -> c.id()).toList());
    }

    @Test
    void NEW_확률이_임계값과_같으면_생성하고_미만이면_최고_기존_후보를_고른다() {
        var scenario = CategoryAssignmentEvalTestData.scenarios().stream()
                .filter(s -> s.source().proposedCategoryTitle().equals("PDF 압축")).findFirst().orElseThrow();
        var probabilities = new LinkedHashMap<String, Double>();
        String expectedIndex = null;
        for (int i = 0; i < scenario.existingCategories().size(); i++) {
            String index = Integer.toString(i + 1);
            probabilities.put(index, 0.0);
            if (scenario.existingCategories().get(i).id().equals(scenario.expected().categoryId())) expectedIndex = index;
        }
        probabilities.put(expectedIndex, 0.6);
        probabilities.put("NEW", 0.4);
        var response = new SystemOneResponse("jev-1.13.0", Map.of("assignment",
                new SystemOneResponse.Choice(expectedIndex, probabilities, 0.5)),
                new SystemOneResponse.Usage(100, 10));
        CategoryAssignmentJevEvalTest.validate(CategoryAssignmentJevEvalTest.request(scenario), response);
        var result = new CategoryAssignmentJevEvalTest.Result(1, scenario, false, null,
                CategoryAssignmentJevEvalTest.request(scenario), response, 1, null);
        assertThat(CategoryAssignmentJevEvalTest.decision(result, 0.4).action()).isEqualTo("CREATE");
        assertThat(CategoryAssignmentJevEvalTest.decision(result, 0.5).categoryId()).isEqualTo(scenario.expected().categoryId());
        assertThat(CategoryAssignmentJevEvalTest.originalDecision(result).categoryId()).isEqualTo(scenario.expected().categoryId());
        assertThat(CategoryAssignmentJevEvalTest.originalScore(java.util.List.of(result)).correct()).isEqualTo(1);
        assertThat(CategoryAssignmentJevEvalTest.score(java.util.List.of(result), 0.4).overCreate()).isEqualTo(1);
    }

    @Test
    void 후보별_확률이_누락되면_응답을_유효한_판정으로_쓰지_않는다() {
        var scenario = CategoryAssignmentEvalTestData.scenarios().getFirst();
        var response = new SystemOneResponse("jev-1.13.0", Map.of("assignment",
                new SystemOneResponse.Choice("NEW", Map.of("NEW", 1.0), 1.0)), null);
        assertThatThrownBy(() -> CategoryAssignmentJevEvalTest.validate(
                CategoryAssignmentJevEvalTest.request(scenario), response)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 확률_합계의_반올림_오차는_허용하지만_큰_누락은_거부한다() {
        var scenario = CategoryAssignmentEvalTestData.scenarios().getFirst();
        var request = CategoryAssignmentJevEvalTest.request(scenario);
        var probabilities = new LinkedHashMap<String, Double>();
        for (int i = 0; i < scenario.existingCategories().size(); i++) probabilities.put(Integer.toString(i + 1), 0.0);
        probabilities.put("NEW", 0.57);
        probabilities.put("1", 0.42);
        var response = new SystemOneResponse("jev-1.13.0", Map.of("assignment",
                new SystemOneResponse.Choice("NEW", probabilities, 0.51)), null);
        CategoryAssignmentJevEvalTest.validate(request, response);
        probabilities.put("NEW", 0.5);
        assertThatThrownBy(() -> CategoryAssignmentJevEvalTest.validate(request, response))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

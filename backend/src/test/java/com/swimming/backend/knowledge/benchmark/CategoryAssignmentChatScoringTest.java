package com.swimming.backend.knowledge.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CategoryAssignmentChatScoringTest {
    @Test
    void 출력_형식_오류는_Invalid로_집계하고_요청_인증_오류만_중단한다() {
        var s = CategoryAssignmentEvalTestData.scenarios().getFirst();
        var mapper = new ObjectMapper();
        var invalid = new CategoryAssignmentChatEvalTest.Result(1, s, java.util.Map.of(),
                mapper.createObjectNode(), null, 1, "IllegalArgumentException");
        assertThat(CategoryAssignmentChatEvalTest.systemicFailure(invalid)).isFalse();
        assertThat(CategoryAssignmentChatEvalTest.score(java.util.List.of(invalid)).invalid()).isEqualTo(1);
        assertThat(CategoryAssignmentChatEvalTest.score(java.util.List.of(invalid)).total()).isEqualTo(1);
        var auth = new CategoryAssignmentChatEvalTest.Result(1, s, java.util.Map.of(),
                mapper.createObjectNode().put("httpStatus", 401), null, 1, "IllegalStateException");
        assertThat(CategoryAssignmentChatEvalTest.systemicFailure(auth)).isTrue();
    }

    @Test
    void 캐시_입력과_출력_토큰을_구분해_비용을_계산한다() {
        assertThat(CategoryAssignmentChatEvalTest.cost(1_000_000, 0, 1_000_000)).isEqualTo(1.4);
        assertThat(CategoryAssignmentChatEvalTest.cost(1_000_000, 1_000_000, 0)).isEqualTo(.02);
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "CATEGORY_CHAT_REPLAY_REPORT", matches = ".+")
    void 저장된_원자료를_API_호출_없이_재집계한다() throws Exception {
        var mapper = new ObjectMapper();
        var path = java.nio.file.Path.of(System.getenv("CATEGORY_CHAT_REPLAY_REPORT"));
        var root = mapper.readTree(path.toFile());
        var results = mapper.convertValue(root.path("results"),
                new com.fasterxml.jackson.core.type.TypeReference<java.util.List<CategoryAssignmentChatEvalTest.Result>>() {});
        var score = CategoryAssignmentChatEvalTest.score(results);
        assertThat(score.total()).isEqualTo(45);
        assertThat(score.invalid()).isZero();
        assertThat(score.correct() + score.wrongReuse() + score.overCreate()).isEqualTo(45);
        ((com.fasterxml.jackson.databind.node.ObjectNode) root).set("score", mapper.valueToTree(score));
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.resolveSibling(
                path.getFileName().toString().replace(".json", "-scored.json")).toFile(), root);
        System.out.println("Category A score: " + mapper.writeValueAsString(score));
    }

    @Test
    void 입력에_소화_결과만_포함하고_제안_이름과_정답을_제외한다() {
        var s = CategoryAssignmentEvalTestData.scenarios().getFirst();
        var input = CategoryAssignmentChatEvalTest.input(s);
        assertThat(input).containsOnlyKeys("source", "categories");
        assertThat((java.util.Map<?, ?>) input.get("source")).hasSize(4);
        assertThat(new ObjectMapper().valueToTree(input).findValue("proposedCategoryTitle")).isNull();
        assertThat(new ObjectMapper().valueToTree(input).findValue("expected")).isNull();
    }

    @Test
    void 축소_입력은_제목_summary만_포함하고_기존_후보를_유지한다() {
        for (var s : CategoryAssignmentEvalTestData.scenarios()) {
            var full = CategoryAssignmentChatEvalTest.input(s);
            var reduced = CategoryAssignmentChatEvalTest.input(s, false);
            var source = new ObjectMapper().valueToTree(reduced.get("source"));
            assertThat(source.size()).isEqualTo(2);
            assertThat(source.path("title").asText()).isEqualTo(s.source().title());
            assertThat(source.path("summary").asText()).isEqualTo(s.source().summary());
            assertThat(source.has("topic")).isFalse();
            assertThat(source.has("subjects")).isFalse();
            assertThat(source.has("proposedCategoryTitle")).isFalse();
            assertThat(reduced.get("categories")).isEqualTo(full.get("categories"));
        }
    }

    @Test
    void 인덱스를_실제_ID로_변환하고_목록_밖의_인덱스를_거절한다() throws Exception {
        var s = CategoryAssignmentEvalTestData.scenarios().getFirst();
        var mapper = new ObjectMapper();
        var output = mapper.readTree("{\"action\":\"REUSE\",\"categoryIndex\":\"1\",\"title\":\"\"}");
        assertThat(CategoryAssignmentChatEvalTest.validate(s, output).categoryId())
                .isEqualTo(s.existingCategories().getFirst().id());
        ((com.fasterxml.jackson.databind.node.ObjectNode) output).put("categoryIndex", "999");
        assertThatThrownBy(() -> CategoryAssignmentChatEvalTest.validate(s, output))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 생성_이름이_기존_제목과_같으면_재사용하고_빈_이름은_거절한다() {
        var s = CategoryAssignmentEvalTestData.scenarios().getFirst();
        var output = new ObjectMapper().createObjectNode().put("action", "CREATE")
                .put("categoryIndex", "").put("title", s.existingCategories().getFirst().title());
        assertThat(CategoryAssignmentChatEvalTest.validate(s, output).normalizedReuse()).isTrue();
        output.put("title", " ");
        assertThatThrownBy(() -> CategoryAssignmentChatEvalTest.validate(s, output))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

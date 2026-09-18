package com.swimming.backend.knowledge.benchmark;

import org.junit.jupiter.api.Test;
import java.util.List;

import static com.swimming.backend.knowledge.benchmark.CategoryAssignmentEvalScenario.Action.CREATE;
import static com.swimming.backend.knowledge.benchmark.CategoryAssignmentEvalScenario.Action.REUSE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CategoryAssignmentEmbeddingScoringTest {
    @Test
    void 코사인_거리는_동일_직교_반대_벡터를_구분한다() {
        assertThat(CategoryAssignmentEmbeddingEvalTest.distance(new double[]{1, 0}, new double[]{2, 0})).isZero();
        assertThat(CategoryAssignmentEmbeddingEvalTest.distance(new double[]{1, 0}, new double[]{0, 1})).isEqualTo(1);
        assertThat(CategoryAssignmentEmbeddingEvalTest.distance(new double[]{1, 0}, new double[]{-1, 0})).isEqualTo(2);
        assertThatThrownBy(() -> CategoryAssignmentEmbeddingEvalTest.distance(new double[]{0}, new double[]{1}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 임계값과_같으면_재사용하고_다른_Category_선택은_오류로_센다() {
        var cases = List.of(
                result(REUSE, "right", "right", 0.4),
                result(REUSE, "right", "wrong", 0.2),
                result(CREATE, null, "wrong", 0.6));
        var score = CategoryAssignmentEmbeddingEvalTest.score(cases, 0.4);
        assertThat(score.correct()).isEqualTo(2);
        assertThat(score.wrongReuse()).isEqualTo(1);
        assertThat(score.overCreate()).isZero();
        var strict = CategoryAssignmentEmbeddingEvalTest.score(cases, 0.1);
        assertThat(strict.correct()).isEqualTo(1);
        assertThat(strict.overCreate()).isEqualTo(2);
        assertThat(strict.wrongReuse()).isZero();
    }

    @Test
    void 임베딩_비용은_입력_토큰을_백만_단위로_환산한다() {
        assertThat(CategoryAssignmentEmbeddingEvalTest.cost(1_000_000)).isEqualTo(0.02);
        assertThat(CategoryAssignmentEmbeddingEvalTest.cost(0)).isZero();
    }

    private CategoryAssignmentEmbeddingEvalTest.CaseResult result(
            CategoryAssignmentEvalScenario.Action action, String expectedId, String nearestId, double distance) {
        return new CategoryAssignmentEmbeddingEvalTest.CaseResult("case", "source", "proposal",
                new CategoryAssignmentEvalScenario.Expected(action, expectedId), false, false, 0,
                List.of(new CategoryAssignmentEmbeddingEvalTest.Candidate(nearestId, "candidate", distance)));
    }
}

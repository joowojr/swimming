package com.swimming.backend.knowledge.benchmark;

import com.swimming.backend.knowledge.domain.NodeTitleNormalizer;
import org.junit.jupiter.api.Test;

import static com.swimming.backend.knowledge.benchmark.CategoryAssignmentEvalScenario.Action.CREATE;
import static com.swimming.backend.knowledge.benchmark.CategoryAssignmentEvalScenario.Action.REUSE;
import static com.swimming.backend.knowledge.benchmark.NodeResolutionEvalScenario.Domain.TECHNOLOGY;
import static org.assertj.core.api.Assertions.assertThat;

class CategoryAssignmentEvalTestDataTest {
    @Test
    void 로컬_사용자_배정과_소화_결과는_평가에_필요한_조건을_갖춘다() {
        var snapshot = CategoryAssignmentEvalTestData.snapshot();
        assertThat(snapshot.userId()).isEqualTo(1);
        assertThat(snapshot.folderId()).isEqualTo(1);
        assertThat(snapshot.domain()).isEqualTo(TECHNOLOGY);
        assertThat(snapshot.provenance()).isEqualTo("LOCAL_DB_USER_RELATIONS");
        assertThat(snapshot.version()).isNotBlank();
        assertThat(snapshot.capturedAt()).isNotBlank();
        assertThat(snapshot.categories()).hasSize(8);
        assertThat(snapshot.sources()).hasSize(15);
        assertThat(snapshot.sources()).extracting(s -> s.id()).doesNotHaveDuplicates();
        assertThat(snapshot.categories()).extracting(c -> c.id()).doesNotHaveDuplicates();
        assertThat(snapshot.categories()).extracting(c -> NodeTitleNormalizer.normalize(c.title()))
                .doesNotHaveDuplicates().doesNotContain("");
        var assignments = snapshot.categories().stream().flatMap(c -> c.assignments().stream()).toList();
        assertThat(assignments).allMatch(a -> a.origin().equals("USER"));
        assertThat(assignments).extracting(a -> a.sourceId()).doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(snapshot.sources().stream().map(s -> s.id()).toList());
        assertThat(snapshot.sources()).allSatisfy(s -> {
            assertThat(s.folder_id()).isEqualTo(snapshot.folderId());
            assertThat(s.processing_status()).isEqualTo("COMPLETED");
            assertThat(s.title()).isNotBlank();
            assertThat(s.summary()).isNotBlank();
            // 단일 topic 전제를 확인해 여러 topic을 조용히 버리지 않는다.
            assertThat(s.topics()).hasSize(1).allSatisfy(t -> assertThat(t).isNotBlank());
            assertThat(s.subjects()).isNotEmpty().allSatisfy(t -> assertThat(t).isNotBlank());
        });
        assertThat(snapshot.excludedSources()).extracting(s -> s.id())
                .doesNotContainAnyElementsOf(snapshot.sources().stream().map(s -> s.id()).toList());
    }

    @Test
    void 단독_Source를_제거한_경우만_CREATE이며_나머지_정답은_후보에_있다() {
        var snapshot = CategoryAssignmentEvalTestData.snapshot();
        var scenarios = CategoryAssignmentEvalTestData.scenarios();
        assertThat(scenarios).hasSize(15);
        assertThat(scenarios).extracting(CategoryAssignmentEvalScenario::id).doesNotHaveDuplicates();
        assertThat(scenarios).filteredOn(s -> s.expected().action() == CREATE).hasSize(2);
        assertThat(scenarios).filteredOn(s -> s.expected().action() == REUSE).hasSize(13);
        assertThat(scenarios).allSatisfy(s -> {
            var original = snapshot.categories().stream()
                    .filter(c -> c.assignments().stream().anyMatch(a -> a.sourceId().equals(s.source().id())))
                    .findFirst().orElseThrow();
            var candidates = snapshot.categories().stream()
                    .filter(c -> c.assignments().stream().anyMatch(a -> !a.sourceId().equals(s.source().id())))
                    .map(c -> c.id()).toList();
            assertThat(s.existingCategories()).extracting(c -> c.id())
                    .containsExactlyElementsOf(candidates);
            if (original.assignments().size() == 1) {
                assertThat(s.expected().action()).isEqualTo(CREATE);
                assertThat(s.expected().categoryId()).isNull();
                assertThat(candidates).doesNotContain(original.id());
            } else {
                assertThat(s.expected().action()).isEqualTo(REUSE);
                assertThat(s.expected().categoryId()).isEqualTo(original.id());
                assertThat(candidates).contains(original.id());
            }
        });
    }

    @Test
    void 제안_이름은_수작업_통제_입력이며_정규화_재사용과_의미_재사용을_모두_포함한다() {
        var snapshot = CategoryAssignmentEvalTestData.snapshot();
        assertThat(snapshot.proposedCategoryTitleStatus()).isEqualTo("MANUAL_CONTROLLED_FIXTURE");
        assertThat(snapshot.sources()).filteredOn(s -> s.proposedCategoryTitleProvenance()
                .equals("MANUAL_EXISTING_TITLE")).hasSize(7);
        assertThat(snapshot.sources()).filteredOn(s -> s.proposedCategoryTitleProvenance()
                .equals("MANUAL_NARROW_TITLE")).hasSize(8);
        var scenarios = CategoryAssignmentEvalTestData.scenarios();
        assertThat(scenarios).allSatisfy(s -> {
            assertThat(s.source().proposedCategoryTitle()).isNotBlank();
            var original = snapshot.categories().stream()
                    .filter(c -> c.assignments().stream().anyMatch(a -> a.sourceId().equals(s.source().id())))
                    .findFirst().orElseThrow();
            var source = snapshot.sources().stream().filter(x -> x.id().equals(s.source().id()))
                    .findFirst().orElseThrow();
            if (source.proposedCategoryTitleProvenance().equals("MANUAL_EXISTING_TITLE")) {
                assertThat(source.proposedCategoryTitle()).isEqualTo(original.title());
            } else {
                assertThat(source.proposedCategoryTitleProvenance()).isEqualTo("MANUAL_NARROW_TITLE");
                assertThat(NodeTitleNormalizer.normalize(source.proposedCategoryTitle()))
                        .isNotEqualTo(NodeTitleNormalizer.normalize(original.title()));
            }
            var exact = s.existingCategories().stream().filter(c -> NodeTitleNormalizer.normalize(c.title())
                    .equals(NodeTitleNormalizer.normalize(s.source().proposedCategoryTitle()))).toList();
            if (s.expected().action() == CREATE) {
                assertThat(exact).isEmpty();
            } else {
                assertThat(exact).allSatisfy(c -> assertThat(c.id()).isEqualTo(s.expected().categoryId()));
            }
        });
        assertThat(scenarios.stream().filter(s -> s.existingCategories().stream().anyMatch(c ->
                NodeTitleNormalizer.normalize(c.title()).equals(
                        NodeTitleNormalizer.normalize(s.source().proposedCategoryTitle())))).count())
                .isEqualTo(5);
        assertThat(scenarios).filteredOn(s -> s.expected().action() == REUSE
                && s.existingCategories().stream().noneMatch(c -> NodeTitleNormalizer.normalize(c.title())
                .equals(NodeTitleNormalizer.normalize(s.source().proposedCategoryTitle())))).hasSize(8);
    }
}

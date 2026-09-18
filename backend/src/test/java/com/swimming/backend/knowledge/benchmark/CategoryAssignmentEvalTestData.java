package com.swimming.backend.knowledge.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

import static com.swimming.backend.knowledge.benchmark.CategoryAssignmentEvalScenario.Action.CREATE;
import static com.swimming.backend.knowledge.benchmark.CategoryAssignmentEvalScenario.Action.REUSE;
import static com.swimming.backend.knowledge.benchmark.NodeResolutionEvalScenario.Priority.STANDARD;

/**
 * 로컬 user 1 / folder 1의 사용자 배정을 고정한 스냅샷.
 * DB를 다시 읽지 않는다. 제안 이름은 기존 제목·하위 주제명을 섞은 수작업 통제 입력이며
 * 실제 digest 출력이 아니다. 사용자 배정 정답은 변경하지 않는다.
 */
final class CategoryAssignmentEvalTestData {
    static final String RESOURCE = "/knowledge/category-assignment/user1-folder1.json";
    private static final Snapshot SNAPSHOT = load();
    static final String VERSION = SNAPSHOT.version();
    static final String PROVENANCE = SNAPSHOT.provenance();

    private CategoryAssignmentEvalTestData() {}

    static Snapshot snapshot() {
        return SNAPSHOT;
    }

    static List<CategoryAssignmentEvalScenario> scenarios() {
        return SNAPSHOT.sources().stream().map(source -> {
            var original = SNAPSHOT.categories().stream()
                    .filter(c -> c.assignments().stream().anyMatch(a -> a.sourceId().equals(source.id())))
                    .findFirst().orElseThrow();
            // 마지막 Source를 제거하면 그 Category는 후보 목록에서도 사라진다.
            var candidates = SNAPSHOT.categories().stream()
                    .filter(c -> c.assignments().stream().anyMatch(a -> !a.sourceId().equals(source.id())))
                    .map(c -> new CategoryAssignmentEvalScenario.CategoryFixture(c.id(), c.title()))
                    .toList();
            boolean create = original.assignments().size() == 1;
            var expected = new CategoryAssignmentEvalScenario.Expected(
                    create ? CREATE : REUSE, create ? null : original.id());
            return new CategoryAssignmentEvalScenario(
                    "local-folder1-" + source.id(), SNAPSHOT.domain(), SNAPSHOT.folderName(), candidates,
                    new CategoryAssignmentEvalScenario.SourceFixture(
                            source.id(), source.title(), source.summary(), source.topics().getFirst(),
                            source.subjects(), source.proposedCategoryTitle()),
                    expected, STANDARD,
                    create ? "사용자 배정의 단독 Source를 제거하면 기존 Category가 사라져 CREATE가 정답이다."
                            : "사용자가 연결한 Category에 다른 Source가 남아 있으므로 해당 Category를 재사용한다.");
        }).toList();
    }

    private static Snapshot load() {
        try (var input = CategoryAssignmentEvalTestData.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Category 평가 스냅샷이 없다: " + RESOURCE);
            }
            return new ObjectMapper().readValue(input, Snapshot.class);
        } catch (IOException e) {
            throw new IllegalStateException("Category 평가 스냅샷을 읽지 못했다", e);
        }
    }

    record Snapshot(
            String version, String provenance, long userId, long folderId, String folderName,
            NodeResolutionEvalScenario.Domain domain, String capturedAt,
            List<Category> categories, List<Source> sources, List<ExcludedSource> excludedSources,
            String proposedCategoryTitleStatus, String snapshotScope
    ) {}

    record Category(String id, String title, List<Assignment> assignments) {}
    record Assignment(String sourceId, String origin) {}
    record Source(
            String id, String title, String summary, long folder_id, String processing_status,
            String created_at, List<String> topics, List<String> subjects,
            String proposedCategoryTitle, String proposedCategoryTitleProvenance
    ) {}
    record ExcludedSource(String id, String reason) {}
}

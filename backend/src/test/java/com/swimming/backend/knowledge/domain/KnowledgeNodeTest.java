package com.swimming.backend.knowledge.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeNodeTest {
    private static final Instant FIRST_RENAMED_AT = Instant.parse("2026-09-17T10:00:00Z");
    private static final Instant SECOND_RENAMED_AT = Instant.parse("2026-09-17T11:00:00Z");

    @ParameterizedTest
    @EnumSource(value = NodeType.class, names = {"CATEGORY", "TOPIC"})
    void 사용자_제목_수정_시각은_실제로_이름을_바꿀_때만_갱신한다(NodeType type) {
        KnowledgeNode node = KnowledgeNode.create(1L, type, "처음 이름", null);
        assertThat(node.getTitleRenamedAt()).isNull();
        node.renameByUser("  처음 이름  ", FIRST_RENAMED_AT);
        assertThat(node.getTitleRenamedAt()).isNull();

        node.renameByUser("  새 이름  ", FIRST_RENAMED_AT);
        assertThat(node.getTitle()).isEqualTo("새 이름");
        assertThat(node.getTitleRenamedAt()).isEqualTo(FIRST_RENAMED_AT);
        node.renameByUser("새 이름", SECOND_RENAMED_AT);
        assertThat(node.getTitleRenamedAt()).isEqualTo(FIRST_RENAMED_AT);

        node.renameByUser("다시 수정", SECOND_RENAMED_AT);
        assertThat(node.getTitleRenamedAt()).isEqualTo(SECOND_RENAMED_AT);
    }

    @Test
    void 시스템에서_정한_제목은_사용자_수정_시각을_기록하지_않는다() {
        KnowledgeNode node = KnowledgeNode.create(1L, NodeType.SOURCE, "URL", null);
        node.rename("원문 제목");
        assertThat(node.getTitleRenamedAt()).isNull();
    }
}

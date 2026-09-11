package com.swimming.backend.knowledge.domain;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeRelationTest {

    private static final Long USER_ID = 1L;

    private KnowledgeNode node(NodeType nodeType, String title) {
        return KnowledgeNode.create(USER_ID, nodeType, title, null);
    }

    @Test
    @DisplayName("Source가 다루는 Subject를 ABOUT으로 연결한다")
    void createsAboutRelation() {
        KnowledgeNode source = node(NodeType.SOURCE, "Spring AI MCP Reference");
        KnowledgeNode subject = node(NodeType.SUBJECT, "MCP");

        KnowledgeRelation relation = KnowledgeRelation.create(
                source,
                subject,
                RelationType.ABOUT,
                RelationOrigin.AI,
                0.9,
                null
        );

        assertThat(relation.getFromNodeId()).isEqualTo(source.getId());
        assertThat(relation.getToNodeId()).isEqualTo(subject.getId());
        assertThat(relation.getRelationType()).isEqualTo(RelationType.ABOUT);
        assertThat(relation.getOrigin()).isEqualTo(RelationOrigin.AI);
        assertThat(relation.getCreatedAt()).isNull();
    }

    @Test
    @DisplayName("ABOUT은 Source에서 Subject 방향으로만 만들 수 있다")
    void rejectsReversedAboutRelation() {
        KnowledgeNode source = node(NodeType.SOURCE, "Spring AI MCP Reference");
        KnowledgeNode subject = node(NodeType.SUBJECT, "MCP");

        assertThatThrownBy(() -> KnowledgeRelation.create(
                subject,
                source,
                RelationType.ABOUT,
                RelationOrigin.AI,
                null,
                null
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_KNOWLEDGE_RELATION);
    }

    @Test
    @DisplayName("SUPPORTS는 Source와 UseCase만 연결한다")
    void rejectsSupportsBetweenWrongTypes() {
        KnowledgeNode source = node(NodeType.SOURCE, "Spring AI MCP Reference");
        KnowledgeNode subject = node(NodeType.SUBJECT, "MCP");

        assertThatThrownBy(() -> KnowledgeRelation.create(
                source,
                subject,
                RelationType.SUPPORTS,
                RelationOrigin.AI,
                null,
                null
        ))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("같은 관계를 AI가 다시 관찰하면 근거를 갱신한다")
    void appliesObservation() {
        KnowledgeNode topic = node(NodeType.TOPIC, "MCP 서버 구현하기");
        KnowledgeNode subject = node(NodeType.SUBJECT, "Tool Calling");

        KnowledgeRelation relation = KnowledgeRelation.create(
                topic,
                subject,
                RelationType.INVOLVES,
                RelationOrigin.AI,
                0.6,
                null
        );

        relation.applyObservation(RelationOrigin.AI, 0.95, "{\"sourceCount\":2}");

        assertThat(relation.getConfidence()).isEqualTo(0.95);
        assertThat(relation.getEvidence()).isEqualTo("{\"sourceCount\":2}");
    }

    @Test
    @DisplayName("사용자가 만든 관계는 AI 재관찰로 덮어쓰지 않는다")
    void keepsUserRelation() {
        KnowledgeNode topic = node(NodeType.TOPIC, "MCP 서버 구현하기");
        KnowledgeNode subject = node(NodeType.SUBJECT, "Tool Calling");

        KnowledgeRelation relation = KnowledgeRelation.create(
                topic,
                subject,
                RelationType.INVOLVES,
                RelationOrigin.USER,
                null,
                null
        );

        relation.applyObservation(RelationOrigin.AI, 0.3, "{\"sourceCount\":1}");

        assertThat(relation.getOrigin()).isEqualTo(RelationOrigin.USER);
        assertThat(relation.getConfidence()).isNull();
        assertThat(relation.getEvidence()).isNull();
    }

    @Test
    @DisplayName("양끝 노드 타입으로 관계를 고른다")
    void 노드_타입으로_관계를_찾는다() {
        assertThat(RelationType.between(NodeType.SOURCE, NodeType.SUBJECT))
                .isEqualTo(RelationType.ABOUT);
        assertThat(RelationType.between(NodeType.SOURCE, NodeType.TOPIC))
                .isEqualTo(RelationType.SUPPORTS);
        assertThat(RelationType.between(NodeType.TOPIC, NodeType.SUBJECT))
                .isEqualTo(RelationType.INVOLVES);
    }

    @Test
    @DisplayName("이을 수 없는 조합이면 관계를 만들지 않는다")
    void 없는_조합은_거절한다() {
        assertThatThrownBy(() -> RelationType.between(NodeType.SUBJECT, NodeType.SOURCE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_KNOWLEDGE_RELATION);
    }
}

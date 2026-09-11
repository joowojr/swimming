package com.swimming.backend.knowledge.service.data;

import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeRelation;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.domain.RelationOrigin;
import com.swimming.backend.knowledge.domain.RelationType;
import com.swimming.backend.knowledge.repository.InMemoryKnowledgeRepositories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeRelationServiceTest {

    private static final Long USER_ID = 1L;

    private InMemoryKnowledgeRepositories.Relations relations;
    private KnowledgeRelationService relationService;

    @BeforeEach
    void setUp() {
        relations = new InMemoryKnowledgeRepositories.Relations();
        relationService = new KnowledgeRelationService(relations);
    }

    private KnowledgeNode node(NodeType nodeType, String title) {
        return KnowledgeNode.create(USER_ID, nodeType, title, null);
    }

    private List<KnowledgeNode> subjects(String... titles) {
        return List.of(titles).stream()
                .map(title -> node(NodeType.SUBJECT, title))
                .toList();
    }

    private List<KnowledgeRelation> about(KnowledgeNode from) {
        return relations.findAllByFromNodeIdIn(
                List.of(from.getId()), List.of(RelationType.ABOUT)
        );
    }

    @Test
    @DisplayName("Subject가 여러 개여도 저장소 조회는 한 번만 한다")
    void 여러_대상을_한_번의_조회로_잇는다() {
        KnowledgeNode source = node(NodeType.SOURCE, "Spring AI MCP Reference");

        relationService.connectAll(
                source, subjects("MCP", "Tool Calling", "Structured Output", "Embedding", "RAG"),
                RelationOrigin.AI
        );

        assertThat(relations.lookupCount).isEqualTo(1);
        assertThat(about(source)).hasSize(5);
    }

    @Test
    @DisplayName("같은 관계를 다시 관찰해도 행이 늘지 않는다")
    void 재관찰은_행을_늘리지_않는다() {
        KnowledgeNode source = node(NodeType.SOURCE, "Spring AI MCP Reference");
        List<KnowledgeNode> targets = subjects("MCP", "Tool Calling");

        relationService.connectAll(source, targets, RelationOrigin.AI);
        relationService.connectAll(source, targets, RelationOrigin.AI);

        assertThat(about(source)).hasSize(2);
    }

    @Test
    @DisplayName("한 요청에 같은 대상이 두 번 들어와도 관계는 하나만 남는다")
    void 중복_대상은_하나로_합친다() {
        KnowledgeNode source = node(NodeType.SOURCE, "Spring AI MCP Reference");
        KnowledgeNode mcp = node(NodeType.SUBJECT, "MCP");

        relationService.connectAll(source, List.of(mcp, mcp), RelationOrigin.AI);

        assertThat(about(source)).hasSize(1);
    }

    @Test
    @DisplayName("사용자가 만든 관계는 AI 재관찰로 덮어쓰지 않는다")
    void 사용자_관계는_보존된다() {
        KnowledgeNode source = node(NodeType.SOURCE, "Spring AI MCP Reference");
        KnowledgeNode mcp = node(NodeType.SUBJECT, "MCP");

        relationService.connect(source, mcp, RelationOrigin.USER);
        relationService.connectAll(source, List.of(mcp), RelationOrigin.AI);

        assertThat(about(source))
                .singleElement()
                .extracting(KnowledgeRelation::getOrigin)
                .isEqualTo(RelationOrigin.USER);
    }

    @Test
    @DisplayName("대상이 없으면 저장소를 부르지 않는다")
    void 대상이_없으면_조회하지_않는다() {
        KnowledgeNode source = node(NodeType.SOURCE, "Spring AI MCP Reference");

        assertThat(relationService.connectAll(source, List.of(), RelationOrigin.AI)).isEmpty();
        assertThat(relations.lookupCount).isZero();
    }
}

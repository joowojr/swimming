package com.swimming.backend.knowledge.service.graph;

import com.swimming.backend.knowledge.domain.KnowledgeRelation;
import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.domain.RelationType;
import com.swimming.backend.knowledge.dto.out.SourceDigestResult;
import com.swimming.backend.knowledge.dto.out.ResolvedNode;
import com.swimming.backend.knowledge.repository.InMemoryKnowledgeRepositories;
import com.swimming.backend.knowledge.service.data.KnowledgeNodeService;
import com.swimming.backend.knowledge.service.data.KnowledgeRelationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SourceGraphWriterTest {

    private static final Long USER_ID = 1L;
    private static final Long FOLDER_ID = 10L;

    private InMemoryKnowledgeRepositories.Nodes nodes;
    private InMemoryKnowledgeRepositories.Relations relations;
    private SourceGraphWriter writer;

    @BeforeEach
    void setUp() {
        nodes = new InMemoryKnowledgeRepositories.Nodes();
        relations = new InMemoryKnowledgeRepositories.Relations();

        writer = new SourceGraphWriter(
                new KnowledgeNodeService(nodes),
                new KnowledgeRelationService(relations)
        );
    }

    private KnowledgeSource source(String url) {
        return KnowledgeSource.create(USER_ID, FOLDER_ID, "Spring AI MCP Reference", url, url);
    }

    private SourceDigestResult result(String topic, String... subjects) {
        return new SourceDigestResult("요약", "백엔드", topic, List.of(subjects));
    }

    private void write(KnowledgeSource source, SourceDigestResult result) {
        List<ResolvedNode> subjects = result.subjects().stream()
                .map(title -> ResolvedNode.created(
                        title,
                        nodes.save(KnowledgeNode.create(USER_ID, NodeType.SUBJECT, title, null))
                ))
                .toList();
        writer.write(source, result, subjects);
    }

    private List<KnowledgeRelation> from(UUID fromNodeId, RelationType relationType) {
        return relations.findAllByFromNodeIdIn(List.of(fromNodeId), List.of(relationType));
    }

    private String titleOf(UUID nodeId) {
        return nodes.findById(nodeId).orElseThrow().getTitle();
    }

    @Test
    @DisplayName("문서가 다룬 개념은 ABOUT으로, 적용 목적은 SUPPORTS로 잇는다")
    void 소스를_개념과_목적에_잇는다() {
        KnowledgeSource source = source("https://a.com/mcp");

        write(source, result("MCP 서버 구현하기", "MCP", "Tool Calling"));

        UUID sourceNodeId = source.getNode().getId();
        assertThat(from(sourceNodeId, RelationType.ABOUT))
                .extracting(relation -> titleOf(relation.getToNodeId()))
                .containsExactlyInAnyOrder("MCP", "Tool Calling");

        List<KnowledgeRelation> supports = from(sourceNodeId, RelationType.SUPPORTS);
        assertThat(supports).hasSize(1);
        assertThat(titleOf(supports.getFirst().getToNodeId())).isEqualTo("MCP 서버 구현하기");
    }

    @Test
    @DisplayName("INVOLVES는 LLM 출력 없이 Topic과 그 Source의 Subject로 파생한다")
    void involves를_파생한다() {
        KnowledgeSource source = source("https://a.com/mcp");

        write(source, result("MCP 서버 구현하기", "MCP", "Tool Calling"));

        UUID topicId = from(source.getNode().getId(), RelationType.SUPPORTS)
                .getFirst().getToNodeId();

        assertThat(from(topicId, RelationType.INVOLVES))
                .extracting(relation -> titleOf(relation.getToNodeId()))
                .containsExactlyInAnyOrder("MCP", "Tool Calling");
    }

    @Test
    @DisplayName("Topic은 재사용하지 않는다. 이름이 같아도 Source마다 새로 만든다")
    void 목적은_매번_만든다() {
        write(source("https://a.com/1"), result("MCP 서버 구현하기", "MCP"));
        write(source("https://a.com/2"), result("MCP 서버 구현하기", "MCP"));

        assertThat(nodes.findAllByUserIdAndNodeType(USER_ID, NodeType.TOPIC)).hasSize(2);
    }

    @Test
    @DisplayName("같은 문서를 다시 반영해도 관계가 늘지 않는다")
    void 관계를_중복_저장하지_않는다() {
        KnowledgeSource source = source("https://a.com/mcp");

        KnowledgeNode subject = nodes.save(KnowledgeNode.create(
                USER_ID, NodeType.SUBJECT, "MCP", null
        ));
        List<ResolvedNode> resolved = List.of(ResolvedNode.created("MCP", subject));

        writer.write(source, result("MCP 서버 구현하기", "MCP"), resolved);
        writer.write(source, result("MCP 서버 구현하기", "MCP"), resolved);

        assertThat(from(source.getNode().getId(), RelationType.ABOUT)).hasSize(1);
    }
}

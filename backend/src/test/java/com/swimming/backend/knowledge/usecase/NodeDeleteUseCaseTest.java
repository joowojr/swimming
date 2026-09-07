package com.swimming.backend.knowledge.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.domain.RelationOrigin;
import com.swimming.backend.knowledge.dto.in.NodeRef;
import com.swimming.backend.knowledge.repository.InMemoryKnowledgeRepositories;
import com.swimming.backend.knowledge.service.KnowledgeNodeService;
import com.swimming.backend.knowledge.service.KnowledgeRelationService;
import com.swimming.backend.knowledge.service.KnowledgeSourceService;
import com.swimming.backend.knowledge.service.SourceConceptReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NodeDeleteUseCaseTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long FOLDER_ID = 10L;

    private InMemoryKnowledgeRepositories.Sources sources;
    private InMemoryKnowledgeRepositories.Nodes nodes;
    private InMemoryKnowledgeRepositories.Relations relations;

    private KnowledgeRelationService relationService;

    private NodeDeleteUseCase useCase;
    private NodeDetailUseCase nodeDetailUseCase;
    private SourceDetailUseCase sourceDetailUseCase;

    @BeforeEach
    void setUp() {
        sources = new InMemoryKnowledgeRepositories.Sources();
        nodes = new InMemoryKnowledgeRepositories.Nodes();
        relations = new InMemoryKnowledgeRepositories.Relations();

        relationService = new KnowledgeRelationService(relations);
        KnowledgeNodeService nodeService = new KnowledgeNodeService(nodes);
        KnowledgeSourceService sourceService = new KnowledgeSourceService(sources);

        useCase = new NodeDeleteUseCase(nodeService);
        nodeDetailUseCase = new NodeDetailUseCase(nodeService, relationService, sourceService);
        sourceDetailUseCase =
                new SourceDetailUseCase(sourceService, new SourceConceptReader(relations, nodes));
    }

    private KnowledgeSource givenSource(String title) {
        KnowledgeSource source = KnowledgeSource.create(
                USER_ID, FOLDER_ID, title, "https://a.com/" + title, "https://a.com/" + title
        );
        source.applyExtractedDocument(title, "본문", "article", "작성자", null);
        source.startDigestion();
        source.completeDigestion(title + " 요약", 1);

        KnowledgeSource saved = sources.save(source);
        nodes.save(saved.getNode());

        return saved;
    }

    private KnowledgeNode givenNode(NodeType nodeType, String title) {
        return nodes.save(KnowledgeNode.create(USER_ID, nodeType, title, null));
    }

    @Test
    @DisplayName("개념을 지우면 그 개념을 다루던 문서는 남고 이름만 사라진다")
    void deletesSubjectAndKeepsSource() {
        KnowledgeSource source = givenSource("문서");
        KnowledgeNode mcp = givenNode(NodeType.SUBJECT, "MCP");
        KnowledgeNode toolCalling = givenNode(NodeType.SUBJECT, "Tool Calling");
        KnowledgeNode topic = givenNode(NodeType.TOPIC, "MCP 서버 구현하기");

        relationService.connect(source.getNode(), mcp, RelationOrigin.AI);
        relationService.connect(source.getNode(), toolCalling, RelationOrigin.AI);
        relationService.connect(source.getNode(), topic, RelationOrigin.AI);

        useCase.delete(USER_ID, mcp.getId());

        assertThat(sourceDetailUseCase.get(USER_ID, source.getId()).subjects())
                .extracting(NodeRef::title)
                .containsExactly("Tool Calling");

        assertThatThrownBy(() -> nodeDetailUseCase.get(USER_ID, mcp.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.KNOWLEDGE_NODE_NOT_FOUND);
    }

    @Test
    @DisplayName("목적은 혼자 지울 수 없다. 문서를 지워야 함께 사라진다")
    void rejectsTopic() {
        // 목적만 지우면 소화가 끝난 문서가 목적을 잃는다.
        KnowledgeNode topic = givenNode(NodeType.TOPIC, "MCP 서버 구현하기");

        assertThatThrownBy(() -> useCase.delete(USER_ID, topic.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.KNOWLEDGE_TOPIC_NOT_DELETABLE);

        assertThat(nodeDetailUseCase.get(USER_ID, topic.getId()).title())
                .isEqualTo("MCP 서버 구현하기");
    }

    @Test
    @DisplayName("SOURCE 노드는 링크 삭제가 맡으므로 404를 준다")
    void rejectsSourceNode() {
        KnowledgeSource source = givenSource("문서");

        assertThatThrownBy(() -> useCase.delete(USER_ID, source.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.KNOWLEDGE_NODE_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 지웠거나 남의 개념은 지울 수 없다")
    void rejectsDeletedAndOtherUsersNode() {
        KnowledgeNode mcp = givenNode(NodeType.SUBJECT, "MCP");

        useCase.delete(USER_ID, mcp.getId());

        assertThatThrownBy(() -> useCase.delete(USER_ID, mcp.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.KNOWLEDGE_NODE_NOT_FOUND);

        assertThatThrownBy(() -> useCase.delete(OTHER_USER_ID, givenNode(NodeType.SUBJECT, "Tool Calling").getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.KNOWLEDGE_NODE_NOT_FOUND);

        assertThatThrownBy(() -> useCase.delete(USER_ID, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.KNOWLEDGE_NODE_NOT_FOUND);
    }
}

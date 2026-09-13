package com.swimming.backend.knowledge.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.domain.RelationOrigin;
import com.swimming.backend.knowledge.dto.in.NodeDetailResponse;
import com.swimming.backend.knowledge.dto.in.NodeRef;
import com.swimming.backend.knowledge.repository.InMemoryKnowledgeRepositories;
import com.swimming.backend.knowledge.service.data.KnowledgeNodeService;
import com.swimming.backend.knowledge.service.data.KnowledgeRelationService;
import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.knowledge.service.SourceGraphReader;
import com.swimming.backend.knowledge.service.data.KnowledgeSourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class NodeUseCaseTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long FOLDER_ID = 10L;

    private InMemoryKnowledgeRepositories.Sources sources;
    private InMemoryKnowledgeRepositories.Nodes nodes;
    private InMemoryKnowledgeRepositories.Relations relations;

    private KnowledgeRelationService relationService;

    private NodeUseCase useCase;

    /** 개념을 지운 뒤 문서 카드에서 이름이 사라졌는지 확인할 때 쓴다. */
    private SourceQueryUseCase sourceQueryUseCase;

    @BeforeEach
    void setUp() {
        sources = new InMemoryKnowledgeRepositories.Sources();
        nodes = new InMemoryKnowledgeRepositories.Nodes();
        relations = new InMemoryKnowledgeRepositories.Relations();

        relationService = new KnowledgeRelationService(relations);

        KnowledgeNodeService nodeService = new KnowledgeNodeService(nodes);
        KnowledgeSourceService sourceService = new KnowledgeSourceService(sources);

        useCase = new NodeUseCase(nodeService, relationService, sourceService);
        sourceQueryUseCase = new SourceQueryUseCase(
                mock(FolderService.class),
                sourceService,
                new SourceGraphReader(relations, nodes)
        );
    }

    private KnowledgeSource givenSource(String title) {
        KnowledgeSource source = KnowledgeSource.create(
                USER_ID, FOLDER_ID, title, "https://a.com/" + title, "https://a.com/" + title
        );
        source.applyExtractedDocument(title, "본문", "article", "작성자", null);
        source.startDigestion();
        source.completeDigestion(title + " 요약", 1);

        KnowledgeSource saved = sources.save(source);
        nodes.create(saved.getNode());

        return saved;
    }

    private KnowledgeNode givenNode(NodeType nodeType, String title) {
        return nodes.create(KnowledgeNode.create(USER_ID, nodeType, title, null));
    }

    /** 소화가 그래프에 남기는 모양 그대로 만든다. Topic은 Source당 하나이고 개념을 함께 걸친다. */
    private void digest(KnowledgeSource source, KnowledgeNode topic, KnowledgeNode... subjects) {
        for (KnowledgeNode subject : subjects) {
            relationService.connect(source.getNode(), subject, RelationOrigin.AI);
            relationService.connect(topic, subject, RelationOrigin.AI);
        }

        relationService.connect(source.getNode(), topic, RelationOrigin.AI);
    }

    @Nested
    @DisplayName("상세")
    class DetailTest {

        @Test
        @DisplayName("Subject는 그 개념을 다루는 문서를 모두 모으고 걸리는 목적을 함께 준다")
        void describesSubject() {
            KnowledgeNode mcp = givenNode(NodeType.SUBJECT, "MCP");

            KnowledgeSource first = givenSource("첫 문서");
            KnowledgeSource second = givenSource("둘째 문서");
            digest(first, givenNode(NodeType.TOPIC, "MCP 서버 구현하기"), mcp);
            digest(second, givenNode(NodeType.TOPIC, "MCP 클라이언트 붙이기"), mcp);

            NodeDetailResponse response = useCase.get(USER_ID, mcp.getId());

            assertThat(response.type()).isEqualTo(NodeType.SUBJECT);
            assertThat(response.title()).isEqualTo("MCP");
            assertThat(response.sources())
                    .extracting(NodeDetailResponse.SourceRef::title)
                    .containsExactlyInAnyOrder("첫 문서", "둘째 문서");
            assertThat(response.topics())
                    .extracting(NodeRef::title)
                    .containsExactlyInAnyOrder("MCP 서버 구현하기", "MCP 클라이언트 붙이기");
            assertThat(response.subjects())
                    .as("Subject 화면에는 다른 개념을 담지 않는다")
                    .isEmpty();
        }

        @Test
        @DisplayName("Topic은 그 목적을 설명하는 문서 하나와 걸치는 개념을 준다")
        void describesTopic() {
            KnowledgeNode topic = givenNode(NodeType.TOPIC, "MCP 서버 구현하기");
            KnowledgeNode mcp = givenNode(NodeType.SUBJECT, "MCP");
            KnowledgeNode toolCalling = givenNode(NodeType.SUBJECT, "Tool Calling");

            digest(givenSource("문서"), topic, mcp, toolCalling);

            NodeDetailResponse response = useCase.get(USER_ID, topic.getId());

            assertThat(response.type()).isEqualTo(NodeType.TOPIC);
            assertThat(response.sources())
                    .as("Topic은 Source마다 새로 만들므로 문서가 늘 하나다")
                    .extracting(NodeDetailResponse.SourceRef::title)
                    .containsExactly("문서");
            assertThat(response.sources().getFirst().summary()).isEqualTo("문서 요약");
            assertThat(response.subjects())
                    .extracting(NodeRef::title)
                    .containsExactlyInAnyOrder("MCP", "Tool Calling");
            assertThat(response.topics()).isEmpty();
        }

        @Test
        @DisplayName("노드가 그래프에 생긴 시각을 함께 준다")
        void includesCreatedAt() {
            KnowledgeNode mcp = givenNode(NodeType.SUBJECT, "MCP");

            assertThat(useCase.get(USER_ID, mcp.getId()).createdAt())
                    .isEqualTo(mcp.getCreatedAt());
        }

        @Test
        @DisplayName("아무 문서도 걸리지 않은 노드는 빈 목록을 준다")
        void describesLoneNode() {
            KnowledgeNode mcp = givenNode(NodeType.SUBJECT, "MCP");

            NodeDetailResponse response = useCase.get(USER_ID, mcp.getId());

            assertThat(response.sources()).isEmpty();
            assertThat(response.topics()).isEmpty();
            assertThat(response.subjects()).isEmpty();
        }

        @Test
        @DisplayName("SOURCE 노드는 Source Detail이 맡으므로 404를 준다")
        void rejectsSourceNode() {
            KnowledgeSource source = givenSource("문서");

            assertThatThrownBy(() -> useCase.get(USER_ID, source.getId()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.KNOWLEDGE_NODE_NOT_FOUND);
        }

        @Test
        @DisplayName("없는 노드와 남의 노드는 구분하지 않고 404를 준다")
        void rejectsUnknownAndOtherUsersNode() {
            KnowledgeNode mine = givenNode(NodeType.SUBJECT, "MCP");

            assertThatThrownBy(() -> useCase.get(USER_ID, UUID.randomUUID()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.KNOWLEDGE_NODE_NOT_FOUND);

            assertThatThrownBy(() -> useCase.get(OTHER_USER_ID, mine.getId()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.KNOWLEDGE_NODE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("삭제")
    class DeleteTest {

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

            assertThat(sourceQueryUseCase.get(USER_ID, source.getId()).subjects())
                    .extracting(NodeRef::title)
                    .containsExactly("Tool Calling");

            assertThatThrownBy(() -> useCase.get(USER_ID, mcp.getId()))
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

            assertThat(useCase.get(USER_ID, topic.getId()).title())
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
}

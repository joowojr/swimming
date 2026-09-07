package com.swimming.backend.knowledge.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.folder.dto.FolderReference;
import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.domain.RelationOrigin;
import com.swimming.backend.knowledge.domain.RelationType;
import com.swimming.backend.knowledge.dto.in.GraphResponse;
import com.swimming.backend.knowledge.repository.InMemoryKnowledgeRepositories;
import com.swimming.backend.knowledge.service.KnowledgeGraphAssembler;
import com.swimming.backend.knowledge.service.KnowledgeNodeService;
import com.swimming.backend.knowledge.service.KnowledgeRelationService;
import com.swimming.backend.knowledge.service.KnowledgeSourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeGraphUseCaseTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long FOLDER_ID = 10L;

    private InMemoryKnowledgeRepositories.Sources sources;
    private InMemoryKnowledgeRepositories.Nodes nodes;
    private InMemoryKnowledgeRepositories.Relations relations;

    private KnowledgeRelationService relationService;
    private FolderService folderService;
    private KnowledgeGraphUseCase useCase;

    @BeforeEach
    void setUp() {
        sources = new InMemoryKnowledgeRepositories.Sources();
        nodes = new InMemoryKnowledgeRepositories.Nodes();
        relations = new InMemoryKnowledgeRepositories.Relations();

        relationService = new KnowledgeRelationService(relations);
        folderService = mock(FolderService.class);
        when(folderService.getReference(USER_ID, FOLDER_ID))
                .thenReturn(new FolderReference(FOLDER_ID, "Spring AI 공부", "설명"));

        KnowledgeNodeService nodeService = new KnowledgeNodeService(nodes);

        useCase = new KnowledgeGraphUseCase(
                folderService,
                new KnowledgeSourceService(sources),
                nodeService,
                relationService,
                new KnowledgeGraphAssembler(nodeService)
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
        nodes.save(saved.getNode());

        return saved;
    }

    private KnowledgeNode givenNode(NodeType nodeType, String title) {
        return nodes.save(KnowledgeNode.create(USER_ID, nodeType, title, null));
    }

    /** 소화가 그래프에 남기는 모양 그대로 만든다. */
    private void digest(KnowledgeSource source, KnowledgeNode topic, KnowledgeNode... subjects) {
        for (KnowledgeNode subject : subjects) {
            relationService.connect(source.getNode(), subject, RelationOrigin.AI);
            relationService.connect(topic, subject, RelationOrigin.AI);
        }

        relationService.connect(source.getNode(), topic, RelationOrigin.AI);
    }

    private static List<String> titlesOf(GraphResponse response) {
        return response.nodes().stream().map(GraphResponse.Node::title).toList();
    }

    @Test
    @DisplayName("Folder로 진입하면 Folder가 루트가 되고 Source와 그 개념·목적이 함께 나온다")
    void drawsFolderGraph() {
        KnowledgeSource source = givenSource("Spring AI MCP Reference");
        KnowledgeNode topic = givenNode(NodeType.TOPIC, "MCP 서버 구현하기");
        KnowledgeNode mcp = givenNode(NodeType.SUBJECT, "MCP");
        digest(source, topic, mcp);

        GraphResponse response = useCase.ofFolder(USER_ID, FOLDER_ID, 20);

        assertThat(response.root())
                .as("Folder는 knowledge_node의 행이 아니라 nodeId가 없다")
                .isEqualTo(new GraphResponse.Root(
                        null, GraphResponse.RootType.FOLDER, FOLDER_ID, "Spring AI 공부"
                ));
        assertThat(titlesOf(response))
                .containsExactlyInAnyOrder("Spring AI MCP Reference", "MCP 서버 구현하기", "MCP");
        assertThat(response.edges())
                .as("Folder → Source 간선은 넣지 않는다. 루트에 달린 SOURCE 노드가 곧 소속이다")
                .extracting(GraphResponse.Edge::from, GraphResponse.Edge::to, GraphResponse.Edge::kind)
                .containsExactlyInAnyOrder(
                        tuple(source.getId(), mcp.getId(), RelationType.ABOUT),
                        tuple(source.getId(), topic.getId(), RelationType.SUPPORTS),
                        tuple(topic.getId(), mcp.getId(), RelationType.INVOLVES)
                );
        assertThat(response.truncated()).isFalse();
        assertThat(response.nodes())
                .as("노드마다 그래프에 생긴 시각을 함께 준다")
                .extracting(GraphResponse.Node::createdAt)
                .doesNotContainNull();
    }

    @Test
    @DisplayName("소화가 끝나지 않은 Source는 간선 없이 노드로만 나온다")
    void drawsUndigestedSourceAlone() {
        KnowledgeSource source = sources.save(
                KnowledgeSource.create(USER_ID, FOLDER_ID, "아직", "https://a.com/1", "https://a.com/1")
        );
        nodes.save(source.getNode());

        GraphResponse response = useCase.ofFolder(USER_ID, FOLDER_ID, 20);

        assertThat(titlesOf(response)).containsExactly("아직");
        assertThat(response.edges()).isEmpty();
    }

    @Test
    @DisplayName("상한에 걸리면 최근 Source만 남기고 잘렸다고 알린다")
    void truncatesFolderGraph() {
        givenSource("첫째");
        givenSource("둘째");
        givenSource("셋째");

        GraphResponse response = useCase.ofFolder(USER_ID, FOLDER_ID, 2);

        assertThat(titlesOf(response)).containsExactly("셋째", "둘째");
        assertThat(response.truncated()).isTrue();
    }

    @Test
    @DisplayName("상한과 Source 수가 같으면 잘리지 않았다고 알린다")
    void doesNotTruncateOnExactLimit() {
        givenSource("첫째");
        givenSource("둘째");

        assertThat(useCase.ofFolder(USER_ID, FOLDER_ID, 2).truncated()).isFalse();
    }

    @Test
    @DisplayName("남의 Folder는 그래프를 그릴 수 없다")
    void rejectsOtherUsersFolder() {
        doThrow(new BusinessException(ErrorCode.FOLDER_NOT_FOUND))
                .when(folderService).getReference(OTHER_USER_ID, FOLDER_ID);

        assertThatThrownBy(() -> useCase.ofFolder(OTHER_USER_ID, FOLDER_ID, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FOLDER_NOT_FOUND);
    }

    @Test
    @DisplayName("Subject를 누르면 그 개념을 다루는 문서와 걸리는 목적이 1-hop으로 나온다")
    void expandsSubjectOneHop() {
        KnowledgeNode mcp = givenNode(NodeType.SUBJECT, "MCP");
        KnowledgeNode topic = givenNode(NodeType.TOPIC, "MCP 서버 구현하기");
        digest(givenSource("문서"), topic, mcp);

        GraphResponse response = useCase.ofNode(USER_ID, mcp.getId(), 1);

        assertThat(response.root())
                .isEqualTo(new GraphResponse.Root(
                        mcp.getId(), GraphResponse.RootType.SUBJECT, null, "MCP"
                ));
        assertThat(titlesOf(response)).containsExactlyInAnyOrder("MCP", "문서", "MCP 서버 구현하기");
        assertThat(response.truncated())
                .as("노드 확장에는 상한이 없다")
                .isFalse();
    }

    @Test
    @DisplayName("Subject의 2-hop은 걸린 목적을 거쳐도 문서 밖으로 나가지 않는다")
    void expandsSubjectTwoHops() {
        KnowledgeNode mcp = givenNode(NodeType.SUBJECT, "MCP");
        KnowledgeNode toolCalling = givenNode(NodeType.SUBJECT, "Tool Calling");
        KnowledgeNode topic = givenNode(NodeType.TOPIC, "MCP 서버 구현하기");

        digest(givenSource("문서"), topic, mcp, toolCalling);
        digest(givenSource("이웃 문서"), givenNode(NodeType.TOPIC, "Tool 붙이기"), toolCalling);

        assertThat(titlesOf(useCase.ofNode(USER_ID, mcp.getId(), 2)))
                .as("Subject → Topic → Source가 유일한 경로다. Topic은 문서 하나에만 걸리므로"
                        + " 1-hop에서 이미 만난 문서로 돌아오고, 개념을 건너뛰어 넓히지 않는다")
                .containsExactlyInAnyOrder("MCP", "문서", "MCP 서버 구현하기");
    }

    @Test
    @DisplayName("Topic의 2-hop은 걸치는 개념을 거쳐 다른 문서까지 넓힌다")
    void expandsTopicTwoHops() {
        KnowledgeNode mcp = givenNode(NodeType.SUBJECT, "MCP");
        KnowledgeNode toolCalling = givenNode(NodeType.SUBJECT, "Tool Calling");
        KnowledgeNode topic = givenNode(NodeType.TOPIC, "MCP 서버 구현하기");

        digest(givenSource("문서"), topic, mcp, toolCalling);
        digest(givenSource("이웃 문서"), givenNode(NodeType.TOPIC, "Tool 붙이기"), toolCalling);

        assertThat(titlesOf(useCase.ofNode(USER_ID, topic.getId(), 1)))
                .as("1-hop은 걸치는 개념까지만 본다")
                .containsExactlyInAnyOrder("MCP 서버 구현하기", "문서", "MCP", "Tool Calling");

        assertThat(titlesOf(useCase.ofNode(USER_ID, topic.getId(), 2)))
                .as("Topic → Subject → Source로 한 겹 더 간다")
                .containsExactlyInAnyOrder(
                        "MCP 서버 구현하기", "문서", "MCP", "Tool Calling", "이웃 문서"
                );
    }

    @Test
    @DisplayName("Topic을 누르면 설명하는 문서와 걸치는 개념이 나온다")
    void expandsTopic() {
        KnowledgeNode topic = givenNode(NodeType.TOPIC, "MCP 서버 구현하기");
        KnowledgeNode mcp = givenNode(NodeType.SUBJECT, "MCP");
        KnowledgeSource source = givenSource("문서");
        digest(source, topic, mcp);

        GraphResponse response = useCase.ofNode(USER_ID, topic.getId(), 1);

        assertThat(titlesOf(response)).containsExactlyInAnyOrder("MCP 서버 구현하기", "문서", "MCP");
        assertThat(response.edges())
                .extracting(GraphResponse.Edge::from, GraphResponse.Edge::to, GraphResponse.Edge::kind)
                .containsExactlyInAnyOrder(
                        tuple(source.getId(), topic.getId(), RelationType.SUPPORTS),
                        tuple(topic.getId(), mcp.getId(), RelationType.INVOLVES)
                );
    }

    @Test
    @DisplayName("Graph에서는 Source 노드도 펼칠 수 있다")
    void expandsSource() {
        KnowledgeSource source = givenSource("문서");
        digest(source, givenNode(NodeType.TOPIC, "MCP 서버 구현하기"), givenNode(NodeType.SUBJECT, "MCP"));

        GraphResponse response = useCase.ofNode(USER_ID, source.getId(), 1);

        assertThat(response.root().type()).isEqualTo(GraphResponse.RootType.SOURCE);
        assertThat(titlesOf(response)).containsExactlyInAnyOrder("문서", "MCP 서버 구현하기", "MCP");
    }

    @Test
    @DisplayName("1과 2가 아닌 확장 단계는 거부한다")
    void rejectsUnsupportedDepth() {
        KnowledgeNode mcp = givenNode(NodeType.SUBJECT, "MCP");

        for (int depth : new int[]{0, 3, -1}) {
            assertThatThrownBy(() -> useCase.ofNode(USER_ID, mcp.getId(), depth))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_GRAPH_DEPTH);
        }
    }

    @Test
    @DisplayName("없는 노드와 남의 노드는 구분하지 않고 404를 준다")
    void rejectsUnknownAndOtherUsersNode() {
        KnowledgeNode mine = givenNode(NodeType.SUBJECT, "MCP");

        assertThatThrownBy(() -> useCase.ofNode(USER_ID, UUID.randomUUID(), 1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.KNOWLEDGE_NODE_NOT_FOUND);

        assertThatThrownBy(() -> useCase.ofNode(OTHER_USER_ID, mine.getId(), 1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.KNOWLEDGE_NODE_NOT_FOUND);
    }
}

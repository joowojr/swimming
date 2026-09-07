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
import com.swimming.backend.knowledge.dto.in.SourceResponse;
import com.swimming.backend.knowledge.dto.in.SourceDeleteResponse;
import com.swimming.backend.knowledge.repository.InMemoryKnowledgeRepositories;
import com.swimming.backend.knowledge.service.KnowledgeGraphAssembler;
import com.swimming.backend.knowledge.service.KnowledgeNodeService;
import com.swimming.backend.knowledge.service.KnowledgeRelationService;
import com.swimming.backend.knowledge.service.KnowledgeSourceService;
import com.swimming.backend.knowledge.service.SourceConceptReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceDeleteUseCaseTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long FOLDER_ID = 10L;

    private InMemoryKnowledgeRepositories.Sources sources;
    private InMemoryKnowledgeRepositories.Nodes nodes;
    private InMemoryKnowledgeRepositories.Relations relations;

    private KnowledgeRelationService relationService;
    private FolderService folderService;

    private SourceDeleteUseCase useCase;
    private SourceListUseCase listUseCase;
    private SourceDetailUseCase detailUseCase;
    private NodeDetailUseCase nodeDetailUseCase;
    private KnowledgeGraphUseCase graphUseCase;

    @BeforeEach
    void setUp() {
        sources = new InMemoryKnowledgeRepositories.Sources();
        nodes = new InMemoryKnowledgeRepositories.Nodes();
        relations = new InMemoryKnowledgeRepositories.Relations();

        relationService = new KnowledgeRelationService(relations);

        KnowledgeNodeService nodeService = new KnowledgeNodeService(nodes);
        KnowledgeSourceService sourceService = new KnowledgeSourceService(sources);
        SourceConceptReader conceptReader = new SourceConceptReader(relations, nodes);

        folderService = mock(FolderService.class);
        when(folderService.getReference(USER_ID, FOLDER_ID))
                .thenReturn(new FolderReference(FOLDER_ID, "Spring AI 공부", "설명"));

        useCase = new SourceDeleteUseCase(sourceService, nodeService, relationService, folderService);
        listUseCase = new SourceListUseCase(folderService, sourceService, conceptReader);
        detailUseCase = new SourceDetailUseCase(sourceService, conceptReader);
        nodeDetailUseCase = new NodeDetailUseCase(nodeService, relationService, sourceService);
        graphUseCase = new KnowledgeGraphUseCase(
                folderService,
                sourceService,
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

    private List<String> listedTitles() {
        return listUseCase.list(USER_ID, FOLDER_ID, null, 20, null).items().stream()
                .map(SourceResponse::title)
                .toList();
    }

    @Test
    @DisplayName("지운 링크는 목록에도 상세에도 그래프에도 나오지 않는다")
    void hidesDeletedSource() {
        KnowledgeNode mcp = givenNode(NodeType.SUBJECT, "MCP");
        KnowledgeSource kept = givenSource("남길 문서");
        KnowledgeSource removed = givenSource("지울 문서");
        digest(kept, givenNode(NodeType.TOPIC, "남길 목적"), mcp);
        digest(removed, givenNode(NodeType.TOPIC, "지울 목적"), mcp);

        useCase.delete(USER_ID, removed.getId());

        assertThat(listedTitles()).containsExactly("남길 문서");

        assertThatThrownBy(() -> detailUseCase.get(USER_ID, removed.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.KNOWLEDGE_NODE_NOT_FOUND);

        assertThat(graphUseCase.ofFolder(USER_ID, FOLDER_ID, 20).nodes())
                .extracting(GraphResponse.Node::title)
                .containsExactlyInAnyOrder("남길 문서", "남길 목적", "MCP");
    }

    @Test
    @DisplayName("딸린 목적도 함께 사라진다")
    void deletesTopicWithSource() {
        // 문서 0개짜리 Topic을 남기면 sources가 항상 하나라는 계약이 깨진다.
        KnowledgeSource source = givenSource("문서");
        KnowledgeNode topic = givenNode(NodeType.TOPIC, "MCP 서버 구현하기");
        digest(source, topic, givenNode(NodeType.SUBJECT, "MCP"));

        useCase.delete(USER_ID, source.getId());

        assertThatThrownBy(() -> nodeDetailUseCase.get(USER_ID, topic.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.KNOWLEDGE_NODE_NOT_FOUND);
    }

    @Test
    @DisplayName("개념은 남는다. 다른 문서가 여전히 쓴다")
    void keepsSubject() {
        KnowledgeNode mcp = givenNode(NodeType.SUBJECT, "MCP");
        KnowledgeSource kept = givenSource("남길 문서");
        KnowledgeSource removed = givenSource("지울 문서");
        digest(kept, givenNode(NodeType.TOPIC, "남길 목적"), mcp);
        digest(removed, givenNode(NodeType.TOPIC, "지울 목적"), mcp);

        useCase.delete(USER_ID, removed.getId());

        assertThat(nodeDetailUseCase.get(USER_ID, mcp.getId()).sources())
                .extracting(source -> source.title())
                .containsExactly("남길 문서");
    }

    @Test
    @DisplayName("관계 행은 지우지 않는다. 조회가 지운 노드를 거를 뿐이다")
    void keepsRelationRows() {
        KnowledgeSource source = givenSource("문서");
        digest(source, givenNode(NodeType.TOPIC, "목적"), givenNode(NodeType.SUBJECT, "MCP"));

        useCase.delete(USER_ID, source.getId());

        assertThat(relations.findAllByFromNodeIdIn(
                List.of(source.getId()), List.of(RelationType.values())
        )).hasSize(2);
    }

    @Test
    @DisplayName("마지막 링크를 지우면 폴더의 링크 표시를 끈다")
    void clearsFolderFlagOnLastSource() {
        KnowledgeSource source = givenSource("문서");
        digest(source, givenNode(NodeType.TOPIC, "목적"), givenNode(NodeType.SUBJECT, "MCP"));

        SourceDeleteResponse response = useCase.delete(USER_ID, source.getId());

        verify(folderService).updateHasSource(USER_ID, FOLDER_ID, false);
        assertThat(response).isEqualTo(new SourceDeleteResponse(FOLDER_ID, false));
    }

    @Test
    @DisplayName("링크가 남아 있으면 폴더 표시를 켠 채로 둔다")
    void keepsFolderFlagWhileSourcesRemain() {
        // 켜진 채로 둬야 링크가 남은 폴더의 삭제를 계속 막는다.
        givenSource("남길 문서");
        KnowledgeSource removed = givenSource("지울 문서");
        digest(removed, givenNode(NodeType.TOPIC, "목적"), givenNode(NodeType.SUBJECT, "MCP"));

        SourceDeleteResponse response = useCase.delete(USER_ID, removed.getId());

        verify(folderService).updateHasSource(USER_ID, FOLDER_ID, true);
        assertThat(response).isEqualTo(new SourceDeleteResponse(FOLDER_ID, true));
    }

    @Test
    @DisplayName("없는 · 이미 지운 · 남의 링크는 지울 수 없다")
    void rejectsDeletedAndOtherUsersSource() {
        KnowledgeSource source = givenSource("문서");
        digest(source, givenNode(NodeType.TOPIC, "목적"), givenNode(NodeType.SUBJECT, "MCP"));

        useCase.delete(USER_ID, source.getId());

        assertThatThrownBy(() -> useCase.delete(USER_ID, source.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.KNOWLEDGE_NODE_NOT_FOUND);

        assertThatThrownBy(() -> useCase.delete(OTHER_USER_ID, givenSource("남의 것").getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.KNOWLEDGE_NODE_NOT_FOUND);

        assertThatThrownBy(() -> useCase.delete(USER_ID, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.KNOWLEDGE_NODE_NOT_FOUND);
    }
}

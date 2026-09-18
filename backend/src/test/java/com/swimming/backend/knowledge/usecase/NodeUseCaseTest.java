package com.swimming.backend.knowledge.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeRelation;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.domain.RelationOrigin;
import com.swimming.backend.knowledge.domain.RelationType;
import com.swimming.backend.knowledge.dto.in.NodeDetailResponse;
import com.swimming.backend.knowledge.dto.in.NodeRef;
import com.swimming.backend.knowledge.repository.InMemoryKnowledgeRepositories;
import com.swimming.backend.knowledge.service.data.KnowledgeNodeService;
import com.swimming.backend.knowledge.service.data.KnowledgeRelationService;
import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.knowledge.service.SourceGraphReader;
import com.swimming.backend.knowledge.service.data.KnowledgeSourceService;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class NodeUseCaseTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long FOLDER_ID = 10L;

    private InMemoryKnowledgeRepositories.Sources sources;
    private InMemoryKnowledgeRepositories.Nodes nodes;
    private InMemoryKnowledgeRepositories.Relations relations;

    private KnowledgeRelationService relationService;

    private NodeUseCase useCase;
    private FolderService folderService;

    /** 개념을 지운 뒤 문서 카드에서 이름이 사라졌는지 확인할 때 쓴다. */
    private SourceQueryUseCase sourceQueryUseCase;

    @BeforeEach
    void setUp() {
        sources = new InMemoryKnowledgeRepositories.Sources();
        relations = new InMemoryKnowledgeRepositories.Relations();
        nodes = new InMemoryKnowledgeRepositories.Nodes().withFolderGraph(sources, relations);
        folderService = mock(FolderService.class);

        relationService = new KnowledgeRelationService(relations);

        KnowledgeNodeService nodeService = new KnowledgeNodeService(nodes);
        KnowledgeSourceService sourceService = new KnowledgeSourceService(sources, org.mockito.Mockito.mock(com.swimming.backend.folder.service.FolderService.class));

        useCase = new NodeUseCase(nodeService, relationService, sourceService, folderService);
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
    @DisplayName("카테고리와 목적 이름 수정")
    class TitleUpdateTest {
        @Test
        @DisplayName("Topic 이름 수정은 공백을 제거하고 문서와 상세 응답에 반영하며 관계를 유지한다")
        void updatesTopicTitle() {
            KnowledgeSource source = givenSource("문서");
            KnowledgeNode topic = givenNode(NodeType.TOPIC, "기존 목적");
            KnowledgeNode subject = givenNode(NodeType.SUBJECT, "개념");
            digest(source, topic, subject);
            assertThat(useCase.updateTitle(USER_ID, topic.getId(), "  새 목적  "))
                    .isEqualTo(new NodeRef(topic.getId(), "새 목적"));
            assertThat(nodes.findById(topic.getId()).orElseThrow().getNormalizedTitle()).isEqualTo("새목적");
            assertThat(sourceQueryUseCase.get(USER_ID, source.getId()).topic().title()).isEqualTo("새 목적");
            assertThat(useCase.get(USER_ID, topic.getId()).subjects()).containsExactly(NodeRef.from(subject));
        }

        @Test
        @DisplayName("Category 이름 수정은 포함된 문서 카드에도 반영한다")
        void updatesCategoryTitle() {
            KnowledgeSource source = givenSource("문서");
            KnowledgeNode category = givenNode(NodeType.CATEGORY, "기존 분류");
            relationService.connect(category, source.getNode(), RelationOrigin.USER);
            useCase.updateTitle(USER_ID, category.getId(), "새 분류");
            assertThat(sourceQueryUseCase.get(USER_ID, source.getId()).category())
                    .isEqualTo(new NodeRef(category.getId(), "새 분류"));
        }

        @Test
        @DisplayName("같은 폴더에 같은 이름의 Category가 있으면 그쪽으로 합치고 원래 것은 지운다")
        void mergesIntoDuplicateCategoryInFolder() {
            KnowledgeSource mine = givenSource("옮겨갈 문서");
            KnowledgeSource theirs = givenSource("이미 담긴 문서");
            KnowledgeNode category = givenNode(NodeType.CATEGORY, "원래 이름");
            KnowledgeNode other = givenNode(NodeType.CATEGORY, "API 설계");
            relationService.connect(category, mine.getNode(), RelationOrigin.USER);
            relationService.connect(other, theirs.getNode(), RelationOrigin.USER);

            NodeRef merged = useCase.updateTitle(USER_ID, category.getId(), "api_설계");

            // 돌려주는 것은 살아남은 쪽이다. 요청한 id와 다르다.
            assertThat(merged.nodeId()).isEqualTo(other.getId());
            assertThat(merged.title()).isEqualTo("API 설계");
            // findById는 지운 노드를 걸러낸다. 비어 있다는 것이 곧 지워졌다는 뜻이다.
            assertThat(nodes.findById(category.getId())).isEmpty();
            assertThat(nodes.findById(other.getId()).orElseThrow().getTitle()).isEqualTo("API 설계");
        }

        @Test
        @DisplayName("합칠 때 담고 있던 문서를 옮기고 원래 묶음의 간선은 지운다")
        void movesContainedSourcesOnMerge() {
            KnowledgeSource mine = givenSource("옮겨갈 문서");
            KnowledgeSource theirs = givenSource("이미 담긴 문서");
            KnowledgeNode category = givenNode(NodeType.CATEGORY, "원래 이름");
            KnowledgeNode other = givenNode(NodeType.CATEGORY, "API 설계");
            relationService.connect(category, mine.getNode(), RelationOrigin.USER);
            relationService.connect(other, theirs.getNode(), RelationOrigin.USER);

            useCase.updateTitle(USER_ID, category.getId(), "API 설계");

            assertThat(relationService.findOutgoing(List.of(other.getId()), List.of(RelationType.CONTAINS)))
                    .extracting(KnowledgeRelation::getToNodeId)
                    .containsExactlyInAnyOrder(mine.getId(), theirs.getId());
            // 간선을 남기지 않는다. 노드만 지우면 조회가 걸러 줄 뿐 행은 남는다.
            assertThat(relationService.findOutgoing(List.of(category.getId()), List.of(RelationType.CONTAINS)))
                    .isEmpty();
        }

        @Test
        @DisplayName("담긴 문서가 없는 Category는 비교할 폴더가 없어 그대로 이름만 바뀐다")
        void renamesEmptyCategoryWithoutMerge() {
            KnowledgeNode category = givenNode(NodeType.CATEGORY, "원래 이름");

            NodeRef renamed = useCase.updateTitle(USER_ID, category.getId(), "새 이름");

            assertThat(renamed.nodeId()).isEqualTo(category.getId());
            assertThat(nodes.findById(category.getId()).orElseThrow().getTitle()).isEqualTo("새 이름");
        }

        @Test
        @DisplayName("Category 이름 수정은 Replace·소화 중 배정과 같은 폴더 잠금을 잡는다")
        void locksFolderWhenRenamingCategory() {
            KnowledgeSource source = givenSource("문서");
            KnowledgeNode category = givenNode(NodeType.CATEGORY, "기존 분류");
            relationService.connect(category, source.getNode(), RelationOrigin.USER);

            useCase.updateTitle(USER_ID, category.getId(), "새 분류");

            verify(folderService).lockOwned(USER_ID, FOLDER_ID);
        }

        @Test
        @DisplayName("담긴 문서가 없는 Category는 어느 폴더에도 보이지 않으므로 잠금 없이 이름을 바꾼다")
        void renamesCategoryWithoutSources() {
            KnowledgeNode category = givenNode(NodeType.CATEGORY, "기존 분류");

            assertThat(useCase.updateTitle(USER_ID, category.getId(), "새 분류").title()).isEqualTo("새 분류");
            verify(folderService, never()).lockOwned(any(), any());
        }

        @Test
        @DisplayName("다른 폴더와 삭제된 카테고리의 이름 및 자기 이름은 중복으로 거절하지 않는다")
        void allowsOtherFolderDeletedAndOwnTitle() {
            KnowledgeSource source = givenSource("문서");
            KnowledgeSource otherSource = sources.save(KnowledgeSource.create(USER_ID, 20L, "다른 문서", "https://b.com", "https://b.com"));
            nodes.create(otherSource.getNode());
            KnowledgeNode category = givenNode(NodeType.CATEGORY, "API 설계");
            KnowledgeNode other = givenNode(NodeType.CATEGORY, "API 설계");
            KnowledgeNode deleted = givenNode(NodeType.CATEGORY, "API 설계");
            relationService.connect(category, source.getNode(), RelationOrigin.USER);
            relationService.connect(other, otherSource.getNode(), RelationOrigin.USER);
            relationService.connect(deleted, source.getNode(), RelationOrigin.USER);
            deleted.delete();
            nodes.delete(deleted);
            assertThat(useCase.updateTitle(USER_ID, category.getId(), "api_설계").title()).isEqualTo("api_설계");
        }

        @Test
        @DisplayName("Topic은 문서별로 독립적이라 같은 이름을 허용한다")
        void allowsDuplicateTopicTitles() {
            givenNode(NodeType.TOPIC, "같은 목적");
            KnowledgeNode topic = givenNode(NodeType.TOPIC, "원래 목적");
            assertThat(useCase.updateTitle(USER_ID, topic.getId(), "같은 목적").title()).isEqualTo("같은 목적");
        }

        @Test
        @DisplayName("Subject와 Source는 이름 수정 대상이 아니다")
        void rejectsUnsupportedTypes() {
            for (NodeType type : java.util.List.of(NodeType.SUBJECT, NodeType.SOURCE)) {
                KnowledgeNode node = givenNode(type, "원래 이름");
                assertThatThrownBy(() -> useCase.updateTitle(USER_ID, node.getId(), "수정"))
                        .isInstanceOf(BusinessException.class)
                        .hasMessage(ErrorCode.KNOWLEDGE_NODE_TITLE_NOT_EDITABLE.getMessage());
                assertThat(nodes.findById(node.getId()).orElseThrow().getTitle()).isEqualTo("원래 이름");
            }
        }

        @Test
        @DisplayName("남의 노드와 삭제된 노드 및 없는 노드는 수정할 수 없다")
        void rejectsUnownedDeletedAndMissingNodes() {
            KnowledgeNode other = nodes.create(KnowledgeNode.create(OTHER_USER_ID, NodeType.TOPIC, "목적", null));
            KnowledgeNode deleted = givenNode(NodeType.CATEGORY, "분류");
            deleted.delete();
            nodes.delete(deleted);
            for (UUID id : java.util.List.of(other.getId(), deleted.getId(), UUID.randomUUID())) {
                assertThatThrownBy(() -> useCase.updateTitle(USER_ID, id, "수정"))
                        .isInstanceOf(BusinessException.class)
                        .hasMessage(ErrorCode.KNOWLEDGE_NODE_NOT_FOUND.getMessage());
            }
        }
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
        @DisplayName("Category는 그 묶음에 담긴 문서를 모으고 다른 묶음의 문서는 넣지 않는다")
        void describesCategory() {
            KnowledgeNode category = givenNode(NodeType.CATEGORY, "MCP 서버 구현");
            KnowledgeNode other = givenNode(NodeType.CATEGORY, "WAL 정리");
            KnowledgeSource first = givenSource("첫 문서");
            KnowledgeSource second = givenSource("둘째 문서");
            relationService.connect(category, first.getNode(), RelationOrigin.USER);
            relationService.connect(category, second.getNode(), RelationOrigin.AI);
            relationService.connect(other, givenSource("다른 문서").getNode(), RelationOrigin.USER);

            NodeDetailResponse response = useCase.get(USER_ID, category.getId());

            assertThat(response.type()).isEqualTo(NodeType.CATEGORY);
            assertThat(response.sources())
                    .extracting(NodeDetailResponse.SourceRef::title)
                    .containsExactlyInAnyOrder("첫 문서", "둘째 문서");
            assertThat(response.topics()).isEmpty();
            assertThat(response.subjects()).isEmpty();
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
        @DisplayName("묶음은 혼자 지울 수 없다. 폴더의 묶음 구성을 다시 정해야 사라진다")
        void rejectsCategory() {
            KnowledgeSource source = givenSource("문서");
            KnowledgeNode category = givenNode(NodeType.CATEGORY, "MCP 서버 구현");
            relationService.connect(category, source.getNode(), RelationOrigin.USER);

            assertThatThrownBy(() -> useCase.delete(USER_ID, category.getId()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.KNOWLEDGE_CATEGORY_NOT_DELETABLE);

            assertThat(sourceQueryUseCase.get(USER_ID, source.getId()).category())
                    .isEqualTo(new NodeRef(category.getId(), "MCP 서버 구현"));
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

package com.swimming.backend.knowledge.usecase;

import com.swimming.backend.common.dto.CursorPage;
import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.domain.RelationOrigin;
import com.swimming.backend.knowledge.domain.SourceSearchOperator;
import com.swimming.backend.knowledge.dto.in.SourceResponse;
import com.swimming.backend.knowledge.repository.InMemoryKnowledgeRepositories;
import com.swimming.backend.knowledge.service.data.KnowledgeRelationService;
import com.swimming.backend.knowledge.service.data.KnowledgeSourceService;
import com.swimming.backend.knowledge.service.SourceGraphReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class SourceSearchUseCaseTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long FOLDER_ID = 10L;
    private static final Long OTHER_FOLDER_ID = 11L;

    private InMemoryKnowledgeRepositories.Sources sources;
    private InMemoryKnowledgeRepositories.Nodes nodes;
    private KnowledgeRelationService relationService;
    private FolderService folderService;
    private SourceSearchUseCase useCase;

    @BeforeEach
    void setUp() {
        sources = new InMemoryKnowledgeRepositories.Sources();
        nodes = new InMemoryKnowledgeRepositories.Nodes();
        InMemoryKnowledgeRepositories.Relations relations = new InMemoryKnowledgeRepositories.Relations();
        relationService = new KnowledgeRelationService(relations);
        folderService = mock(FolderService.class);
        useCase = new SourceSearchUseCase(
                folderService,
                relationService,
                new KnowledgeSourceService(sources),
                new SourceGraphReader(relations, nodes)
        );
    }

    private KnowledgeSource source(Long userId, Long folderId, String title) {
        KnowledgeSource source = KnowledgeSource.create(
                userId, folderId, title, "https://a.com/" + title, "https://a.com/" + title
        );
        source.applyExtractedDocument(title, "본문", "article", null, null);
        return sources.save(source);
    }

    private KnowledgeNode node(NodeType type, String title) {
        return nodes.save(KnowledgeNode.create(USER_ID, type, title, null));
    }

    private List<String> search(
            List<UUID> subjectIds, UUID topicId, SourceSearchOperator operator, Long folderId
    ) {
        return useCase.search(USER_ID, subjectIds, topicId, operator, folderId, 20, null)
                .items().stream().map(SourceResponse::title).toList();
    }

    @Test
    @DisplayName("AND는 모든 개념을, OR은 하나 이상의 개념을 다루는 Source를 찾는다")
    void searchesSubjectsWithOperator() {
        KnowledgeNode mcp = node(NodeType.SUBJECT, "MCP");
        KnowledgeNode tool = node(NodeType.SUBJECT, "Tool Calling");
        KnowledgeSource both = source(USER_ID, FOLDER_ID, "둘 다");
        KnowledgeSource one = source(USER_ID, FOLDER_ID, "하나만");
        relationService.connect(both.getNode(), mcp, RelationOrigin.AI);
        relationService.connect(both.getNode(), tool, RelationOrigin.AI);
        relationService.connect(one.getNode(), mcp, RelationOrigin.AI);

        assertThat(search(List.of(mcp.getId(), tool.getId()), null, SourceSearchOperator.AND, null))
                .containsExactly("둘 다");
        assertThat(search(List.of(mcp.getId(), tool.getId()), null, SourceSearchOperator.OR, null))
                .containsExactlyInAnyOrder("둘 다", "하나만");
    }

    @Test
    @DisplayName("Topic과 Subject를 함께 주면 두 관계를 모두 만족하는 Source만 찾는다")
    void combinesTopicAndSubject() {
        KnowledgeNode mcp = node(NodeType.SUBJECT, "MCP");
        KnowledgeNode topic = node(NodeType.TOPIC, "서버 구현");
        KnowledgeSource match = source(USER_ID, FOLDER_ID, "일치");
        KnowledgeSource subjectOnly = source(USER_ID, FOLDER_ID, "개념만");
        relationService.connect(match.getNode(), mcp, RelationOrigin.AI);
        relationService.connect(match.getNode(), topic, RelationOrigin.AI);
        relationService.connect(subjectOnly.getNode(), mcp, RelationOrigin.AI);

        assertThat(search(List.of(mcp.getId()), topic.getId(), SourceSearchOperator.AND, null))
                .containsExactly("일치");
    }

    @Test
    @DisplayName("선택한 폴더와 인증 사용자의 Source만 조회하고 커서로 이어 읽는다")
    void isolatesOwnershipAndPages() {
        KnowledgeNode mcp = node(NodeType.SUBJECT, "MCP");
        KnowledgeSource first = source(USER_ID, FOLDER_ID, "첫째");
        KnowledgeSource second = source(USER_ID, FOLDER_ID, "둘째");
        KnowledgeSource otherFolder = source(USER_ID, OTHER_FOLDER_ID, "다른 폴더");
        KnowledgeSource otherUser = source(OTHER_USER_ID, FOLDER_ID, "남의 것");
        relationService.connect(first.getNode(), mcp, RelationOrigin.AI);
        relationService.connect(second.getNode(), mcp, RelationOrigin.AI);
        relationService.connect(otherFolder.getNode(), mcp, RelationOrigin.AI);
        relationService.connect(otherUser.getNode(), mcp, RelationOrigin.AI);

        CursorPage<SourceResponse> firstPage = useCase.search(
                USER_ID, List.of(mcp.getId()), null, SourceSearchOperator.AND, FOLDER_ID, 1, null
        );
        CursorPage<SourceResponse> secondPage = useCase.search(
                USER_ID, List.of(mcp.getId()), null, SourceSearchOperator.AND,
                FOLDER_ID, 1, firstPage.nextCursor()
        );

        assertThat(firstPage.items()).hasSize(1);
        assertThat(secondPage.items()).hasSize(1);
        assertThat(List.of(firstPage.items().getFirst().title(), secondPage.items().getFirst().title()))
                .containsExactlyInAnyOrder("첫째", "둘째");
        assertThat(secondPage.hasNext()).isFalse();
    }

    @Test
    @DisplayName("남의 Folder 조건은 조회 전에 거절한다")
    void rejectsOtherUsersFolder() {
        doThrow(new BusinessException(ErrorCode.FOLDER_NOT_FOUND))
                .when(folderService).validateOwnership(USER_ID, FOLDER_ID);

        assertThatThrownBy(() -> search(List.of(), null, SourceSearchOperator.AND, FOLDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FOLDER_NOT_FOUND);
    }
}

package com.swimming.backend.knowledge.usecase;

import com.swimming.backend.common.dto.CursorPage;
import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.domain.RelationOrigin;
import com.swimming.backend.knowledge.domain.SourceProcessingStatus;
import com.swimming.backend.knowledge.dto.in.NodeRef;
import com.swimming.backend.knowledge.dto.in.SourceDetailResponse;
import com.swimming.backend.knowledge.dto.in.SourceResponse;
import com.swimming.backend.knowledge.repository.InMemoryKnowledgeRepositories;
import com.swimming.backend.knowledge.service.SourceGraphReader;
import com.swimming.backend.knowledge.service.data.KnowledgeRelationService;
import com.swimming.backend.knowledge.service.data.KnowledgeSourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class SourceQueryUseCaseTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long FOLDER_ID = 10L;
    private static final Long OTHER_FOLDER_ID = 11L;
    private static final Instant PUBLISHED_AT = Instant.parse("2026-03-01T00:00:00Z");

    private InMemoryKnowledgeRepositories.Sources sources;
    private InMemoryKnowledgeRepositories.Nodes nodes;
    private InMemoryKnowledgeRepositories.Relations relations;

    private FolderService folderService;
    private SourceQueryUseCase useCase;

    @BeforeEach
    void setUp() {
        sources = new InMemoryKnowledgeRepositories.Sources();
        nodes = new InMemoryKnowledgeRepositories.Nodes();
        relations = new InMemoryKnowledgeRepositories.Relations();
        folderService = mock(FolderService.class);

        useCase = new SourceQueryUseCase(
                folderService,
                new KnowledgeSourceService(sources),
                new SourceGraphReader(relations, nodes)
        );
    }

    @Nested
    @DisplayName("목록")
    class ListTest {

        /** 저장 순서가 곧 생성 순서다. 목록은 이 역순으로 나와야 한다. */
        private KnowledgeSource given(Long userId, Long folderId, String title, boolean digested) {
            KnowledgeSource source = KnowledgeSource.create(
                    userId, folderId, title, "https://a.com/" + title, "https://a.com/" + title
            );
            source.applyExtractedDocument(title, "본문", "article", "작성자", null);

            if (digested) {
                source.startDigestion();
                source.completeDigestion(title + " 요약", 1);
            }

            return sources.save(source);
        }

        /** 생성 시각을 못 박아 저장한다. 대역이 시각을 채우기 전에 이미 값이 있으면 그대로 둔다. */
        private void givenAt(Instant createdAt, String title) {
            KnowledgeNode node = KnowledgeNode.restore(
                    UUID.randomUUID(), USER_ID, NodeType.SOURCE, title, null, false, createdAt, createdAt, null
            );

            sources.save(KnowledgeSource.restore(
                    node, FOLDER_ID, "https://a.com/" + title, "https://a.com/" + title,
                    "본문", title + " 요약", "article", "작성자", null,
                    SourceProcessingStatus.COMPLETED, 1, null, false, null
            ));
        }

        private CursorPage<SourceResponse> list(int size, String cursor) {
            return useCase.list(USER_ID, FOLDER_ID, null, size, cursor);
        }

        private List<String> titlesOf(CursorPage<SourceResponse> response) {
            return response.items().stream().map(SourceResponse::title).toList();
        }

        @Test
        @DisplayName("Folder에 저장한 Source를 최근 순으로 돌려준다")
        void listsRecentFirst() {
            given(USER_ID, FOLDER_ID, "첫째", true);
            given(USER_ID, FOLDER_ID, "둘째", true);
            given(USER_ID, FOLDER_ID, "셋째", true);

            assertThat(titlesOf(list(20, null))).containsExactly("셋째", "둘째", "첫째");
        }

        @Test
        @DisplayName("다른 Folder와 다른 사용자의 Source는 섞이지 않는다")
        void isolatesFolderAndUser() {
            given(USER_ID, FOLDER_ID, "내 것", true);
            given(USER_ID, OTHER_FOLDER_ID, "다른 폴더", true);
            given(OTHER_USER_ID, FOLDER_ID, "남의 것", true);

            assertThat(titlesOf(list(20, null))).containsExactly("내 것");
        }

        @Test
        @DisplayName("소화가 끝난 Source는 개념과 목적을 함께 담는다")
        void includesSubjectsAndTopic() {
            KnowledgeSource source = given(USER_ID, FOLDER_ID, "문서", true);

            KnowledgeNode mcp = nodes.create(KnowledgeNode.create(USER_ID, NodeType.SUBJECT, "MCP", null));
            KnowledgeNode topic = nodes.create(
                    KnowledgeNode.create(USER_ID, NodeType.TOPIC, "MCP 서버 구현하기", null)
            );

            KnowledgeRelationService relationService = new KnowledgeRelationService(relations);
            relationService.connect(source.getNode(), mcp, RelationOrigin.AI);
            relationService.connect(source.getNode(), topic, RelationOrigin.AI);

            SourceResponse card = list(20, null).items().getFirst();

            assertThat(card.summary()).isEqualTo("문서 요약");
            assertThat(card.topic().title()).isEqualTo("MCP 서버 구현하기");
            assertThat(card.subjects()).extracting(NodeRef::title).containsExactly("MCP");
            assertThat(card.domain()).isEqualTo("a.com");
            assertThat(card.createdAt())
                    .as("저장한 시각을 함께 준다")
                    .isEqualTo(source.getNode().getCreatedAt());
        }

        @Test
        @DisplayName("아직 소화되지 않은 Source도 상태만 담아 그대로 내려준다")
        void includesUndigestedSource() {
            given(USER_ID, FOLDER_ID, "아직", false);

            SourceResponse card = list(20, null).items().getFirst();

            assertThat(card.status()).isEqualTo(SourceProcessingStatus.PENDING);
            assertThat(card.summary()).isNull();
            assertThat(card.topic()).isNull();
            assertThat(card.subjects()).isEmpty();
            assertThat(card.category()).isNull();
        }

        @Test
        void 문서마다_자신의_카테고리를_담고_배정되지_않았으면_null이다() {
            KnowledgeSource first = given(USER_ID, FOLDER_ID, "API 문서", true);
            KnowledgeSource second = given(USER_ID, FOLDER_ID, "DB 문서", true);
            given(USER_ID, FOLDER_ID, "카테고리 없는 문서", true);
            KnowledgeNode api = nodes.create(KnowledgeNode.create(USER_ID, NodeType.CATEGORY, "API 설계", null));
            KnowledgeNode db = nodes.create(KnowledgeNode.create(USER_ID, NodeType.CATEGORY, "데이터 모델", null));
            KnowledgeRelationService service = new KnowledgeRelationService(relations);
            service.connect(api, first.getNode(), RelationOrigin.USER);
            service.connect(db, second.getNode(), RelationOrigin.USER);

            var items = list(20, null).items();
            assertThat(items).filteredOn(item -> item.sourceId().equals(first.getId()))
                    .extracting(SourceResponse::category).containsExactly(new NodeRef(api.getId(), "API 설계"));
            assertThat(items).filteredOn(item -> item.sourceId().equals(second.getId()))
                    .extracting(SourceResponse::category).containsExactly(new NodeRef(db.getId(), "데이터 모델"));
            assertThat(items).filteredOn(item -> item.title().equals("카테고리 없는 문서"))
                    .allSatisfy(item -> assertThat(item.category()).isNull());
        }

        @Test
        void 삭제된_카테고리의_관계가_남아도_카테고리는_null이다() {
            KnowledgeSource source = given(USER_ID, FOLDER_ID, "문서", true);
            KnowledgeNode category = nodes.create(KnowledgeNode.create(USER_ID, NodeType.CATEGORY, "이전 카테고리", null));
            new KnowledgeRelationService(relations).connect(category, source.getNode(), RelationOrigin.USER);
            category.delete();
            nodes.delete(category);

            assertThat(list(20, null).items().getFirst().category()).isNull();
        }

        @Test
        void 교체된_카테고리는_목록과_상세에서_같은_값을_반환한다() {
            KnowledgeSource source = given(USER_ID, FOLDER_ID, "문서", true);
            KnowledgeNode previous = nodes.create(KnowledgeNode.create(USER_ID, NodeType.CATEGORY, "이전 카테고리", null));
            KnowledgeNode current = nodes.create(KnowledgeNode.create(USER_ID, NodeType.CATEGORY, "현재 카테고리", null));
            KnowledgeRelationService service = new KnowledgeRelationService(relations);
            service.connect(previous, source.getNode(), RelationOrigin.USER);
            service.connect(current, source.getNode(), RelationOrigin.USER);
            previous.delete();
            nodes.delete(previous);

            NodeRef expected = new NodeRef(current.getId(), "현재 카테고리");
            assertThat(list(20, null).items().getFirst().category()).isEqualTo(expected);
            assertThat(useCase.get(USER_ID, source.getId()).category()).isEqualTo(expected);
        }

        @Test
        @DisplayName("상태로 걸러낸다")
        void filtersByStatus() {
            given(USER_ID, FOLDER_ID, "끝난 것", true);
            given(USER_ID, FOLDER_ID, "아직", false);

            CursorPage<SourceResponse> completed =
                    useCase.list(USER_ID, FOLDER_ID, SourceProcessingStatus.COMPLETED, 20, null);

            assertThat(titlesOf(completed)).containsExactly("끝난 것");
        }

        @Test
        @DisplayName("커서로 다음 페이지를 이어 읽고 마지막에는 커서를 주지 않는다")
        void pagesWithCursor() {
            given(USER_ID, FOLDER_ID, "첫째", true);
            given(USER_ID, FOLDER_ID, "둘째", true);
            given(USER_ID, FOLDER_ID, "셋째", true);

            CursorPage<SourceResponse> first = list(2, null);
            assertThat(titlesOf(first)).containsExactly("셋째", "둘째");
            assertThat(first.nextCursor()).isNotNull();

            CursorPage<SourceResponse> second = list(2, first.nextCursor());
            assertThat(titlesOf(second)).containsExactly("첫째");
            assertThat(second.nextCursor())
                    .as("남은 것이 없으면 커서를 주지 않는다")
                    .isNull();
        }

        @Test
        @DisplayName("한 번에 저장해 생성 시각이 같아도 페이지 경계에서 빠뜨리지 않는다")
        void pagesThroughSameInstant() {
            // 링크를 한 번에 저장하면 여러 행이 같은 시각을 갖는다. 커서가 시각만 담으면
            // 여기서 행이 겹치거나 빠진다.
            Instant sameMoment = Instant.parse("2026-03-01T00:00:00Z");

            for (int index = 0; index < 5; index++) {
                givenAt(sameMoment, "문서" + index);
            }

            List<String> collected = new ArrayList<>();
            String cursor = null;

            do {
                CursorPage<SourceResponse> page = list(2, cursor);
                collected.addAll(titlesOf(page));
                cursor = page.nextCursor();
            } while (cursor != null);

            assertThat(collected)
                    .hasSize(5)
                    .doesNotHaveDuplicates()
                    .containsExactlyInAnyOrder("문서0", "문서1", "문서2", "문서3", "문서4");
        }

        @Test
        @DisplayName("저장한 것이 없으면 빈 목록을 준다")
        void returnsEmptyList() {
            CursorPage<SourceResponse> response = list(20, null);

            assertThat(response.items()).isEmpty();
            assertThat(response.nextCursor()).isNull();
        }

        @Test
        @DisplayName("망가진 커서는 조회를 거부한다")
        void rejectsBrokenCursor() {
            assertThatThrownBy(() -> list(20, "not-a-cursor"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_KNOWLEDGE_CURSOR);
        }

        @Test
        @DisplayName("남의 Folder는 조회할 수 없다")
        void rejectsOtherUsersFolder() {
            doThrow(new BusinessException(ErrorCode.FOLDER_NOT_FOUND))
                    .when(folderService).validateOwnership(USER_ID, FOLDER_ID);

            assertThatThrownBy(() -> list(20, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FOLDER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("상세")
    class DetailTest {

        private KnowledgeSource givenSource(boolean digested) {
            KnowledgeSource source = KnowledgeSource.create(
                    USER_ID,
                    FOLDER_ID,
                    "임시 제목",
                    "https://docs.spring.io/mcp.html?utm_source=x",
                    "https://docs.spring.io/mcp.html"
            );
            source.applyExtractedDocument(
                    "Spring AI MCP Reference", "본문", "article", "Spring", PUBLISHED_AT
            );

            if (digested) {
                source.startDigestion();
                source.completeDigestion("MCP Server를 구성하는 방법을 설명한다.", 1);
            }

            return sources.save(source);
        }

        @Test
        @DisplayName("Source 하나를 원문 정보까지 붙여 돌려준다")
        void describesSource() {
            KnowledgeSource source = givenSource(true);

            KnowledgeNode mcp = nodes.create(KnowledgeNode.create(USER_ID, NodeType.SUBJECT, "MCP", null));
            KnowledgeNode topic = nodes.create(
                    KnowledgeNode.create(USER_ID, NodeType.TOPIC, "MCP 서버 구현하기", null)
            );

            KnowledgeRelationService relationService = new KnowledgeRelationService(relations);
            relationService.connect(source.getNode(), mcp, RelationOrigin.AI);
            relationService.connect(source.getNode(), topic, RelationOrigin.AI);

            SourceDetailResponse response = useCase.get(USER_ID, source.getId());

            assertThat(response.sourceId()).isEqualTo(source.getId());
            assertThat(response.title()).isEqualTo("Spring AI MCP Reference");
            assertThat(response.url()).isEqualTo("https://docs.spring.io/mcp.html?utm_source=x");
            assertThat(response.canonicalUrl()).isEqualTo("https://docs.spring.io/mcp.html");
            assertThat(response.domain()).isEqualTo("docs.spring.io");
            assertThat(response.sourceType()).isEqualTo("article");
            assertThat(response.author()).isEqualTo("Spring");
            assertThat(response.publishedAt()).isEqualTo(PUBLISHED_AT);
            assertThat(response.folderId()).isEqualTo(FOLDER_ID);
            assertThat(response.createdAt())
                    .as("저장한 시각. 문서가 발행된 시각과 다른 축이다")
                    .isEqualTo(source.getNode().getCreatedAt())
                    .isNotEqualTo(response.publishedAt());
            assertThat(response.status()).isEqualTo(SourceProcessingStatus.COMPLETED);
            assertThat(response.summary()).isEqualTo("MCP Server를 구성하는 방법을 설명한다.");
            assertThat(response.topic().title()).isEqualTo("MCP 서버 구현하기");
            assertThat(response.subjects()).extracting(NodeRef::title).containsExactly("MCP");
        }

        @Test
        @DisplayName("소화되지 않은 Source도 상태만 담아 그대로 내려준다")
        void describesUndigestedSource() {
            KnowledgeSource source = givenSource(false);

            SourceDetailResponse response = useCase.get(USER_ID, source.getId());

            assertThat(response.status()).isEqualTo(SourceProcessingStatus.PENDING);
            assertThat(response.summary()).isNull();
            assertThat(response.topic()).isNull();
            assertThat(response.subjects()).isEmpty();
        }

        @Test
        @DisplayName("없는 Source와 남의 Source는 구분하지 않고 404를 준다")
        void rejectsUnknownAndOtherUsersSource() {
            KnowledgeSource mine = givenSource(true);

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
}

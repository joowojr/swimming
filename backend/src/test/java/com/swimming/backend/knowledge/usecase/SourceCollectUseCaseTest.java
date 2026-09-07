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
import com.swimming.backend.knowledge.domain.SourceProcessingStatus;
import com.swimming.backend.knowledge.dto.in.GraphResponse;
import com.swimming.backend.knowledge.dto.in.NodeRef;
import com.swimming.backend.knowledge.dto.in.SourceCollectRequest;
import com.swimming.backend.knowledge.dto.in.SourceCollectResponse;
import com.swimming.backend.knowledge.dto.in.SourceDeleteResponse;
import com.swimming.backend.knowledge.dto.in.SourceResponse;
import com.swimming.backend.knowledge.dto.out.FetchedDocument;
import com.swimming.backend.knowledge.dto.out.SourceDigestResult;
import com.swimming.backend.knowledge.dto.out.SourceFetchResult;
import com.swimming.backend.knowledge.repository.InMemoryKnowledgeRepositories;
import com.swimming.backend.knowledge.service.SourceGraphReader;
import com.swimming.backend.knowledge.service.crawl.WebFetchService;
import com.swimming.backend.knowledge.service.data.KnowledgeNodeService;
import com.swimming.backend.knowledge.service.data.KnowledgeRelationService;
import com.swimming.backend.knowledge.service.data.KnowledgeSourceService;
import com.swimming.backend.knowledge.service.graph.KnowledgeGraphAssembler;
import com.swimming.backend.knowledge.service.graph.NodeResolver;
import com.swimming.backend.knowledge.service.graph.SourceGraphWriter;
import com.swimming.backend.knowledge.service.llm.SourceDigestProcessor;
import com.swimming.backend.knowledge.service.llm.SourceDigestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceCollectUseCaseTest {

    @Nested
    @DisplayName("수집")
    class CollectTest {

        private static final Long USER_ID = 1L;
        private static final Long FOLDER_ID = 10L;
        private static final Long OTHER_FOLDER_ID = 11L;

        private InMemoryKnowledgeRepositories.Sources sources;
        private InMemoryKnowledgeRepositories.Nodes nodes;
        private InMemoryKnowledgeRepositories.Relations relations;

        private WebFetchService fetchService;
        private SourceDigestService digestService;
        private FolderService folderService;
        private SourceCollectUseCase useCase;

        @BeforeEach
        void setUp() {
            sources = new InMemoryKnowledgeRepositories.Sources();
            nodes = new InMemoryKnowledgeRepositories.Nodes();
            relations = new InMemoryKnowledgeRepositories.Relations();

            fetchService = mock(WebFetchService.class);
            digestService = mock(SourceDigestService.class);
            folderService = mock(FolderService.class);

            KnowledgeSourceService sourceService = new KnowledgeSourceService(sources);
            KnowledgeNodeService nodeService = new KnowledgeNodeService(nodes);

            useCase = new SourceCollectUseCase(
                    fetchService,
                    folderService,
                    sourceService,
                    new SourceDigestProcessor(
                            sourceService,
                            nodeService,
                            digestService,
                            new SourceGraphWriter(
                                    nodeService,
                                    new NodeResolver(nodes),
                                    new KnowledgeRelationService(relations)
                            )
                    ),
                    new SourceGraphReader(relations, nodes)
            );

            givenDigested("MCP 서버 구현하기", "MCP", "Tool Calling");
        }

        private SourceFetchResult success(String url, String canonical, String title) {
            return SourceFetchResult.success(url, new FetchedDocument(
                    url, canonical, title, "작성자",
                    Instant.parse("2026-03-01T00:00:00Z"), "article",
                    "# " + title + "\n\n본문입니다.", false
            ));
        }

        private void givenFetch(SourceFetchResult... results) {
            when(fetchService.fetchAll(anyList())).thenReturn(List.of(results));
        }

        private void givenDigested(String topic, String... subjects) {
            when(digestService.digest(any())).thenReturn(
                    new SourceDigestResult("요약입니다.", "백엔드", topic, List.of(subjects))
            );
        }

        private SourceCollectResponse collect(Long folderId, String... urls) {
            return useCase.collect(USER_ID, folderId, new SourceCollectRequest(List.of(urls)));
        }

        @Test
        @DisplayName("링크 여러 개를 Folder 아래 Source로 저장한다")
        void savesEachLinkUnderFolder() {
            givenFetch(
                    success("https://a.com/1", "https://a.com/1", "첫 문서"),
                    success("https://b.com/2", "https://b.com/2", "둘째 문서")
            );

            SourceCollectResponse response = collect(FOLDER_ID, "https://a.com/1", "https://b.com/2");

            assertThat(response.items()).hasSize(2);
            assertThat(response.items())
                    .allMatch(item -> item.result() == SourceCollectResponse.Result.CREATED);
            assertThat(response.items().getFirst().source().title()).isEqualTo("첫 문서");

            assertThat(sources.findAllByIds(
                    response.items().stream().map(item -> item.source().sourceId()).toList()
            )).allMatch(source -> source.getFolderId().equals(FOLDER_ID));
        }

        @Test
        @DisplayName("저장 한 번으로 소화까지 끝내 카드에 개념과 목적이 담긴다")
        void digestsWithinTheSameCall() {
            givenFetch(success("https://a.com/1", "https://a.com/1", "첫 문서"));

            SourceResponse card = collect(FOLDER_ID, "https://a.com/1").items().getFirst().source();

            assertThat(card.status()).isEqualTo(SourceProcessingStatus.COMPLETED);
            assertThat(card.summary()).isEqualTo("요약입니다.");
            assertThat(card.topic().title()).isEqualTo("MCP 서버 구현하기");
            assertThat(card.subjects())
                    .extracting(NodeRef::title)
                    .containsExactlyInAnyOrder("MCP", "Tool Calling");

            assertThat(card.domain()).isEqualTo("a.com");
            assertThat(card.sourceType()).isEqualTo("article");
        }

        @Test
        @DisplayName("저장한 Source는 문서 본문과 메타데이터를 갖는다")
        void storesDocument() {
            givenFetch(success("https://a.com/1", "https://a.com/1", "첫 문서"));

            UUID sourceId = collect(FOLDER_ID, "https://a.com/1").items().getFirst().source().sourceId();
            var saved = sources.findById(sourceId).orElseThrow();

            assertThat(saved.getNode().getNodeType()).isEqualTo(NodeType.SOURCE);
            assertThat(saved.getNode().getTitle()).isEqualTo("첫 문서");
            assertThat(saved.getFolderId()).isEqualTo(FOLDER_ID);
            assertThat(saved.getContent()).contains("본문입니다");
            assertThat(saved.getAuthor()).isEqualTo("작성자");
        }

        @Test
        @DisplayName("소화에 실패해도 저장은 남는다")
        void keepsSourceWhenDigestionFails() {
            givenFetch(success("https://a.com/1", "https://a.com/1", "첫 문서"));
            when(digestService.digest(any())).thenThrow(new RuntimeException("model timeout"));

            SourceCollectResponse.Item item = collect(FOLDER_ID, "https://a.com/1").items().getFirst();

            assertThat(item.result())
                    .as("링크는 저장했다")
                    .isEqualTo(SourceCollectResponse.Result.CREATED);
            assertThat(item.source().status())
                    .as("소화만 실패했다")
                    .isEqualTo(SourceProcessingStatus.FAILED);
            assertThat(item.source().summary()).isNull();
            assertThat(item.source().subjects()).isEmpty();

            assertThat(sources.findById(item.source().sourceId()).orElseThrow().getContent())
                    .contains("본문입니다");
        }

        @Test
        @DisplayName("한 링크가 실패해도 나머지는 저장한다")
        void isolatesFailures() {
            givenFetch(
                    SourceFetchResult.failure("https://bad.com", SourceFetchResult.Failure.HTTP_ERROR, "404"),
                    success("https://good.com", "https://good.com", "정상 문서")
            );

            SourceCollectResponse response = collect(FOLDER_ID, "https://bad.com", "https://good.com");

            assertThat(response.items().getFirst().result()).isEqualTo(SourceCollectResponse.Result.FAILED);
            assertThat(response.items().getFirst().reason()).isEqualTo(SourceFetchResult.Failure.HTTP_ERROR);
            assertThat(response.items().getFirst().source()).isNull();

            assertThat(response.items().getLast().result()).isEqualTo(SourceCollectResponse.Result.CREATED);
        }

        @Test
        @DisplayName("가져오지 못한 링크는 모델을 부르지 않는다")
        void doesNotDigestFailedFetch() {
            givenFetch(SourceFetchResult.failure(
                    "https://bad.com", SourceFetchResult.Failure.HTTP_ERROR, "404"
            ));

            collect(FOLDER_ID, "https://bad.com");

            verify(digestService, never()).digest(any());
        }

        @Test
        @DisplayName("canonical URL이 같으면 다시 만들지도 다시 소화하지도 않는다")
        void doesNotDuplicateSameDocument() {
            givenFetch(success("https://a.com/1?utm_source=x", "https://a.com/1", "같은 문서"));
            collect(FOLDER_ID, "https://a.com/1?utm_source=x");

            givenFetch(success("https://a.com/1", "https://a.com/1", "같은 문서"));
            SourceCollectResponse second = collect(FOLDER_ID, "https://a.com/1");

            assertThat(second.items().getFirst().result())
                    .isEqualTo(SourceCollectResponse.Result.ALREADY_SAVED);
            assertThat(second.items().getFirst().source())
                    .as("이미 저장한 문서도 카드는 그대로 돌려준다")
                    .isNotNull();

            verify(digestService, times(1)).digest(any());
        }

        @Test
        @DisplayName("다른 Folder에 있는 같은 문서는 이 Folder에 새로 저장한다")
        void savesSameDocumentInAnotherFolder() {
            givenFetch(success("https://a.com/1", "https://a.com/1", "같은 문서"));
            SourceCollectResponse first = collect(FOLDER_ID, "https://a.com/1");

            givenFetch(success("https://a.com/1", "https://a.com/1", "같은 문서"));
            SourceCollectResponse second = collect(OTHER_FOLDER_ID, "https://a.com/1");

            assertThat(second.items().getFirst().result())
                    .as("저쪽 폴더에 있다는 이유로 저장을 막지 않는다")
                    .isEqualTo(SourceCollectResponse.Result.CREATED);
            assertThat(second.items().getFirst().source().sourceId())
                    .isNotEqualTo(first.items().getFirst().source().sourceId());
            assertThat(sources.findById(second.items().getFirst().source().sourceId())
                    .orElseThrow()
                    .getFolderId())
                    .isEqualTo(OTHER_FOLDER_ID);
            assertThat(sources.findById(first.items().getFirst().source().sourceId())
                    .orElseThrow()
                    .getFolderId())
                    .as("처음 저장한 Folder는 그대로 둔다")
                    .isEqualTo(FOLDER_ID);
        }

        @Test
        @DisplayName("링크를 저장하면 폴더에 링크가 있다고 기록한다")
        void marksFolderHasSource() {
            givenFetch(success("https://a.com/1", "https://a.com/1", "첫 문서"));

            collect(FOLDER_ID, "https://a.com/1");

            verify(folderService).updateHasSource(USER_ID, FOLDER_ID, true);
        }

        @Test
        @DisplayName("한 링크도 저장되지 않으면 폴더 표시를 건드리지 않는다")
        void leavesFolderFlagWhenNothingSaved() {
            // 끄면 안 된다. 이 Folder에 전부터 있던 링크까지 없는 것으로 만든다.
            givenFetch(SourceFetchResult.failure("https://a.com/1", SourceFetchResult.Failure.HTTP_ERROR, "404"));

            collect(FOLDER_ID, "https://a.com/1");

            verify(folderService, never()).updateHasSource(anyLong(), anyLong(), anyBoolean());
        }

        @Test
        @DisplayName("남의 Folder에는 저장할 수 없다")
        void rejectsOtherUsersFolder() {
            doThrow(new BusinessException(ErrorCode.FOLDER_NOT_FOUND))
                    .when(folderService).validateOwnership(USER_ID, FOLDER_ID);

            assertThatThrownBy(() -> collect(FOLDER_ID, "https://a.com/1"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FOLDER_NOT_FOUND);

            verify(fetchService, never()).fetchAll(anyList());
        }
    }

    @Nested
    @DisplayName("재소화")
    class RetryTest {

        private static final Long USER_ID = 1L;
        private static final Long FOLDER_ID = 10L;

        private InMemoryKnowledgeRepositories.Sources sources;
        private SourceDigestService digestService;
        private SourceCollectUseCase useCase;

        @BeforeEach
        void setUp() {
            sources = new InMemoryKnowledgeRepositories.Sources();
            InMemoryKnowledgeRepositories.Nodes nodes = new InMemoryKnowledgeRepositories.Nodes();
            InMemoryKnowledgeRepositories.Relations relations = new InMemoryKnowledgeRepositories.Relations();
            KnowledgeSourceService sourceService = new KnowledgeSourceService(sources);
            KnowledgeNodeService nodeService = new KnowledgeNodeService(nodes);
            digestService = mock(SourceDigestService.class);
            SourceDigestProcessor digestProcessor = new SourceDigestProcessor(
                    sourceService,
                    nodeService,
                    digestService,
                    mock(SourceGraphWriter.class)
            );
            useCase = new SourceCollectUseCase(
                    mock(WebFetchService.class),
                    mock(FolderService.class),
                    sourceService,
                    digestProcessor,
                    new SourceGraphReader(relations, nodes)
            );
        }

        private KnowledgeSource failedSource() {
            KnowledgeSource source = KnowledgeSource.create(
                    USER_ID, FOLDER_ID, "문서", "https://a.com", "https://a.com"
            );
            source.applyExtractedDocument("문서", "파싱한 본문", "article", null, null);
            source.failDigestion();
            return sources.save(source);
        }

        @Test
        @DisplayName("파싱 본문을 다시 수집하지 않고 LLM 소화만 재시도한다")
        void retriesDigestWithStoredContent() {
            KnowledgeSource source = failedSource();
            when(digestService.digest(any())).thenReturn(new SourceDigestResult(
                    "새 요약", "개발", "구현하기", List.of("MCP")
            ));

            var response = useCase.retry(USER_ID, source.getId());

            assertThat(response.status()).isEqualTo(SourceProcessingStatus.COMPLETED);
            assertThat(response.summary()).isEqualTo("새 요약");
            verify(digestService).digest(any());
        }

        @Test
        @DisplayName("완료된 Source는 다시 분석하지 않는다")
        void rejectsCompletedSource() {
            KnowledgeSource source = failedSource();
            source.prepareRetry();
            source.completeDigestion("완료", 1);
            sources.save(source);

            assertThatThrownBy(() -> useCase.retry(USER_ID, source.getId()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.KNOWLEDGE_SOURCE_NOT_RETRYABLE);
        }
    }

}

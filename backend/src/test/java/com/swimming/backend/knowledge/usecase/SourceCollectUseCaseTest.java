package com.swimming.backend.knowledge.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.domain.SourceProcessingStatus;
import com.swimming.backend.knowledge.dto.in.NodeRef;
import com.swimming.backend.knowledge.dto.in.SourceResponse;
import com.swimming.backend.knowledge.dto.in.SourceCollectRequest;
import com.swimming.backend.knowledge.dto.in.SourceCollectResponse;
import com.swimming.backend.knowledge.dto.out.FetchedDocument;
import com.swimming.backend.knowledge.dto.out.SourceDigestResult;
import com.swimming.backend.knowledge.dto.out.SourceFetchResult;
import com.swimming.backend.knowledge.repository.InMemoryKnowledgeRepositories;
import com.swimming.backend.knowledge.service.KnowledgeNodeService;
import com.swimming.backend.knowledge.service.KnowledgeRelationService;
import com.swimming.backend.knowledge.service.KnowledgeSourceService;
import com.swimming.backend.knowledge.service.NodeResolver;
import com.swimming.backend.knowledge.service.SourceConceptReader;
import com.swimming.backend.knowledge.service.SourceDigestService;
import com.swimming.backend.knowledge.service.SourceFetchService;
import com.swimming.backend.knowledge.service.SourceGraphWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 저장 하나로 수집과 소화가 모두 끝나는지 본다. 네트워크와 모델만 대역으로 바꾸고 나머지는
 * 실제 구현을 쓴다.
 */
class SourceCollectUseCaseTest {

    private static final Long USER_ID = 1L;
    private static final Long FOLDER_ID = 10L;
    private static final Long OTHER_FOLDER_ID = 11L;

    private InMemoryKnowledgeRepositories.Sources sources;
    private InMemoryKnowledgeRepositories.Nodes nodes;
    private InMemoryKnowledgeRepositories.Relations relations;

    private SourceFetchService fetchService;
    private SourceDigestService digestService;
    private FolderService folderService;
    private SourceCollectUseCase useCase;

    @BeforeEach
    void setUp() {
        sources = new InMemoryKnowledgeRepositories.Sources();
        nodes = new InMemoryKnowledgeRepositories.Nodes();
        relations = new InMemoryKnowledgeRepositories.Relations();

        fetchService = mock(SourceFetchService.class);
        digestService = mock(SourceDigestService.class);
        folderService = mock(FolderService.class);

        KnowledgeSourceService sourceService = new KnowledgeSourceService(sources);
        KnowledgeNodeService nodeService = new KnowledgeNodeService(nodes);

        useCase = new SourceCollectUseCase(
                fetchService,
                folderService,
                sourceService,
                new SourceDigestUseCase(
                        sourceService,
                        nodeService,
                        digestService,
                        new SourceGraphWriter(
                                nodeService,
                                new NodeResolver(nodes),
                                new KnowledgeRelationService(relations)
                        )
                ),
                new SourceConceptReader(relations, nodes)
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
    @DisplayName("이미 저장한 문서는 다른 Folder에 넣어도 새로 만들지 않는다")
    void doesNotDuplicateAcrossFolders() {
        givenFetch(success("https://a.com/1", "https://a.com/1", "같은 문서"));
        SourceCollectResponse first = collect(FOLDER_ID, "https://a.com/1");

        givenFetch(success("https://a.com/1", "https://a.com/1", "같은 문서"));
        SourceCollectResponse second = collect(OTHER_FOLDER_ID, "https://a.com/1");

        assertThat(second.items().getFirst().source().sourceId())
                .isEqualTo(first.items().getFirst().source().sourceId());
        assertThat(sources.findById(first.items().getFirst().source().sourceId())
                .orElseThrow()
                .getFolderId())
                .as("처음 저장한 Folder를 그대로 둔다")
                .isEqualTo(FOLDER_ID);
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

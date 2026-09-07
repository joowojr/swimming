package com.swimming.backend.knowledge.usecase;

import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.knowledge.domain.KnowledgeRelation;
import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.NodeTitleNormalizer;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.domain.RelationType;
import com.swimming.backend.knowledge.domain.SourceProcessingStatus;
import com.swimming.backend.knowledge.dto.in.SourceCollectRequest;
import com.swimming.backend.knowledge.dto.in.SourceCollectResponse;
import com.swimming.backend.knowledge.dto.in.SourceDigestResponse;
import com.swimming.backend.knowledge.dto.out.FetchedDocument;
import com.swimming.backend.knowledge.dto.out.SourceDigestResult;
import com.swimming.backend.knowledge.dto.out.SourceFetchResult;
import com.swimming.backend.knowledge.dto.out.ResolvedNode;
import com.swimming.backend.knowledge.repository.InMemoryKnowledgeRepositories;
import com.swimming.backend.knowledge.service.data.KnowledgeNodeService;
import com.swimming.backend.knowledge.service.data.KnowledgeRelationService;
import com.swimming.backend.knowledge.service.data.KnowledgeSourceService;
import com.swimming.backend.knowledge.service.graph.NodeResolutionService;
import com.swimming.backend.knowledge.service.llm.SourceDigestProcessor;
import com.swimming.backend.knowledge.service.llm.SourceDigestService;
import com.swimming.backend.knowledge.service.crawl.WebFetchService;
import com.swimming.backend.knowledge.service.SourceGraphReader;
import com.swimming.backend.knowledge.service.graph.SourceGraphWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 링크 저장부터 그래프 반영까지를 한 번에 돌린다.
 *
 * <p>네트워크와 모델만 대역으로 바꾸고 나머지는 실제 구현을 그대로 쓴다. 단위 테스트가
 * 각각 통과해도 use case 사이에서 어긋나는 것들 — 수집이 만든 Source 노드에 소화 결과가
 * 실제로 붙는지, 두 문서가 같은 Subject 노드를 공유하는지 — 이 여기서 드러난다.
 */
class SourcePipelineTest {

    private static final Long USER_ID = 1L;
    private static final Long FOLDER_ID = 10L;

    private InMemoryKnowledgeRepositories.Sources sources;
    private InMemoryKnowledgeRepositories.Nodes nodes;
    private InMemoryKnowledgeRepositories.Relations relations;

    private WebFetchService fetchService;
    private SourceDigestService digestService;

    private SourceCollectUseCase collectUseCase;
    private SourceDigestProcessor digestProcessor;

    @BeforeEach
    void setUp() {
        sources = new InMemoryKnowledgeRepositories.Sources();
        nodes = new InMemoryKnowledgeRepositories.Nodes();
        relations = new InMemoryKnowledgeRepositories.Relations();

        fetchService = mock(WebFetchService.class);
        digestService = mock(SourceDigestService.class);

        KnowledgeSourceService sourceService = new KnowledgeSourceService(sources);
        NodeResolutionService resolutionService = mock(NodeResolutionService.class);
        when(resolutionService.resolveSubjects(any(), any(), any())).thenAnswer(invocation -> {
            List<String> candidates = invocation.getArgument(2);
            return candidates.stream()
                    .map(candidate -> nodes.findByUserIdAndNodeTypeAndNormalizedTitle(
                                    USER_ID,
                                    NodeType.SUBJECT,
                                    NodeTitleNormalizer.normalize(candidate)
                            )
                            .map(node -> ResolvedNode.exact(candidate, node))
                            .orElseGet(() -> ResolvedNode.created(
                                    candidate,
                                    nodes.save(KnowledgeNode.create(
                                            USER_ID, NodeType.SUBJECT, candidate, null
                                    ))
                            )))
                    .toList();
        });

        digestProcessor = new SourceDigestProcessor(
                sourceService,
                new KnowledgeNodeService(nodes),
                digestService,
                resolutionService,
                new SourceGraphWriter(
                        new KnowledgeNodeService(nodes),
                        new KnowledgeRelationService(relations)
                )
        );
        collectUseCase = new SourceCollectUseCase(
                fetchService,
                mock(FolderService.class),
                sourceService,
                digestProcessor,
                new SourceGraphReader(relations, nodes)
        );
    }

    private void givenFetched(String url, String title) {
        when(fetchService.fetchAll(anyList())).thenReturn(List.of(
                SourceFetchResult.success(url, new FetchedDocument(
                        url, url, title, "작성자", null, "article",
                        "# " + title + "\n\n본문입니다.", false
                ))
        ));
    }

    private void givenDigested(String topic, String... subjects) {
        when(digestService.digest(any())).thenReturn(
                new SourceDigestResult("요약입니다.", "백엔드", topic, List.of(subjects))
        );
    }

    /** 링크 하나를 저장한다. 저장 한 번으로 소화까지 끝난다. */
    private UUID collectAndDigest(String url, String title, String topic, String... subjects) {
        givenFetched(url, title);
        givenDigested(topic, subjects);

        SourceCollectResponse collected =
                collectUseCase.collect(USER_ID, FOLDER_ID, new SourceCollectRequest(List.of(url)));

        return collected.items().getFirst().source().sourceId();
    }

    private List<KnowledgeRelation> from(UUID fromNodeId, RelationType relationType) {
        return relations.findAllByFromNodeIdIn(List.of(fromNodeId), List.of(relationType));
    }

    private String titleOf(UUID nodeId) {
        return nodes.findById(nodeId).orElseThrow().getTitle();
    }

    @Test
    @DisplayName("링크를 저장하고 소화하면 원문·요약과 개념·목적 연결이 모두 남는다")
    void 수집부터_그래프까지_이어진다() {
        UUID sourceId = collectAndDigest(
                "https://docs.spring.io/mcp.html", "Spring AI MCP Reference",
                "MCP 서버 구현하기", "MCP", "Tool Calling"
        );

        KnowledgeSource saved = sources.findById(sourceId).orElseThrow();
        assertThat(saved.getProcessingStatus()).isEqualTo(SourceProcessingStatus.COMPLETED);
        assertThat(saved.getContent()).contains("본문입니다.");
        assertThat(saved.getSummary()).isEqualTo("요약입니다.");

        UUID sourceNodeId = saved.getNode().getId();
        assertThat(from(sourceNodeId, RelationType.ABOUT))
                .extracting(relation -> titleOf(relation.getToNodeId()))
                .containsExactlyInAnyOrder("MCP", "Tool Calling");

        UUID topicId = from(sourceNodeId, RelationType.SUPPORTS).getFirst().getToNodeId();
        assertThat(titleOf(topicId)).isEqualTo("MCP 서버 구현하기");
        assertThat(from(topicId, RelationType.INVOLVES))
                .extracting(relation -> titleOf(relation.getToNodeId()))
                .containsExactlyInAnyOrder("MCP", "Tool Calling");
    }

    @Test
    @DisplayName("두 문서가 같은 개념을 다루면 하나의 Subject를 함께 가리킨다")
    void 문서들이_개념을_공유한다() {
        UUID first = collectAndDigest(
                "https://docs.spring.io/mcp.html", "MCP Reference",
                "MCP 서버 구현하기", "MCP", "Tool Calling"
        );
        UUID second = collectAndDigest(
                "https://tech.kakao.com/mcp", "MCP 도입기",
                "MCP 서버 운영하기", "mcp"
        );

        assertThat(nodes.findAllByUserIdAndNodeType(USER_ID, NodeType.SUBJECT))
                .extracting(node -> node.getTitle())
                .containsExactlyInAnyOrder("MCP", "Tool Calling");

        UUID mcpId = from(sources.findById(second).orElseThrow().getNode().getId(), RelationType.ABOUT)
                .getFirst().getToNodeId();

        assertThat(from(sources.findById(first).orElseThrow().getNode().getId(), RelationType.ABOUT))
                .extracting(KnowledgeRelation::getToNodeId)
                .contains(mcpId);
    }

    @Test
    @DisplayName("같은 문서를 다시 수집하면 Source도 소화도 다시 하지 않는다")
    void 같은_문서를_두_번_쌓지_않는다() {
        String url = "https://docs.spring.io/mcp.html";
        UUID sourceId = collectAndDigest(url, "MCP Reference", "MCP 서버 구현하기", "MCP");

        givenFetched(url, "MCP Reference");
        SourceCollectResponse again =
                collectUseCase.collect(USER_ID, FOLDER_ID, new SourceCollectRequest(List.of(url)));

        assertThat(again.items().getFirst().source().sourceId()).isEqualTo(sourceId);

        SourceDigestResponse digestedAgain = digestProcessor.digest(USER_ID, sourceId);
        assertThat(digestedAgain.result()).isNull();
        verify(digestService, times(1)).digest(any());
        assertThat(from(sources.findById(sourceId).orElseThrow().getNode().getId(), RelationType.ABOUT))
                .hasSize(1);
    }

    @Test
    @DisplayName("수집에 실패한 링크는 소화 단계로 넘어가지 않는다")
    void 수집_실패는_그래프에_남지_않는다() {
        when(fetchService.fetchAll(anyList())).thenReturn(List.of(
                SourceFetchResult.failure(
                        "https://gone.com", SourceFetchResult.Failure.HTTP_ERROR, "404"
                )
        ));

        SourceCollectResponse collected = collectUseCase.collect(
                USER_ID, FOLDER_ID, new SourceCollectRequest(List.of("https://gone.com"))
        );

        assertThat(collected.items().getFirst().source()).isNull();
        assertThat(nodes.findAllByUserIdAndNodeType(USER_ID, NodeType.SUBJECT)).isEmpty();
    }

    @Test
    @DisplayName("같은 이름의 목적이라도 문서마다 따로 만든다")
    void 목적은_문서마다_새로_만든다() {
        collectAndDigest("https://docs.spring.io/mcp.html", "MCP Reference", "MCP 서버 구현하기", "MCP");
        collectAndDigest("https://tech.kakao.com/mcp", "MCP 도입기", "MCP 서버 구현하기", "MCP");

        assertThat(nodes.findAllByUserIdAndNodeType(USER_ID, NodeType.TOPIC))
                .as("Topic은 재사용 판정을 하지 않는다")
                .hasSize(2)
                .extracting(node -> node.getTitle())
                .containsOnly("MCP 서버 구현하기");

        assertThat(nodes.findAllByUserIdAndNodeType(USER_ID, NodeType.SUBJECT))
                .as("Subject는 재사용한다")
                .hasSize(1);
    }

    @Test
    @DisplayName("소화에 실패한 문서는 다시 부르지 않는다")
    void 실패한_문서를_다시_부르지_않는다() {
        String url = "https://docs.spring.io/mcp.html";
        givenFetched(url, "MCP Reference");
        when(digestService.digest(any())).thenThrow(new RuntimeException("model timeout"));

        UUID sourceId = collectUseCase
                .collect(USER_ID, FOLDER_ID, new SourceCollectRequest(List.of(url)))
                .items().getFirst().source().sourceId();

        assertThat(sources.findById(sourceId).orElseThrow().getProcessingStatus())
                .isEqualTo(SourceProcessingStatus.FAILED);

        SourceDigestResponse again = digestProcessor.digest(USER_ID, sourceId);

        assertThat(again.status()).isEqualTo(SourceProcessingStatus.FAILED);
        assertThat(again.result()).isNull();
        verify(digestService, times(1)).digest(any());
    }

    @Test
    @DisplayName("두 개념을 모두 다루는 문서를 관계 교집합으로 찾을 수 있다")
    void 개념_교집합으로_문서를_찾는다() {
        UUID both = collectAndDigest(
                "https://docs.spring.io/mcp.html", "MCP Reference",
                "MCP 서버 구현하기", "MCP", "Tool Calling"
        );
        UUID onlyMcp = collectAndDigest(
                "https://tech.kakao.com/mcp", "MCP 도입기", "MCP 서버 운영하기", "MCP"
        );

        UUID mcpId = subjectId("MCP");
        UUID toolCallingId = subjectId("Tool Calling");

        assertThat(sourceNodesAbout(mcpId))
                .containsExactlyInAnyOrder(nodeIdOf(both), nodeIdOf(onlyMcp));
        assertThat(sourceNodesAbout(mcpId).stream().filter(sourceNodesAbout(toolCallingId)::contains).toList())
                .containsExactly(nodeIdOf(both));
    }

    private UUID nodeIdOf(UUID sourceId) {
        return sources.findById(sourceId).orElseThrow().getNode().getId();
    }

    private UUID subjectId(String title) {
        return nodes.findAllByUserIdAndNodeType(USER_ID, NodeType.SUBJECT).stream()
                .filter(node -> node.getTitle().equals(title))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    /** §11.2 처럼 Subject 하나를 다루는 Source들을 거꾸로 찾는다. */
    private List<UUID> sourceNodesAbout(UUID subjectNodeId) {
        return relations.findAllByToNodeIdIn(List.of(subjectNodeId), List.of(RelationType.ABOUT))
                .stream()
                .map(KnowledgeRelation::getFromNodeId)
                .toList();
    }
}

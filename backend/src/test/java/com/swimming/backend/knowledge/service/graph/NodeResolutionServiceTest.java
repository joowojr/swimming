package com.swimming.backend.knowledge.service.graph;

import com.swimming.backend.knowledge.config.ResolutionProperties;
import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.domain.RelationOrigin;
import com.swimming.backend.knowledge.dto.out.ResolvedNode;
import com.swimming.backend.knowledge.repository.InMemoryKnowledgeRepositories;
import com.swimming.backend.knowledge.service.data.KnowledgeNodeService;
import com.swimming.backend.knowledge.service.data.KnowledgeRelationService;
import com.swimming.backend.knowledge.service.data.KnowledgeSourceService;
import com.swimming.backend.knowledge.service.data.KnowledgeVectorSearchService;
import com.swimming.backend.common.client.EmbeddingClient;
import com.swimming.backend.knowledge.service.llm.NodeResolutionLlmDecision;
import com.swimming.backend.knowledge.service.llm.NodeResolutionLlmRequest;
import com.swimming.backend.knowledge.service.llm.NodeResolutionLlmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NodeResolutionServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long FOLDER_ID = 10L;
    private static final String SUMMARY = "OpenID Connect 인증 흐름을 설명한다.";

    private InMemoryKnowledgeRepositories.Sources sources;
    private InMemoryKnowledgeRepositories.Nodes nodes;
    private InMemoryKnowledgeRepositories.Relations relations;
    private EmbeddingClient embeddingClient;
    private NodeResolutionLlmService llmService;
    private KnowledgeRelationService relationService;
    private NodeResolutionService service;

    @BeforeEach
    void setUp() {
        sources = new InMemoryKnowledgeRepositories.Sources();
        nodes = new InMemoryKnowledgeRepositories.Nodes();
        relations = new InMemoryKnowledgeRepositories.Relations();
        embeddingClient = mock(EmbeddingClient.class);
        llmService = mock(NodeResolutionLlmService.class);
        relationService = new KnowledgeRelationService(relations);

        when(embeddingClient.embed(anyString())).thenReturn(vector(1));
        when(embeddingClient.embed(anyList())).thenAnswer(invocation ->
                invocation.<List<String>>getArgument(0).stream()
                        .map(ignored -> vector(1))
                        .toList());

        service = new NodeResolutionService(
                embeddingClient,
                new ResolutionProperties(5, 0.5, 3),
                new KnowledgeSourceService(sources, org.mockito.Mockito.mock(com.swimming.backend.folder.service.FolderService.class)),
                new KnowledgeNodeService(nodes),
                new KnowledgeVectorSearchService(
                        new InMemoryKnowledgeRepositories.VectorSearch(nodes, sources)),
                relationService,
                llmService
        );
    }

    private float[] vector(float first) {
        float[] value = new float[NodeResolutionService.EMBEDDING_DIMENSIONS];
        value[0] = first;
        return value;
    }

    private KnowledgeSource source(Long userId, String url) {
        KnowledgeSource source = KnowledgeSource.create(
                userId, FOLDER_ID, "문서", url, url
        );
        source.applyExtractedDocument("문서", "본문", "article", null, null);
        return sources.save(source);
    }

    private KnowledgeSource completedSource(Long userId, String url, float[] embedding) {
        KnowledgeSource source = source(userId, url);
        source.startDigestion();
        source.completeDigestion("기존 요약", 1);
        KnowledgeSource saved = sources.save(source);
        sources.saveSummaryEmbedding(
                userId, saved.getId(), embedding, NodeResolutionService.EMBEDDING_MODEL
        );
        return saved;
    }

    private KnowledgeNode subjectInFolder(String title) {
        KnowledgeNode subject = nodes.create(KnowledgeNode.create(
                USER_ID, NodeType.SUBJECT, title, null));
        KnowledgeSource linkedSource = source(USER_ID, "https://a.com/subject/" + title);
        relationService.connect(linkedSource.getNode(), subject, RelationOrigin.AI);
        return subject;
    }

    @Test
    @DisplayName("정규화한 이름이 같으면 기존 Subject를 재사용하고 Resolution LLM을 부르지 않는다")
    void reusesExactSubjectBeforeLlm() {
        KnowledgeNode existing = subjectInFolder("AWS OIDC");
        KnowledgeSource current = source(USER_ID, "https://a.com/current");

        List<ResolvedNode> result = service.resolveSubjects(
                current, SUMMARY, List.of("aws-oidc", "AWS_OIDC")
        );

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().node().getId()).isEqualTo(existing.getId());
        assertThat(result.getFirst().match()).isEqualTo(ResolvedNode.Match.EXACT);
        assertThat(sources.summaryEmbeddingOf(current.getId())).hasSize(768);
        assertThat(sources.summaryEmbeddingModelOf(current.getId()))
                .isEqualTo("text-embedding-3-small");
        verify(llmService, never()).resolve(any());
    }

    @Test
    @DisplayName("유사 Source의 Subject를 Context로 주고 의미가 같으면 기존 노드를 재사용한다")
    void reusesSubjectFromSimilarSource() {
        KnowledgeNode oidc = nodes.create(KnowledgeNode.create(
                USER_ID, NodeType.SUBJECT, "OpenID Connect", null
        ));
        KnowledgeSource similar = completedSource(
                USER_ID, "https://a.com/oidc", vector(1)
        );
        relationService.connect(similar.getNode(), oidc, RelationOrigin.AI);

        when(llmService.resolve(any())).thenReturn(List.of(
                NodeResolutionLlmDecision.reuse("oidc", oidc.getId())
        ));

        KnowledgeSource current = source(USER_ID, "https://a.com/current");
        ResolvedNode result = service.resolveSubjects(
                current, SUMMARY, List.of("OIDC")
        ).getFirst();

        assertThat(result.node().getId()).isEqualTo(oidc.getId());
        assertThat(result.match()).isEqualTo(ResolvedNode.Match.SEMANTIC);

        ArgumentCaptor<NodeResolutionLlmRequest> captor = ArgumentCaptor.forClass(
                NodeResolutionLlmRequest.class);
        verify(llmService).resolve(captor.capture());
        assertThat(captor.getValue().contextSubjects())
                .extracting(NodeResolutionLlmRequest.ReusableSubject::title)
                .containsExactly("OpenID Connect");
    }

    @Test
    @DisplayName("Subject 직접 임베딩 유사도가 높으면 더 먼 Source의 후보라도 먼저 제공한다")
    void ranksSubjectsByDirectEmbeddingSimilarity() {
        KnowledgeNode unrelated = nodes.create(KnowledgeNode.create(
                USER_ID, NodeType.SUBJECT, "OAuth Authorization", null
        ));
        KnowledgeNode keywordMatch = nodes.create(KnowledgeNode.create(
                USER_ID, NodeType.SUBJECT, "OIDC Authentication", null
        ));
        nodes.saveTitleEmbedding(
                USER_ID, unrelated.getId(), vector(-1), NodeResolutionService.EMBEDDING_MODEL
        );
        nodes.saveTitleEmbedding(
                USER_ID, keywordMatch.getId(), vector(1), NodeResolutionService.EMBEDDING_MODEL
        );
        KnowledgeSource nearest = completedSource(
                USER_ID, "https://a.com/nearest", vector(1)
        );
        KnowledgeSource farther = completedSource(
                USER_ID, "https://a.com/farther", vector(-1)
        );
        relationService.connect(nearest.getNode(), unrelated, RelationOrigin.AI);
        relationService.connect(farther.getNode(), keywordMatch, RelationOrigin.AI);

        when(llmService.resolve(any())).thenReturn(List.of(
                NodeResolutionLlmDecision.create("oidcprotocol", "OIDC Protocol")
        ));

        service.resolveSubjects(
                source(USER_ID, "https://a.com/current"), SUMMARY, List.of("OIDC protocol")
        );

        ArgumentCaptor<NodeResolutionLlmRequest> captor = ArgumentCaptor.forClass(
                NodeResolutionLlmRequest.class);
        verify(llmService).resolve(captor.capture());
        assertThat(captor.getValue().candidates().getFirst().matches())
                .extracting(NodeResolutionLlmRequest.ReusableSubject::title)
                .containsExactly("OIDC Authentication", "OAuth Authorization");
        assertThat(captor.getValue().contextSubjects())
                .extracting(NodeResolutionLlmRequest.ReusableSubject::title)
                .containsExactly("OAuth Authorization");
    }

    @Test
    @DisplayName("Subject 직접 임베딩 후보 순서는 Source 유사도 순서보다 우선한다")
    void prioritizesDirectEmbeddingOrderOverSimilarSourceRank() {
        KnowledgeNode fartherSubject = nodes.create(KnowledgeNode.create(
                USER_ID, NodeType.SUBJECT, "JSON Web Token", null
        ));
        KnowledgeNode nearestSubject = nodes.create(KnowledgeNode.create(
                USER_ID, NodeType.SUBJECT, "OAuth 2.0", null
        ));
        nodes.saveTitleEmbedding(
                USER_ID, fartherSubject.getId(), vector(1), NodeResolutionService.EMBEDDING_MODEL
        );
        nodes.saveTitleEmbedding(
                USER_ID, nearestSubject.getId(), vector(-1), NodeResolutionService.EMBEDDING_MODEL
        );
        KnowledgeSource nearest = completedSource(
                USER_ID, "https://a.com/nearest", vector(1)
        );
        KnowledgeSource farther = completedSource(
                USER_ID, "https://a.com/farther", vector(-1)
        );
        relationService.connect(farther.getNode(), fartherSubject, RelationOrigin.AI);
        relationService.connect(nearest.getNode(), nearestSubject, RelationOrigin.AI);

        when(llmService.resolve(any())).thenReturn(List.of(
                NodeResolutionLlmDecision.create("identitystandard", "Identity Standard")
        ));

        service.resolveSubjects(
                source(USER_ID, "https://a.com/current"), SUMMARY, List.of("identity standard")
        );

        ArgumentCaptor<NodeResolutionLlmRequest> captor = ArgumentCaptor.forClass(
                NodeResolutionLlmRequest.class);
        verify(llmService).resolve(captor.capture());
        assertThat(captor.getValue().candidates().getFirst().matches())
                .extracting(NodeResolutionLlmRequest.ReusableSubject::title)
                .containsExactly("JSON Web Token", "OAuth 2.0");
    }

    @Test
    @DisplayName("적절한 기존 Subject가 없으면 LLM이 제안한 값으로 새 노드를 만든다")
    void createsNewSubject() {
        when(llmService.resolve(any())).thenReturn(List.of(
                NodeResolutionLlmDecision.create("oidcprotocol", "OpenID Connect")
        ));

        ResolvedNode result = service.resolveSubjects(
                source(USER_ID, "https://a.com/current"),
                SUMMARY,
                List.of("oidc protocol")
        ).getFirst();

        assertThat(result.node().getTitle()).isEqualTo("OpenID Connect");
        assertThat(result.node().getNodeType()).isEqualTo(NodeType.SUBJECT);
        assertThat(result.match()).isEqualTo(ResolvedNode.Match.CREATED);
        assertThat(nodes.titleEmbeddingOf(result.node().getId())).hasSize(768);
        assertThat(nodes.titleEmbeddingModelOf(result.node().getId()))
                .isEqualTo(NodeResolutionService.EMBEDDING_MODEL);
    }

    @Test
    @DisplayName("여러 CREATE 결정의 canonical title이 같으면 한 번만 임베딩하고 생성한다")
    void 같은_신규_Subject는_한_번만_임베딩하고_생성한다() {
        when(llmService.resolve(any())).thenReturn(List.of(
                NodeResolutionLlmDecision.create("oidc", "OpenID Connect"),
                NodeResolutionLlmDecision.create("openidprotocol", "OpenID Connect")
        ));

        List<ResolvedNode> result = service.resolveSubjects(
                source(USER_ID, "https://a.com/current"),
                SUMMARY,
                List.of("OIDC", "OpenID protocol")
        );

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().node().getTitle()).isEqualTo("OpenID Connect");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> embeddingInputs = ArgumentCaptor.forClass(List.class);
        verify(embeddingClient, times(2)).embed(embeddingInputs.capture());
        assertThat(embeddingInputs.getAllValues().get(1))
                .containsExactly("openid connect");
    }

    @Test
    @DisplayName("후보가 여러 개여도 정규화 제목 조회는 단계마다 한 번씩만 한다")
    void 정규화_조회를_묶어서_한다() {
        subjectInFolder("AWS OIDC");
        when(llmService.resolve(any())).thenReturn(List.of(
                NodeResolutionLlmDecision.create("oidcprotocol", "OpenID Connect"),
                NodeResolutionLlmDecision.create("saml", "SAML"),
                NodeResolutionLlmDecision.create("scim", "SCIM")
        ));
        nodes.normalizedTitleLookupCount = 0;

        List<ResolvedNode> result = service.resolveSubjects(
                source(USER_ID, "https://a.com/current"),
                SUMMARY,
                List.of("aws-oidc", "oidc protocol", "saml", "scim")
        );

        assertThat(result).hasSize(4);
        // 후보 4개에 신규 판정 3개인데도 정규화 조회는 LLM 앞뒤로 한 번씩 두 번뿐이다.
        assertThat(nodes.normalizedTitleLookupCount).isEqualTo(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> embeddingInputs = ArgumentCaptor.forClass(List.class);
        verify(embeddingClient, times(2)).embed(embeddingInputs.capture());
        assertThat(embeddingInputs.getAllValues()).containsExactly(
                List.of("oidc protocol", "saml", "scim"),
                List.of("openid connect", "saml", "scim")
        );
    }

    @Test
    @DisplayName("LLM을 기다리는 사이 같은 Subject가 생기면 새로 만들지 않고 재사용한다")
    void 동시에_생긴_중복은_재사용한다() {
        when(llmService.resolve(any())).thenAnswer(invocation -> {
            // LLM이 도는 동안 다른 요청이 같은 Subject를 만든 상황.
            nodes.create(KnowledgeNode.create(USER_ID, NodeType.SUBJECT, "OpenID Connect", null));
            return List.of(NodeResolutionLlmDecision.create(
                    "oidcprotocol", "OpenID Connect"));
        });

        ResolvedNode result = service.resolveSubjects(
                source(USER_ID, "https://a.com/current"),
                SUMMARY,
                List.of("oidc protocol")
        ).getFirst();

        assertThat(result.match()).isEqualTo(ResolvedNode.Match.EXACT);
        assertThat(result.node().getTitle()).isEqualTo("OpenID Connect");
    }

    @Test
    @DisplayName("답하지 않은 후보는 건너뛰고 답한 후보만 확정한다")
    void 답하지_않은_후보는_건너뛴다() {
        when(llmService.resolve(any())).thenReturn(List.of(
                NodeResolutionLlmDecision.create("saml", "SAML")
        ));

        List<ResolvedNode> result = service.resolveSubjects(
                source(USER_ID, "https://a.com/current"), SUMMARY, List.of("OIDC", "saml")
        );

        assertThat(result).extracting(item -> item.node().getTitle()).containsExactly("SAML");
    }

    @Test
    @DisplayName("정확히 일치한 후보는 제외하고 미해결 후보만 LLM에 넘긴다")
    void 미해결_후보만_LLM에_넘긴다() {
        subjectInFolder("AWS OIDC");
        when(llmService.resolve(any())).thenReturn(List.of(
                NodeResolutionLlmDecision.create("saml", "SAML"),
                NodeResolutionLlmDecision.create("scim", "SCIM")
        ));

        service.resolveSubjects(
                source(USER_ID, "https://a.com/current"),
                SUMMARY,
                List.of("aws-oidc", "saml", "scim")
        );

        ArgumentCaptor<NodeResolutionLlmRequest> captor = ArgumentCaptor.forClass(
                NodeResolutionLlmRequest.class);
        verify(llmService).resolve(captor.capture());
        assertThat(captor.getValue().candidates())
                .extracting(NodeResolutionLlmRequest.Candidate::key,
                        NodeResolutionLlmRequest.Candidate::value)
                .containsExactly(tuple("saml", "saml"), tuple("scim", "scim"));
    }

    @Test
    @DisplayName("쓸 수 있는 결정이 하나도 없으면 재시도할 수 있게 실패로 둔다")
    void rejectsResponseWithoutUsableDecision() {
        when(llmService.resolve(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.resolveSubjects(
                source(USER_ID, "https://a.com/current"), SUMMARY, List.of("OIDC")
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no usable decision");
    }

    @Test
    @DisplayName("다른 사용자의 유사 Source와 Subject는 후보 Context에 넣지 않는다")
    void excludesOtherUsersContext() {
        KnowledgeNode otherSubject = nodes.create(KnowledgeNode.create(
                OTHER_USER_ID, NodeType.SUBJECT, "OpenID Connect", null
        ));
        KnowledgeSource otherSource = completedSource(
                OTHER_USER_ID, "https://other.com/oidc", vector(1)
        );
        relationService.connect(otherSource.getNode(), otherSubject, RelationOrigin.AI);

        when(llmService.resolve(any())).thenReturn(List.of(
                NodeResolutionLlmDecision.create("oidc", "OIDC")
        ));

        service.resolveSubjects(
                source(USER_ID, "https://a.com/current"), SUMMARY, List.of("OIDC")
        );

        ArgumentCaptor<NodeResolutionLlmRequest> captor = ArgumentCaptor.forClass(
                NodeResolutionLlmRequest.class);
        verify(llmService).resolve(captor.capture());
        assertThat(captor.getValue().candidates().getFirst().matches()).isEmpty();
        assertThat(captor.getValue().contextSubjects()).isEmpty();
    }

    @Test
    @DisplayName("설정한 768차원이 아닌 임베딩은 저장하거나 검색하지 않는다")
    void rejectsWrongEmbeddingDimensions() {
        when(embeddingClient.embed(anyString())).thenReturn(new float[1536]);

        assertThatThrownBy(() -> service.resolveSubjects(
                source(USER_ID, "https://a.com/current"), SUMMARY, List.of("OIDC")
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected 768 embedding dimensions");

        verify(llmService, never()).resolve(any());
    }
}

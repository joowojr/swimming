package com.swimming.backend.knowledge.service.graph;

import com.swimming.backend.knowledge.config.ResolutionProperties;
import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.domain.RelationOrigin;
import com.swimming.backend.knowledge.dto.out.NodeResolutionInputV2;
import com.swimming.backend.knowledge.dto.out.NodeResolutionResultV2;
import com.swimming.backend.knowledge.dto.out.ResolvedNode;
import com.swimming.backend.knowledge.repository.InMemoryKnowledgeRepositories;
import com.swimming.backend.knowledge.service.data.KnowledgeNodeService;
import com.swimming.backend.knowledge.service.data.KnowledgeRelationService;
import com.swimming.backend.knowledge.service.data.KnowledgeSourceService;
import com.swimming.backend.knowledge.service.data.KnowledgeVectorSearchService;
import com.swimming.backend.common.client.EmbeddingClient;
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
                new KnowledgeSourceService(sources),
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
        KnowledgeNode subject = nodes.save(KnowledgeNode.create(
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
        verify(llmService, never()).resolveV2(any());
    }

    @Test
    @DisplayName("유사 Source의 Subject를 Context로 주고 의미가 같으면 기존 노드를 재사용한다")
    void reusesSubjectFromSimilarSource() {
        KnowledgeNode oidc = nodes.save(KnowledgeNode.create(
                USER_ID, NodeType.SUBJECT, "OpenID Connect", null
        ));
        KnowledgeSource similar = completedSource(
                USER_ID, "https://a.com/oidc", vector(1)
        );
        relationService.connect(similar.getNode(), oidc, RelationOrigin.AI);

        when(llmService.resolveV2(any())).thenReturn(new NodeResolutionResultV2(List.of(
                new NodeResolutionResultV2.Decision(
                        1, 1, ""
                )
        )));

        KnowledgeSource current = source(USER_ID, "https://a.com/current");
        ResolvedNode result = service.resolveSubjects(
                current, SUMMARY, List.of("OIDC")
        ).getFirst();

        assertThat(result.node().getId()).isEqualTo(oidc.getId());
        assertThat(result.match()).isEqualTo(ResolvedNode.Match.SEMANTIC);

        ArgumentCaptor<NodeResolutionInputV2> captor = ArgumentCaptor.forClass(NodeResolutionInputV2.class);
        verify(llmService).resolveV2(captor.capture());
        assertThat(captor.getValue().contextSubjects())
                .extracting(NodeResolutionInputV2.ContextSubject::value)
                .containsExactly("OpenID Connect");
        assertThat(captor.getValue().contextSubjects())
                .extracting(NodeResolutionInputV2.ContextSubject::index)
                .containsExactly(1);
    }

    @Test
    @DisplayName("Subject 직접 임베딩 유사도가 높으면 더 먼 Source의 후보라도 먼저 제공한다")
    void ranksSubjectsByDirectEmbeddingSimilarity() {
        KnowledgeNode unrelated = nodes.save(KnowledgeNode.create(
                USER_ID, NodeType.SUBJECT, "OAuth Authorization", null
        ));
        KnowledgeNode keywordMatch = nodes.save(KnowledgeNode.create(
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

        when(llmService.resolveV2(any())).thenReturn(new NodeResolutionResultV2(List.of(
                new NodeResolutionResultV2.Decision(
                        1, 0, "OIDC Protocol"
                )
        )));

        service.resolveSubjects(
                source(USER_ID, "https://a.com/current"), SUMMARY, List.of("OIDC protocol")
        );

        ArgumentCaptor<NodeResolutionInputV2> captor = ArgumentCaptor.forClass(NodeResolutionInputV2.class);
        verify(llmService).resolveV2(captor.capture());
        assertThat(captor.getValue().candidates().getFirst().matches())
                .extracting(NodeResolutionInputV2.Match::value)
                .containsExactly("OIDC Authentication", "OAuth Authorization");
        assertThat(captor.getValue().candidates().getFirst().matches())
                .extracting(NodeResolutionInputV2.Match::index)
                .containsExactly(1, 2);
        assertThat(captor.getValue().contextSubjects()).isEmpty();
    }

    @Test
    @DisplayName("Subject 직접 임베딩 후보 순서는 Source 유사도 순서보다 우선한다")
    void prioritizesDirectEmbeddingOrderOverSimilarSourceRank() {
        KnowledgeNode fartherSubject = nodes.save(KnowledgeNode.create(
                USER_ID, NodeType.SUBJECT, "JSON Web Token", null
        ));
        KnowledgeNode nearestSubject = nodes.save(KnowledgeNode.create(
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

        when(llmService.resolveV2(any())).thenReturn(new NodeResolutionResultV2(List.of(
                new NodeResolutionResultV2.Decision(
                        1, 0, "Identity Standard"
                )
        )));

        service.resolveSubjects(
                source(USER_ID, "https://a.com/current"), SUMMARY, List.of("identity standard")
        );

        ArgumentCaptor<NodeResolutionInputV2> captor = ArgumentCaptor.forClass(NodeResolutionInputV2.class);
        verify(llmService).resolveV2(captor.capture());
        assertThat(captor.getValue().candidates().getFirst().matches())
                .extracting(NodeResolutionInputV2.Match::value)
                .containsExactly("JSON Web Token", "OAuth 2.0");
    }

    @Test
    @DisplayName("적절한 기존 Subject가 없으면 LLM이 제안한 값으로 새 노드를 만든다")
    void createsNewSubject() {
        when(llmService.resolveV2(any())).thenReturn(new NodeResolutionResultV2(List.of(
                new NodeResolutionResultV2.Decision(
                        1, 0, "OpenID Connect"
                )
        )));

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
        when(llmService.resolveV2(any())).thenReturn(new NodeResolutionResultV2(List.of(
                new NodeResolutionResultV2.Decision(1, 0, "OpenID Connect"),
                new NodeResolutionResultV2.Decision(2, 0, "OpenID Connect")
        )));

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
        when(llmService.resolveV2(any())).thenReturn(new NodeResolutionResultV2(List.of(
                new NodeResolutionResultV2.Decision(
                        1, 0, "OpenID Connect"
                ),
                new NodeResolutionResultV2.Decision(
                        2, 0, "SAML"
                ),
                new NodeResolutionResultV2.Decision(
                        3, 0, "SCIM"
                )
        )));
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
    @DisplayName("같은 Subject가 여러 후보와 유사 Source에 있어도 하나의 전역 R 번호를 사용한다")
    void 중복_Subject는_전역_R_번호를_재사용한다() {
        KnowledgeNode oidc = nodes.save(KnowledgeNode.create(
                USER_ID, NodeType.SUBJECT, "OpenID Connect", null));
        nodes.saveTitleEmbedding(
                USER_ID, oidc.getId(), vector(1), NodeResolutionService.EMBEDDING_MODEL);
        KnowledgeSource similar = completedSource(
                USER_ID, "https://a.com/oidc", vector(1));
        relationService.connect(similar.getNode(), oidc, RelationOrigin.AI);

        when(llmService.resolveV2(any())).thenReturn(new NodeResolutionResultV2(List.of(
                new NodeResolutionResultV2.Decision(1, 1, ""),
                new NodeResolutionResultV2.Decision(2, 1, "")
        )));

        service.resolveSubjects(
                source(USER_ID, "https://a.com/current"),
                SUMMARY,
                List.of("OIDC", "authentication")
        );

        ArgumentCaptor<NodeResolutionInputV2> captor = ArgumentCaptor.forClass(
                NodeResolutionInputV2.class);
        verify(llmService).resolveV2(captor.capture());
        assertThat(captor.getValue().candidates())
                .allSatisfy(candidate -> assertThat(candidate.matches())
                        .extracting(NodeResolutionInputV2.Match::index)
                        .containsExactly(1));
        assertThat(captor.getValue().contextSubjects()).isEmpty();
    }

    @Test
    @DisplayName("LLM을 기다리는 사이 같은 Subject가 생기면 새로 만들지 않고 재사용한다")
    void 동시에_생긴_중복은_재사용한다() {
        when(llmService.resolveV2(any())).thenAnswer(invocation -> {
            // LLM이 도는 동안 다른 요청이 같은 Subject를 만든 상황.
            nodes.save(KnowledgeNode.create(USER_ID, NodeType.SUBJECT, "OpenID Connect", null));
            return new NodeResolutionResultV2(List.of(
                    new NodeResolutionResultV2.Decision(
                            1, 0, "OpenID Connect"
                    )
            ));
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
    @DisplayName("Context에 없는 Subject index를 고른 결정은 그 후보만 버린다")
    void dropsDecisionWithUnknownSubjectIndex() {
        when(llmService.resolveV2(any())).thenReturn(new NodeResolutionResultV2(List.of(
                new NodeResolutionResultV2.Decision(
                        1, 99, ""
                ),
                new NodeResolutionResultV2.Decision(
                        2, 0, "SAML"
                )
        )));

        List<ResolvedNode> result = service.resolveSubjects(
                source(USER_ID, "https://a.com/current"), SUMMARY, List.of("OIDC", "saml")
        );

        assertThat(result).extracting(item -> item.node().getTitle()).containsExactly("SAML");
    }

    @Test
    @DisplayName("재사용 결정에 대상 이름이 함께 와도 버리지 않는다")
    void 재사용_결정의_value는_무시한다() {
        KnowledgeNode oidc = nodes.save(KnowledgeNode.create(
                USER_ID, NodeType.SUBJECT, "OpenID Connect", null
        ));
        nodes.saveTitleEmbedding(
                USER_ID, oidc.getId(), vector(1), NodeResolutionService.EMBEDDING_MODEL
        );
        when(llmService.resolveV2(any())).thenReturn(new NodeResolutionResultV2(List.of(
                // 재사용 대상은 reuseIndex가 정하므로 value는 읽지 않는다.
                new NodeResolutionResultV2.Decision(
                        1, 1, "OpenID Connect"
                )
        )));

        ResolvedNode result = service.resolveSubjects(
                source(USER_ID, "https://a.com/current"), SUMMARY, List.of("OIDC")
        ).getFirst();

        assertThat(result.node().getId()).isEqualTo(oidc.getId());
        assertThat(result.match()).isEqualTo(ResolvedNode.Match.SEMANTIC);
    }

    @Test
    @DisplayName("음수 Subject index를 쓴 결정은 그 후보만 버린다")
    void dropsDecisionWithNegativeSubjectIndex() {
        when(llmService.resolveV2(any())).thenReturn(new NodeResolutionResultV2(List.of(
                new NodeResolutionResultV2.Decision(
                        1, -1, "OpenID Connect"
                ),
                new NodeResolutionResultV2.Decision(
                        2, 0, "SAML"
                )
        )));

        List<ResolvedNode> result = service.resolveSubjects(
                source(USER_ID, "https://a.com/current"), SUMMARY, List.of("OIDC", "saml")
        );

        assertThat(result).extracting(item -> item.node().getTitle()).containsExactly("SAML");
    }

    @Test
    @DisplayName("답하지 않은 후보는 건너뛰고 답한 후보만 확정한다")
    void 답하지_않은_후보는_건너뛴다() {
        when(llmService.resolveV2(any())).thenReturn(new NodeResolutionResultV2(List.of(
                new NodeResolutionResultV2.Decision(
                        2, 0, "SAML"
                )
        )));

        List<ResolvedNode> result = service.resolveSubjects(
                source(USER_ID, "https://a.com/current"), SUMMARY, List.of("OIDC", "saml")
        );

        assertThat(result).extracting(item -> item.node().getTitle()).containsExactly("SAML");
    }

    @Test
    @DisplayName("같은 후보 index에 두 번 답하면 뒤의 결정을 버린다")
    void 중복_결정은_뒤를_버린다() {
        when(llmService.resolveV2(any())).thenReturn(new NodeResolutionResultV2(List.of(
                new NodeResolutionResultV2.Decision(
                        1, 0, "OpenID Connect"
                ),
                new NodeResolutionResultV2.Decision(
                        1, 0, "SAML"
                )
        )));

        List<ResolvedNode> result = service.resolveSubjects(
                source(USER_ID, "https://a.com/current"), SUMMARY, List.of("OIDC")
        );

        assertThat(result).extracting(item -> item.node().getTitle())
                .containsExactly("OpenID Connect");
    }

    @Test
    @DisplayName("미해결 후보는 1부터 순서대로 index를 붙여 LLM에 넘긴다")
    void 후보에_index를_붙여_넘긴다() {
        subjectInFolder("AWS OIDC");
        when(llmService.resolveV2(any())).thenReturn(new NodeResolutionResultV2(List.of(
                new NodeResolutionResultV2.Decision(
                        1, 0, "SAML"
                ),
                new NodeResolutionResultV2.Decision(
                        2, 0, "SCIM"
                )
        )));

        service.resolveSubjects(
                source(USER_ID, "https://a.com/current"),
                SUMMARY,
                List.of("aws-oidc", "saml", "scim")
        );

        ArgumentCaptor<NodeResolutionInputV2> captor = ArgumentCaptor.forClass(NodeResolutionInputV2.class);
        verify(llmService).resolveV2(captor.capture());
        assertThat(captor.getValue().candidates())
                .extracting(NodeResolutionInputV2.Candidate::index, NodeResolutionInputV2.Candidate::value)
                .containsExactly(tuple(1, "saml"), tuple(2, "scim"));
    }

    @Test
    @DisplayName("쓸 수 있는 결정이 하나도 없으면 재시도할 수 있게 실패로 둔다")
    void rejectsResponseWithoutUsableDecision() {
        when(llmService.resolveV2(any())).thenReturn(new NodeResolutionResultV2(List.of(
                // 후보 index를 0부터 세거나 Subject index 규칙과 섞어 쓴 응답.
                new NodeResolutionResultV2.Decision(
                        0, 0, "OpenID Connect"
                )
        )));

        assertThatThrownBy(() -> service.resolveSubjects(
                source(USER_ID, "https://a.com/current"), SUMMARY, List.of("OIDC")
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no usable decision");
    }

    @Test
    @DisplayName("다른 사용자의 유사 Source와 Subject는 후보 Context에 넣지 않는다")
    void excludesOtherUsersContext() {
        KnowledgeNode otherSubject = nodes.save(KnowledgeNode.create(
                OTHER_USER_ID, NodeType.SUBJECT, "OpenID Connect", null
        ));
        KnowledgeSource otherSource = completedSource(
                OTHER_USER_ID, "https://other.com/oidc", vector(1)
        );
        relationService.connect(otherSource.getNode(), otherSubject, RelationOrigin.AI);

        when(llmService.resolveV2(any())).thenReturn(new NodeResolutionResultV2(List.of(
                new NodeResolutionResultV2.Decision(
                        1, 0, "OIDC"
                )
        )));

        service.resolveSubjects(
                source(USER_ID, "https://a.com/current"), SUMMARY, List.of("OIDC")
        );

        ArgumentCaptor<NodeResolutionInputV2> captor = ArgumentCaptor.forClass(NodeResolutionInputV2.class);
        verify(llmService).resolveV2(captor.capture());
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

        verify(llmService, never()).resolveV2(any());
    }
}

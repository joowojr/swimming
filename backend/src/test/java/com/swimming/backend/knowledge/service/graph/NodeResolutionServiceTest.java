package com.swimming.backend.knowledge.service.graph;

import com.swimming.backend.knowledge.config.KnowledgeResolutionProperties;
import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.domain.RelationOrigin;
import com.swimming.backend.knowledge.dto.out.NodeResolutionInput;
import com.swimming.backend.knowledge.dto.out.NodeResolutionResult;
import com.swimming.backend.knowledge.dto.out.ResolvedNode;
import com.swimming.backend.knowledge.repository.InMemoryKnowledgeRepositories;
import com.swimming.backend.knowledge.service.data.KnowledgeNodeService;
import com.swimming.backend.knowledge.service.data.KnowledgeRelationService;
import com.swimming.backend.knowledge.service.data.KnowledgeSourceService;
import com.swimming.backend.common.client.EmbeddingClient;
import com.swimming.backend.knowledge.service.llm.NodeResolutionLlmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
                new KnowledgeResolutionProperties(5, 3),
                new KnowledgeSourceService(sources),
                new KnowledgeNodeService(nodes),
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

    @Test
    @DisplayName("정규화한 이름이 같으면 기존 Subject를 재사용하고 Resolution LLM을 부르지 않는다")
    void reusesExactSubjectBeforeLlm() {
        KnowledgeNode existing = nodes.save(KnowledgeNode.create(
                USER_ID, NodeType.SUBJECT, "AWS OIDC", null
        ));
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
        KnowledgeNode oidc = nodes.save(KnowledgeNode.create(
                USER_ID, NodeType.SUBJECT, "OpenID Connect", null
        ));
        KnowledgeSource similar = completedSource(
                USER_ID, "https://a.com/oidc", vector(1)
        );
        relationService.connect(similar.getNode(), oidc, RelationOrigin.AI);

        when(llmService.resolve(any())).thenReturn(new NodeResolutionResult(List.of(
                new NodeResolutionResult.Decision(
                        "OIDC", NodeResolutionResult.Action.REUSE, 1, ""
                )
        )));

        KnowledgeSource current = source(USER_ID, "https://a.com/current");
        ResolvedNode result = service.resolveSubjects(
                current, SUMMARY, List.of("OIDC")
        ).getFirst();

        assertThat(result.node().getId()).isEqualTo(oidc.getId());
        assertThat(result.match()).isEqualTo(ResolvedNode.Match.SEMANTIC);

        ArgumentCaptor<NodeResolutionInput> captor = ArgumentCaptor.forClass(NodeResolutionInput.class);
        verify(llmService).resolve(captor.capture());
        assertThat(captor.getValue().existingSubjects())
                .extracting(NodeResolutionInput.ExistingSubject::value)
                .containsExactly("OpenID Connect");
        assertThat(captor.getValue().existingSubjects())
                .extracting(NodeResolutionInput.ExistingSubject::index)
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

        when(llmService.resolve(any())).thenReturn(new NodeResolutionResult(List.of(
                new NodeResolutionResult.Decision(
                        "OIDC protocol", NodeResolutionResult.Action.CREATE, 0, "OIDC Protocol"
                )
        )));

        service.resolveSubjects(
                source(USER_ID, "https://a.com/current"), SUMMARY, List.of("OIDC protocol")
        );

        ArgumentCaptor<NodeResolutionInput> captor = ArgumentCaptor.forClass(NodeResolutionInput.class);
        verify(llmService).resolve(captor.capture());
        assertThat(captor.getValue().existingSubjects())
                .extracting(NodeResolutionInput.ExistingSubject::value)
                .containsExactly("OIDC Authentication", "OAuth Authorization");
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

        when(llmService.resolve(any())).thenReturn(new NodeResolutionResult(List.of(
                new NodeResolutionResult.Decision(
                        "identity standard", NodeResolutionResult.Action.CREATE, 0, "Identity Standard"
                )
        )));

        service.resolveSubjects(
                source(USER_ID, "https://a.com/current"), SUMMARY, List.of("identity standard")
        );

        ArgumentCaptor<NodeResolutionInput> captor = ArgumentCaptor.forClass(NodeResolutionInput.class);
        verify(llmService).resolve(captor.capture());
        assertThat(captor.getValue().existingSubjects())
                .extracting(NodeResolutionInput.ExistingSubject::value)
                .containsExactly("JSON Web Token", "OAuth 2.0");
    }

    @Test
    @DisplayName("적절한 기존 Subject가 없으면 LLM이 제안한 값으로 새 노드를 만든다")
    void createsNewSubject() {
        when(llmService.resolve(any())).thenReturn(new NodeResolutionResult(List.of(
                new NodeResolutionResult.Decision(
                        "oidc protocol", NodeResolutionResult.Action.CREATE, 0, "OpenID Connect"
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
    @DisplayName("LLM은 Context에 제공하지 않은 Subject index를 재사용할 수 없다")
    void rejectsUnknownSubjectIndex() {
        when(llmService.resolve(any())).thenReturn(new NodeResolutionResult(List.of(
                new NodeResolutionResult.Decision(
                        "OIDC", NodeResolutionResult.Action.REUSE, 1, ""
                )
        )));

        assertThatThrownBy(() -> service.resolveSubjects(
                source(USER_ID, "https://a.com/current"), SUMMARY, List.of("OIDC")
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid existing subject");
    }

    @Test
    @DisplayName("신규 Subject 응답은 기존 Subject index 대신 0을 사용해야 한다")
    void rejectsExistingSubjectIndexForCreate() {
        when(llmService.resolve(any())).thenReturn(new NodeResolutionResult(List.of(
                new NodeResolutionResult.Decision(
                        "OIDC", NodeResolutionResult.Action.CREATE, 1, "OpenID Connect"
                )
        )));

        assertThatThrownBy(() -> service.resolveSubjects(
                source(USER_ID, "https://a.com/current"), SUMMARY, List.of("OIDC")
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid new subject");
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

        when(llmService.resolve(any())).thenReturn(new NodeResolutionResult(List.of(
                new NodeResolutionResult.Decision(
                        "OIDC", NodeResolutionResult.Action.CREATE, 0, "OIDC"
                )
        )));

        service.resolveSubjects(
                source(USER_ID, "https://a.com/current"), SUMMARY, List.of("OIDC")
        );

        ArgumentCaptor<NodeResolutionInput> captor = ArgumentCaptor.forClass(NodeResolutionInput.class);
        verify(llmService).resolve(captor.capture());
        assertThat(captor.getValue().existingSubjects()).isEmpty();
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

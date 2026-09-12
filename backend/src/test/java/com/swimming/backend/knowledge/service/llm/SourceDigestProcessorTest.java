package com.swimming.backend.knowledge.service.llm;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeRelation;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.SourceProcessingStatus;
import com.swimming.backend.knowledge.dto.in.SourceDigestResponse;
import com.swimming.backend.knowledge.dto.out.SourceDigestInput;
import com.swimming.backend.knowledge.dto.out.SourceDigestResult;
import com.swimming.backend.knowledge.dto.out.ResolvedNode;
import com.swimming.backend.knowledge.repository.InMemoryKnowledgeRepositories;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.domain.RelationType;
import com.swimming.backend.knowledge.service.data.KnowledgeNodeService;
import com.swimming.backend.knowledge.service.data.KnowledgeRelationService;
import com.swimming.backend.knowledge.service.data.KnowledgeSourceService;
import com.swimming.backend.knowledge.service.graph.NodeResolutionService;
import com.swimming.backend.knowledge.service.graph.SourceGraphWriter;
import com.swimming.backend.knowledge.service.llm.SourceDigestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.retry.NonTransientAiException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceDigestProcessorTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long FOLDER_ID = 10L;

    private InMemoryKnowledgeRepositories.Sources sources;
    private InMemoryKnowledgeRepositories.Nodes nodes;
    private InMemoryKnowledgeRepositories.Relations relations;
    private SourceDigestService digestService;
    private NodeResolutionService nodeResolutionService;
    private SourceGraphWriter graphWriter;
    private SourceDigestProcessor useCase;

    @BeforeEach
    void setUp() {
        sources = new InMemoryKnowledgeRepositories.Sources();
        nodes = new InMemoryKnowledgeRepositories.Nodes();
        relations = new InMemoryKnowledgeRepositories.Relations();
        digestService = mock(SourceDigestService.class);
        nodeResolutionService = mock(NodeResolutionService.class);

        when(nodeResolutionService.resolveSubjects(any(), any(), any())).thenAnswer(invocation -> {
            List<String> candidates = invocation.getArgument(2);
            return candidates.stream()
                    .map(candidate -> ResolvedNode.created(
                            candidate,
                            nodes.save(KnowledgeNode.create(
                                    USER_ID, NodeType.SUBJECT, candidate, null
                            ))
                    ))
                    .toList();
        });

        graphWriter = new SourceGraphWriter(
                new KnowledgeNodeService(nodes),
                new KnowledgeRelationService(relations)
        );

        useCase = new SourceDigestProcessor(
                new KnowledgeSourceService(sources),
                new KnowledgeNodeService(nodes),
                digestService,
                nodeResolutionService,
                graphWriter
        );
    }

    private List<KnowledgeRelation> relationsFrom(UUID fromNodeId, RelationType relationType) {
        return relations.findAllByFromNodeIdIn(List.of(fromNodeId), List.of(relationType));
    }

    private String titleOf(UUID nodeId) {
        return nodes.findById(nodeId).orElseThrow().getTitle();
    }

    private KnowledgeSource sourceNamed(String url) {
        KnowledgeSource source = KnowledgeSource.create(
                USER_ID, FOLDER_ID, "Spring AI MCP Reference", url, url
        );
        source.applyExtractedDocument(
                "Spring AI MCP Reference",
                digestibleContent(),
                "DOCS", "Spring", null
        );
        return source;
    }

    private KnowledgeSource savedSource() {
        KnowledgeSource source = KnowledgeSource.create(
                USER_ID, FOLDER_ID, "Spring AI MCP Reference",
                "https://docs.spring.io/mcp.html", "https://docs.spring.io/mcp.html"
        );
        source.applyExtractedDocument(
                "Spring AI MCP Reference",
                digestibleContent(),
                "DOCS", "Spring", null
        );
        return sources.save(source);
    }

    private String digestibleContent() {
        return "# MCP\n\nMCP Server를 구성하는 방법을 설명한다. ".repeat(4);
    }

    private KnowledgeSource savedSourceWithContent(String content) {
        KnowledgeSource source = KnowledgeSource.create(
                USER_ID, FOLDER_ID, "제목", "https://a.com", "https://a.com"
        );
        source.applyExtractedDocument("제목", content, "DOCS", null, null);
        return sources.save(source);
    }

    private SourceDigestResult digestResult() {
        return new SourceDigestResult(
                "Spring AI에서 MCP Server를 구성하는 방법을 설명한다.",
                "백엔드",
                "MCP 서버 구현하기",
                List.of("MCP", "Tool Calling")
        );
    }

    @Test
    @DisplayName("소화에 성공하면 Summary를 저장하고 완료 상태로 바꾼다")
    void completesDigestion() {
        KnowledgeSource source = savedSource();
        when(digestService.digest(any())).thenReturn(digestResult());

        SourceDigestResponse response = useCase.digest(USER_ID, source.getId());

        assertThat(response.status()).isEqualTo(SourceProcessingStatus.COMPLETED);
        assertThat(response.result().category()).isEqualTo("백엔드");
        assertThat(response.result().topic()).isEqualTo("MCP 서버 구현하기");
        assertThat(response.result().subjects()).containsExactly("MCP", "Tool Calling");
        assertThat(response.failureMessage()).isNull();
        assertThat(response.retryable()).isFalse();

        KnowledgeSource saved = sources.findById(source.getId()).orElseThrow();
        assertThat(saved.getProcessingStatus()).isEqualTo(SourceProcessingStatus.COMPLETED);
        assertThat(saved.getSummary()).isEqualTo("Spring AI에서 MCP Server를 구성하는 방법을 설명한다.");
        assertThat(saved.getAnalysisVersion()).isEqualTo(5);
    }

    @Test
    @DisplayName("소화에 실패해도 원문은 남기고 상태만 실패로 바꾼다")
    void keepsContentWhenDigestionFails() {
        KnowledgeSource source = savedSource();
        when(digestService.digest(any())).thenThrow(new RuntimeException("model timeout"));

        SourceDigestResponse response = useCase.digest(USER_ID, source.getId());

        assertThat(response.status()).isEqualTo(SourceProcessingStatus.FAILED);
        assertThat(response.result()).isNull();
        assertThat(response.failureMessage())
                .isEqualTo("SOURCE_DIGEST_FAILURE");
        assertThat(response.retryable()).isTrue();

        KnowledgeSource saved = sources.findById(source.getId()).orElseThrow();
        assertThat(saved.getProcessingStatus()).isEqualTo(SourceProcessingStatus.FAILED);
        assertThat(saved.getContent()).contains("MCP Server를 구성하는 방법");
        assertThat(saved.getSummary()).isNull();
        assertThat(saved.getFailureMessage()).isEqualTo(response.failureMessage());
        assertThat(saved.isRetryable()).isTrue();
    }

    @Test
    @DisplayName("이미 소화한 Source는 다시 부르지 않는다")
    void skipsAlreadyDigestedSource() {
        KnowledgeSource source = savedSource();
        when(digestService.digest(any())).thenReturn(digestResult());
        useCase.digest(USER_ID, source.getId());

        SourceDigestResponse second = useCase.digest(USER_ID, source.getId());

        assertThat(second.status()).isEqualTo(SourceProcessingStatus.COMPLETED);
        verify(digestService, org.mockito.Mockito.times(1)).digest(any());
    }

    @Test
    @DisplayName("문서 본문이 비어 있으면 부르지 않고 실패로 남긴다")
    void doesNotCallModelWithoutContent() {
        KnowledgeSource source = sources.save(KnowledgeSource.create(
                USER_ID, FOLDER_ID, "제목", "https://a.com", "https://a.com"
        ));

        SourceDigestResponse response = useCase.digest(USER_ID, source.getId());

        assertThat(response.status()).isEqualTo(SourceProcessingStatus.FAILED);
        assertThat(response.failureMessage())
                .isEqualTo("SOURCE_EMPTY_CONTENT");
        assertThat(response.retryable()).isFalse();
        verify(digestService, never()).digest(any());
    }

    @Test
    @DisplayName("본문이 정확히 100자면 LLM 호출 없이 SOURCE_NOT_DIGEST로 완료한다")
    void 본문이_100자면_LLM_소화를_생략한다() {
        KnowledgeSource source = savedSourceWithContent("가".repeat(100));

        SourceDigestResponse response = useCase.digest(USER_ID, source.getId());

        assertThat(response.status()).isEqualTo(SourceProcessingStatus.SOURCE_NOT_DIGEST);
        assertThat(response.failureMessage()).isNull();
        assertThat(response.retryable()).isFalse();
        assertThat(response.result()).isNull();
        verify(digestService, never()).digest(any());
        verify(nodeResolutionService, never()).resolveSubjects(any(), any(), any());
    }

    @Test
    @DisplayName("본문이 101자면 기존 LLM 소화 흐름을 실행한다")
    void 본문이_101자면_LLM으로_소화한다() {
        KnowledgeSource source = savedSourceWithContent("가".repeat(101));
        when(digestService.digest(any())).thenReturn(digestResult());

        SourceDigestResponse response = useCase.digest(USER_ID, source.getId());

        assertThat(response.status()).isEqualTo(SourceProcessingStatus.COMPLETED);
        verify(digestService).digest(any());
        verify(nodeResolutionService).resolveSubjects(any(), any(), any());
    }

    @Test
    @DisplayName("AI의 잘못된 요청 오류는 사용자에게 원문을 숨기고 재시도 불가로 남긴다")
    void hidesNonTransientAiFailure() {
        KnowledgeSource source = savedSource();
        when(digestService.digest(any()))
                .thenThrow(new NonTransientAiException("400 invalid api request: secret detail"));

        SourceDigestResponse response = useCase.digest(USER_ID, source.getId());

        assertThat(response.failureMessage())
                .isEqualTo("SOURCE_DIGEST_NON_RETRYABLE_FAILURE")
                .doesNotContain("secret detail");
        assertThat(response.retryable()).isFalse();
    }

    @Test
    @DisplayName("개념 연결 실패는 별도 사용자 메시지와 함께 저장한다")
    void reportsSubjectResolutionFailure() {
        KnowledgeSource source = savedSource();
        when(digestService.digest(any())).thenReturn(digestResult());
        doThrow(new RuntimeException("embedding provider unavailable"))
                .when(nodeResolutionService).resolveSubjects(any(), any(), any());

        SourceDigestResponse response = useCase.digest(USER_ID, source.getId());

        assertThat(response.failureMessage())
                .isEqualTo("SOURCE_DIGEST_RESOLUTION_FAILURE")
                .doesNotContain("embedding provider unavailable");
        assertThat(response.retryable()).isTrue();

        KnowledgeSource saved = sources.findById(source.getId()).orElseThrow();
        assertThat(saved.getFailureMessage()).isEqualTo(response.failureMessage());
    }

    @Test
    @DisplayName("남의 Source는 소화할 수 없다")
    void rejectsOtherUsersSource() {
        KnowledgeSource source = savedSource();

        assertThatThrownBy(() -> useCase.digest(OTHER_USER_ID, source.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.KNOWLEDGE_NODE_NOT_FOUND);
    }

    @Test
    @DisplayName("그래프 반영이 실패해도 요약은 남기고 완료 상태를 유지한다")
    void keepsSummaryWhenGraphWriteFails() {
        KnowledgeSource source = savedSource();
        when(digestService.digest(any())).thenReturn(digestResult());

        SourceGraphWriter failing = mock(SourceGraphWriter.class);
        doThrow(new RuntimeException("db down")).when(failing).write(any(), any(), any());
        useCase = new SourceDigestProcessor(
                new KnowledgeSourceService(sources),
                new KnowledgeNodeService(nodes),
                digestService,
                nodeResolutionService,
                failing
        );

        SourceDigestResponse response = useCase.digest(USER_ID, source.getId());

        assertThat(response.status()).isEqualTo(SourceProcessingStatus.COMPLETED);

        KnowledgeSource saved = sources.findById(source.getId()).orElseThrow();
        assertThat(saved.getProcessingStatus()).isEqualTo(SourceProcessingStatus.COMPLETED);
        assertThat(saved.getSummary()).isNotNull();
    }

    @Test
    @DisplayName("없는 Source면 찾을 수 없다고 알린다")
    void rejectsMissingSource() {
        assertThatThrownBy(() -> useCase.digest(USER_ID, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.KNOWLEDGE_NODE_NOT_FOUND);
    }

    @Test
    @DisplayName("모델이 Topic을 주지 않으면 소화를 실패로 돌린다")
    void failsDigestionWithoutTopic() {
        // Topic 없는 COMPLETED Source를 남기면 되살릴 경로가 없다. 원문만 남기고 실패로 둔다.
        KnowledgeSource source = savedSource();
        when(digestService.digest(any())).thenReturn(new SourceDigestResult(
                "요약입니다.", "백엔드", " ", List.of("MCP", "Tool Calling")
        ));

        SourceDigestResponse response = useCase.digest(USER_ID, source.getId());

        assertThat(response.status()).isEqualTo(SourceProcessingStatus.FAILED);
        assertThat(response.failureMessage())
                .isEqualTo("SOURCE_DIGEST_FAILURE");
        assertThat(response.retryable()).isTrue();

        KnowledgeSource saved = sources.findById(source.getId()).orElseThrow();
        assertThat(saved.getContent()).contains("MCP Server를 구성하는 방법");
        assertThat(saved.getSummary()).isNull();
        assertThat(relations.findAllByFromNodeIdIn(
                List.of(saved.getNode().getId()), List.of(RelationType.values())
        )).isEmpty();
        assertThat(nodes.findAllByUserIdAndNodeType(USER_ID, NodeType.TOPIC)).isEmpty();
    }

    @Test
    @DisplayName("다루는 개념이 없으면 목적 연결만 남는다")
    void leavesOnlyTopicRelationWithoutSubjects() {
        KnowledgeSource source = savedSource();
        when(digestService.digest(any())).thenReturn(new SourceDigestResult(
                "요약입니다.", "백엔드", "MCP 서버 구현하기", List.of()
        ));

        SourceDigestResponse response = useCase.digest(USER_ID, source.getId());

        assertThat(response.status()).isEqualTo(SourceProcessingStatus.COMPLETED);

        UUID sourceNodeId = sources.findById(source.getId()).orElseThrow().getNode().getId();
        assertThat(relationsFrom(sourceNodeId, RelationType.ABOUT)).isEmpty();

        UUID topicId = relationsFrom(sourceNodeId, RelationType.SUPPORTS).getFirst().getToNodeId();
        assertThat(titleOf(topicId)).isEqualTo("MCP 서버 구현하기");
        assertThat(relationsFrom(topicId, RelationType.INVOLVES)).isEmpty();
    }

    @Test
    @DisplayName("소화 입력에 Source의 제목·URL·원문을 그대로 넘긴다")
    void passesDocumentToModel() {
        KnowledgeSource source = savedSource();
        when(digestService.digest(any())).thenReturn(digestResult());

        useCase.digest(USER_ID, source.getId());

        ArgumentCaptor<SourceDigestInput> captor = ArgumentCaptor.forClass(SourceDigestInput.class);
        verify(digestService).digest(captor.capture());

        SourceDigestInput input = captor.getValue();
        assertThat(input.title()).isEqualTo("Spring AI MCP Reference");
        assertThat(input.url()).isEqualTo("https://docs.spring.io/mcp.html");
        assertThat(input.content()).contains("MCP Server를 구성하는 방법");
    }

    @Test
    @DisplayName("소화가 실패하면 그래프에는 아무것도 남기지 않는다")
    void writesNothingToGraphWhenDigestionFails() {
        KnowledgeSource source = savedSource();
        when(digestService.digest(any())).thenThrow(new RuntimeException("model timeout"));

        useCase.digest(USER_ID, source.getId());

        UUID sourceNodeId = sources.findById(source.getId()).orElseThrow().getNode().getId();
        assertThat(relationsFrom(sourceNodeId, RelationType.ABOUT)).isEmpty();
        assertThat(relationsFrom(sourceNodeId, RelationType.SUPPORTS)).isEmpty();
        assertThat(nodes.findAllByUserIdAndNodeType(USER_ID, NodeType.SUBJECT)).isEmpty();
    }

    @Test
    @DisplayName("이미 만든 Topic 이름을 참고 맥락으로 넘겨 이름이 갈라지지 않게 한다")
    void passesExistingTopicsAsContext() {
        when(digestService.digest(any())).thenReturn(digestResult());
        useCase.digest(USER_ID, savedSource().getId());

        KnowledgeSource second = sources.save(sourceNamed("https://tech.kakao.com/mcp"));
        useCase.digest(USER_ID, second.getId());

        ArgumentCaptor<SourceDigestInput> captor = ArgumentCaptor.forClass(SourceDigestInput.class);
        verify(digestService, org.mockito.Mockito.times(2)).digest(captor.capture());

        assertThat(captor.getAllValues().getFirst().existingTopics())
                .as("첫 소화 시점에는 참고할 Topic이 없다")
                .isEmpty();
        assertThat(captor.getAllValues().getLast().existingTopics())
                .containsExactly("MCP 서버 구현하기");
    }

    @Test
    @DisplayName("참고 맥락에는 Subject와 다른 사용자의 Topic을 넣지 않는다")
    void limitsExistingTopicsToOwnTopics() {
        nodes.save(KnowledgeNode.create(OTHER_USER_ID, NodeType.TOPIC, "남의 목적", null));

        when(digestService.digest(any())).thenReturn(digestResult());
        useCase.digest(USER_ID, savedSource().getId());

        KnowledgeSource second = sources.save(sourceNamed("https://tech.kakao.com/mcp"));
        useCase.digest(USER_ID, second.getId());

        ArgumentCaptor<SourceDigestInput> captor = ArgumentCaptor.forClass(SourceDigestInput.class);
        verify(digestService, org.mockito.Mockito.times(2)).digest(captor.capture());

        assertThat(captor.getAllValues().getLast().existingTopics())
                .doesNotContain("남의 목적", "MCP", "Tool Calling");
    }
}

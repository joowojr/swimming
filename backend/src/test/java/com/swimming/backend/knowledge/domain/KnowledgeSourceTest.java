package com.swimming.backend.knowledge.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeSourceTest {

    private static final Long USER_ID = 1L;
    private static final Long FOLDER_ID = 10L;
    private static final String URL = "https://docs.spring.io/spring-ai/reference/api/mcp.html?utm_source=x";
    private static final String CANONICAL_URL = "https://docs.spring.io/spring-ai/reference/api/mcp.html";

    @Test
    @DisplayName("Source를 만들면 SOURCE 노드와 처리 대기 상태를 함께 갖는다")
    void createsSourceWithPendingStatus() {
        KnowledgeSource source = KnowledgeSource.create(
                USER_ID,
                FOLDER_ID,
                "Spring AI MCP Reference",
                URL,
                CANONICAL_URL
        );

        assertThat(source.getNode().getNodeType()).isEqualTo(NodeType.SOURCE);
        assertThat(source.getNode().getTitle()).isEqualTo("Spring AI MCP Reference");
        assertThat(source.getId()).isEqualTo(source.getNode().getId());
        assertThat(source.getUserId()).isEqualTo(USER_ID);
        assertThat(source.getFolderId()).isEqualTo(FOLDER_ID);
        assertThat(source.getProcessingStatus()).isEqualTo(SourceProcessingStatus.PENDING);
        assertThat(source.getSummary()).isNull();
    }

    @Test
    @DisplayName("본문 추출 결과로 제목과 문서 메타데이터를 채운다")
    void appliesExtractedDocument() {
        KnowledgeSource source = KnowledgeSource.create(USER_ID, FOLDER_ID, "제목 없음", URL, CANONICAL_URL);
        Instant publishedAt = Instant.parse("2026-03-01T00:00:00Z");

        source.applyExtractedDocument(
                "Model Context Protocol",
                "MCP Server를 구성하는 방법...",
                "DOCS",
                "Spring",
                publishedAt
        );

        assertThat(source.getNode().getTitle()).isEqualTo("Model Context Protocol");
        assertThat(source.getContent()).isEqualTo("MCP Server를 구성하는 방법...");
        assertThat(source.getSourceType()).isEqualTo("DOCS");
        assertThat(source.getAuthor()).isEqualTo("Spring");
        assertThat(source.getPublishedAt()).isEqualTo(publishedAt);
    }

    @Test
    @DisplayName("소화가 끝나면 Summary와 분석 버전을 남기고 완료로 바꾼다")
    void completesDigestion() {
        KnowledgeSource source = KnowledgeSource.create(USER_ID, FOLDER_ID, "제목", URL, CANONICAL_URL);

        source.startDigestion();
        source.completeDigestion("MCP Server 구성 방법을 설명한다.", 1);

        assertThat(source.getProcessingStatus()).isEqualTo(SourceProcessingStatus.COMPLETED);
        assertThat(source.getSummary()).isEqualTo("MCP Server 구성 방법을 설명한다.");
        assertThat(source.getAnalysisVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("AI 소화가 실패해도 원문은 남고 상태만 실패로 바뀐다")
    void keepsContentWhenDigestionFails() {
        KnowledgeSource source = KnowledgeSource.create(USER_ID, FOLDER_ID, "제목", URL, CANONICAL_URL);
        source.applyExtractedDocument("제목", "원문", null, null, null);

        source.startDigestion();
        source.failDigestion();

        assertThat(source.getProcessingStatus()).isEqualTo(SourceProcessingStatus.FAILED);
        assertThat(source.getContent()).isEqualTo("원문");
        assertThat(source.getSummary()).isNull();
    }

    @Test
    @DisplayName("읽음으로 표시하면 읽은 시각이 남고, 해제하면 시각도 함께 사라진다")
    void marksReadAndUnread() {
        KnowledgeSource source = pendingSource();
        Instant readAt = Instant.parse("2026-09-07T09:00:00Z");

        assertThat(source.getReadAt()).isNull();

        source.markRead(readAt);
        assertThat(source.getReadAt()).isEqualTo(readAt);

        source.markUnread();
        assertThat(source.getReadAt()).isNull();
    }

    @Test
    @DisplayName("이미 읽은 링크를 다시 표시해도 처음 읽은 시각을 지킨다")
    void keepsFirstReadAt() {
        KnowledgeSource source = pendingSource();
        Instant first = Instant.parse("2026-09-07T09:00:00Z");

        source.markRead(first);
        source.markRead(first.plusSeconds(600));

        assertThat(source.getReadAt()).isEqualTo(first);
    }

    private KnowledgeSource pendingSource() {
        return KnowledgeSource.create(USER_ID, FOLDER_ID, "Spring AI MCP Reference", URL, CANONICAL_URL);
    }

    @Test
    @DisplayName("본문이 있는 실패 Source는 다시 분석 대기 상태로 돌릴 수 있다")
    void preparesFailedSourceForRetry() {
        KnowledgeSource source = KnowledgeSource.create(USER_ID, FOLDER_ID, "제목", URL, CANONICAL_URL);
        source.applyExtractedDocument("제목", "원문", null, null, null);
        source.failDigestion();

        source.prepareRetry();

        assertThat(source.getProcessingStatus()).isEqualTo(SourceProcessingStatus.PENDING);
        assertThat(source.getContent()).isEqualTo("원문");
    }

    @Test
    @DisplayName("완료했거나 본문이 없는 Source는 다시 분석할 수 없다")
    void rejectsNonRetryableSource() {
        KnowledgeSource completed = KnowledgeSource.create(USER_ID, FOLDER_ID, "완료", URL, CANONICAL_URL);
        completed.applyExtractedDocument("완료", "원문", null, null, null);
        completed.completeDigestion("요약", 1);
        KnowledgeSource empty = KnowledgeSource.create(USER_ID, FOLDER_ID, "빈 문서", URL, CANONICAL_URL);

        assertThatThrownBy(completed::prepareRetry)
                .isInstanceOf(com.swimming.backend.common.exception.BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(com.swimming.backend.common.exception.ErrorCode.KNOWLEDGE_SOURCE_NOT_RETRYABLE);
        assertThatThrownBy(empty::prepareRetry)
                .isInstanceOf(com.swimming.backend.common.exception.BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(com.swimming.backend.common.exception.ErrorCode.KNOWLEDGE_SOURCE_NOT_RETRYABLE);
    }
}

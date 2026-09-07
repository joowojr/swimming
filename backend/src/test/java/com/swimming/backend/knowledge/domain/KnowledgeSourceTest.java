package com.swimming.backend.knowledge.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

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
}

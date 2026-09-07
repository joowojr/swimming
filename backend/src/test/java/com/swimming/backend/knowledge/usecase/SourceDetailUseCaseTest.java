package com.swimming.backend.knowledge.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.domain.RelationOrigin;
import com.swimming.backend.knowledge.domain.SourceProcessingStatus;
import com.swimming.backend.knowledge.dto.in.NodeRef;
import com.swimming.backend.knowledge.dto.in.SourceDetailResponse;
import com.swimming.backend.knowledge.repository.InMemoryKnowledgeRepositories;
import com.swimming.backend.knowledge.service.KnowledgeRelationService;
import com.swimming.backend.knowledge.service.KnowledgeSourceService;
import com.swimming.backend.knowledge.service.SourceConceptReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceDetailUseCaseTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long FOLDER_ID = 10L;
    private static final Instant PUBLISHED_AT = Instant.parse("2026-03-01T00:00:00Z");

    private InMemoryKnowledgeRepositories.Sources sources;
    private InMemoryKnowledgeRepositories.Nodes nodes;
    private InMemoryKnowledgeRepositories.Relations relations;

    private SourceDetailUseCase useCase;

    @BeforeEach
    void setUp() {
        sources = new InMemoryKnowledgeRepositories.Sources();
        nodes = new InMemoryKnowledgeRepositories.Nodes();
        relations = new InMemoryKnowledgeRepositories.Relations();

        useCase = new SourceDetailUseCase(
                new KnowledgeSourceService(sources),
                new SourceConceptReader(relations, nodes)
        );
    }

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

        KnowledgeNode mcp = nodes.save(KnowledgeNode.create(USER_ID, NodeType.SUBJECT, "MCP", null));
        KnowledgeNode topic = nodes.save(
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

package com.swimming.backend.knowledge.service.graph;

import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.dto.out.CategoryAssignmentDecision;
import com.swimming.backend.knowledge.dto.out.CategoryAssignmentInput;
import com.swimming.backend.knowledge.dto.out.SourceDigestResult;
import com.swimming.backend.knowledge.service.data.KnowledgeNodeService;
import com.swimming.backend.knowledge.service.llm.CategoryAssignmentDecider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoryAssignmentServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long FOLDER_ID = 10L;

    private final KnowledgeNode mcp = KnowledgeNode.create(USER_ID, NodeType.CATEGORY, "MCP 서버 구현", null);

    private KnowledgeNodeService nodeService;
    private CategoryAssignmentDecider decider;
    private SourceGraphWriter graphWriter;
    private CategoryAssignmentService service;

    @BeforeEach
    void setUp() {
        nodeService = mock(KnowledgeNodeService.class);
        decider = mock(CategoryAssignmentDecider.class);
        graphWriter = mock(SourceGraphWriter.class);
        service = new CategoryAssignmentService(nodeService, decider, graphWriter);
        when(nodeService.findCategoriesInFolder(USER_ID, FOLDER_ID)).thenReturn(List.of(mcp));
    }

    private KnowledgeSource completedSource() {
        KnowledgeSource source = KnowledgeSource.create(USER_ID, FOLDER_ID, "문서", "https://a.com", "https://a.com");
        source.applyExtractedDocument("문서", "본문", "article", "작성자", null);
        source.startDigestion();
        source.completeDigestion("요약", 1);
        return source;
    }

    private SourceDigestResult digest(String proposedCategory) {
        return new SourceDigestResult("요약", proposedCategory, "목적", List.of("개념"));
    }

    @Test
    @DisplayName("제안 이름이 기존 Category와 정규화 기준으로 같으면 판정기를 부르지 않고 그 Category에 담는다")
    void 같은_이름은_판정기_없이_재사용한다() {
        KnowledgeSource source = completedSource();

        service.assign(source, digest("mcp서버 구현"));

        verify(decider, never()).decide(any());
        verify(graphWriter).createCategoryAssignmentInTransaction(source, new CategoryAssignmentDecision.Reuse(mcp.getId()));
    }

    @Test
    @DisplayName("제안 이름이 기존 이름과 다르면 판정기의 결과를 저장한다")
    void 다른_이름은_판정기에_맡긴다() {
        KnowledgeSource source = completedSource();
        when(decider.decide(any())).thenReturn(new CategoryAssignmentDecision.Create("WAL 정리"));

        service.assign(source, digest("WAL 정리"));

        ArgumentCaptor<CategoryAssignmentInput> input = ArgumentCaptor.forClass(CategoryAssignmentInput.class);
        verify(decider).decide(input.capture());
        assertThat(input.getValue().proposedCategoryTitle()).isEqualTo("WAL 정리");
        verify(graphWriter).createCategoryAssignmentInTransaction(source, new CategoryAssignmentDecision.Create("WAL 정리"));
    }

    @Test
    @DisplayName("제안 이름이 비면 이름 비교 없이 판정기에 재사용 판정을 맡긴다")
    void 빈_이름은_판정기에_맡긴다() {
        KnowledgeSource source = completedSource();
        when(decider.decide(any())).thenReturn(new CategoryAssignmentDecision.Skip());

        service.assign(source, digest(""));

        verify(decider).decide(any());
    }

    @Test
    @DisplayName("폴더에 Category 구성이 없으면 판정도 저장도 하지 않는다")
    void 구성이_없으면_건너뛴다() {
        when(nodeService.findCategoriesInFolder(USER_ID, FOLDER_ID)).thenReturn(List.of());

        service.assign(completedSource(), digest("MCP 서버 구현"));

        verify(decider, never()).decide(any());
        verify(graphWriter, never()).createCategoryAssignmentInTransaction(any(), any());
    }
}

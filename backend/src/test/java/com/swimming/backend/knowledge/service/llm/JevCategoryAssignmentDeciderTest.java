package com.swimming.backend.knowledge.service.llm;

import com.swimming.backend.common.client.TypeSafeClient;
import com.swimming.backend.common.client.dto.SystemOneRequest;
import com.swimming.backend.common.client.dto.SystemOneResponse;
import com.swimming.backend.knowledge.dto.in.NodeRef;
import com.swimming.backend.knowledge.dto.out.CategoryAssignmentDecision;
import com.swimming.backend.knowledge.dto.out.CategoryAssignmentInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JevCategoryAssignmentDeciderTest {

    private static final NodeRef MCP = new NodeRef(UUID.randomUUID(), "MCP 서버 구현");
    private static final NodeRef WAL = new NodeRef(UUID.randomUUID(), "WAL 정리");

    private TypeSafeClient client;
    private JevCategoryAssignmentDecider decider;

    @BeforeEach
    void setUp() {
        client = mock(TypeSafeClient.class);
        decider = new JevCategoryAssignmentDecider(client);
    }

    private CategoryAssignmentInput input(String proposedCategoryTitle) {
        return new CategoryAssignmentInput("제목", "MCP 서버를 구성하는 방법을 설명한다.",
                proposedCategoryTitle, List.of(MCP, WAL));
    }

    private void answer(String choice, Map<String, Double> probabilities) {
        when(client.systemOne(any())).thenReturn(new SystemOneResponse("jev-1.13.0",
                Map.of("assignment", new SystemOneResponse.Choice(choice, probabilities, 0.9)), null));
    }

    private SystemOneRequest sentRequest() {
        ArgumentCaptor<SystemOneRequest> request = ArgumentCaptor.forClass(SystemOneRequest.class);
        verify(client).systemOne(request.capture());
        return request.getValue();
    }

    private Map<String, ?> criteriaOf(SystemOneRequest request) {
        return ((SystemOneRequest.Choice) request.questions().get("assignment")).criteria();
    }

    @Test
    @DisplayName("제안 이름이 있으면 NEW를 선택지로 두고 제안 이름과 요약을 함께 보낸다")
    void 제안_이름이_있으면_NEW를_둔다() {
        answer("NEW", Map.of("1", 0.1, "2", 0.1, "NEW", 0.8));

        CategoryAssignmentDecision decision = decider.decide(input("PDF 압축"));

        assertThat(decision).isEqualTo(new CategoryAssignmentDecision.Create("PDF 압축"));
        SystemOneRequest request = sentRequest();
        assertThat(criteriaOf(request).keySet()).containsExactly("1", "2", "NEW");
        assertThat(request.state()).isEqualTo(Map.of(
                "proposedCategoryTitle", "PDF 압축", "summary", "MCP 서버를 구성하는 방법을 설명한다."));
    }

    @Test
    @DisplayName("제안 이름이 비면 NEW 대신 NONE을 두고 요약만 보낸다")
    void 제안_이름이_비면_NONE을_둔다() {
        answer("1", Map.of("1", 0.8, "2", 0.1, "NONE", 0.1));

        CategoryAssignmentDecision decision = decider.decide(input(" "));

        assertThat(decision).isEqualTo(new CategoryAssignmentDecision.Reuse(MCP.nodeId()));
        SystemOneRequest request = sentRequest();
        assertThat(criteriaOf(request).keySet()).containsExactly("1", "2", "NONE");
        assertThat(request.state()).isEqualTo(Map.of("summary", "MCP 서버를 구성하는 방법을 설명한다."));
    }

    @Test
    @DisplayName("제안 이름 없이 맞는 기존 Category가 없다고 판정하면 배정하지 않는다")
    void NONE이면_건너뛴다() {
        answer("NONE", Map.of("1", 0.1, "2", 0.1, "NONE", 0.8));

        CategoryAssignmentDecision decision = decider.decide(input(null));

        assertThat(decision).isEqualTo(new CategoryAssignmentDecision.Skip());
    }
}

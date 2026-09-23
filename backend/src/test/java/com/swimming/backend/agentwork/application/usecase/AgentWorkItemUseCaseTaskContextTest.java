package com.swimming.backend.agentwork.application.usecase;

import com.swimming.backend.agentwork.domain.BoardLane;
import com.swimming.backend.agentwork.domain.WorkResourceType;
import com.swimming.backend.agentwork.application.dto.AgentWorkItemRow;
import com.swimming.backend.agentwork.application.service.AgentSessionEventReadService;
import com.swimming.backend.agentwork.application.service.AgentSessionReadService;
import com.swimming.backend.agentwork.application.service.AgentWorkItemReadService;
import com.swimming.backend.agentwork.application.service.AgentWorkItemWriteService;
import com.swimming.backend.agentwork.application.port.LinkedSource;
import com.swimming.backend.agentwork.application.port.WorkItem;
import com.swimming.backend.agentwork.application.port.WorkItemId;
import com.swimming.backend.agentwork.application.port.WorkItemReader;
import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentWorkItemUseCaseTaskContextTest {
    private static final Instant CREATED_AT = Instant.parse("2026-09-18T00:00:00Z");

    private final AgentWorkItemReadService workItemReadService = mock(AgentWorkItemReadService.class);
    private final WorkItemReader workItemReader = mock(WorkItemReader.class);
    private final AgentWorkItemUseCase useCase = new AgentWorkItemUseCase(workItemReadService,
            mock(AgentWorkItemWriteService.class), mock(AgentSessionReadService.class),
            mock(AgentSessionEventReadService.class), workItemReader);

    @Test
    @DisplayName("보드에 없는 Task도 연결된 지식과 함께 시작 전 맥락으로 돌려준다")
    void readsUnregisteredTask() {
        LinkedSource source = new LinkedSource(UUID.randomUUID(), "설계 문서", "https://example.com", "요약");
        when(workItemReader.readAll(1L, List.of(id("007")))).thenReturn(Map.of(id("007"), task()));
        when(workItemReadService.findBoardItems(1L)).thenReturn(List.of());
        when(workItemReader.readLinkedSources(1L, id("7"))).thenReturn(List.of(source));

        var context = useCase.getTaskContext(1L, id("007"));

        assertThat(context.workItemId()).isNull();
        assertThat(context.lane()).isEqualTo(BoardLane.NOT_STARTED);
        assertThat(context.session()).isNull();
        assertThat(context.task().id()).isEqualTo("7");
        assertThat(context.task().title()).isEqualTo("MCP 서버 구현하기");
        assertThat(context.linkedSources()).containsExactly(source);
    }

    @Test
    @DisplayName("보드에 등록된 Task는 카드 식별자를 함께 돌려준다")
    void readsRegisteredTask() {
        when(workItemReader.readAll(1L, List.of(id("7")))).thenReturn(Map.of(id("7"), task()));
        when(workItemReadService.findBoardItems(1L)).thenReturn(List.of(
                new AgentWorkItemRow(30L, WorkResourceType.SWIMMING_TASK, "7", null, CREATED_AT)));
        when(workItemReader.readLinkedSources(1L, id("7"))).thenReturn(List.of());

        var context = useCase.getTaskContext(1L, id("7"));

        assertThat(context.workItemId()).isEqualTo(30L);
        assertThat(context.lane()).isEqualTo(BoardLane.NOT_STARTED);
        verify(workItemReader).readLinkedSources(1L, id("7"));
    }

    @Test
    @DisplayName("타인·삭제·존재하지 않는 Task는 TASK_NOT_FOUND로 거절한다")
    void rejectsUnavailableTask() {
        when(workItemReader.readAll(1L, List.of(id("7")))).thenReturn(Map.of());

        assertThatThrownBy(() -> useCase.getTaskContext(1L, id("7")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND));
    }

    private WorkItem task() {
        return WorkItem.builder().type(WorkResourceType.SWIMMING_TASK).id("7").title("MCP 서버 구현하기")
                .containerId(3L).containerName("업무").status(0).createdAt(CREATED_AT).build();
    }

    private WorkItemId id(String value) {
        return WorkItemId.builder().type(WorkResourceType.SWIMMING_TASK).id(value).build();
    }
}

package com.swimming.backend.agentwork.interfaces.router;

import com.swimming.backend.agentwork.domain.AgentWorkStatus;
import com.swimming.backend.agentwork.application.event.AgentWorkStatusChangedEvent;
import com.swimming.backend.agentwork.application.service.AgentWorkSseService;
import com.swimming.backend.common.security.AuthUser;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

class AgentWorkEventsStreamTest {
    private static final Instant NOW = Instant.parse("2026-09-18T05:00:00Z");

    private final AgentWorkSseService service = new AgentWorkSseService();

    @Test
    @DisplayName("연결하면 재연결 간격을 알리고 스트림을 연다")
    void opensStreamWithRetry() throws Exception {
        MvcResult tab = connect(1L);

        assertThat(tab.getResponse().getContentType()).startsWith("text/event-stream");
        assertThat(tab.getResponse().getContentAsString()).contains("retry:3000").contains(":connected");
    }

    @Test
    @DisplayName("변경 알림은 해당 사용자의 모든 탭에만 전달되고 사용자 식별자는 싣지 않는다")
    void deliversOnlyToOwnerTabs() throws Exception {
        MvcResult firstTab = connect(1L);
        MvcResult secondTab = connect(1L);
        MvcResult otherUser = connect(2L);

        service.publish(new AgentWorkStatusChangedEvent(1L, 10L, AgentWorkStatus.COMPLETED, NOW));

        for (MvcResult tab : new MvcResult[]{firstTab, secondTab}) {
            assertThat(tab.getResponse().getContentAsString())
                    .contains("event:agent-work")
                    .contains("\"sessionId\":10")
                    .contains("\"status\":\"COMPLETED\"")
                    .doesNotContain("userId");
        }
        assertThat(otherUser.getResponse().getContentAsString()).doesNotContain("event:agent-work");
    }

    @Test
    @DisplayName("타임아웃으로 끝난 연결에는 더 이상 전송하지 않는다")
    void removesTimedOutConnection() throws Exception {
        MvcResult tab = connect(1L);
        MockAsyncContext asyncContext = (MockAsyncContext) tab.getRequest().getAsyncContext();
        for (AsyncListener listener : asyncContext.getListeners()) {
            listener.onTimeout(new AsyncEvent(asyncContext));
        }
        String before = tab.getResponse().getContentAsString();

        service.publish(new AgentWorkStatusChangedEvent(1L, 10L, AgentWorkStatus.WORKING, NOW));

        assertThat(tab.getResponse().getContentAsString()).isEqualTo(before);
    }

    private MvcResult connect(Long userId) throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new AgentWorkEventsController(service))
                .setCustomArgumentResolvers(new AuthUserArgumentResolver(new AuthUser(userId, null)))
                .build();
        return mockMvc.perform(get("/api/agent-work/events")).andExpect(request().asyncStarted()).andReturn();
    }
}

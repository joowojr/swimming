package com.swimming.backend.agentwork.interfaces.router;

import com.swimming.backend.agentwork.interfaces.router.dto.CreateAgentAccessTokenRequest;
import com.swimming.backend.agentwork.application.dto.IssuedAgentAccessTokenResponse;
import com.swimming.backend.agentwork.infra.persistence.AgentAccessTokenRepository;
import com.swimming.backend.agentwork.infra.persistence.entity.AgentAccessTokenEntity;
import com.swimming.backend.agentwork.application.service.AgentAccessTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PAT가 실제 보안 필터 체인에서 어디까지 인증되는지 검증한다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agent-access-token;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never",
        "spring.ai.openai.api-key=test",
        "app.place.background.cdn-base-url=https://cdn.example.com"
})
@AutoConfigureMockMvc
class AgentAccessTokenIntegrationTest {
    private static final AtomicLong USER_IDS = new AtomicLong(9_000);
    private static final String MCP_INITIALIZE = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",
            "capabilities":{},"clientInfo":{"name":"test","version":"0"}}}""";

    @Autowired private MockMvc mockMvc;
    @Autowired private AgentAccessTokenService tokenService;
    @Autowired private AgentAccessTokenRepository repository;

    @Test
    @DisplayName("발급한 PAT로 Agent Work API와 MCP 초기화를 인증하고 서버 안내와 마지막 사용 시각을 남긴다")
    void authenticatesAgentPaths() throws Exception {
        var issued = issue(USER_IDS.incrementAndGet());

        mockMvc.perform(get("/api/agent-work/board").header("Authorization", bearer(issued.token())))
                .andExpect(status().isOk());
        var initialized = mockMvc.perform(post("/mcp").header("Authorization", bearer(issued.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .content(MCP_INITIALIZE))
                .andExpect(status().isOk())
                .andReturn();
        // 서버 instructions는 자바 코드에서 넣는다.
        assertThat(initialized.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("Swimming Cowork Board");

        assertThat(repository.findByTokenHash(tokenService.hash(issued.token())).orElseThrow().getLastUsedAt())
                .isNotNull();
    }

    @Test
    @DisplayName("PAT는 Agent Work 밖의 일반 API 인증에 쓰이지 않는다")
    void rejectsGeneralApi() throws Exception {
        var issued = issue(USER_IDS.incrementAndGet());

        mockMvc.perform(get("/api/folders").header("Authorization", bearer(issued.token())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PAT로는 토큰 관리 API를 쓸 수 없어 새 PAT 발급·목록 조회·폐기가 막힌다")
    void rejectsTokenManagementWithPat() throws Exception {
        var issued = issue(USER_IDS.incrementAndGet());

        mockMvc.perform(post("/api/agent-work/tokens").header("Authorization", bearer(issued.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"탈취\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/agent-work/tokens").header("Authorization", bearer(issued.token())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/agent-work/tokens/" + issued.id()).header("Authorization", bearer(issued.token())))
                .andExpect(status().isUnauthorized());
        assertThat(repository.findByTokenHash(tokenService.hash(issued.token())).orElseThrow().getRevokedAt()).isNull();
    }

    @Test
    @DisplayName("폐기·만료·존재하지 않는 PAT는 Agent Work API와 MCP에서 거절한다")
    void rejectsInvalidTokens() throws Exception {
        Long userId = USER_IDS.incrementAndGet();
        var revoked = issue(userId);
        tokenService.revoke(userId, revoked.id());
        String expired = "swm_pat_expired-" + userId;
        repository.save(AgentAccessTokenEntity.issue(userId, "만료", "swm_pat_", "xxxxxx", tokenService.hash(expired),
                "agent-work:read,agent-work:write", Instant.now().minus(1, ChronoUnit.DAYS)));

        for (String token : new String[]{revoked.token(), expired, "swm_pat_unknown"}) {
            mockMvc.perform(get("/api/agent-work/board").header("Authorization", bearer(token)))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(post("/mcp").header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                            .content(MCP_INITIALIZE))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("PAT 원문은 DB에 저장되지 않고 발급 뒤 목록 조회에서 다시 내려가지 않는다")
    void neverStoresOrReturnsRawToken() throws Exception {
        Long userId = USER_IDS.incrementAndGet();
        var issued = issue(userId);

        AgentAccessTokenEntity stored = repository.findByTokenHash(tokenService.hash(issued.token())).orElseThrow();
        assertThat(stored.getTokenHash()).isNotEqualTo(issued.token()).hasSize(64);
        assertThat(JsonMapper.builder().build().writeValueAsString(stored)).doesNotContain(issued.token());
        assertThat(JsonMapper.builder().build().writeValueAsString(tokenService.findAll(userId)))
                .doesNotContain(issued.token());
    }

    @Test
    @DisplayName("다른 사용자의 PAT는 폐기할 수 없다")
    void rejectsRevokingOthersToken() {
        var issued = issue(USER_IDS.incrementAndGet());

        assertThatThrownBy(() -> tokenService.revoke(USER_IDS.incrementAndGet(), issued.id()))
                .hasMessageContaining("찾을 수 없습니다");
        assertThat(repository.findByTokenHash(tokenService.hash(issued.token())).orElseThrow().getRevokedAt()).isNull();
    }

    private IssuedAgentAccessTokenResponse issue(Long userId) {
        return tokenService.issue(userId, new CreateAgentAccessTokenRequest("테스트", null));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}

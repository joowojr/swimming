package com.swimming.backend.mcp;

import com.swimming.backend.agentwork.domain.AgentType;
import com.swimming.backend.agentwork.domain.WorkResourceType;
import com.swimming.backend.agentwork.application.usecase.AgentBoardUseCase;
import com.swimming.backend.agentwork.application.usecase.AgentSessionReportUseCase;
import com.swimming.backend.agentwork.application.usecase.AgentWorkItemUseCase;
import com.swimming.backend.common.security.AuthUser;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AgentWorkMcpToolsTest {
    private final AgentWorkItemUseCase workItemUseCase = mock(AgentWorkItemUseCase.class);
    private final AgentSessionReportUseCase sessionReportUseCase = mock(AgentSessionReportUseCase.class);
    private final AgentWorkMcpTools tools = new AgentWorkMcpTools(mock(AgentBoardUseCase.class), workItemUseCase,
            sessionReportUseCase, Validation.buildDefaultValidatorFactory().getValidator());
    private final Map<String, JsonNode> schemas = Arrays.stream(
                    MethodToolCallbackProvider.builder().toolObjects(tools).build().getToolCallbacks())
            .map(ToolCallback::getToolDefinition)
            .collect(Collectors.toMap(definition -> definition.name(),
                    definition -> JsonMapper.builder().build().readTree(definition.inputSchema())));

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new PreAuthenticatedAuthenticationToken(
                new AuthUser(1L, null), "token", List.of(new SimpleGrantedAuthority("SCOPE_agent-work:read"),
                new SimpleGrantedAuthority("SCOPE_agent-work:write"))));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("에이전트는 Task 식별자와 sessionId로만 부르는 Tool 목록을 받는다")
    void exposesTools() {
        assertThat(schemas).containsOnlyKeys(
                "get_task", "get_session_events", "attach_work_item", "start_work", "complete_work");
    }

    @Test
    @DisplayName("입력은 request 래퍼 없이 펼쳐지고 모든 파라미터에 설명이 있다")
    void flattensParametersWithDescriptions() {
        assertThat(fieldNames(schemas.get("get_task").get("properties"))).containsExactly("resourceType", "resourceId");
        assertThat(fieldNames(schemas.get("start_work").get("properties")))
                .containsExactly("workItems", "agentType", "instruction");
        assertThat(fieldNames(schemas.get("complete_work").get("properties")))
                .containsExactly("sessionId", "summary", "details");
        schemas.values().forEach(schema -> schema.get("properties").properties()
                .forEach(property -> assertThat(property.getValue().path("description").asString())
                        .as(property.getKey()).isNotBlank()));
        JsonNode taskRef = schemas.get("start_work").at("/properties/workItems/items/properties");
        assertThat(taskRef.at("/resourceId/description").asString()).isNotBlank();
    }

    @Test
    @DisplayName("선택 입력인 instruction과 details는 필수 목록에 없다")
    void marksOptionalParameters() {
        assertThat(textValues(schemas.get("start_work").get("required"))).containsOnly("workItems", "agentType");
        assertThat(textValues(schemas.get("complete_work").get("required"))).containsOnly("sessionId", "summary");
    }

    @Test
    @DisplayName("REST와 같은 제약으로 입력을 검증하고 UseCase를 호출하지 않는다")
    void validatesLikeRest() {
        assertThatThrownBy(() -> tools.completeWork(1L, " ", null))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> tools.startWork(List.of(), AgentType.CODEX, null))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> tools.getTask(WorkResourceType.SWIMMING_TASK, ""))
                .isInstanceOf(ConstraintViolationException.class);
        verifyNoInteractions(workItemUseCase, sessionReportUseCase);
    }

    private List<String> fieldNames(JsonNode node) {
        return node.propertyNames().stream().toList();
    }

    private List<String> textValues(JsonNode node) {
        return node.valueStream().map(JsonNode::asString).toList();
    }
}

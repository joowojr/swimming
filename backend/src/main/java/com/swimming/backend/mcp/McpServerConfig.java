package com.swimming.backend.mcp;

import org.springframework.ai.mcp.customizer.McpSyncServerCustomizer;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class McpServerConfig {
    static final String INSTRUCTIONS = """
            Swimming Cowork Board에 에이전트 작업 상태를 보고하는 서버다.
            사용자가 Swimming 할 일(Task)을 맡기면 다음 순서로 호출한다.
            1. get_task: 사용자가 준 resourceType·resourceId로 할 일과 연결된 지식을 읽는다.
            2. start_work: 작업을 시작하며 응답의 session.id를 보관한다.
            3. 작업을 마치면 complete_work에 session.id와 결과 한 줄을 보고한다.
            식별자는 Task 식별자(resourceType + resourceId)와 sessionId 두 가지만 쓴다.
            """;

    @Bean
    ToolCallbackProvider agentWorkToolCallbackProvider(AgentWorkMcpTools tools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(tools)
                .build();
    }

    /**
     * Spring AI는 customizer를 하나만 적용한다. 서블릿 기본 customizer 대신 이 빈이 쓰이므로
     * 기본 동작인 immediateExecution도 함께 설정한다.
     */
    @Bean
    @Primary
    McpSyncServerCustomizer agentWorkMcpServerCustomizer() {
        return spec -> spec
                .immediateExecution(true)
                .instructions(INSTRUCTIONS);
    }
}

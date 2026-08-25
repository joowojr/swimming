package com.swimming.backend.common.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LLMChatConfig {

    @Bean
    SimpleLoggerAdvisor simpleLoggerAdvisor() {
        return SimpleLoggerAdvisor.builder().build();
    }

    @Bean
    ChatClient chatClient(
            ChatClient.Builder builder,
            SimpleLoggerAdvisor simpleLoggerAdvisor
    ) {
        return builder
                .defaultAdvisors(simpleLoggerAdvisor)
                .build();
    }
}

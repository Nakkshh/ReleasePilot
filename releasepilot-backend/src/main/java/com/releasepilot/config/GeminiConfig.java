package com.releasepilot.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeminiConfig {

    private static final String SYSTEM_PROMPT = """
            You are ReleasePilot, an assistant for software release workflows.
            You help with GitHub repositories, CI/CD pipelines, pull requests,
            and release notes. Keep answers short and clear.
            """;

    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }
}
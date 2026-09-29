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
        When using tool results, only state facts present in them. Never infer or
        invent details (such as what a commit changed or why a build failed) that
        the tools did not return; say so if the information is not available.
        """;

    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }
}
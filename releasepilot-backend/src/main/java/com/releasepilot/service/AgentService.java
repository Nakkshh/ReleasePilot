package com.releasepilot.service;

import com.releasepilot.config.ReasoningStripAdvisor;
import com.releasepilot.exception.GeminiServiceException;
import com.releasepilot.tool.GitHubTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final ChatClient chatClient;
    private final GitHubTools gitHubTools;
    private final ObjectProvider<ReasoningStripAdvisor> reasoningStripAdvisor;

    public AgentService(ChatClient chatClient,
                        GitHubTools gitHubTools,
                        ObjectProvider<ReasoningStripAdvisor> reasoningStripAdvisor) {
        this.chatClient = chatClient;
        this.gitHubTools = gitHubTools;
        this.reasoningStripAdvisor = reasoningStripAdvisor;
    }

    public String ask(String message) {
        try {
            var spec = chatClient.prompt()
                    .user(message)
                    .tools(gitHubTools);

            ReasoningStripAdvisor strip = reasoningStripAdvisor.getIfAvailable();
            if (strip != null) {
                spec = spec.advisors(strip);
            }

            return spec.call().content();
        } catch (Exception e) {
            log.error("Agent call failed for message: {}", message, e);
            throw new GeminiServiceException("Agent failed to process request", e);
        }
    }
}
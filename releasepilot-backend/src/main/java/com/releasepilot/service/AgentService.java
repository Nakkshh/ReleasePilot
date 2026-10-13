package com.releasepilot.service;

import com.releasepilot.config.ReasoningStripAdvisor;
import com.releasepilot.exception.GeminiServiceException;
import com.releasepilot.knowledge.KnowledgeTools;
import com.releasepilot.tool.GitHubActionsTools;
import com.releasepilot.tool.GitHubTools;
import com.releasepilot.agent.ProviderGlitch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final int MAX_ATTEMPTS = 3;

    private final ChatClient chatClient;
    private final GitHubTools gitHubTools;
    private final GitHubActionsTools gitHubActionsTools;
    private final KnowledgeTools knowledgeTools;
    private final ObjectProvider<ReasoningStripAdvisor> reasoningStripAdvisor;

    public AgentService(ChatClient chatClient,
                        GitHubTools gitHubTools,
                        GitHubActionsTools gitHubActionsTools,
                        KnowledgeTools knowledgeTools,
                        ObjectProvider<ReasoningStripAdvisor> reasoningStripAdvisor) {
        this.chatClient = chatClient;
        this.gitHubTools = gitHubTools;
        this.gitHubActionsTools = gitHubActionsTools;
        this.knowledgeTools = knowledgeTools;
        this.reasoningStripAdvisor = reasoningStripAdvisor;
    }

    public String ask(String message) {
        Exception last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return askOnce(message);
            } catch (Exception e) {
                last = e;
                if (attempt < MAX_ATTEMPTS && isProviderToolGlitch(e)) {
                    log.warn("[ASK] provider rejected the model's tool call (attempt {}/{}), retrying",
                            attempt, MAX_ATTEMPTS);
                    continue;
                }
                break;
            }
        }
        log.error("Agent call failed for message: {}", message, last);
        throw new GeminiServiceException("Agent failed to process request", last);
    }

    private String askOnce(String message) {
        var spec = chatClient.prompt()
                .user(message)
                .tools(gitHubTools, gitHubActionsTools, knowledgeTools);

        ReasoningStripAdvisor strip = reasoningStripAdvisor.getIfAvailable();
        if (strip != null) {
            spec = spec.advisors(strip);
        }
        return spec.call().content();
    }

    /** Same patterns as ReleaseAgent; to be consolidated into one shared helper in Phase 10. */
    private static boolean isProviderToolGlitch(Throwable t) {
        return ProviderGlitch.matches(t);
    }
}
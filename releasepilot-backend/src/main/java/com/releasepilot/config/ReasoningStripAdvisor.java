package com.releasepilot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Workaround for spring-ai issue #6968: Groq rejects "reasoning_content" on assistant
 * messages, but Spring AI 2.0.0 replays it on the second request of a tool-calling loop.
 * Only active with the "groq" profile.
 */
@Component
public class ReasoningStripAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(ReasoningStripAdvisor.class);
    private static final String REASONING_KEY = "reasoningContent";

    @Override
    public String getName() {
        return "ReasoningStripAdvisor";
    }

    @Override
    public int getOrder() {
        // Larger value = later in the chain, i.e. downstream of ToolCallingAdvisor,
        // so this runs on every iteration of the tool loop.
        return ToolCallingAdvisor.DEFAULT_ORDER + 100;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        Prompt original = request.prompt();
        List<Message> cleaned = new ArrayList<>();
        boolean changed = false;

        for (Message m : original.getInstructions()) {
            if (m instanceof AssistantMessage am) {
                log.debug("Assistant message metadata keys: {}", am.getMetadata().keySet());
                if (am.getMetadata().containsKey(REASONING_KEY)) {
                    Map<String, Object> props = new HashMap<>(am.getMetadata());
                    props.remove(REASONING_KEY);
                    cleaned.add(AssistantMessage.builder()
                            .content(am.getText())
                            .properties(props)
                            .toolCalls(am.getToolCalls())
                            .media(am.getMedia())
                            .build());
                    changed = true;
                    continue;
                }
            }
            cleaned.add(m);
        }

        if (!changed) {
            return chain.nextCall(request);
        }
        log.debug("Stripped reasoningContent from assistant message(s) before replay");
        Prompt newPrompt = new Prompt(cleaned, original.getOptions());
        return chain.nextCall(new ChatClientRequest(newPrompt, request.context()));
    }
}
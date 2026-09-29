package com.releasepilot.service;

import com.releasepilot.dto.ChangeAnalysis;
import com.releasepilot.exception.GeminiServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private static final String ANALYZE_TEMPLATE = """
            Analyze the following software change description.
            Classify it and summarize it according to the required schema.

            Change description:
            {text}
            """;

    private final ChatClient chatClient;

    public GeminiService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public GeminiCallResult ask(String message, Double temperature) {
        try {
            var requestSpec = chatClient.prompt().user(message);

            if (temperature != null) {
                // .options() takes a ChatOptions.Builder in Spring AI 2.0 — do NOT call .build() here.
                requestSpec = requestSpec.options(
                        GoogleGenAiChatOptions.builder().temperature(temperature)
                );
            }

            ChatResponse response = requestSpec.call().chatResponse();
            String text = response.getResult().getOutput().getText();

            logUsage(response);

            return toResult(text, response);
        } catch (Exception e) {
            throw new GeminiServiceException("Failed to get a response from Gemini", e);
        }
    }

    public ChangeAnalysis analyzeChange(String text) {
        try {
            return chatClient.prompt()
                    .user(u -> u.text(ANALYZE_TEMPLATE).params(Map.of("text", text)))
                    .options(GoogleGenAiChatOptions.builder().temperature(0.1)) // low temp: consistent classification, not creativity
                    .call()
                    .entity(ChangeAnalysis.class);
        } catch (Exception e) {
            throw new GeminiServiceException("Failed to analyze change", e);
        }
    }

    private void logUsage(ChatResponse response) {
        Usage usage = response.getMetadata().getUsage();
        if (usage != null) {
            log.debug("Gemini call used {} prompt + {} completion = {} total tokens",
                    usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
        }
    }

    private GeminiCallResult toResult(String text, ChatResponse response) {
        Usage usage = response.getMetadata().getUsage();
        Integer promptTokens = usage != null ? usage.getPromptTokens() : null;
        Integer completionTokens = usage != null ? usage.getCompletionTokens() : null;
        Integer totalTokens = usage != null ? usage.getTotalTokens() : null;
        return new GeminiCallResult(text, promptTokens, completionTokens, totalTokens);
    }

    public record GeminiCallResult(
            String reply,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens
    ) {
    }
}
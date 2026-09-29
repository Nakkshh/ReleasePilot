package com.releasepilot.controller;

import com.releasepilot.dto.AnalyzeRequest;
import com.releasepilot.dto.ChangeAnalysis;
import com.releasepilot.dto.ChatRequest;
import com.releasepilot.dto.ChatResponse;
import com.releasepilot.service.GeminiService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final GeminiService geminiService;

    public ChatController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @GetMapping("/health")
    public String health() {
        return "ReleasePilot backend is running";
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        GeminiService.GeminiCallResult result = geminiService.ask(request.message(), request.temperature());
        return ChatResponse.of(result.reply(), result.promptTokens(), result.completionTokens(), result.totalTokens());
    }

    @PostMapping("/analyze")
    public ChangeAnalysis analyze(@Valid @RequestBody AnalyzeRequest request) {
        return geminiService.analyzeChange(request.text());
    }
}
package com.releasepilot.controller;

import com.releasepilot.dto.ChatRequest;
import com.releasepilot.dto.ChatResponse;
import com.releasepilot.service.AgentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/ask")
    public ChatResponse ask(@Valid @RequestBody ChatRequest request) {
        String reply = agentService.ask(request.message());
        // Token usage isn't tracked here yet: a single agent turn can involve
        // multiple LLM round-trips (one per tool call), so per-turn usage
        // tracking needs the observability work in Phase 12. Usage fields
        // are null for this endpoint for now.
        return ChatResponse.of(reply, null, null, null);
    }
}
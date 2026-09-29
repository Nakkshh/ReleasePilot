package com.releasepilot.controller;

import com.releasepilot.agent.ReleaseAgent;
import com.releasepilot.dto.ReleaseCheckResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class ReleaseCheckController {

    private final ReleaseAgent releaseAgent;

    public ReleaseCheckController(ReleaseAgent releaseAgent) {
        this.releaseAgent = releaseAgent;
    }

    @PostMapping("/release-check")
    public ReleaseCheckResponse releaseCheck() {
        return releaseAgent.check();
    }
}
package com.releasepilot.controller;

import com.releasepilot.agent.ReleaseNotesAgent;
import com.releasepilot.dto.ReleaseNotesResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class ReleaseNotesController {

    private final ReleaseNotesAgent agent;

    public ReleaseNotesController(ReleaseNotesAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/release-notes")
    public ReleaseNotesResponse releaseNotes() {
        return agent.draft();
    }
}
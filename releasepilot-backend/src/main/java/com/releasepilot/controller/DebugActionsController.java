package com.releasepilot.controller;

import com.releasepilot.tool.GitHubActionsTools;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/debug/ci")
public class DebugActionsController {

    private final GitHubActionsTools tools;

    public DebugActionsController(GitHubActionsTools tools) {
        this.tools = tools;
    }

    @GetMapping("/latest")
    public String latest() {
        return tools.getLatestWorkflow();
    }

    @GetMapping("/status")
    public String status(@RequestParam(required = false) Integer count) {
        return tools.getWorkflowStatus(count);
    }

    @GetMapping("/failed-jobs")
    public String failedJobs(@RequestParam(required = false) Long runId) {
        return tools.getFailedJobs(runId);
    }

    @GetMapping("/logs")
    public String logs(@RequestParam(required = false) Long jobId) {
        return tools.getFailureLogs(jobId);
    }
}
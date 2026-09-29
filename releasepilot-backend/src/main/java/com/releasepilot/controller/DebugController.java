package com.releasepilot.controller;

import com.releasepilot.tool.GitHubTools;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/debug")
public class DebugController {

    private final GitHubTools tools;

    public DebugController(GitHubTools tools) {
        this.tools = tools;
    }

    @GetMapping("/repo")
    public String repo() {
        return tools.getRepository();
    }

    @GetMapping("/commits")
    public String commits(@RequestParam(required = false) Integer count) {
        return tools.getRecentCommits(count);
    }

    @GetMapping("/prs")
    public String prs() {
        return tools.getPullRequests();
    }

    @GetMapping("/issues")
    public String issues() {
        return tools.getIssues();
    }

    @GetMapping("/prs/reviews")
    public String prReviews(@RequestParam(required = false) Integer number) {
        return tools.getPullRequestReviews(number);
    }
}
package com.releasepilot.controller;

import com.releasepilot.tool.GitHubTools;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.releasepilot.dto.github.GitHubModels;
import java.util.List;

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
        return tools.recentCommits(count);
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
        return tools.pullRequestReviews(number);
    }

    @GetMapping("/releases/latest")
    public Object latestRelease() {
        GitHubModels.Release r = tools.getLatestRelease();
        return r == null ? "No releases yet." : r;
    }

    @GetMapping("/prs/merged")
    public List<GitHubModels.ClosedPull> closedPrs(@RequestParam(defaultValue = "20") int count) {
        return tools.getClosedPullRequests(Math.min(count, 100));
    }
}
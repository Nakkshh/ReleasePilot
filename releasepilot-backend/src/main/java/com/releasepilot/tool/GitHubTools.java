package com.releasepilot.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tools the agent can call to inspect the GitHub repository.
 *
 * PHASE 3: bodies return mock data so we can prove the tool-calling
 * mechanism works end to end.
 *
 * PHASE 4: these method bodies get replaced with real GitHub REST API
 * calls. The @Tool signatures below are deliberately final — Phase 4
 * changes only what's inside each method.
 */
@Component
public class GitHubTools {

    private static final Logger log = LoggerFactory.getLogger(GitHubTools.class);

    @Tool(description = "Get basic information about the configured GitHub repository: "
            + "its name, description, star count and default branch.")
    public String getRepository() {
        log.info("[TOOL CALLED] getRepository()");
        return """
                Repository: Nakkshh/releasepilot
                Description: AI-powered software release workflow agent
                Stars: 12
                Default branch: main
                """;
    }

    @Tool(description = "Get the most recent commits on the repository's default branch, "
            + "newest first.")
    public String getRecentCommits(
            @ToolParam(description = "How many recent commits to return. Defaults to 5 if not specified.")
            Integer count) {

        int n = (count == null || count <= 0) ? 5 : count;
        log.info("[TOOL CALLED] getRecentCommits(count={})", n);

        List<String> allCommits = List.of(
                "a1b2c3d Fix null pointer in ReleaseService",
                "d4e5f6a Add PR tool integration",
                "9f8e7d6 Update README with setup instructions",
                "1234abc Refactor GeminiService for temperature support",
                "5678def Add structured output for change analysis",
                "abcd123 Bump Spring Boot to 4.1.1",
                "ffff000 Initial commit"
        );

        return allCommits.stream()
                .limit(n)
                .collect(Collectors.joining("\n"));
    }

    @Tool(description = "Get the list of currently open pull requests on the repository, "
            + "including their approval status.")
    public String getPullRequests() {
        log.info("[TOOL CALLED] getPullRequests()");
        return """
                #42 Add release notes generator (open, 2 approvals)
                #41 Fix flaky CI test in PaymentServiceTest (open, needs review)
                #39 Update Docker base image (open, 1 approval)
                """;
    }

    @Tool(description = "Get the list of currently open issues on the repository, "
            + "including their labels.")
    public String getIssues() {
        log.info("[TOOL CALLED] getIssues()");
        return """
                #101 Release notes missing breaking-change section (bug)
                #98 Add support for pre-release tags (enhancement)
                #85 Flaky integration test on CI (bug)
                """;
    }
}
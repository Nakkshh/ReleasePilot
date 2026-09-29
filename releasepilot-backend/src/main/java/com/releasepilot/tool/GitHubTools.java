package com.releasepilot.tool;

import com.releasepilot.dto.github.GitHubModels;
import com.releasepilot.exception.GitHubApiException;
import com.releasepilot.service.GitHubClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class GitHubTools {

    private static final Logger log = LoggerFactory.getLogger(GitHubTools.class);

    private final GitHubClient github;
    private static final int MAX_REVIEWED_PRS = 10;

    public GitHubTools(GitHubClient github) {
        this.github = github;
    }

    @Tool(description = "Get basic information about the configured GitHub repository: "
            + "its name, description, star count and default branch.")
    public String getRepository() {
        log.info("[TOOL CALLED] getRepository()");
        try {
            GitHubModels.Repo r = github.getRepository();
            return "Repository: " + r.fullName()
                    + "\nDescription: " + (r.description() == null ? "(none)" : r.description())
                    + "\nStars: " + r.stars()
                    + "\nDefault branch: " + r.defaultBranch()
                    + "\nOpen issues + PRs: " + r.openIssuesCount();
        } catch (GitHubApiException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    @Tool(description = "Get the most recent commits on the repository's default branch, "
            + "newest first.")
    public String getRecentCommits(
            @ToolParam(description = "How many recent commits to return. Defaults to 5 if not specified.")
            Integer count) {

        int n = (count == null || count <= 0) ? 5 : Math.min(count, 30);
        log.info("[TOOL CALLED] getRecentCommits(count={})", n);
        try {
            List<GitHubModels.Commit> commits = github.getRecentCommits(n);
            if (commits.isEmpty()) {
                return "No commits found.";
            }
            return commits.stream()
                    .map(c -> c.sha().substring(0, 7) + " " + firstLine(c.commit().message())
                            + " (" + c.commit().author().name() + ", " + c.commit().author().date() + ")")
                    .collect(Collectors.joining("\n"));
        } catch (GitHubApiException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    @Tool(description = "Get the list of currently open pull requests on the repository "
            + "with number, title, author and draft status.")
    public String getPullRequests() {
        log.info("[TOOL CALLED] getPullRequests()");
        try {
            List<GitHubModels.Pull> pulls = github.getOpenPullRequests();
            if (pulls.isEmpty()) {
                return "No open pull requests.";
            }
            return pulls.stream()
                    .map(p -> "#" + p.number() + " " + p.title()
                            + " (by " + (p.user() == null ? "unknown" : p.user().login())
                            + (Boolean.TRUE.equals(p.draft()) ? ", draft" : "") + ")")
                    .collect(Collectors.joining("\n"));
        } catch (GitHubApiException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    @Tool(description = "Get the list of currently open issues on the repository, "
            + "including their labels. Pull requests are excluded.")
    public String getIssues() {
        log.info("[TOOL CALLED] getIssues()");
        try {
            List<GitHubModels.Issue> issues = github.getOpenIssues().stream()
                    .filter(i -> i.pullRequest() == null)
                    .toList();
            if (issues.isEmpty()) {
                return "No open issues.";
            }
            return issues.stream()
                    .map(i -> "#" + i.number() + " " + i.title() + " ("
                            + (i.labels() == null || i.labels().isEmpty()
                            ? "no labels"
                            : i.labels().stream().map(GitHubModels.Label::name)
                            .collect(Collectors.joining(", ")))
                            + ")")
                    .collect(Collectors.joining("\n"));
        } catch (GitHubApiException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    @Tool(description = "Get the review status of pull requests: for each reviewer whether they approved, "
            + "requested changes or were dismissed, plus an overall status per pull request. "
            + "If no pull request number is given, covers all open pull requests.")
    public String getPullRequestReviews(
            @ToolParam(description = "Pull request number. Optional; omit to check all open pull requests.",
                    required = false)
            Integer pullNumber) {
        log.info("[TOOL CALLED] getPullRequestReviews(pullNumber={})", pullNumber);
        try {
            if (pullNumber != null) {
                return describeReviews(pullNumber, null, null);
            }
            List<GitHubModels.Pull> pulls = github.getOpenPullRequests();
            if (pulls.isEmpty()) {
                return "No open pull requests.";
            }
            String body = pulls.stream()
                    .limit(MAX_REVIEWED_PRS)
                    .map(p -> describeReviews(p.number(), p.title(), p.head() == null ? null : p.head().sha()))
                    .collect(Collectors.joining("\n"));
            return pulls.size() > MAX_REVIEWED_PRS
                    ? body + "\n(Only the first " + MAX_REVIEWED_PRS + " of " + pulls.size() + " open PRs shown.)"
                    : body;
        } catch (GitHubApiException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * A reviewer's standing is their latest APPROVED / CHANGES_REQUESTED / DISMISSED review.
     * Plain comment reviews do not change a reviewer's standing. Reviews arrive chronologically.
     */
    private String describeReviews(int number, String title, String headSha) {
        List<GitHubModels.Review> reviews = github.getReviews(number);
        if (reviews == null) {
            reviews = List.of();
        }
        Map<String, GitHubModels.Review> standing = new LinkedHashMap<>();
        for (GitHubModels.Review r : reviews) {
            String s = r.state();
            if ("APPROVED".equals(s) || "CHANGES_REQUESTED".equals(s) || "DISMISSED".equals(s)) {
                String login = r.user() == null ? "unknown" : r.user().login();
                standing.put(login, r);
            }
        }
        long changes = standing.values().stream().filter(r -> "CHANGES_REQUESTED".equals(r.state())).count();
        long approvals = standing.values().stream().filter(r -> "APPROVED".equals(r.state())).count();
        String overall = changes > 0 ? "CHANGES_REQUESTED" : approvals > 0 ? "APPROVED" : "NO_APPROVAL";

        String reviewers = standing.isEmpty()
                ? "no approvals or change requests yet"
                : standing.entrySet().stream()
                .map(e -> e.getKey() + " " + e.getValue().state() + staleNote(e.getValue(), headSha))
                .collect(Collectors.joining("; "));

        return "PR #" + number + (title == null ? "" : " \"" + title + "\"")
                + ": overall=" + overall + "; reviewers: " + reviewers
                + (headSha == null ? " (staleness not checked)" : "");
    }

    private static String staleNote(GitHubModels.Review r, String headSha) {
        boolean stale = headSha != null && r.commitId() != null
                && "APPROVED".equals(r.state()) && !headSha.equals(r.commitId());
        return stale ? " (stale: approved an older commit)" : "";
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "";
        }
        int nl = message.indexOf('\n');
        return nl < 0 ? message : message.substring(0, nl);
    }
}
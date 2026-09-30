package com.releasepilot.dto.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class GitHubModels {

    private GitHubModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Repo(
            @JsonProperty("full_name") String fullName,
            String description,
            @JsonProperty("stargazers_count") Integer stars,
            @JsonProperty("default_branch") String defaultBranch,
            @JsonProperty("open_issues_count") Integer openIssuesCount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Commit(String sha, CommitDetail commit) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommitDetail(String message, CommitAuthor author) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommitAuthor(String name, String date) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pull(Integer number, String title, Boolean draft, User user, Head head) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Head(String sha) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Review(
            User user,
            String state,
            @JsonProperty("submitted_at") String submittedAt,
            @JsonProperty("commit_id") String commitId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(String login) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Issue(
            Integer number,
            String title,
            List<Label> labels,
            @JsonProperty("pull_request") Object pullRequest) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Label(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PullLabel(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClosedPull(
            Integer number,
            String title,
            User user,
            List<PullLabel> labels,
            @JsonProperty("merged_at") String mergedAt,
            @JsonProperty("closed_at") String closedAt,
            @JsonProperty("html_url") String htmlUrl) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Release(
            @JsonProperty("tag_name") String tagName,
            String name,
            Boolean draft,
            Boolean prerelease,
            @JsonProperty("published_at") String publishedAt,
            @JsonProperty("html_url") String htmlUrl) {
    }
}
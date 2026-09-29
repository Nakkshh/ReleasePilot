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
    public record Pull(Integer number, String title, Boolean draft, User user) {
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
}
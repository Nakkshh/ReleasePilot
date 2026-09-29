package com.releasepilot.service;

import com.releasepilot.dto.github.GitHubModels;
import com.releasepilot.exception.GitHubApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Service
public class GitHubClient {

    private final RestClient rest;
    private final String owner;
    private final String repo;

    public GitHubClient(RestClient githubRestClient,
                        @Value("${github.repo.owner}") String owner,
                        @Value("${github.repo.name}") String repo) {
        this.rest = githubRestClient;
        this.owner = owner;
        this.repo = repo;
    }

    public GitHubModels.Repo getRepository() {
        return rest.get()
                .uri("/repos/{o}/{r}", owner, repo)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::fail)
                .body(GitHubModels.Repo.class);
    }

    public List<GitHubModels.Commit> getRecentCommits(int count) {
        return rest.get()
                .uri("/repos/{o}/{r}/commits?per_page={n}", owner, repo, count)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::fail)
                .body(new ParameterizedTypeReference<List<GitHubModels.Commit>>() {});
    }

    public List<GitHubModels.Pull> getOpenPullRequests() {
        return rest.get()
                .uri("/repos/{o}/{r}/pulls?state=open&per_page=30", owner, repo)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::fail)
                .body(new ParameterizedTypeReference<List<GitHubModels.Pull>>() {});
    }

    public List<GitHubModels.Issue> getOpenIssues() {
        return rest.get()
                .uri("/repos/{o}/{r}/issues?state=open&per_page=30", owner, repo)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::fail)
                .body(new ParameterizedTypeReference<List<GitHubModels.Issue>>() {});
    }

    private void fail(org.springframework.http.HttpRequest request, ClientHttpResponse response)
            throws IOException {
        int status = response.getStatusCode().value();
        String remaining = response.getHeaders().getFirst("X-RateLimit-Remaining");
        String reset = response.getHeaders().getFirst("X-RateLimit-Reset");

        if ((status == 403 || status == 429) && "0".equals(remaining)) {
            String when = reset != null ? Instant.ofEpochSecond(Long.parseLong(reset)).toString() : "later";
            throw new GitHubApiException("GitHub API rate limit exceeded. Resets at " + when + ".");
        }
        switch (status) {
            case 401 -> throw new GitHubApiException("GitHub rejected the token (401). Check GITHUB_TOKEN.");
            case 403 -> throw new GitHubApiException("GitHub denied access (403). The token may lack permissions.");
            case 404 -> throw new GitHubApiException(
                    "GitHub returned 404. Repository " + owner + "/" + repo
                            + " was not found (or is private and no valid token is set).");
            default -> throw new GitHubApiException("GitHub API error, HTTP " + status + ".");
        }
    }
}
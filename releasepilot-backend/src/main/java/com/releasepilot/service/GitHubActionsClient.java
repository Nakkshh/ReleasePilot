package com.releasepilot.service;

import com.releasepilot.dto.github.ActionsModels;
import com.releasepilot.exception.GitHubApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Service
public class GitHubActionsClient {

    private final RestClient api;       // GitHub API, redirects NOT followed (we read Location ourselves)
    private final RestClient download;  // plain client for the pre-signed log URL (no auth header)
    private final String owner;
    private final String repo;

    public GitHubActionsClient(@Value("${github.api.base-url}") String baseUrl,
                               @Value("${github.token:}") String token,
                               @Value("${github.repo.owner}") String owner,
                               @Value("${github.repo.name}") String repo) {
        HttpClient noRedirects = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        RestClient.Builder builder = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(noRedirects))
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader(HttpHeaders.USER_AGENT, "ReleasePilot");
        if (!token.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        this.api = builder.build();
        this.download = RestClient.create();
        this.owner = owner;
        this.repo = repo;
    }

    public List<ActionsModels.Run> listRuns(int count) {
        ActionsModels.RunsResponse resp = api.get()
                .uri("/repos/{o}/{r}/actions/runs?per_page={n}", owner, repo, count)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::fail)
                .body(ActionsModels.RunsResponse.class);
        return (resp == null || resp.runs() == null) ? List.of() : resp.runs();
    }

    public List<ActionsModels.Job> listJobs(long runId) {
        ActionsModels.JobsResponse resp = api.get()
                .uri("/repos/{o}/{r}/actions/runs/{id}/jobs?filter=latest&per_page=100", owner, repo, runId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::fail)
                .body(ActionsModels.JobsResponse.class);
        return (resp == null || resp.jobs() == null) ? List.of() : resp.jobs();
    }

    /** GitHub answers 302 with a Location header (valid ~1 min); we then fetch that URL without auth. */
    public String downloadJobLogs(long jobId) {
        ResponseEntity<Void> resp = api.get()
                .uri("/repos/{o}/{r}/actions/jobs/{id}/logs", owner, repo, jobId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::fail)
                .toBodilessEntity();

        URI location = resp.getHeaders().getLocation();
        if (location == null) {
            throw new GitHubApiException("GitHub did not return a log download URL (HTTP "
                    + resp.getStatusCode().value() + ").");
        }
        byte[] bytes = download.get().uri(location).retrieve().body(byte[].class);
        return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
    }

    private void fail(HttpRequest request, ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        String remaining = response.getHeaders().getFirst("X-RateLimit-Remaining");
        String reset = response.getHeaders().getFirst("X-RateLimit-Reset");

        if ((status == 403 || status == 429) && "0".equals(remaining)) {
            String when = reset != null ? Instant.ofEpochSecond(Long.parseLong(reset)).toString() : "later";
            throw new GitHubApiException("GitHub API rate limit exceeded. Resets at " + when + ".");
        }
        switch (status) {
            case 401 -> throw new GitHubApiException("GitHub rejected the token (401). Check GITHUB_TOKEN.");
            case 403 -> throw new GitHubApiException(
                    "GitHub denied access (403). The token likely lacks the 'Actions: Read' permission.");
            case 404 -> throw new GitHubApiException(
                    "GitHub returned 404 for " + owner + "/" + repo + " (repo, run or job not found).");
            case 410 -> throw new GitHubApiException("Logs are no longer available (410). They may have expired.");
            default -> throw new GitHubApiException("GitHub API error, HTTP " + status + ".");
        }
    }
}
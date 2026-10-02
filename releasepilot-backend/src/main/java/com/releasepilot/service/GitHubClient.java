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

import org.springframework.http.MediaType;
import java.util.LinkedHashMap;
import java.util.Map;

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

    public List<GitHubModels.Review> getReviews(int pullNumber) {
        return rest.get()
                .uri("/repos/{o}/{r}/pulls/{n}/reviews?per_page=100", owner, repo, pullNumber)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::fail)
                .body(new ParameterizedTypeReference<List<GitHubModels.Review>>() {});
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

    public List<GitHubModels.ClosedPull> getClosedPullRequests(int perPage) {
        return rest.get()
                .uri("/repos/{o}/{r}/pulls?state=closed&sort=updated&direction=desc&per_page={n}",
                        owner, repo, perPage)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::fail)
                .body(new ParameterizedTypeReference<List<GitHubModels.ClosedPull>>() {});
    }

    /** Returns null when the repo has no published release (GitHub answers 404). */
    public GitHubModels.Release getLatestRelease() {
        return rest.get()
                .uri("/repos/{o}/{r}/releases/latest", owner, repo)
                .exchange((request, response) -> {
                    if (response.getStatusCode().value() == 404) {
                        return null;
                    }
                    if (response.getStatusCode().isError()) {
                        fail(request, response);   // throws GitHubApiException, as elsewhere
                    }
                    return response.bodyTo(GitHubModels.Release.class);
                });
    }

    /** True if the git tag exists. Uses the exact-match ref endpoint; 404 means no such tag. */
    public boolean tagExists(String tag) {
        Boolean exists = rest.get()
                .uri("/repos/{o}/{r}/git/ref/tags/{t}", owner, repo, tag)
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    if (status == 404) {
                        return false;
                    }
                    if (response.getStatusCode().isError()) {
                        fail(request, response);   // throws GitHubApiException
                    }
                    return true;
                });
        return Boolean.TRUE.equals(exists);
    }

    /** Null when no release exists for the tag (published releases only; GitHub hides drafts here). */
    public GitHubModels.Release getReleaseByTag(String tag) {
        return rest.get()
                .uri("/repos/{o}/{r}/releases/tags/{t}", owner, repo, tag)
                .exchange((request, response) -> {
                    if (response.getStatusCode().value() == 404) {
                        return null;
                    }
                    if (response.getStatusCode().isError()) {
                        fail(request, response);
                    }
                    return response.bodyTo(GitHubModels.Release.class);
                });
    }

    /** Creates and publishes a release (draft=false). The tag is created from targetSha if it doesn't exist. */
    public GitHubModels.Release createRelease(String tag, String targetSha, String name,
                                              String body, boolean prerelease) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tag_name", tag);
        if (targetSha != null) {
            payload.put("target_commitish", targetSha);
        }
        payload.put("name", name);
        payload.put("body", body);
        payload.put("draft", false);
        payload.put("prerelease", prerelease);
        payload.put("generate_release_notes", false);

        return rest.post()
                .uri("/repos/{o}/{r}/releases", owner, repo)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    if (status == 200 || status == 201) {
                        return response.bodyTo(GitHubModels.Release.class);
                    }
                    if (status == 422) {
                        throw new GitHubApiException("GitHub rejected the release (422): "
                                + snippet(response.bodyTo(String.class)));
                    }
                    if (status == 404) {
                        throw new GitHubApiException("GitHub returned 404 while creating the release. "
                                + "Check that the token has Contents: write on this repository. GitHub also answers 404 "
                                + "if the pinned commit changes files under .github/workflows relative to the default "
                                + "branch and the token lacks Workflows: write.");
                    }
                    fail(request, response);   // always throws (401, 403, rate limit, other)
                    return null;
                });
    }

    private static String snippet(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", " ").strip();
        return t.length() <= 300 ? t : t.substring(0, 300) + "...";
    }
}
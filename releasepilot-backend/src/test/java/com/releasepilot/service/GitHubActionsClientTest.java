package com.releasepilot.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubActionsClientTest {

    private static final String SHA_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String SHA_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    private HttpServer server;
    private GitHubActionsClient client;
    private final AtomicInteger requests = new AtomicInteger();
    private volatile String responseJson;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/repos/test-owner/test-repo/actions/runs", exchange -> {
            requests.incrementAndGet();

            byte[] body = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();

        GitHubCache cache = new GitHubCache(60, 100, System::nanoTime);
        client = new GitHubActionsClient(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "",
                "test-owner",
                "test-repo",
                cache
        );
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void returnsRunsMatchingTheRequestedSha() {
        responseJson = runsJson(run(SHA_A, 101), run(SHA_B, 102));

        var runs = client.listRunsForSha(SHA_A, 20);

        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).headSha()).isEqualTo(SHA_A);
    }

    @Test
    void excludesRunsWhoseActualHeadShaDoesNotMatch() {
        responseJson = runsJson(run(SHA_B, 102));

        var runs = client.listRunsForSha(SHA_A, 20);

        assertThat(runs).isEmpty();
    }

    @Test
    void returnsEmptyListWhenNoRunMatchesTheSha() {
        responseJson = runsJson(run(SHA_B, 102));

        assertThat(client.listRunsForSha(SHA_A, 20)).isEmpty();
    }

    @Test
    void cachesEachShaSeparately() {
        responseJson = runsJson(run(SHA_A, 101));
        assertThat(client.listRunsForSha(SHA_A, 20)).hasSize(1);

        responseJson = runsJson(run(SHA_B, 102));
        assertThat(client.listRunsForSha(SHA_B, 20)).hasSize(1);

        // Both SHA-specific requests must reach the server independently.
        assertThat(requests.get()).isEqualTo(2);

        // A repeat request for SHA A must use A's cache entry, not B's.
        assertThat(client.listRunsForSha(SHA_A, 20))
                .extracting("headSha")
                .containsExactly(SHA_A);
        assertThat(requests.get()).isEqualTo(2);
    }

    private static String runsJson(String... runs) {
        return "{\"total_count\":" + runs.length
                + ",\"workflow_runs\":[" + String.join(",", runs) + "]}";
    }

    private static String run(String sha, long id) {
        return """
                {
                  "id": %d,
                  "name": "CI",
                  "head_sha": "%s",
                  "status": "completed",
                  "conclusion": "success",
                  "html_url": "https://example.test/actions/runs/%d",
                  "created_at": "2026-10-19T10:00:00Z",
                  "updated_at": "2026-10-19T10:01:00Z",
                  "run_number": %d,
                  "run_attempt": 1,
                  "event": "push",
                  "head_branch": "main",
                  "path": ".github/workflows/ci.yml"
                }
                """.formatted(id, sha, id, id);
    }
}

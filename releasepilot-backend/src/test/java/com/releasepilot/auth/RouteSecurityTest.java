package com.releasepilot.auth;

import com.releasepilot.dto.ApproveRequest;
import com.releasepilot.knowledge.KnowledgeIngestionService;
import com.releasepilot.knowledge.KnowledgeSearchService;
import com.releasepilot.model.Role;
import com.releasepilot.service.AgentService;
import com.releasepilot.service.DraftService;
import com.releasepilot.service.ReleasePublisher;
import com.releasepilot.tool.GitHubActionsTools;
import com.releasepilot.tool.GitHubTools;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "ratelimit.agent.enabled=false")   // this class tests who may call, not how often
class RouteSecurityTest {

    private static final String AUTH = "Authorization";
    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;

    @MockitoBean DraftService drafts;
    @MockitoBean ReleasePublisher publisher;
    @MockitoBean AgentService agent;
    @MockitoBean KnowledgeSearchService search;
    @MockitoBean KnowledgeIngestionService ingestion;
    @MockitoBean GitHubTools githubTools;
    @MockitoBean GitHubActionsTools actionsTools;

    private String bearer(String user, Role role) {
        return "Bearer " + jwt.issue(user, role).token();
    }

    @Test
    void publicReadsAreOpenToAnonymousVisitors() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        mvc.perform(get("/api/release/drafts")).andExpect(status().isOk());
        mvc.perform(get("/api/release/drafts/1")).andExpect(status().isOk());
        mvc.perform(get("/api/knowledge/search").param("q", "hotfix")).andExpect(status().isOk());
        mvc.perform(get("/api/knowledge/status")).andExpect(status().isOk());
    }

    @Test
    void agentEndpointsStayOpen() throws Exception {
        when(agent.ask(anyString())).thenReturn("ok");

        mvc.perform(post("/api/agent/ask").contentType(JSON).content("{\"message\":\"hello\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void everyWriteNeedsALogin() throws Exception {
        mvc.perform(post("/api/release/drafts").contentType(JSON).content("{\"tag\":\"v9.9.9\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(post("/api/release/drafts/1/approve").contentType(JSON).content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(post("/api/release/drafts/1/reject").contentType(JSON).content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(post("/api/release/drafts/1/retry"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void anApproverCanCreateApproveRejectAndRetry() throws Exception {
        String token = bearer("alice", Role.APPROVER);

        mvc.perform(post("/api/release/drafts").header(AUTH, token).contentType(JSON).content("{\"tag\":\"v9.9.9\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/release/drafts/1/approve").header(AUTH, token).contentType(JSON).content("{}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/release/drafts/1/reject").header(AUTH, token).contentType(JSON).content("{}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/release/drafts/1/retry").header(AUTH, token))
                .andExpect(status().isOk());
    }

    @Test
    void decidedByComesFromTheTokenNotFromTheBody() throws Exception {
        String token = bearer("alice", Role.APPROVER);

        mvc.perform(post("/api/release/drafts/5/approve").header(AUTH, token).contentType(JSON)
                        .content("{\"decidedBy\":\"mallory\",\"comment\":\"ok\",\"override\":true,"
                                + "\"overrideReason\":\"a valid reason here\",\"title\":\"T\"}"))
                .andExpect(status().isOk());
        ArgumentCaptor<ApproveRequest> captor = ArgumentCaptor.forClass(ApproveRequest.class);
        verify(publisher).approve(eq(5L), captor.capture());
        assertThat(captor.getValue().decidedBy()).isEqualTo("alice");
        assertThat(captor.getValue().comment()).isEqualTo("ok");
        assertThat(captor.getValue().override()).isTrue();
        assertThat(captor.getValue().overrideReason()).isEqualTo("a valid reason here");
        assertThat(captor.getValue().title()).isEqualTo("T");

        mvc.perform(post("/api/release/drafts/6/reject").header(AUTH, token).contentType(JSON)
                        .content("{\"decidedBy\":\"mallory\",\"comment\":\"no\"}"))
                .andExpect(status().isOk());
        verify(drafts).reject(eq(6L), eq("alice"), eq("no"));
    }

    @Test
    void adminOnlyRoutesRejectApproversAndAnonymous() throws Exception {
        String approver = bearer("alice", Role.APPROVER);
        String admin = bearer("root", Role.ADMIN);

        mvc.perform(post("/api/knowledge/reindex")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/knowledge/reindex").header(AUTH, approver))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(post("/api/knowledge/reindex").header(AUTH, admin)).andExpect(status().isOk());

        mvc.perform(get("/api/debug/repo")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/debug/repo").header(AUTH, approver))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/debug/repo").header(AUTH, admin)).andExpect(status().isOk());
    }

    @Test
    void anAdminAlsoHasApproverRights() throws Exception {
        mvc.perform(post("/api/release/drafts/1/retry").header(AUTH, bearer("root", Role.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void aTokenSignedWithAnotherSecretIsRejected() throws Exception {
        String forged = new JwtService("another-secret-9876543210-zyxwvutsrqpo", 60)
                .issue("alice", Role.ADMIN).token();

        mvc.perform(post("/api/release/drafts/1/retry").header(AUTH, "Bearer " + forged))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void theOldAdminTokenHeaderNoLongerOpensAnything() throws Exception {
        mvc.perform(post("/api/release/drafts/1/retry").header("X-Admin-Token", "test-admin-token-1234567890"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void llmProxyRoutesAndUnknownPathsAreClosedToAnonymous() throws Exception {
        mvc.perform(post("/api/chat").contentType(JSON).content("{\"message\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/analyze").contentType(JSON).content("{\"text\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/does-not-exist")).andExpect(status().isUnauthorized());
    }

    @Test
    void draftCreationIsAttributedToTheTokenUser() throws Exception {
        mvc.perform(post("/api/release/drafts").header(AUTH, bearer("alice", Role.APPROVER)).contentType(JSON)
                        .content("{\"tag\":\"v9.9.9\",\"prerelease\":true}"))
                .andExpect(status().isCreated());

        verify(drafts).create("v9.9.9", true, "alice");
    }
}
package com.releasepilot.release;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminGuardTest {

    private static final String TOKEN = "test-admin-token-1234567890";   // from application-test.properties

    @Autowired MockMvc mvc;

    @Test
    void noToken_is401_withOurJsonErrorBody() throws Exception {
        mvc.perform(get("/api/release/drafts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void wrongToken_is401() throws Exception {
        mvc.perform(get("/api/release/drafts").header("X-Admin-Token", "wrong-wrong-wrong-wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void writeEndpoints_areGuardedToo() throws Exception {
        mvc.perform(post("/api/release/drafts/1/approve")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());   // 401, not 404: the guard runs first
    }

    @Test
    void correctToken_reachesTheController() throws Exception {
        mvc.perform(get("/api/release/drafts").header("X-Admin-Token", TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void unknownDraft_is404_withErrorCode() throws Exception {
        mvc.perform(get("/api/release/drafts/987654").header("X-Admin-Token", TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRAFT_NOT_FOUND"));
    }

    @Test
    void invalidTag_is400_validationError() throws Exception {
        mvc.perform(post("/api/release/drafts").header("X-Admin-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tag\":\"1.0\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void health_staysOpen() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
    }
}
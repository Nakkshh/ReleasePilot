package com.releasepilot.ratelimit;

import com.releasepilot.service.AgentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.releasepilot.knowledge.KnowledgeSearchService;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ratelimit.agent.per-client-per-minute=2",
        "ratelimit.agent.global-per-minute=100",
        "ratelimit.agent.max-concurrent=5"
})
class RateLimitWiringTest {

    @Autowired MockMvc mvc;
    @MockitoBean AgentService agentService;
    @MockitoBean KnowledgeSearchService search;

    @Test
    void thirdAgentRequestIs429_withCodeAndRetryAfter() throws Exception {
        when(agentService.ask(anyString())).thenReturn("ok");
        String body = "{\"message\":\"hello\"}";

        mvc.perform(post("/api/agent/ask").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(post("/api/agent/ask").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(post("/api/agent/ask").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void healthIsNeverLimited() throws Exception {
        for (int n = 0; n < 5; n++) {
            mvc.perform(get("/api/health")).andExpect(status().isOk());
        }
    }

    @Test
    void knowledgeSearchIsLimitedToo() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/api/knowledge/search").param("q", "x").with(from("10.0.0.7")))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/knowledge/search").param("q", "x").with(from("10.0.0.7")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    /** A separate client address, so this test never shares a bucket with the others. */
    private static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
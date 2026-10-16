package com.releasepilot.config;

import com.releasepilot.ratelimit.AgentRateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class RateLimitConfig implements WebMvcConfigurer {

    private final AgentRateLimitInterceptor agentRateLimitInterceptor;

    public RateLimitConfig(AgentRateLimitInterceptor agentRateLimitInterceptor) {
        this.agentRateLimitInterceptor = agentRateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Public endpoints that cost CPU or LLM quota. Knowledge search runs the local embedding model on every call.
        registry.addInterceptor(agentRateLimitInterceptor)
                .addPathPatterns("/api/agent/**", "/api/knowledge/search");
    }
}
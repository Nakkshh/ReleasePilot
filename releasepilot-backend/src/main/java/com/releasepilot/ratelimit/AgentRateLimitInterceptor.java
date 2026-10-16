package com.releasepilot.ratelimit;

import com.releasepilot.exception.ClientRateLimitedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.Semaphore;
import java.util.function.LongSupplier;

/** Per-client and global request limits plus a cap on concurrent agent runs, for /api/agent/**. */
@Component
public class AgentRateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AgentRateLimitInterceptor.class);
    private static final String ACQUIRED = AgentRateLimitInterceptor.class.getName() + ".acquired";
    private static final String GLOBAL_KEY = "global";
    private static final long MAX_CLIENTS = 10_000;
    private static final long BUSY_RETRY_AFTER_SECONDS = 10;

    private final boolean enabled;
    private final TokenBucketLimiter perClient;   // null = disabled
    private final TokenBucketLimiter global;      // null = disabled
    private final Semaphore concurrent;           // null = disabled

    @Autowired
    public AgentRateLimitInterceptor(@Value("${ratelimit.agent.enabled:true}") boolean enabled,
                                     @Value("${ratelimit.agent.per-client-per-minute:6}") int perClientPerMinute,
                                     @Value("${ratelimit.agent.global-per-minute:20}") int globalPerMinute,
                                     @Value("${ratelimit.agent.max-concurrent:3}") int maxConcurrent) {
        this(enabled, perClientPerMinute, globalPerMinute, maxConcurrent, System::nanoTime);
    }

    /** Test seam: lets tests control the clock. */
    AgentRateLimitInterceptor(boolean enabled, int perClientPerMinute, int globalPerMinute,
                              int maxConcurrent, LongSupplier clock) {
        this.enabled = enabled;
        this.perClient = perClientPerMinute > 0
                ? TokenBucketLimiter.perMinute(perClientPerMinute, MAX_CLIENTS, clock) : null;
        this.global = globalPerMinute > 0
                ? TokenBucketLimiter.perMinute(globalPerMinute, 1, clock) : null;
        this.concurrent = maxConcurrent > 0 ? new Semaphore(maxConcurrent) : null;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!enabled || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;   // CORS preflight must not use up budget
        }
        String client = request.getRemoteAddr();

        if (perClient != null) {
            TokenBucketLimiter.Decision d = perClient.tryAcquire(client);
            if (!d.allowed()) {
                log.warn("[RATE-LIMIT] per-client limit hit by {}", client);
                throw new ClientRateLimitedException("RATE_LIMITED",
                        "Too many requests. Try again in " + d.retryAfterSeconds() + " seconds.",
                        d.retryAfterSeconds());
            }
        }
        if (global != null) {
            TokenBucketLimiter.Decision d = global.tryAcquire(GLOBAL_KEY);
            if (!d.allowed()) {
                log.warn("[RATE-LIMIT] global limit hit (request from {})", client);
                throw new ClientRateLimitedException("RATE_LIMITED",
                        "The service is receiving too many requests. Try again in "
                                + d.retryAfterSeconds() + " seconds.",
                        d.retryAfterSeconds());
            }
        }
        if (concurrent != null) {
            if (!concurrent.tryAcquire()) {
                log.warn("[RATE-LIMIT] concurrency cap reached (request from {})", client);
                throw new ClientRateLimitedException("AGENT_BUSY",
                        "Too many agent runs are in progress. Try again shortly.", BUSY_RETRY_AFTER_SECONDS);
            }
            request.setAttribute(ACQUIRED, Boolean.TRUE);   // acquired last, so a rejection above never leaks a slot
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        if (concurrent != null && Boolean.TRUE.equals(request.getAttribute(ACQUIRED))) {
            request.removeAttribute(ACQUIRED);
            concurrent.release();
        }
    }
}
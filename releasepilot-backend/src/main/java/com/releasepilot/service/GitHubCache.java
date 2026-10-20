package com.releasepilot.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/** Short-lived in-memory cache for GitHub read calls. Failures are not cached; null results are. */
@Component
public class GitHubCache {

    private static final Logger log = LoggerFactory.getLogger(GitHubCache.class);

    private final Cache<String, Optional<Object>> cache;   // Optional so a null result can be cached
    private final boolean enabled;

    @Autowired
    public GitHubCache(@Value("${github.cache.ttl-seconds:30}") long ttlSeconds,
                       @Value("${github.cache.max-entries:200}") long maxEntries) {
        this(ttlSeconds, maxEntries, Ticker.systemTicker());
    }

    /** Test seam: lets tests move time without sleeping. */
    GitHubCache(long ttlSeconds, long maxEntries, Ticker ticker) {
        this.enabled = ttlSeconds > 0;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(Math.max(1, ttlSeconds)))
                .maximumSize(Math.max(1, maxEntries))
                .ticker(ticker)
                .build();
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Supplier<T> loader) {
        if (!enabled) {
            return loader.get();
        }
        Optional<Object> value = cache.get(key, k -> {
            log.debug("[GH-CACHE] miss {}", k);
            return Optional.ofNullable(loader.get());
        });
        return (T) value.orElse(null);
    }

    public void invalidate(String key) {
        cache.invalidate(key);
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }
}
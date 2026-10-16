package com.releasepilot.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.function.LongSupplier;

/** In-memory token bucket per key. Idle buckets expire; the number of keys is capped. */
public final class TokenBucketLimiter {

    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    private static final Decision ALLOWED = new Decision(true, 0);

    private static final class Bucket {
        double tokens;
        long lastNanos;

        Bucket(double tokens, long lastNanos) {
            this.tokens = tokens;
            this.lastNanos = lastNanos;
        }
    }

    private final int capacity;
    private final double refillPerSecond;
    private final LongSupplier clock;
    private final Cache<String, Bucket> buckets;

    public TokenBucketLimiter(int capacity, double refillPerSecond, long maxKeys, LongSupplier clock) {
        if (capacity < 1 || refillPerSecond <= 0) {
            throw new IllegalArgumentException("capacity must be >= 1 and refillPerSecond > 0");
        }
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.clock = clock;
        // A bucket idle for longer than a full refill is indistinguishable from a new one, so dropping it is safe.
        long idleSeconds = (long) Math.ceil(capacity / refillPerSecond) + 60;
        this.buckets = Caffeine.newBuilder()
                .maximumSize(Math.max(1, maxKeys))
                .expireAfterAccess(Duration.ofSeconds(idleSeconds))
                .build();
    }

    public static TokenBucketLimiter perMinute(int perMinute, long maxKeys, LongSupplier clock) {
        return new TokenBucketLimiter(perMinute, perMinute / 60.0, maxKeys, clock);
    }

    public Decision tryAcquire(String key) {
        Bucket bucket = buckets.get(key, k -> new Bucket(capacity, clock.getAsLong()));
        synchronized (bucket) {
            long now = clock.getAsLong();
            double elapsedSeconds = Math.max(0, now - bucket.lastNanos) / 1_000_000_000.0;
            bucket.tokens = Math.min(capacity, bucket.tokens + elapsedSeconds * refillPerSecond);
            bucket.lastNanos = now;
            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                return ALLOWED;
            }
            long wait = (long) Math.ceil((1.0 - bucket.tokens) / refillPerSecond);
            return new Decision(false, Math.max(1, wait));
        }
    }
}
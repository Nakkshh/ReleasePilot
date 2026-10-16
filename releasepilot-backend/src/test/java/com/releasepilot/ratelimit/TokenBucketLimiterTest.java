package com.releasepilot.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketLimiterTest {

    private static final long SECOND = 1_000_000_000L;
    private final AtomicLong nanos = new AtomicLong();

    private TokenBucketLimiter limiter(int capacity, double refillPerSecond) {
        return new TokenBucketLimiter(capacity, refillPerSecond, 100, nanos::get);
    }

    @Test
    void allowsTheBurstThenDenies() {
        TokenBucketLimiter l = limiter(3, 1.0);
        assertThat(l.tryAcquire("a").allowed()).isTrue();
        assertThat(l.tryAcquire("a").allowed()).isTrue();
        assertThat(l.tryAcquire("a").allowed()).isTrue();

        TokenBucketLimiter.Decision denied = l.tryAcquire("a");
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isEqualTo(1);
    }

    @Test
    void refillsOverTimeButNeverAboveCapacity() {
        TokenBucketLimiter l = limiter(3, 1.0);
        for (int i = 0; i < 3; i++) {
            l.tryAcquire("a");
        }
        assertThat(l.tryAcquire("a").allowed()).isFalse();

        nanos.addAndGet(SECOND);
        assertThat(l.tryAcquire("a").allowed()).isTrue();
        assertThat(l.tryAcquire("a").allowed()).isFalse();

        nanos.addAndGet(100 * SECOND);   // far longer than a full refill
        assertThat(l.tryAcquire("a").allowed()).isTrue();
        assertThat(l.tryAcquire("a").allowed()).isTrue();
        assertThat(l.tryAcquire("a").allowed()).isTrue();
        assertThat(l.tryAcquire("a").allowed()).isFalse();   // capped at 3, not 100
    }

    @Test
    void keysHaveIndependentBuckets() {
        TokenBucketLimiter l = limiter(1, 1.0);
        assertThat(l.tryAcquire("a").allowed()).isTrue();
        assertThat(l.tryAcquire("a").allowed()).isFalse();
        assertThat(l.tryAcquire("b").allowed()).isTrue();
    }

    @Test
    void retryAfterReflectsTheRefillRate() {
        TokenBucketLimiter l = TokenBucketLimiter.perMinute(1, 100, nanos::get);   // one token per 60 s
        assertThat(l.tryAcquire("a").allowed()).isTrue();

        TokenBucketLimiter.Decision denied = l.tryAcquire("a");
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isBetween(60L, 61L);   // floating point may round up by 1
    }
}
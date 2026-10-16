package com.releasepilot.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHubCacheTest {

    private final AtomicLong nanos = new AtomicLong();
    private final AtomicInteger loads = new AtomicInteger();

    private GitHubCache cache(long ttlSeconds) {
        return new GitHubCache(ttlSeconds, 100, nanos::get);
    }

    @Test
    void secondCallWithinTheTtlIsServedFromCache() {
        GitHubCache c = cache(30);
        assertThat(c.get("k", () -> "v" + loads.incrementAndGet())).isEqualTo("v1");
        nanos.addAndGet(10_000_000_000L);   // +10 s
        assertThat(c.get("k", () -> "v" + loads.incrementAndGet())).isEqualTo("v1");
        assertThat(loads.get()).isEqualTo(1);
    }

    @Test
    void entryExpiresAfterTheTtl() {
        GitHubCache c = cache(30);
        c.get("k", () -> "v" + loads.incrementAndGet());
        nanos.addAndGet(31_000_000_000L);   // +31 s
        assertThat(c.get("k", () -> "v" + loads.incrementAndGet())).isEqualTo("v2");
    }

    @Test
    void nullResultsAreCachedToo() {
        GitHubCache c = cache(30);
        assertThat(c.<String>get("none", () -> { loads.incrementAndGet(); return null; })).isNull();
        assertThat(c.<String>get("none", () -> { loads.incrementAndGet(); return null; })).isNull();
        assertThat(loads.get()).isEqualTo(1);
    }

    @Test
    void failuresAreNotCached() {
        GitHubCache c = cache(30);
        assertThatThrownBy(() -> c.get("k", () -> {
            loads.incrementAndGet();
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class).hasMessage("boom");

        assertThat(c.get("k", () -> "ok" + loads.incrementAndGet())).isEqualTo("ok2");
    }

    @Test
    void ttlZeroDisablesCaching() {
        GitHubCache c = cache(0);
        c.get("k", () -> "v" + loads.incrementAndGet());
        c.get("k", () -> "v" + loads.incrementAndGet());
        assertThat(loads.get()).isEqualTo(2);
    }

    @Test
    void invalidateAllForcesAReload() {
        GitHubCache c = cache(30);
        c.get("k", () -> "v" + loads.incrementAndGet());
        c.invalidateAll();
        assertThat(c.get("k", () -> "v" + loads.incrementAndGet())).isEqualTo("v2");
    }
}
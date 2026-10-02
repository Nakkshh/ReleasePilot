package com.releasepilot.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestClockConfig {

    /** @Primary: wins over the production Clock bean wherever a Clock is injected. */
    @Bean
    @Primary
    public MutableClock testClock() {
        return new MutableClock();
    }
}
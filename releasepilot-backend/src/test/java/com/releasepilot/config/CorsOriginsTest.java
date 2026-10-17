package com.releasepilot.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorsOriginsTest {

    @Test
    void blankMeansNoCrossOriginAccess() {
        assertThat(SecurityConfig.parseOrigins(null)).isEmpty();
        assertThat(SecurityConfig.parseOrigins("")).isEmpty();
        assertThat(SecurityConfig.parseOrigins(" , ")).isEmpty();
    }

    @Test
    void parsesTrimsAndDropsTrailingSlashes() {
        assertThat(SecurityConfig.parseOrigins("https://app.example.com/, http://localhost:5173"))
                .containsExactly("https://app.example.com", "http://localhost:5173");
    }

    @Test
    void wildcardsAndNonOriginsFailFast() {
        for (String bad : new String[]{"*", "https://*.example.com", "app.example.com", "https://a.example.com/path"}) {
            assertThatThrownBy(() -> SecurityConfig.parseOrigins(bad))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
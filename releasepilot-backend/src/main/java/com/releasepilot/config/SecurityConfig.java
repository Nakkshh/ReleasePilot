package com.releasepilot.config;

import com.releasepilot.auth.AccountGuard;
import com.releasepilot.auth.JsonSecurityErrorHandler;
import com.releasepilot.auth.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Signature + expiry first, then the account check (user enabled, not stale, roles from the DB). */
    @Bean
    public JwtDecoder jwtDecoder(JwtService jwtService, AccountGuard guard) {
        JwtDecoder base = jwtService.decoder();
        return new JwtDecoder() {
            @Override
            public Jwt decode(String token) throws JwtException {
                return guard.verify(base.decode(token));
            }
        };
    }

    /** Reads the "roles" claim (["ADMIN","APPROVER"]) as ROLE_ADMIN / ROLE_APPROVER. */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    private static final Pattern ORIGIN = Pattern.compile("^https?://[A-Za-z0-9.-]+(:\\d{1,5})?$");

    /**
     * Browser access from other origins. Empty (the default) means none: nothing is registered, so the
     * browser blocks cross-origin calls. Exact origins only, never "*". No credentials: the bearer token
     * travels in a header, not in a cookie. Retry-After is exposed so the UI can read a 429's wait time.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${cors.allowed-origins:}") String origins) {
        List<String> allowed = parseOrigins(origins);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (!allowed.isEmpty()) {
            CorsConfiguration cfg = new CorsConfiguration();
            cfg.setAllowedOrigins(allowed);
            cfg.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
            cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
            cfg.setExposedHeaders(List.of("Retry-After"));
            cfg.setAllowCredentials(false);
            cfg.setMaxAge(3600L);
            source.registerCorsConfiguration("/api/**", cfg);
        }
        return source;
    }

    /** Comma-separated plain origins (scheme://host[:port]). A wildcard or a malformed entry stops the app. */
    static List<String> parseOrigins(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String o = part.strip();
            if (o.isEmpty()) {
                continue;
            }
            if (o.endsWith("/")) {
                o = o.substring(0, o.length() - 1);
            }
            if (!ORIGIN.matcher(o).matches()) {
                throw new IllegalStateException("cors.allowed-origins: not a plain http(s) origin: " + o);
            }
            out.add(o);
        }
        return List.copyOf(out);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationConverter converter,
                                                   JsonSecurityErrorHandler errors,
                                                   @Qualifier("corsConfigurationSource") CorsConfigurationSource cors) throws Exception {
        http
                .cors(c -> c.configurationSource(cors))
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .csrf(AbstractHttpConfigurer::disable)    // stateless bearer tokens, no cookies
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // The first matching rule wins, so order matters.
                        .requestMatchers("/error").permitAll()   // error dispatches are authorised too
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/me", "/api/auth/password").authenticated()

                        // Read-only demo: anyone may look at drafts, search the knowledge base and run the agents.
                        .requestMatchers(HttpMethod.GET, "/api/release/drafts", "/api/release/drafts/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/knowledge/search", "/api/knowledge/status").permitAll()
                        .requestMatchers("/api/agent/**").permitAll()   // rate-limited by AgentRateLimitInterceptor

                        // Anything that changes a release needs an approver; admins carry that role too.
                        .requestMatchers("/api/release/**").hasRole("APPROVER")
                        .requestMatchers("/api/knowledge/**").hasRole("ADMIN")   // reindex
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")       // user management
                        .requestMatchers("/api/debug/**").hasRole("ADMIN")

                        // Default deny: /api/chat, /api/analyze and anything unknown need a login.
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors));
        return http.build();
    }
}
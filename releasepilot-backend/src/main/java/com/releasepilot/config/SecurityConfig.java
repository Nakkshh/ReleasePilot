package com.releasepilot.config;

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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtService jwtService) {
        return jwtService.decoder();
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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationConverter converter,
                                                   JsonSecurityErrorHandler errors) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)   // stateless bearer tokens, no cookies
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // The first matching rule wins, so order matters.
                        .requestMatchers("/error").permitAll()   // error dispatches are authorised too
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/me").authenticated()

                        // Read-only demo: anyone may look at drafts, search the knowledge base and run the agents.
                        .requestMatchers(HttpMethod.GET, "/api/release/drafts", "/api/release/drafts/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/knowledge/search", "/api/knowledge/status").permitAll()
                        .requestMatchers("/api/agent/**").permitAll()   // rate-limited by AgentRateLimitInterceptor

                        // Anything that changes a release needs an approver; admins carry that role too.
                        .requestMatchers("/api/release/**").hasRole("APPROVER")
                        .requestMatchers("/api/knowledge/**").hasRole("ADMIN")   // reindex
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
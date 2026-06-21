package com.chandan.randomchat.config;

import com.chandan.randomchat.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * SecurityConfig — configures Spring Security for JWT stateless auth.
 *
 * Key decisions:
 *   - STATELESS session: no HttpSession created. JWT carries all identity.
 *   - CSRF disabled: we're a REST API consumed by Android, not a browser.
 *     CSRF attacks require cookies — we use Authorization header instead.
 *   - Default login form disabled: we have no browser login page.
 *   - JwtAuthFilter runs before Spring's UsernamePasswordAuthenticationFilter.
 *
 * Public endpoints (no JWT required):
 *   POST /api/user/init        — identity establishment
 *   GET  /actuator/health      — load balancer health check
 *   GET  /actuator/info
 *   GET  /ws/**                — WebSocket handshake (auth via first WS message)
 *
 * Everything else requires valid JWT.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF — REST API with JWT, no browser cookies
                .csrf(AbstractHttpConfigurer::disable)

                // No sessions — stateless JWT
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Disable default login form and basic auth
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                // Endpoint authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Public — no JWT needed
                        .requestMatchers(
                                "/api/user/init",
                                "/actuator/health",
                                "/actuator/info",
                                "/ws/**"               // WebSocket upgrade
                        ).permitAll()

                        // Everything else requires authenticated JWT
                        .anyRequest().authenticated()
                )

                // Add our JWT filter before Spring's auth filter
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCrypt password encoder for admin passwords.
     * Strength 12 — secure but not too slow for admin login.
     * Android users never have passwords — only admin users do.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
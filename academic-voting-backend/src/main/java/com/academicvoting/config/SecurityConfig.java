package com.academicvoting.config;

import com.academicvoting.auth.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 6 configuration for the Academic Voting backend.
 *
 * <p>Security model:
 * <ul>
 *   <li><b>Stateless</b> — no sessions; every request is authenticated via JWT</li>
 *   <li><b>CSRF disabled</b> — REST API consumed by non-browser clients</li>
 *   <li><b>Public</b> — login, read-only poll endpoints, and the voting endpoint
 *       (auth for voting is the ephemeral private key itself, not JWT)</li>
 *   <li><b>Authenticated</b> — credential issuance requires a valid JWT</li>
 *   <li><b>Admin-only</b> — poll creation, whitelisting, and admin operations</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    // ── Security Filter Chain ─────────────────────────────────────────────────

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ── No CSRF for a stateless REST API ─────────────────────────────
            .csrf(AbstractHttpConfigurer::disable)

            // ── Stateless sessions — Spring Security must NOT create HttpSessions
            .sessionManagement(sm ->
                sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ── Authorization rules ───────────────────────────────────────────
            .authorizeHttpRequests(auth -> auth

                // Public: login
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()

                // Public: all read-only poll queries
                .requestMatchers(HttpMethod.GET, "/api/polls/**").permitAll()

                // Public: cast vote — auth is the ephemeral private key, not JWT
                .requestMatchers(HttpMethod.POST, "/api/polls/*/vote").permitAll()

                // Only students can obtain voting credentials
                .requestMatchers(HttpMethod.POST, "/api/auth/voting-credential").hasRole("STUDENT")

                // Any authenticated user can view their own info
                .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()

                // Admin-only: poll management
                .requestMatchers(HttpMethod.POST, "/api/polls").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/polls/*/whitelist").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/polls/*/whitelist/batch").hasRole("ADMIN")

                // Admin-only: admin operations
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // Everything else requires authentication
                .anyRequest().authenticated()
            )

            // ── Plug in our JWT filter ────────────────────────────────────────
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ── Password Encoder ──────────────────────────────────────────────────────

    /**
     * BCrypt password encoder with default strength (10 rounds).
     * Used by {@link com.academicvoting.auth.MockUserStore} to hash passwords at startup.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

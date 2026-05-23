package com.academicvoting.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Spring Security filter that intercepts every request and validates the
 * {@code Authorization: Bearer <token>} header.
 *
 * <p>If a valid JWT is found, populates the {@link SecurityContextHolder}
 * so downstream controllers and method-security can use it.
 * If no token is present (or it is invalid), the request passes through
 * unauthenticated — public endpoints remain accessible.
 *
 * <p>Registered in {@link com.academicvoting.config.SecurityConfig} before
 * {@link org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest  request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain         chain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // ── No token present — let Spring Security handle as anonymous ────────
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        // ── Validate token ────────────────────────────────────────────────────
        if (!jwtUtil.isValid(token)) {
            log.debug("Invalid or expired JWT received from {}", request.getRemoteAddr());
            chain.doFilter(request, response);
            return;
        }

        // ── Already authenticated (e.g., nested filters) ──────────────────────
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        // ── Build authentication token ────────────────────────────────────────
        Claims claims  = jwtUtil.validateAndParse(token);
        String userId  = claims.getSubject();
        String role    = claims.get("role", String.class);

        // Spring Security roles must be prefixed with ROLE_
        SimpleGrantedAuthority authority =
            new SimpleGrantedAuthority("ROLE_" + role);

        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(userId, null, List.of(authority));

        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);

        log.debug("Authenticated user: {} [{}] for {}", userId, role, request.getRequestURI());
        chain.doFilter(request, response);
    }
}

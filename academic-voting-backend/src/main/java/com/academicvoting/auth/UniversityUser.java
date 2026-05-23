package com.academicvoting.auth;

import lombok.Builder;
import lombok.Data;

/**
 * Represents an authenticated university member (student or admin).
 * Loaded from the mock user store; replace with a DB entity in production.
 */
@Data
@Builder
public class UniversityUser {

    /** Unique institutional identifier (e.g., student001). */
    private String userId;

    /** Display name. */
    private String name;

    /** Institutional email address. */
    private String email;

    /**
     * Role string — either {@code "STUDENT"} or {@code "ADMIN"}.
     * Spring Security roles are prefixed with {@code ROLE_} internally.
     */
    private String role;

    /**
     * BCrypt-hashed password.
     * The plain-text password is never stored after hashing.
     */
    private String passwordHash;
}

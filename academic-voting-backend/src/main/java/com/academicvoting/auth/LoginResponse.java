package com.academicvoting.auth;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Response payload for {@code POST /api/auth/login}.
 */
@Data
@Builder
public class LoginResponse {

    /** Signed JWT — include in every subsequent request as {@code Authorization: Bearer <token>}. */
    private String token;

    /** Token type — always "Bearer". */
    @Builder.Default
    private String tokenType = "Bearer";

    /** Token expiry as an ISO-8601 timestamp. */
    private String expiresAt;

    /** Authenticated user's ID. */
    private String userId;

    /** Authenticated user's display name. */
    private String name;

    /** Authenticated user's email. */
    private String email;

    /** Authenticated user's role (STUDENT or ADMIN). */
    private String role;
}

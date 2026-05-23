package com.academicvoting.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for {@code POST /api/auth/login}.
 */
@Data
public class LoginRequest {

    /** Institutional user ID (e.g., "student001"). */
    @NotBlank(message = "userId must not be blank")
    private String userId;

    /** Plain-text password — compared against the BCrypt hash in the store. */
    @NotBlank(message = "password must not be blank")
    private String password;
}

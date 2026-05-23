package com.academicvoting.auth;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * Request body for {@code POST /api/auth/voting-credential}.
 * Requires a valid JWT in the Authorization header.
 */
@Data
public class CredentialRequest {

    /** The poll ID the voter wants to participate in. */
    @Min(value = 0, message = "pollId must be >= 0")
    private long pollId;
}

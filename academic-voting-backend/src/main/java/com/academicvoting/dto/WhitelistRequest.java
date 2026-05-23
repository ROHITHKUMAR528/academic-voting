package com.academicvoting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Request body for {@code POST /api/polls/{pollId}/whitelist}.
 * Whitelists a single voter address.
 */
@Data
public class WhitelistRequest {

    /**
     * Ethereum address to whitelist (checksummed or lowercase).
     * Pattern: 0x followed by 40 hex characters.
     */
    @NotBlank(message = "voterAddress must not be blank")
    @Pattern(
        regexp = "^0x[0-9a-fA-F]{40}$",
        message = "voterAddress must be a valid Ethereum address (0x + 40 hex chars)"
    )
    private String voterAddress;
}

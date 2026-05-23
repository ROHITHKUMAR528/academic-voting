package com.academicvoting.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Request body for {@code POST /api/polls/{pollId}/vote}.
 *
 * <p>The voter signs using their own private key so the transaction is sent
 * from their address — enabling the contract's single-vote-per-address guard.
 */
@Data
public class VoteRequest {

    /**
     * Private key of the voter (hex, with or without 0x prefix).
     *
     * <p><strong>Security note:</strong> In Sprint 3 this is replaced by
     * ephemeral wallet generation + identity blinding so the real key
     * never leaves the client. For Sprint 2 this is an MVP simplification.
     */
    @NotBlank(message = "voterPrivateKey must not be blank")
    private String voterPrivateKey;

    /**
     * Zero-based index of the option the voter chooses.
     * Must be >= 0; upper bound is validated on-chain.
     */
    @Min(value = 0, message = "optionIndex must be >= 0")
    private int optionIndex;
}

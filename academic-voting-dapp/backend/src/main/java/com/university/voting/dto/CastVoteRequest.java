package com.university.voting.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Request body for POST /api/votes */
@Data
public class CastVoteRequest {

    @NotBlank(message = "pollId is required")
    private String pollId;

    private long optionIndex;

    /**
     * The voter's ephemeral wallet address (injected by the anonymity layer in Sprint 3).
     * For Sprint 2 this is the caller's address passed directly from the client for testing.
     */
    @NotBlank(message = "voterAddress is required")
    private String voterAddress;

    /**
     * The ephemeral wallet's private key (Sprint 3 will inject this server-side;
     * for Sprint 2 the test client supplies it directly).
     */
    @NotBlank(message = "voterPrivateKey is required")
    private String voterPrivateKey;
}

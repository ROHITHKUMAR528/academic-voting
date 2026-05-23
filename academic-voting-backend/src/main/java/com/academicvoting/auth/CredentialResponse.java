package com.academicvoting.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * Response payload for {@code POST /api/auth/voting-credential}.
 *
 * <p><strong>⚠️ Security warning</strong>: The {@code ephemeralPrivateKey} is shown
 * <em>exactly once</em> and is NEVER stored by the server after this response.
 * The voter must save it securely to cast their vote.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CredentialResponse {

    /** The poll this credential is valid for. */
    private long   pollId;

    /**
     * Ephemeral Ethereum wallet address (whitelisted on-chain).
     * This address IS visible on the blockchain — but it is NOT linked to
     * the voter's real identity.
     */
    private String ephemeralAddress;

    /**
     * Ephemeral private key (hex, 0x-prefixed).
     *
     * <p><strong>SHOWN ONCE — server does NOT store this after response.</strong>
     * Use it as {@code voterPrivateKey} in the castVote request.
     */
    private String ephemeralPrivateKey;

    /**
     * One-way commitment proving the voter requested a credential for this poll.
     * Format: {@code SHA-256(userId:pollId:base64(randomSalt))}.
     * Stored server-side to prevent double-credential issuance.
     */
    private String commitment;

    /** Transaction hash of the on-chain whitelist operation. */
    private String whitelistTxHash;

    /** Human-readable warning about key safety. */
    @Builder.Default
    private String warning =
        "Save your ephemeralPrivateKey now — it will NOT be shown again. " +
        "Use it as voterPrivateKey when casting your vote.";
}

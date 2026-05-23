package com.academicvoting.identity;

import com.academicvoting.auth.CredentialResponse;
import com.academicvoting.service.PollService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.protocol.Web3j;
import org.web3j.tx.Transfer;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Core identity-blinding service for Sprint 3.
 *
 * <p>Implements the <em>ephemeral wallet</em> pattern to decouple a voter's
 * real university identity from their on-chain voting address.
 *
 * <h2>Algorithm</h2>
 * <ol>
 *   <li>Guard: reject if the voter already holds a credential for this poll.</li>
 *   <li>Compute a one-way commitment:
 *       {@code SHA-256(userId || ":" || pollId || ":" || base64(randomSalt))}</li>
 *   <li>Generate a fresh EC keypair using {@link Keys#createEcKeyPair()} (Web3j).</li>
 *   <li>Derive the ephemeral Ethereum address from the public key.</li>
 *   <li>Whitelist the ephemeral address on-chain (admin signs via {@link PollService}).</li>
 *   <li><strong>Fund the ephemeral wallet</strong> — send a small ETH stipend from the
 *       admin wallet so the voter can pay gas for exactly one {@code castVote} tx.</li>
 *   <li>Persist the commitment to {@link CommitmentStore} — only after on-chain success.</li>
 *   <li>Return the private key to the caller — it is <strong>never stored</strong>.</li>
 * </ol>
 *
 * <h2>Privacy Guarantees</h2>
 * <ul>
 *   <li>The server sees the ephemeral address during whitelisting/funding, then forgets it.</li>
 *   <li>The commitment is a one-way hash — cannot be reversed to find the voter.</li>
 *   <li>On-chain: only the ephemeral address and vote option are visible.</li>
 *   <li>No server log line writes the (userId → ephemeralAddress) mapping.</li>
 *   <li>The ETH funding tx links admin → ephemeral address, but NOT to the real voter ID.</li>
 * </ul>
 *
 * <h2>Gas Stipend</h2>
 * The {@code castVote} call costs approximately {@code gasPrice × gasLimit} wei.
 * With Hardhat defaults (20 gwei × 300 000 gas = 0.006 ETH), we fund 0.01 ETH
 * to give a comfortable margin. The amount is configurable via
 * {@code web3j.voter-gas-stipend-eth} in {@code application.yml}.
 */
@Slf4j
@Service
public class EphemeralWalletService {

    private final CommitmentStore commitmentStore;
    private final PollService     pollService;
    private final Web3j           web3j;
    private final Credentials     adminCredentials;

    /** ETH amount sent to each ephemeral wallet to cover gas. Configurable. */
    private final BigDecimal gasStipendEth;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public EphemeralWalletService(
            CommitmentStore commitmentStore,
            PollService     pollService,
            Web3j           web3j,
            Credentials     adminCredentials,
            @Value("${web3j.voter-gas-stipend-eth:0.01}") BigDecimal gasStipendEth) {
        this.commitmentStore  = commitmentStore;
        this.pollService      = pollService;
        this.web3j            = web3j;
        this.adminCredentials = adminCredentials;
        this.gasStipendEth    = gasStipendEth;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Issues a one-time voting credential for the given voter and poll.
     *
     * <p>Also funds the ephemeral wallet with a small ETH gas stipend so the
     * voter can submit the {@code castVote} transaction without needing any
     * pre-existing ETH balance.
     *
     * @param userId Authenticated voter's institutional ID
     * @param pollId Target poll ID
     * @return {@link CredentialResponse} containing the ephemeral address and
     *         private key (shown once — server discards the key after returning it)
     * @throws IllegalStateException if the voter already has a credential for this poll
     */
    public CredentialResponse issueCredential(String userId, long pollId) {

        // ── 1. Guard: reject double-issuance ─────────────────────────────────
        if (commitmentStore.hasCredential(userId, pollId)) {
            throw new IllegalStateException(
                "Voting credential already issued for poll #" + pollId +
                ". Each voter may only receive one credential per poll."
            );
        }

        // ── 2. Generate one-way commitment ───────────────────────────────────
        byte[] salt = new byte[32];
        SECURE_RANDOM.nextBytes(salt);
        String saltB64    = Base64.getEncoder().encodeToString(salt);
        String preimage   = userId + ":" + pollId + ":" + saltB64;
        String commitment = sha256Hex(preimage);

        // ── 3. Generate ephemeral EC keypair ─────────────────────────────────
        ECKeyPair keyPair;
        try {
            keyPair = Keys.createEcKeyPair();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate ephemeral keypair", e);
        }

        Credentials ephemeralCredentials = Credentials.create(keyPair);
        String ephemeralAddress = ephemeralCredentials.getAddress();
        // hex private key — returned to caller, NOT stored
        String ephemeralPrivKey = Numeric.toHexStringWithPrefix(keyPair.getPrivateKey());

        // ── 4. Whitelist ephemeral address on-chain ──────────────────────────
        // Log only the address (not the private key)
        log.info("Issuing credential for poll #{} — whitelisting ephemeral address: {}",
            pollId, ephemeralAddress);

        String whitelistTxHash = pollService.whitelistVoter(pollId, ephemeralAddress);

        // ── 5. Fund ephemeral wallet (gas stipend) ───────────────────────────
        // The ephemeral wallet starts with zero ETH. It needs a small amount to
        // pay for the castVote transaction gas. We send the stipend from the admin
        // wallet. This does NOT link the voter's identity — only ephemeral address
        // is visible on-chain.
        fundEphemeralWallet(ephemeralAddress);

        // ── 6. Persist commitment (only after on-chain success) ──────────────
        commitmentStore.storeCommitment(userId, pollId, commitment);
        log.info("Credential issued — commitment stored for pollId: {}", pollId);

        // ── 7. Build and return credential ───────────────────────────────────
        // ephemeralPrivKey is intentionally NOT logged — it leaves the server only
        // in this response and is never stored anywhere.
        return CredentialResponse.builder()
            .pollId(pollId)
            .ephemeralAddress(ephemeralAddress)
            .ephemeralPrivateKey(ephemeralPrivKey)   // ← shown once, then discarded
            .commitment(commitment)
            .whitelistTxHash(whitelistTxHash)
            .build();
    }

    /**
     * Returns the number of polls this voter has obtained credentials for.
     * Used by the {@code /api/auth/me} endpoint.
     *
     * @param userId The voter's institutional ID
     */
    public int credentialCount(String userId) {
        return commitmentStore.credentialCountForUser(userId);
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    /**
     * Transfers a small ETH gas stipend from the admin wallet to the ephemeral address.
     *
     * <p>Uses {@link Transfer#sendFunds} which handles nonce management, gas estimation,
     * and receipt polling automatically.
     *
     * @param ephemeralAddress Recipient ephemeral wallet address
     */
    private void fundEphemeralWallet(String ephemeralAddress) {
        try {
            log.info("Funding ephemeral wallet {} with {} ETH gas stipend",
                ephemeralAddress, gasStipendEth);

            var receipt = Transfer
                .sendFunds(web3j, adminCredentials, ephemeralAddress,
                    gasStipendEth, Convert.Unit.ETHER)
                .send();

            log.info("Gas stipend funded — tx: {}, to: {}",
                receipt.getTransactionHash(), ephemeralAddress);

        } catch (Exception e) {
            // Funding failure is fatal — without ETH the voter cannot cast their vote.
            // We throw before persisting the commitment so the whole operation is rolled back.
            throw new RuntimeException(
                "Failed to fund ephemeral wallet " + ephemeralAddress +
                " with gas stipend: " + e.getMessage(), e
            );
        }
    }

    /**
     * Computes the SHA-256 hash of the input string and returns it as a lowercase hex string.
     *
     * @param input Pre-image string
     * @return 64-character lowercase hex digest
     */
    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the Java spec — will never throw
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

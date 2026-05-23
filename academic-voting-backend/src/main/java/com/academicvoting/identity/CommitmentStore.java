package com.academicvoting.identity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory store of voter commitments.
 *
 * <p><strong>Purpose</strong>: Prevent a single university member from obtaining
 * more than one voting credential for the same poll — which would allow
 * double-voting from two ephemeral wallets.
 *
 * <p><strong>What is stored</strong>:
 * <pre>
 *   userId → Set&lt;commitment&gt;
 *
 *   commitment = "pollId:" + SHA-256(userId + ":" + pollId + ":" + base64(randomSalt))
 * </pre>
 *
 * <p>The commitment is a one-way hash. Even if this store is compromised,
 * an attacker cannot reverse it to discover:
 * <ul>
 *   <li>Which ephemeral wallet belongs to which voter</li>
 *   <li>Which option the voter chose</li>
 * </ul>
 *
 * <p><strong>Production replacement</strong>: Persist to Redis or a DB so
 * commitments survive application restarts.
 */
@Slf4j
@Component
public class CommitmentStore {

    /**
     * userId → Set of "pollId:commitmentHash" strings.
     * ConcurrentHashMap + synchronizedSet ensures thread-safe reads/writes.
     */
    private final ConcurrentHashMap<String, Set<String>> store =
        new ConcurrentHashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Checks whether a voter already has a credential for the given poll.
     *
     * @param userId The voter's institutional ID
     * @param pollId The poll ID
     * @return {@code true} if a credential was already issued
     */
    public boolean hasCredential(String userId, long pollId) {
        Set<String> userCommitments = store.get(userId);
        if (userCommitments == null) return false;

        String prefix = pollId + ":";
        return userCommitments.stream().anyMatch(c -> c.startsWith(prefix));
    }

    /**
     * Records that a voting credential was issued for the given poll.
     *
     * @param userId     The voter's institutional ID
     * @param pollId     The poll ID
     * @param commitment SHA-256 hash of (userId:pollId:salt)
     */
    public void storeCommitment(String userId, long pollId, String commitment) {
        store.computeIfAbsent(userId, k ->
            Collections.newSetFromMap(new ConcurrentHashMap<>())
        ).add(pollId + ":" + commitment);

        log.debug("Commitment stored — userId: {}, pollId: {}", userId, pollId);
    }

    /**
     * Returns the number of voters who have credentials (for diagnostics).
     */
    public int totalVotersWithCredentials() {
        return store.size();
    }

    /**
     * Returns how many polls the given voter has obtained credentials for.
     *
     * @param userId The voter's institutional ID
     */
    public int credentialCountForUser(String userId) {
        Set<String> commitments = store.get(userId);
        return commitments == null ? 0 : commitments.size();
    }
}

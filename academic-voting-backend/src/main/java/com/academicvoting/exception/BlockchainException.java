package com.academicvoting.exception;

/**
 * Thrown when a blockchain operation fails (reverted transaction,
 * insufficient gas, connectivity issues, etc.).
 *
 * <p>The message preserves the Solidity revert reason when available
 * (e.g., "AlreadyVoted()", "NotWhitelisted()").
 */
public class BlockchainException extends RuntimeException {

    private final String revertReason;

    public BlockchainException(String message) {
        super(message);
        this.revertReason = extractRevertReason(message);
    }

    public BlockchainException(String message, Throwable cause) {
        super(message, cause);
        this.revertReason = extractRevertReason(cause != null ? cause.getMessage() : message);
    }

    /** The Solidity custom error name if extractable, otherwise the raw message. */
    public String getRevertReason() {
        return revertReason;
    }

    /**
     * Attempts to extract a Solidity custom error name from the raw exception message.
     * Web3j 4.x embeds the encoded revert data in the exception message string.
     */
    private static String extractRevertReason(String raw) {
        if (raw == null) return "UnknownError";

        // Pattern: "Transaction reverted: <error_name>()"
        if (raw.contains("Transaction reverted:")) {
            int start = raw.indexOf("Transaction reverted:") + 22;
            String sub = raw.substring(start).trim();
            int end = sub.indexOf('\n');
            return end > 0 ? sub.substring(0, end).trim() : sub.trim();
        }

        // Fallback: use the full message
        return raw.length() > 200 ? raw.substring(0, 200) + "…" : raw;
    }
}

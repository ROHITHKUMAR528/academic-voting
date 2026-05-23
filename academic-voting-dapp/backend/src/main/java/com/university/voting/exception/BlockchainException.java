package com.university.voting.exception;

/**
 * Unchecked exception thrown when any blockchain interaction fails.
 * Wraps Web3j checked exceptions so callers don't need to declare them.
 */
public class BlockchainException extends RuntimeException {

    public BlockchainException(String message) {
        super(message);
    }

    public BlockchainException(String message, Throwable cause) {
        super(message, cause);
    }
}

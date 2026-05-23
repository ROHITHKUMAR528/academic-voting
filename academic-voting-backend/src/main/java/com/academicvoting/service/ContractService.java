package com.academicvoting.service;

import com.academicvoting.contracts.AcademicVoting;
import com.academicvoting.exception.BlockchainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.gas.StaticGasProvider;

import java.math.BigInteger;

/**
 * Low-level service for contract loading, deployment, and admin operations.
 *
 * <p>Higher-level services ({@link PollService}, {@link VotingService}) delegate here
 * when they need the base contract instance or admin-signed transactions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractService {

    private final AcademicVoting    contract;
    private final Credentials       adminCredentials;
    private final StaticGasProvider gasProvider;

    // ── Admin Info ────────────────────────────────────────────────────────────

    /**
     * Returns the admin address as reported by the contract.
     */
    public String getAdminAddress() {
        try {
            return contract.admin().send();
        } catch (Exception e) {
            throw new BlockchainException("Failed to fetch admin address", e);
        }
    }

    /**
     * Returns the locally configured admin wallet address (from credentials).
     */
    public String getLocalAdminAddress() {
        return adminCredentials.getAddress();
    }

    /**
     * Returns the total number of polls ever created.
     */
    public BigInteger getTotalPolls() {
        try {
            return contract.totalPolls().send();
        } catch (Exception e) {
            throw new BlockchainException("Failed to fetch totalPolls", e);
        }
    }

    // ── Contract Reference ────────────────────────────────────────────────────

    /**
     * Returns the underlying contract address (checksummed).
     */
    public String getContractAddress() {
        return contract.getContractAddress();
    }

    /**
     * Returns the loaded contract wrapper (for use by sibling services).
     */
    public AcademicVoting getContract() {
        return contract;
    }

    // ── Admin Transfer ────────────────────────────────────────────────────────

    /**
     * Transfers the on-chain admin role to a new address.
     *
     * @param newAdminAddress Ethereum address of the new admin
     * @return Transaction hash of the transfer transaction
     */
    public String transferAdmin(String newAdminAddress) {
        validateAddress(newAdminAddress);
        try {
            log.info("Transferring admin role to: {}", newAdminAddress);
            TransactionReceipt receipt = contract.transferAdmin(newAdminAddress).send();
            String txHash = receipt.getTransactionHash();
            log.info("Admin transfer complete — tx: {}", txHash);
            return txHash;
        } catch (Exception e) {
            throw new BlockchainException("transferAdmin failed", e);
        }
    }

    // ── Shared Utilities ──────────────────────────────────────────────────────

    /**
     * Validates that a string looks like a valid Ethereum address.
     *
     * @throws IllegalArgumentException if the address is malformed
     */
    public void validateAddress(String address) {
        if (address == null || !address.matches("^0x[0-9a-fA-F]{40}$")) {
            throw new IllegalArgumentException(
                "Invalid Ethereum address: " + address
            );
        }
    }

    /**
     * Wraps a raw exception in a {@link BlockchainException}, extracting
     * any Solidity revert reason from the message chain.
     */
    public BlockchainException wrapException(String context, Exception e) {
        log.error("{}: {}", context, e.getMessage());
        return new BlockchainException(context + ": " + e.getMessage(), e);
    }
}

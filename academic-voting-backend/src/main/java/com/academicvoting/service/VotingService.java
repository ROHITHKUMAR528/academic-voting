package com.academicvoting.service;

import com.academicvoting.contracts.AcademicVoting;
import com.academicvoting.dto.PollResultResponse;
import com.academicvoting.dto.VoteRequest;
import com.academicvoting.exception.BlockchainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.gas.StaticGasProvider;
import org.web3j.tuples.generated.Tuple4;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service layer for voting operations and result retrieval.
 *
 * <p>Handles:
 * <ul>
 *   <li>Vote casting — loads a per-voter contract instance signed with the voter's key</li>
 *   <li>Result retrieval — on-chain public read</li>
 *   <li>hasVoted checks</li>
 * </ul>
 *
 * <p><strong>Security design:</strong> Each {@code castVote} call creates a short-lived
 * {@link AcademicVoting} contract instance using the voter's private key. The vote
 * transaction is thus sent <em>from the voter's address</em>, satisfying the contract's
 * per-address single-vote enforcement. The private key is never stored.
 *
 * <p>Sprint 3 replaces direct private-key passing with ephemeral wallet generation +
 * off-chain identity blinding.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VotingService {

    private final ContractService   contractService;
    private final Web3j             web3j;
    private final StaticGasProvider gasProvider;

    // ── Vote Casting ──────────────────────────────────────────────────────────

    /**
     * Casts a vote on behalf of the voter identified by their private key.
     *
     * <p>A per-request {@link AcademicVoting} instance is created using the voter's
     * credentials, ensuring the on-chain {@code msg.sender} is the voter's address.
     *
     * @param pollId  Target poll ID
     * @param request Vote payload containing the voter's private key and option index
     * @return Transaction hash of the castVote transaction
     */
    public String castVote(long pollId, VoteRequest request) {
        // Derive voter credentials (throws if key is malformed)
        Credentials voterCredentials;
        try {
            voterCredentials = Credentials.create(request.getVoterPrivateKey());
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Invalid voterPrivateKey: " + e.getMessage());
        }

        String voterAddress = voterCredentials.getAddress();
        log.info("castVote — poll #{}, voter: {}, option: {}",
            pollId, voterAddress, request.getOptionIndex());

        // Create a custom gas provider for voting with a lower gas limit (200,000)
        // to keep the max upfront transaction cost within the ephemeral wallet's stipend.
        var txGasProvider = new org.web3j.tx.gas.StaticGasProvider(
            gasProvider.getGasPrice(),
            BigInteger.valueOf(200000L)
        );

        // Load a voter-signed contract instance (msg.sender = voter)
        AcademicVoting voterContract = AcademicVoting.load(
            contractService.getContractAddress(),
            web3j,
            voterCredentials,
            txGasProvider
        );

        try {
            TransactionReceipt receipt = voterContract
                .castVote(
                    BigInteger.valueOf(pollId),
                    BigInteger.valueOf(request.getOptionIndex())
                )
                .send();

            // Verify the VoteCast event was emitted
            List<AcademicVoting.VoteCastEventResponse> events =
                voterContract.getVoteCastEvents(receipt);

            if (events.isEmpty()) {
                throw new BlockchainException(
                    "castVote tx succeeded but no VoteCast event found");
            }

            String txHash = receipt.getTransactionHash();
            log.info("Vote cast — tx: {}, voter: {}, option: {}",
                txHash, voterAddress, events.get(0).optionIndex);
            return txHash;

        } catch (BlockchainException e) {
            throw e;
        } catch (Exception e) {
            throw contractService.wrapException(
                "castVote failed for poll #" + pollId, e);
        }
    }

    // ── Result Retrieval ──────────────────────────────────────────────────────

    /**
     * Retrieves full vote results for a poll.
     *
     * @param pollId Target poll ID
     * @return {@link PollResultResponse} with per-option tallies and percentages
     */
    public PollResultResponse getResults(long pollId) {
        try {
            Tuple4<String, List<String>, List<BigInteger>, BigInteger> tuple =
                contractService.getContract()
                    .getResults(BigInteger.valueOf(pollId))
                    .send();

            String       question   = tuple.component1();
            List<String> options    = tuple.component2();
            List<Long>   counts     = tuple.component3().stream()
                .map(BigInteger::longValue)
                .collect(Collectors.toList());
            long         totalVotes = tuple.component4().longValue();

            return PollResultResponse.from(pollId, question, options, counts, totalVotes);

        } catch (Exception e) {
            throw contractService.wrapException(
                "getResults failed for pollId=" + pollId, e);
        }
    }

    /**
     * Returns the vote count for a specific option.
     *
     * @param pollId      Poll ID
     * @param optionIndex Zero-based option index
     * @return Vote count as long
     */
    public long getVoteCount(long pollId, int optionIndex) {
        try {
            BigInteger count = contractService.getContract()
                .getVoteCount(
                    BigInteger.valueOf(pollId),
                    BigInteger.valueOf(optionIndex)
                )
                .send();
            return count.longValue();
        } catch (Exception e) {
            throw contractService.wrapException(
                "getVoteCount failed for pollId=" + pollId + " option=" + optionIndex, e);
        }
    }

    // ── Status Checks ─────────────────────────────────────────────────────────

    /**
     * Checks whether a voter has already voted in a given poll.
     *
     * @param pollId  Poll ID
     * @param address Voter's Ethereum address
     * @return {@code true} if the address has already voted
     */
    public boolean hasVoted(long pollId, String address) {
        contractService.validateAddress(address);
        try {
            return contractService.getContract()
                .hasVoted(BigInteger.valueOf(pollId), address)
                .send();
        } catch (Exception e) {
            throw contractService.wrapException("hasVoted check failed", e);
        }
    }
}

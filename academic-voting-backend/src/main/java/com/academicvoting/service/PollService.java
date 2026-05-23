package com.academicvoting.service;

import com.academicvoting.contracts.AcademicVoting;
import com.academicvoting.dto.CreatePollRequest;
import com.academicvoting.dto.PollInfoResponse;
import com.academicvoting.exception.BlockchainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.util.List;

/**
 * Service layer for poll lifecycle management.
 *
 * <p>Handles:
 * <ul>
 *   <li>Poll creation</li>
 *   <li>Single and batch voter whitelisting</li>
 *   <li>Poll info retrieval</li>
 * </ul>
 *
 * <p>All write operations use the admin credentials configured in {@code Web3jConfig}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PollService {

    private final ContractService contractService;

    // ── Poll Creation ─────────────────────────────────────────────────────────

    /**
     * Creates a new poll on-chain.
     *
     * @param request Validated poll creation payload
     * @return Poll ID assigned by the contract (BigInteger for EVM compatibility)
     */
    public BigInteger createPoll(CreatePollRequest request) {
        // Validate no duplicate option labels
        long distinctCount = request.getOptions().stream().distinct().count();
        if (distinctCount != request.getOptions().size()) {
            throw new IllegalArgumentException("Poll options must all be unique");
        }

        try {
            log.info("Creating poll: \"{}\" with {} options, duration={}s",
                request.getQuestion(), request.getOptions().size(), request.getDurationSeconds());

            AcademicVoting contract = contractService.getContract();

            TransactionReceipt receipt = contract.createPoll(
                request.getQuestion(),
                request.getOptions(),
                BigInteger.valueOf(request.getStartTime()),
                BigInteger.valueOf(request.getDurationSeconds())
            ).send();

            // Extract poll ID from the PollCreated event
            List<AcademicVoting.PollCreatedEventResponse> events =
                contract.getPollCreatedEvents(receipt);

            if (events.isEmpty()) {
                throw new BlockchainException("createPoll tx succeeded but no PollCreated event found");
            }

            BigInteger pollId = events.get(0).pollId;
            log.info("Poll #{} created — tx: {}", pollId, receipt.getTransactionHash());
            return pollId;

        } catch (BlockchainException e) {
            throw e;
        } catch (Exception e) {
            throw contractService.wrapException("createPoll failed", e);
        }
    }

    // ── Voter Whitelisting ────────────────────────────────────────────────────

    /**
     * Whitelists a single voter for the given poll.
     *
     * @param pollId       Target poll ID
     * @param voterAddress Voter's Ethereum address
     * @return Transaction hash
     */
    public String whitelistVoter(long pollId, String voterAddress) {
        contractService.validateAddress(voterAddress);
        try {
            log.info("Whitelisting {} for poll #{}", voterAddress, pollId);
            TransactionReceipt receipt = contractService.getContract()
                .whitelistVoter(BigInteger.valueOf(pollId), voterAddress)
                .send();
            String txHash = receipt.getTransactionHash();
            log.info("Whitelist tx: {}", txHash);
            return txHash;
        } catch (Exception e) {
            throw contractService.wrapException("whitelistVoter failed", e);
        }
    }

    /**
     * Batch-whitelists up to 200 voters for the given poll in a single transaction.
     *
     * @param pollId  Target poll ID
     * @param voters  List of voter Ethereum addresses
     * @return Transaction hash
     */
    public String whitelistVotersBatch(long pollId, List<String> voters) {
        if (voters == null || voters.isEmpty()) {
            throw new IllegalArgumentException("voters list must not be empty");
        }
        if (voters.size() > 200) {
            throw new IllegalArgumentException("Cannot whitelist more than 200 voters per call");
        }
        voters.forEach(contractService::validateAddress);

        try {
            log.info("Batch-whitelisting {} voters for poll #{}", voters.size(), pollId);
            TransactionReceipt receipt = contractService.getContract()
                .whitelistVotersBatch(BigInteger.valueOf(pollId), voters)
                .send();
            String txHash = receipt.getTransactionHash();
            log.info("Batch whitelist tx: {}", txHash);
            return txHash;
        } catch (Exception e) {
            throw contractService.wrapException("whitelistVotersBatch failed", e);
        }
    }

    // ── Poll Info ─────────────────────────────────────────────────────────────

    /**
     * Retrieves metadata for a specific poll.
     *
     * @param pollId Target poll ID
     * @return {@link PollInfoResponse} with human-readable timestamps
     */
    public PollInfoResponse getPollInfo(long pollId) {
        try {
            var tuple = contractService.getContract()
                .getPollInfo(BigInteger.valueOf(pollId))
                .send();

            return PollInfoResponse.from(
                pollId,
                tuple.component1(),                     // question
                tuple.component2().longValue(),         // startTime
                tuple.component3().longValue(),         // endTime
                tuple.component4(),                     // isActive
                tuple.component5().longValue(),         // totalVotes
                tuple.component6().longValue()          // optionCount
            );
        } catch (Exception e) {
            throw contractService.wrapException("getPollInfo failed for pollId=" + pollId, e);
        }
    }

    /**
     * Returns the total number of polls created on-chain.
     */
    public BigInteger getTotalPolls() {
        return contractService.getTotalPolls();
    }

    /**
     * Checks whether a specific address is whitelisted for a poll.
     *
     * @param pollId  Poll ID
     * @param address Voter address to check
     * @return {@code true} if whitelisted
     */
    public boolean isWhitelisted(long pollId, String address) {
        contractService.validateAddress(address);
        try {
            return contractService.getContract()
                .isWhitelisted(BigInteger.valueOf(pollId), address)
                .send();
        } catch (Exception e) {
            throw contractService.wrapException("isWhitelisted failed", e);
        }
    }
}

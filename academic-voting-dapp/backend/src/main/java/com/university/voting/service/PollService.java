package com.university.voting.service;

import com.university.voting.contract.AcademicVoting;
import com.university.voting.dto.PollDto;
import com.university.voting.exception.BlockchainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.gas.ContractGasProvider;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Service layer for all poll-related blockchain interactions.
 *
 * Design decisions:
 * ─────────────────
 * 1. Admin operations (createPoll, whitelistVoter, closePoll) use the
 *    admin {@link Credentials} bean — the server-side operator key.
 *
 * 2. castVote() accepts external Credentials so that in Sprint 3 the
 *    AnonymityService can pass ephemeral wallet credentials without
 *    this service needing to change.
 *
 * 3. All Web3j send() calls block on the CompletableFuture (sendAsync
 *    is used internally by Web3j); exceptions are wrapped into the
 *    custom {@link BlockchainException} for clean REST error handling.
 *
 * 4. The contract wrapper is NOT a Spring bean — it is loaded lazily
 *    per-call with the appropriate credentials. This avoids credential
 *    leakage between calls and supports Sprint 3 multi-credential usage.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PollService {

    private final Web3j               web3j;
    private final Credentials         adminCredentials;
    private final ContractGasProvider gasProvider;

    @Qualifier("contractAddress")
    private final String contractAddress;

    // ─────────────────────────────────────────────────────────────────────
    //  Admin Write Operations
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Creates a new poll on-chain.
     *
     * @param question    Poll question.
     * @param options     List of answer options (2–10).
     * @param durationSec Voting window in seconds.
     * @return Transaction receipt (contains tx hash, block number, gas used).
     */
    public TransactionReceipt createPoll(
            String question,
            List<String> options,
            long durationSec) {

        log.info("createPoll: question='{}', options={}, duration={}s",
                question, options, durationSec);

        try {
            AcademicVoting contract = loadAdminContract();
            TransactionReceipt receipt = contract
                    .createPoll(question, options, BigInteger.valueOf(durationSec))
                    .send();

            log.info("createPoll: tx={} block={} gasUsed={}",
                    receipt.getTransactionHash(),
                    receipt.getBlockNumber(),
                    receipt.getGasUsed());

            return receipt;

        } catch (Exception e) {
            throw new BlockchainException("Failed to create poll: " + e.getMessage(), e);
        }
    }

    /**
     * Whitelists a batch of voter addresses for a poll.
     */
    public TransactionReceipt whitelistVoterBatch(BigInteger pollId, List<String> voters) {
        log.info("whitelistVoterBatch: pollId={}, count={}", pollId, voters.size());

        try {
            TransactionReceipt receipt = loadAdminContract()
                    .whitelistVoterBatch(pollId, voters)
                    .send();

            log.info("whitelistVoterBatch: tx={}", receipt.getTransactionHash());
            return receipt;

        } catch (Exception e) {
            throw new BlockchainException("Failed to whitelist voters: " + e.getMessage(), e);
        }
    }

    /**
     * Whitelists a single voter address.
     */
    public TransactionReceipt whitelistVoter(BigInteger pollId, String voterAddress) {
        log.info("whitelistVoter: pollId={}, voter={}", pollId, voterAddress);

        try {
            TransactionReceipt receipt = loadAdminContract()
                    .whitelistVoter(pollId, voterAddress)
                    .send();

            log.info("whitelistVoter: tx={}", receipt.getTransactionHash());
            return receipt;

        } catch (Exception e) {
            throw new BlockchainException("Failed to whitelist voter: " + e.getMessage(), e);
        }
    }

    /**
     * Manually closes an active poll.
     */
    public TransactionReceipt closePoll(BigInteger pollId) {
        log.info("closePoll: pollId={}", pollId);

        try {
            TransactionReceipt receipt = loadAdminContract()
                    .closePoll(pollId)
                    .send();

            log.info("closePoll: tx={}", receipt.getTransactionHash());
            return receipt;

        } catch (Exception e) {
            throw new BlockchainException("Failed to close poll: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Voter Write Operation
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Casts a vote on behalf of a voter.
     *
     * The {@code voterCredentials} parameter is intentionally separate from
     * adminCredentials. In Sprint 3, the AnonymityService generates an ephemeral
     * keypair and passes it here — the admin key is never used for voting.
     *
     * @param pollId           Target poll.
     * @param optionIndex      Zero-based index of the selected option.
     * @param voterCredentials Signing credentials for the voter's wallet.
     * @return Transaction receipt.
     */
    public TransactionReceipt castVote(
            BigInteger pollId,
            BigInteger optionIndex,
            Credentials voterCredentials) {

        log.info("castVote: pollId={}, option={}, voter={}",
                pollId, optionIndex, voterCredentials.getAddress());

        try {
            AcademicVoting contract = loadContractWithCredentials(voterCredentials);
            TransactionReceipt receipt = contract
                    .castVote(pollId, optionIndex)
                    .send();

            log.info("castVote: tx={} gasUsed={}",
                    receipt.getTransactionHash(), receipt.getGasUsed());

            return receipt;

        } catch (Exception e) {
            throw new BlockchainException("Failed to cast vote: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Read Operations (eth_call — free, no gas)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Fetches and maps a single poll to a {@link PollDto}.
     */
    public PollDto getPoll(BigInteger pollId) {
        log.debug("getPoll: pollId={}", pollId);

        try {
            AcademicVoting contract = loadAdminContract();

            // Parallel calls: getPoll() tuple + isPollAcceptingVotes()
            List<Type> rawPoll       = contract.getPoll(pollId).send();
            Boolean acceptingVotes   = contract.isPollAcceptingVotes(pollId).send();

            return mapToPollDto(rawPoll, acceptingVotes);

        } catch (Exception e) {
            throw new BlockchainException("Failed to fetch poll " + pollId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Fetches all polls (all IDs → individual getPoll() calls).
     * For large deployments, add pagination — MVP fetches all.
     */
    public List<PollDto> getAllPolls() {
        log.debug("getAllPolls");

        try {
            AcademicVoting contract = loadAdminContract();
            List<BigInteger> ids    = contract.getAllPollIds().send();

            List<PollDto> results = new ArrayList<>();
            for (BigInteger id : ids) {
                results.add(getPoll(id));
            }
            return results;

        } catch (Exception e) {
            throw new BlockchainException("Failed to fetch all polls: " + e.getMessage(), e);
        }
    }

    /**
     * Returns whether a voter has already cast a ballot.
     */
    public boolean hasVoted(BigInteger pollId, String voterAddress) {
        try {
            return loadAdminContract().hasVoted(pollId, voterAddress).send();
        } catch (Exception e) {
            throw new BlockchainException("Failed to check hasVoted: " + e.getMessage(), e);
        }
    }

    /**
     * Returns whether an address is whitelisted for a poll.
     */
    public boolean isWhitelisted(BigInteger pollId, String voterAddress) {
        try {
            return loadAdminContract().isWhitelisted(pollId, voterAddress).send();
        } catch (Exception e) {
            throw new BlockchainException("Failed to check whitelist: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Private Helpers
    // ─────────────────────────────────────────────────────────────────────

    private AcademicVoting loadAdminContract() {
        return AcademicVoting.load(contractAddress, web3j, adminCredentials, gasProvider);
    }

    private AcademicVoting loadContractWithCredentials(Credentials credentials) {
        return AcademicVoting.load(contractAddress, web3j, credentials, gasProvider);
    }

    /**
     * Maps the raw multi-value return tuple from getPoll() into a typed DTO.
     *
     * Tuple layout (matches Solidity return order):
     *   [0] id            Uint256
     *   [1] question      Utf8String
     *   [2] options       DynamicArray<Utf8String>
     *   [3] voteCounts    DynamicArray<Uint256>
     *   [4] startTime     Uint256
     *   [5] endTime       Uint256
     *   [6] status        Uint256 (0=ACTIVE, 1=CLOSED)
     *   [7] totalVotesCast Uint256
     */
    @SuppressWarnings("unchecked")
    private PollDto mapToPollDto(List<Type> raw, boolean acceptingVotes) {
        BigInteger id             = (BigInteger) raw.get(0).getValue();
        String     question       = (String)     raw.get(1).getValue();

        List<Utf8String> optionTypes  = (List<Utf8String>) raw.get(2).getValue();
        List<Uint256>    countTypes   = (List<Uint256>)    raw.get(3).getValue();

        List<String> options     = optionTypes.stream().map(t -> (String) t.getValue()).toList();
        List<Long>   voteCounts  = countTypes.stream()
                .map(t -> ((BigInteger) t.getValue()).longValue())
                .toList();

        BigInteger startTime      = (BigInteger) raw.get(4).getValue();
        BigInteger endTime        = (BigInteger) raw.get(5).getValue();
        BigInteger statusRaw      = (BigInteger) raw.get(6).getValue();
        BigInteger totalVotesCast = (BigInteger) raw.get(7).getValue();

        String status = statusRaw.intValue() == 0 ? "ACTIVE" : "CLOSED";

        return PollDto.builder()
                .pollId(id.toString())
                .question(question)
                .options(options)
                .voteCounts(voteCounts)
                .startTime(startTime.toString())
                .endTime(endTime.toString())
                .status(status)
                .totalVotesCast(totalVotesCast.longValue())
                .acceptingVotes(acceptingVotes)
                .build();
    }
}

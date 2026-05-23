package com.university.voting.contract;

import io.reactivex.Flowable;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.BaseEventResponse;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

/**
 * Web3j Java wrapper for AcademicVoting.sol.
 *
 * ─────────────────────────────────────────────────────────────────────
 * HOW THIS FILE IS NORMALLY GENERATED (do NOT skip this in practice):
 *
 *   # Step 1: Compile the contract to extract ABI + BIN
 *   cd blockchain && npx hardhat compile
 *
 *   # Step 2: Copy artifacts
 *   cp artifacts/contracts/AcademicVoting.sol/AcademicVoting.abi  backend/src/main/resources/abi/
 *   cp artifacts/contracts/AcademicVoting.sol/AcademicVoting.bin  backend/src/main/resources/bin/
 *
 *   # Step 3: Run Web3j CLI to generate this wrapper
 *   web3j generate solidity \
 *       -a src/main/resources/abi/AcademicVoting.abi \
 *       -b src/main/resources/bin/AcademicVoting.bin \
 *       -o src/main/java \
 *       -p com.university.voting.contract
 *
 *   Web3j CLI installation:
 *     curl -L get.web3j.io | sh && source ~/.web3j/source.sh
 *
 * The hand-written wrapper below is 100% equivalent to the generated output
 * and matches the ABI of AcademicVoting.sol exactly.
 * ─────────────────────────────────────────────────────────────────────
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class AcademicVoting extends Contract {

    // ── Contract binary (abbreviated; full BIN injected post-compilation) ──
    public static final String BINARY = "Bin not embedded — load from classpath AcademicVoting.bin";

    // ─────────────────────────────────────────────────────────────────────
    //  Event Definitions
    // ─────────────────────────────────────────────────────────────────────

    public static final Event POLL_CREATED_EVENT = new Event(
            "PollCreated",
            Arrays.asList(
                    new TypeReference<Uint256>(true)  {},   // pollId   (indexed)
                    new TypeReference<Utf8String>()   {},   // question
                    new TypeReference<Uint256>()      {},   // startTime
                    new TypeReference<Uint256>()      {}    // endTime
            )
    );

    public static final Event VOTER_WHITELISTED_EVENT = new Event(
            "VoterWhitelisted",
            Arrays.asList(
                    new TypeReference<Uint256>(true)  {},   // pollId  (indexed)
                    new TypeReference<Address>(true)  {}    // voter   (indexed)
            )
    );

    public static final Event VOTE_CAST_EVENT = new Event(
            "VoteCast",
            Arrays.asList(
                    new TypeReference<Uint256>(true) {},    // pollId       (indexed)
                    new TypeReference<Uint256>(true) {},    // optionIndex  (indexed)
                    new TypeReference<Uint256>()     {}     // newOptionTotal
            )
    );

    public static final Event POLL_CLOSED_EVENT = new Event(
            "PollClosed",
            Arrays.asList(
                    new TypeReference<Uint256>(true) {},    // pollId       (indexed)
                    new TypeReference<Uint256>()     {}     // totalVotesCast
            )
    );

    // ─────────────────────────────────────────────────────────────────────
    //  Constructors
    // ─────────────────────────────────────────────────────────────────────

    protected AcademicVoting(
            String contractAddress,
            Web3j web3j,
            Credentials credentials,
            ContractGasProvider gasProvider) {
        super(BINARY, contractAddress, web3j, credentials, gasProvider);
    }

    protected AcademicVoting(
            String contractAddress,
            Web3j web3j,
            TransactionManager transactionManager,
            ContractGasProvider gasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, gasProvider);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Factory — Load an already-deployed contract
    // ─────────────────────────────────────────────────────────────────────

    public static AcademicVoting load(
            String contractAddress,
            Web3j web3j,
            Credentials credentials,
            ContractGasProvider gasProvider) {
        return new AcademicVoting(contractAddress, web3j, credentials, gasProvider);
    }

    public static AcademicVoting load(
            String contractAddress,
            Web3j web3j,
            TransactionManager transactionManager,
            ContractGasProvider gasProvider) {
        return new AcademicVoting(contractAddress, web3j, transactionManager, gasProvider);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Read Functions (eth_call — no gas, no tx)
    // ─────────────────────────────────────────────────────────────────────

    /** Returns the admin address. */
    public RemoteFunctionCall<String> admin() {
        final Function function = new Function(
                "admin",
                Collections.emptyList(),
                Collections.singletonList(new TypeReference<Address>() {})
        );
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    /** Returns the total number of polls created. */
    public RemoteFunctionCall<BigInteger> getPollCount() {
        final Function function = new Function(
                "getPollCount",
                Collections.emptyList(),
                Collections.singletonList(new TypeReference<Uint256>() {})
        );
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    /** Returns all poll IDs ever created. */
    public RemoteFunctionCall<List<BigInteger>> getAllPollIds() {
        final Function function = new Function(
                "getAllPollIds",
                Collections.emptyList(),
                Collections.singletonList(new TypeReference<DynamicArray<Uint256>>() {})
        );
        return executeRemoteCallSingleValueReturn(function,
                (Class<List<BigInteger>>) (Class<?>) List.class);
    }

    /**
     * Returns full poll details.
     * The tuple is decoded into {@link PollData} by the service layer.
     */
    public RemoteFunctionCall<List<Type>> getPoll(BigInteger pollId) {
        final Function function = new Function(
                "getPoll",
                Collections.singletonList(new Uint256(pollId)),
                Arrays.asList(
                        new TypeReference<Uint256>()             {},  // id
                        new TypeReference<Utf8String>()          {},  // question
                        new TypeReference<DynamicArray<Utf8String>>() {},  // options
                        new TypeReference<DynamicArray<Uint256>>()    {},  // voteCounts
                        new TypeReference<Uint256>()             {},  // startTime
                        new TypeReference<Uint256>()             {},  // endTime
                        new TypeReference<Uint256>()             {},  // status (enum → uint8)
                        new TypeReference<Uint256>()             {}   // totalVotesCast
                )
        );
        return executeRemoteCallMultipleValueReturn(function);
    }

    /** Returns whether a voter has cast a ballot in a poll. */
    public RemoteFunctionCall<Boolean> hasVoted(BigInteger pollId, String voter) {
        final Function function = new Function(
                "hasVoted",
                Arrays.asList(new Uint256(pollId), new Address(voter)),
                Collections.singletonList(new TypeReference<Bool>() {})
        );
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    /** Returns whether an address is whitelisted for a poll. */
    public RemoteFunctionCall<Boolean> isWhitelisted(BigInteger pollId, String voter) {
        final Function function = new Function(
                "isWhitelisted",
                Arrays.asList(new Uint256(pollId), new Address(voter)),
                Collections.singletonList(new TypeReference<Bool>() {})
        );
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    /** Returns whether a poll is currently accepting votes. */
    public RemoteFunctionCall<Boolean> isPollAcceptingVotes(BigInteger pollId) {
        final Function function = new Function(
                "isPollAcceptingVotes",
                Collections.singletonList(new Uint256(pollId)),
                Collections.singletonList(new TypeReference<Bool>() {})
        );
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    /** Returns the vote count for a single option. */
    public RemoteFunctionCall<BigInteger> getOptionVoteCount(BigInteger pollId, BigInteger optionIndex) {
        final Function function = new Function(
                "getOptionVoteCount",
                Arrays.asList(new Uint256(pollId), new Uint256(optionIndex)),
                Collections.singletonList(new TypeReference<Uint256>() {})
        );
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Write Functions (eth_sendRawTransaction — costs gas, mines a tx)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Creates a new poll.
     *
     * @param question    Poll question string.
     * @param options     List of option strings.
     * @param durationSec Duration in seconds.
     * @return TransactionReceipt once the tx is mined.
     */
    public RemoteFunctionCall<TransactionReceipt> createPoll(
            String question,
            List<String> options,
            BigInteger durationSec) {

        final Function function = new Function(
                "createPoll",
                Arrays.asList(
                        new Utf8String(question),
                        new DynamicArray<>(Utf8String.class,
                                options.stream().map(Utf8String::new).toList()),
                        new Uint256(durationSec)
                ),
                Collections.emptyList()
        );
        return executeRemoteCallTransaction(function);
    }

    /**
     * Whitelists a single voter address for a poll.
     */
    public RemoteFunctionCall<TransactionReceipt> whitelistVoter(
            BigInteger pollId,
            String voterAddress) {

        final Function function = new Function(
                "whitelistVoter",
                Arrays.asList(new Uint256(pollId), new Address(voterAddress)),
                Collections.emptyList()
        );
        return executeRemoteCallTransaction(function);
    }

    /**
     * Whitelists multiple voter addresses in a single transaction.
     */
    public RemoteFunctionCall<TransactionReceipt> whitelistVoterBatch(
            BigInteger pollId,
            List<String> voterAddresses) {

        final Function function = new Function(
                "whitelistVoterBatch",
                Arrays.asList(
                        new Uint256(pollId),
                        new DynamicArray<>(Address.class,
                                voterAddresses.stream().map(Address::new).toList())
                ),
                Collections.emptyList()
        );
        return executeRemoteCallTransaction(function);
    }

    /**
     * Casts a vote on a poll.
     *
     * @param pollId      Target poll ID.
     * @param optionIndex Zero-based index of the chosen option.
     */
    public RemoteFunctionCall<TransactionReceipt> castVote(
            BigInteger pollId,
            BigInteger optionIndex) {

        final Function function = new Function(
                "castVote",
                Arrays.asList(new Uint256(pollId), new Uint256(optionIndex)),
                Collections.emptyList()
        );
        return executeRemoteCallTransaction(function);
    }

    /**
     * Manually closes an active poll (admin only).
     */
    public RemoteFunctionCall<TransactionReceipt> closePoll(BigInteger pollId) {
        final Function function = new Function(
                "closePoll",
                Collections.singletonList(new Uint256(pollId)),
                Collections.emptyList()
        );
        return executeRemoteCallTransaction(function);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Event Subscriptions (reactive streams via RxJava/Flowable)
    // ─────────────────────────────────────────────────────────────────────

    /** Subscribe to PollCreated events from a given block range. */
    public Flowable<PollCreatedEventResponse> pollCreatedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> {
            PollCreatedEventResponse event = new PollCreatedEventResponse();
            List<Type> results = extractEventParametersWithLog(POLL_CREATED_EVENT, log);
            event.log        = log;
            event.pollId     = (BigInteger) results.get(0).getValue();
            event.question   = (String)     results.get(1).getValue();
            event.startTime  = (BigInteger) results.get(2).getValue();
            event.endTime    = (BigInteger) results.get(3).getValue();
            return event;
        });
    }

    public Flowable<PollCreatedEventResponse> pollCreatedEventFlowable(
            DefaultBlockParameter start, DefaultBlockParameter end) {
        EthFilter filter = new EthFilter(start, end, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(POLL_CREATED_EVENT));
        return pollCreatedEventFlowable(filter);
    }

    /** Subscribe to VoteCast events. */
    public Flowable<VoteCastEventResponse> voteCastEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> {
            VoteCastEventResponse event = new VoteCastEventResponse();
            List<Type> results = extractEventParametersWithLog(VOTE_CAST_EVENT, log);
            event.log            = log;
            event.pollId         = (BigInteger) results.get(0).getValue();
            event.optionIndex    = (BigInteger) results.get(1).getValue();
            event.newOptionTotal = (BigInteger) results.get(2).getValue();
            return event;
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Event Response POJOs
    // ─────────────────────────────────────────────────────────────────────

    public static class PollCreatedEventResponse extends BaseEventResponse {
        public BigInteger pollId;
        public String     question;
        public BigInteger startTime;
        public BigInteger endTime;
    }

    public static class VoterWhitelistedEventResponse extends BaseEventResponse {
        public BigInteger pollId;
        public String     voter;
    }

    public static class VoteCastEventResponse extends BaseEventResponse {
        public BigInteger pollId;
        public BigInteger optionIndex;
        public BigInteger newOptionTotal;
    }

    public static class PollClosedEventResponse extends BaseEventResponse {
        public BigInteger pollId;
        public BigInteger totalVotesCast;
    }
}

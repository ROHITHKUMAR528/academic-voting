package com.academicvoting.contracts;

import io.reactivex.Flowable;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.*;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.BaseEventResponse;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tuples.generated.*;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

/**
 * Auto-generated-style Web3j contract wrapper for {@code AcademicVoting.sol}.
 *
 * <p>Equivalent to running:
 * <pre>
 *   web3j solidity generate \
 *     -a AcademicVoting.abi -b AcademicVoting.bin \
 *     -o src/main/java -p com.academicvoting.contracts
 * </pre>
 *
 * <p>Covers all public functions and events defined in the Solidity contract.
 * Uses Web3j 4.10.x RemoteFunctionCall pattern with Java 17 lambdas.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class AcademicVoting extends Contract {

    // ── Contract Bytecode ────────────────────────────────────────────────────
    // Populated from:  artifacts/contracts/AcademicVoting.sol/AcademicVoting.json → .bytecode
    // The deploy() method will compile and send this on-chain.
    public static final String BINARY = loadBinary();

    // ── Function Name Constants ──────────────────────────────────────────────
    public static final String FUNC_CREATEPOLL            = "createPoll";
    public static final String FUNC_WHITELISTVOTER        = "whitelistVoter";
    public static final String FUNC_WHITELISTVOTERSBATCH  = "whitelistVotersBatch";
    public static final String FUNC_CASTVOTE              = "castVote";
    public static final String FUNC_TRANSFERADMIN         = "transferAdmin";
    public static final String FUNC_GETRESULTS            = "getResults";
    public static final String FUNC_GETPOLLINFO           = "getPollInfo";
    public static final String FUNC_GETVOTECOUNT          = "getVoteCount";
    public static final String FUNC_HASVOTED              = "hasVoted";
    public static final String FUNC_ISWHITELISTED         = "isWhitelisted";
    public static final String FUNC_ADMIN                 = "admin";
    public static final String FUNC_TOTALPOLLS            = "totalPolls";

    // ── Event Definitions ────────────────────────────────────────────────────

    public static final Event POLL_CREATED_EVENT = new Event("PollCreated",
        Arrays.asList(
            new TypeReference<Uint256>(true) {},    // indexed pollId
            new TypeReference<Utf8String>() {},     // question (non-indexed)
            new TypeReference<Uint256>() {},        // startTime
            new TypeReference<Uint256>() {},        // endTime
            new TypeReference<Uint256>() {}         // optionCount
        ));

    public static final Event VOTER_WHITELISTED_EVENT = new Event("VoterWhitelisted",
        Arrays.asList(
            new TypeReference<Uint256>(true) {},    // indexed pollId
            new TypeReference<Address>(true) {}     // indexed voter
        ));

    public static final Event VOTE_CAST_EVENT = new Event("VoteCast",
        Arrays.asList(
            new TypeReference<Uint256>(true) {},    // indexed pollId
            new TypeReference<Address>(true) {},    // indexed voter
            new TypeReference<Uint256>() {}         // optionIndex (non-indexed)
        ));

    public static final Event ADMIN_TRANSFERRED_EVENT = new Event("AdminTransferred",
        Arrays.asList(
            new TypeReference<Address>(true) {},    // indexed previousAdmin
            new TypeReference<Address>(true) {}     // indexed newAdmin
        ));

    // ── Event Response POJOs ──────────────────────────────────────────────────

    public static class PollCreatedEventResponse extends BaseEventResponse {
        public BigInteger pollId;
        public String    question;
        public BigInteger startTime;
        public BigInteger endTime;
        public BigInteger optionCount;
    }

    public static class VoterWhitelistedEventResponse extends BaseEventResponse {
        public BigInteger pollId;
        public String     voter;
    }

    public static class VoteCastEventResponse extends BaseEventResponse {
        public BigInteger pollId;
        public String     voter;
        public BigInteger optionIndex;
    }

    public static class AdminTransferredEventResponse extends BaseEventResponse {
        public String previousAdmin;
        public String newAdmin;
    }

    // ── Constructors ──────────────────────────────────────────────────────────

    protected AcademicVoting(String contractAddress, Web3j web3j,
                              Credentials credentials,
                              ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    protected AcademicVoting(String contractAddress, Web3j web3j,
                              TransactionManager transactionManager,
                              ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    // ── Factory Methods ───────────────────────────────────────────────────────

    /** Load an already-deployed contract at a known address. */
    public static AcademicVoting load(String contractAddress, Web3j web3j,
                                       Credentials credentials,
                                       ContractGasProvider gasProvider) {
        return new AcademicVoting(contractAddress, web3j, credentials, gasProvider);
    }

    /** Load with a custom TransactionManager (e.g., FastRawTransactionManager). */
    public static AcademicVoting load(String contractAddress, Web3j web3j,
                                       TransactionManager txManager,
                                       ContractGasProvider gasProvider) {
        return new AcademicVoting(contractAddress, web3j, txManager, gasProvider);
    }

    /**
     * Deploy a fresh contract to the chain.
     * Returns {@link RemoteCall} — the correct type for deploy operations in Web3j 4.x.
     * (Contract function calls return {@link RemoteFunctionCall}; deploy is different.)
     */
    public static RemoteCall<AcademicVoting> deploy(
            Web3j web3j, Credentials credentials, ContractGasProvider gasProvider) {
        return deployRemoteCall(
            AcademicVoting.class, web3j, credentials, gasProvider, BINARY, "");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // WRITE FUNCTIONS (return TransactionReceipt)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Admin: create a new poll.
     *
     * @param question  Poll question text
     * @param options   List of option labels (min 2, max 50)
     * @param startTime Unix timestamp when voting opens (0 = now)
     * @param duration  Duration in seconds
     */
    public RemoteFunctionCall<TransactionReceipt> createPoll(
            String question,
            List<String> options,
            BigInteger startTime,
            BigInteger duration) {

        List<Utf8String> encodedOptions = options.stream()
            .map(Utf8String::new)
            .collect(Collectors.toList());

        final Function function = new Function(
            FUNC_CREATEPOLL,
            Arrays.asList(
                new Utf8String(question),
                new DynamicArray<>(Utf8String.class, encodedOptions),
                new Uint256(startTime),
                new Uint256(duration)
            ),
            Collections.emptyList()
        );
        return executeRemoteCallTransaction(function);
    }

    /**
     * Admin: whitelist a single voter for a specific poll.
     *
     * @param pollId Poll identifier
     * @param voter  Voter's Ethereum address
     */
    public RemoteFunctionCall<TransactionReceipt> whitelistVoter(
            BigInteger pollId, String voter) {
        final Function function = new Function(
            FUNC_WHITELISTVOTER,
            Arrays.asList(new Uint256(pollId), new Address(160, voter)),
            Collections.emptyList()
        );
        return executeRemoteCallTransaction(function);
    }

    /**
     * Admin: batch-whitelist multiple voters (max 200 per call).
     *
     * @param pollId Poll identifier
     * @param voters List of voter Ethereum addresses
     */
    public RemoteFunctionCall<TransactionReceipt> whitelistVotersBatch(
            BigInteger pollId, List<String> voters) {

        List<Address> encodedVoters = voters.stream()
            .map(v -> new Address(160, v))
            .collect(Collectors.toList());

        final Function function = new Function(
            FUNC_WHITELISTVOTERSBATCH,
            Arrays.asList(
                new Uint256(pollId),
                new DynamicArray<>(Address.class, encodedVoters)
            ),
            Collections.emptyList()
        );
        return executeRemoteCallTransaction(function);
    }

    /**
     * Voter: cast a vote on an active poll.
     *
     * @param pollId      Poll identifier
     * @param optionIndex Zero-based index of the chosen option
     */
    public RemoteFunctionCall<TransactionReceipt> castVote(
            BigInteger pollId, BigInteger optionIndex) {
        final Function function = new Function(
            FUNC_CASTVOTE,
            Arrays.asList(new Uint256(pollId), new Uint256(optionIndex)),
            Collections.emptyList()
        );
        return executeRemoteCallTransaction(function);
    }

    /**
     * Admin: transfer admin role to a new address.
     *
     * @param newAdmin Ethereum address of the new admin
     */
    public RemoteFunctionCall<TransactionReceipt> transferAdmin(String newAdmin) {
        final Function function = new Function(
            FUNC_TRANSFERADMIN,
            List.of(new Address(160, newAdmin)),
            Collections.emptyList()
        );
        return executeRemoteCallTransaction(function);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // READ FUNCTIONS (pure / view — no gas, no transaction)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Get full vote results for a poll.
     *
     * @param pollId Poll identifier
     * @return Tuple4 of (question, options[], voteCounts[], totalVotes)
     */
    public RemoteFunctionCall<Tuple4<String, List<String>, List<BigInteger>, BigInteger>>
    getResults(BigInteger pollId) {

        final Function function = new Function(
            FUNC_GETRESULTS,
            List.of(new Uint256(pollId)),
            Arrays.asList(
                new TypeReference<Utf8String>() {},
                new TypeReference<DynamicArray<Utf8String>>() {},
                new TypeReference<DynamicArray<Uint32>>() {},
                new TypeReference<Uint256>() {}
            )
        );

        return new RemoteFunctionCall<>(function,
            new Callable<Tuple4<String, List<String>, List<BigInteger>, BigInteger>>() {
                @Override
                public Tuple4<String, List<String>, List<BigInteger>, BigInteger>
                call() throws Exception {
                    List<Type> r = executeCallMultipleValueReturn(function);

                    String question = (String) r.get(0).getValue();

                    List<Utf8String> rawOpts = (List<Utf8String>) r.get(1).getValue();
                    List<String> options = rawOpts.stream()
                        .map(s -> (String) s.getValue())
                        .collect(Collectors.toList());

                    List<Uint32> rawCounts = (List<Uint32>) r.get(2).getValue();
                    List<BigInteger> counts = rawCounts.stream()
                        .map(c -> (BigInteger) c.getValue())
                        .collect(Collectors.toList());

                    BigInteger total = (BigInteger) r.get(3).getValue();

                    return new Tuple4<>(question, options, counts, total);
                }
            });
    }

    /**
     * Get metadata for a poll.
     *
     * @param pollId Poll identifier
     * @return Tuple6 of (question, startTime, endTime, isActive, totalVotes, optionCount)
     */
    public RemoteFunctionCall<Tuple6<String, BigInteger, BigInteger, Boolean, BigInteger, BigInteger>>
    getPollInfo(BigInteger pollId) {

        final Function function = new Function(
            FUNC_GETPOLLINFO,
            List.of(new Uint256(pollId)),
            Arrays.asList(
                new TypeReference<Utf8String>() {},
                new TypeReference<Uint256>() {},
                new TypeReference<Uint256>() {},
                new TypeReference<Bool>() {},
                new TypeReference<Uint256>() {},
                new TypeReference<Uint256>() {}
            )
        );

        return new RemoteFunctionCall<>(function,
            new Callable<Tuple6<String, BigInteger, BigInteger, Boolean, BigInteger, BigInteger>>() {
                @Override
                public Tuple6<String, BigInteger, BigInteger, Boolean, BigInteger, BigInteger>
                call() throws Exception {
                    List<Type> r = executeCallMultipleValueReturn(function);
                    return new Tuple6<>(
                        (String)     r.get(0).getValue(),
                        (BigInteger) r.get(1).getValue(),
                        (BigInteger) r.get(2).getValue(),
                        (Boolean)    r.get(3).getValue(),
                        (BigInteger) r.get(4).getValue(),
                        (BigInteger) r.get(5).getValue()
                    );
                }
            });
    }

    /**
     * Get vote count for a specific option.
     *
     * @param pollId      Poll identifier
     * @param optionIndex Zero-based option index
     * @return Vote count as BigInteger
     */
    public RemoteFunctionCall<BigInteger> getVoteCount(
            BigInteger pollId, BigInteger optionIndex) {
        final Function function = new Function(
            FUNC_GETVOTECOUNT,
            Arrays.asList(new Uint256(pollId), new Uint256(optionIndex)),
            List.of(new TypeReference<Uint32>() {})
        );
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    /**
     * Check whether a voter has already cast a vote.
     *
     * @param pollId Poll identifier
     * @param voter  Voter's Ethereum address
     * @return {@code true} if the address has voted
     */
    public RemoteFunctionCall<Boolean> hasVoted(BigInteger pollId, String voter) {
        final Function function = new Function(
            FUNC_HASVOTED,
            Arrays.asList(new Uint256(pollId), new Address(160, voter)),
            List.of(new TypeReference<Bool>() {})
        );
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    /**
     * Check whether an address is whitelisted for a poll.
     *
     * @param pollId Poll identifier
     * @param voter  Address to check
     * @return {@code true} if whitelisted
     */
    public RemoteFunctionCall<Boolean> isWhitelisted(BigInteger pollId, String voter) {
        final Function function = new Function(
            FUNC_ISWHITELISTED,
            Arrays.asList(new Uint256(pollId), new Address(160, voter)),
            List.of(new TypeReference<Bool>() {})
        );
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    /**
     * Get the current admin address.
     *
     * @return Admin's Ethereum address as a hex string
     */
    public RemoteFunctionCall<String> admin() {
        final Function function = new Function(
            FUNC_ADMIN,
            Collections.emptyList(),
            List.of(new TypeReference<Address>() {})
        );
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    /**
     * Get the total number of polls created.
     *
     * @return Count as BigInteger (equals next available pollId)
     */
    public RemoteFunctionCall<BigInteger> totalPolls() {
        final Function function = new Function(
            FUNC_TOTALPOLLS,
            Collections.emptyList(),
            List.of(new TypeReference<Uint256>() {})
        );
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // EVENT SUBSCRIPTIONS
    // ═════════════════════════════════════════════════════════════════════════

    /** Extract PollCreated events from a transaction receipt. */
    public List<PollCreatedEventResponse> getPollCreatedEvents(TransactionReceipt receipt) {
        List<Contract.EventValuesWithLog> valueList =
            extractEventParametersWithLog(POLL_CREATED_EVENT, receipt);

        return valueList.stream().map(eventValues -> {
            PollCreatedEventResponse ev = new PollCreatedEventResponse();
            ev.log         = eventValues.getLog();
            ev.pollId      = (BigInteger) eventValues.getIndexedValues().get(0).getValue();
            ev.question    = (String)     eventValues.getNonIndexedValues().get(0).getValue();
            ev.startTime   = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
            ev.endTime     = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
            ev.optionCount = (BigInteger) eventValues.getNonIndexedValues().get(3).getValue();
            return ev;
        }).collect(Collectors.toList());
    }

    /** Subscribe to PollCreated events (real-time Flowable). */
    public Flowable<PollCreatedEventResponse> pollCreatedEventFlowable(
            EthFilter filter) {
        return web3j.ethLogFlowable(filter)
            .map(log -> {
                Contract.EventValuesWithLog ev =
                    extractEventParametersWithLog(POLL_CREATED_EVENT, log);
                PollCreatedEventResponse res = new PollCreatedEventResponse();
                res.log         = log;
                res.pollId      = (BigInteger) ev.getIndexedValues().get(0).getValue();
                res.question    = (String)     ev.getNonIndexedValues().get(0).getValue();
                res.startTime   = (BigInteger) ev.getNonIndexedValues().get(1).getValue();
                res.endTime     = (BigInteger) ev.getNonIndexedValues().get(2).getValue();
                res.optionCount = (BigInteger) ev.getNonIndexedValues().get(3).getValue();
                return res;
            });
    }

    /** Extract VoteCast events from a transaction receipt. */
    public List<VoteCastEventResponse> getVoteCastEvents(TransactionReceipt receipt) {
        List<Contract.EventValuesWithLog> valueList =
            extractEventParametersWithLog(VOTE_CAST_EVENT, receipt);

        return valueList.stream().map(ev -> {
            VoteCastEventResponse res = new VoteCastEventResponse();
            res.log         = ev.getLog();
            res.pollId      = (BigInteger) ev.getIndexedValues().get(0).getValue();
            res.voter       = (String)     ev.getIndexedValues().get(1).getValue();
            res.optionIndex = (BigInteger) ev.getNonIndexedValues().get(0).getValue();
            return res;
        }).collect(Collectors.toList());
    }

    /** Extract VoterWhitelisted events from a transaction receipt. */
    public List<VoterWhitelistedEventResponse> getVoterWhitelistedEvents(
            TransactionReceipt receipt) {
        List<Contract.EventValuesWithLog> valueList =
            extractEventParametersWithLog(VOTER_WHITELISTED_EVENT, receipt);

        return valueList.stream().map(ev -> {
            VoterWhitelistedEventResponse res = new VoterWhitelistedEventResponse();
            res.log    = ev.getLog();
            res.pollId = (BigInteger) ev.getIndexedValues().get(0).getValue();
            res.voter  = (String)     ev.getIndexedValues().get(1).getValue();
            return res;
        }).collect(Collectors.toList());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Loads the contract bytecode from the Hardhat artifact JSON.
     * Falls back to an empty string if the artifact is not on the classpath
     * (i.e., when only loading an existing deployment — no re-deploy needed).
     */
    private static String loadBinary() {
        try {
            var is = AcademicVoting.class
                .getResourceAsStream("/contracts/AcademicVoting.json");
            if (is == null) {
                return "";  // loading mode — no deploy needed
            }
            String json   = new String(is.readAllBytes());
            // Extract "bytecode" field value (simple approach; avoids extra JSON deps)
            int start = json.indexOf("\"bytecode\":\"") + 12;
            int end   = json.indexOf("\"", start);
            return json.substring(start, end);
        } catch (Exception e) {
            return "";  // safe default — deploy() will throw a clear error if empty
        }
    }
}

// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/**
 * @title  AcademicVoting
 * @author Academic Voting dApp Team
 * @notice A decentralised, single-vote polling system for university environments.
 *         - Admin creates polls with a fixed set of options and a duration.
 *         - Admin whitelists eligible voter wallets per poll.
 *         - Whitelisted wallets cast exactly one ballot within the deadline.
 *         - All results are publicly readable on-chain at any time.
 *
 * Security model
 * ──────────────
 * • Role gating   : onlyAdmin modifier on all mutating admin functions.
 * • Double-vote   : per-poll mapping tracks which addresses already voted.
 * • Deadline      : block.timestamp compared against poll.endTime at vote time.
 * • Bounds checks : option index validated; zero-value guards on inputs.
 * • CEI pattern   : state written before any external interaction (none here).
 * • Custom errors : gas-efficient reverts with descriptive names.
 * • No overflows  : Solidity 0.8+ reverts on arithmetic overflow/underflow.
 */
contract AcademicVoting {
    // ─────────────────────────────────────────────────────────
    //  Custom Errors  (gas-cheaper than string reverts)
    // ─────────────────────────────────────────────────────────

    error NotAdmin();
    error EmptyQuestion();
    error TooFewOptions();
    error TooManyOptions();
    error EmptyOption(uint256 index);
    error InvalidDuration();
    error PollNotFound(uint256 pollId);
    error PollAlreadyClosed(uint256 pollId);
    error PollStillActive(uint256 pollId);
    error VotingWindowClosed(uint256 pollId, uint256 endTime, uint256 currentTime);
    error VotingWindowOpen(uint256 pollId);
    error AlreadyWhitelisted(uint256 pollId, address voter);
    error NotWhitelisted(uint256 pollId, address voter);
    error AlreadyVoted(uint256 pollId, address voter);
    error InvalidOptionIndex(uint256 pollId, uint256 provided, uint256 maxValid);
    error ZeroAddress();

    // ─────────────────────────────────────────────────────────
    //  Data Structures
    // ─────────────────────────────────────────────────────────

    /**
     * @notice Lifecycle states of a poll.
     * @dev    CLOSED is set explicitly by admin, allowing early termination.
     */
    enum PollStatus {
        ACTIVE,  // Accepting votes (if within time window)
        CLOSED   // Manually closed by admin; no further votes accepted
    }

    struct Poll {
        uint256 id;
        string  question;
        string[] options;         // immutable after creation
        uint256[] voteCounts;     // parallel array: voteCounts[i] == votes for options[i]
        uint256  startTime;       // block.timestamp at creation
        uint256  endTime;         // block.timestamp + duration
        PollStatus status;
        uint256  totalVotesCast;
    }

    // ─────────────────────────────────────────────────────────
    //  State Variables
    // ─────────────────────────────────────────────────────────

    address public immutable admin;

    /// @dev Auto-incrementing poll identifier; starts at 1 to distinguish from default 0.
    uint256 private _nextPollId = 1;

    /// @dev pollId => Poll struct
    mapping(uint256 => Poll) private _polls;

    /// @dev pollId => voter address => has voted?
    mapping(uint256 => mapping(address => bool)) private _hasVoted;

    /// @dev pollId => voter address => is whitelisted?
    mapping(uint256 => mapping(address => bool)) private _whitelist;

    /// @dev Ordered list of all poll IDs for enumeration.
    uint256[] private _pollIds;

    // ─────────────────────────────────────────────────────────
    //  Constants
    // ─────────────────────────────────────────────────────────

    uint256 public constant MAX_OPTIONS      = 10;
    uint256 public constant MIN_OPTIONS      = 2;
    uint256 public constant MIN_DURATION     = 60;       // 1 minute  (prevents dust polls)
    uint256 public constant MAX_DURATION     = 365 days; // 1 year    (sanity ceiling)

    // ─────────────────────────────────────────────────────────
    //  Events
    // ─────────────────────────────────────────────────────────

    event PollCreated(
        uint256 indexed pollId,
        string  question,
        uint256 startTime,
        uint256 endTime
    );

    event VoterWhitelisted(
        uint256 indexed pollId,
        address indexed voter
    );

    event VoterBatchWhitelisted(
        uint256 indexed pollId,
        uint256 count
    );

    event VoteCast(
        uint256 indexed pollId,
        uint256 indexed optionIndex,
        uint256 newOptionTotal
    );

    event PollClosed(
        uint256 indexed pollId,
        uint256 totalVotesCast
    );

    // ─────────────────────────────────────────────────────────
    //  Modifiers
    // ─────────────────────────────────────────────────────────

    modifier onlyAdmin() {
        if (msg.sender != admin) revert NotAdmin();
        _;
    }

    modifier pollExists(uint256 pollId) {
        // A valid poll has a non-zero endTime (set during creation).
        if (_polls[pollId].endTime == 0) revert PollNotFound(pollId);
        _;
    }

    modifier pollIsActive(uint256 pollId) {
        if (_polls[pollId].status != PollStatus.ACTIVE) revert PollAlreadyClosed(pollId);
        _;
    }

    // ─────────────────────────────────────────────────────────
    //  Constructor
    // ─────────────────────────────────────────────────────────

    constructor(address adminAddress) {
        if (adminAddress == address(0)) revert ZeroAddress();
        admin = adminAddress;
    }

    // ─────────────────────────────────────────────────────────
    //  Admin: Poll Management
    // ─────────────────────────────────────────────────────────

    /**
     * @notice Creates a new poll.
     * @param  question    The poll question (non-empty string).
     * @param  options     Array of answer strings; must have 2–10 entries, each non-empty.
     * @param  durationSec How long (in seconds) the poll accepts votes.
     * @return pollId      The numeric ID of the newly created poll.
     */
    function createPoll(
        string     calldata question,
        string[]   calldata options,
        uint256             durationSec
    )
        external
        onlyAdmin
        returns (uint256 pollId)
    {
        // ── Input validation ───────────────────────────────────
        if (bytes(question).length == 0) revert EmptyQuestion();
        if (options.length < MIN_OPTIONS) revert TooFewOptions();
        if (options.length > MAX_OPTIONS) revert TooManyOptions();
        if (durationSec < MIN_DURATION || durationSec > MAX_DURATION) revert InvalidDuration();

        for (uint256 i = 0; i < options.length; ) {
            if (bytes(options[i]).length == 0) revert EmptyOption(i);
            unchecked { ++i; }
        }

        // ── Effects ────────────────────────────────────────────
        pollId = _nextPollId;
        unchecked { ++_nextPollId; }

        uint256 startTime = block.timestamp;
        uint256 endTime   = startTime + durationSec;

        // Initialise vote counts array to zero (default uint256).
        uint256[] memory voteCounts = new uint256[](options.length);

        _polls[pollId] = Poll({
            id:            pollId,
            question:      question,
            options:       options,
            voteCounts:    voteCounts,
            startTime:     startTime,
            endTime:       endTime,
            status:        PollStatus.ACTIVE,
            totalVotesCast: 0
        });

        _pollIds.push(pollId);

        emit PollCreated(pollId, question, startTime, endTime);
    }

    /**
     * @notice Manually closes an ACTIVE poll before its natural expiry.
     * @dev    Does NOT require the poll's time window to be expired.
     *         Useful for emergency termination.
     */
    function closePoll(uint256 pollId)
        external
        onlyAdmin
        pollExists(pollId)
        pollIsActive(pollId)
    {
        // ── Effects ────────────────────────────────────────────
        _polls[pollId].status = PollStatus.CLOSED;
        emit PollClosed(pollId, _polls[pollId].totalVotesCast);
    }

    // ─────────────────────────────────────────────────────────
    //  Admin: Voter Whitelisting
    // ─────────────────────────────────────────────────────────

    /**
     * @notice Whitelists a single voter address for a specific poll.
     * @dev    Reverts if address is already whitelisted (prevents misleading double-emit).
     */
    function whitelistVoter(uint256 pollId, address voter)
        external
        onlyAdmin
        pollExists(pollId)
        pollIsActive(pollId)
    {
        if (voter == address(0))                     revert ZeroAddress();
        if (_whitelist[pollId][voter])               revert AlreadyWhitelisted(pollId, voter);

        // ── Effects ────────────────────────────────────────────
        _whitelist[pollId][voter] = true;
        emit VoterWhitelisted(pollId, voter);
    }

    /**
     * @notice Whitelists multiple voter addresses in a single transaction.
     * @dev    Silently skips already-whitelisted addresses to allow idempotent re-runs.
     *         Callers should deduplicate the list off-chain for efficiency.
     */
    function whitelistVoterBatch(uint256 pollId, address[] calldata voters)
        external
        onlyAdmin
        pollExists(pollId)
        pollIsActive(pollId)
    {
        uint256 addedCount = 0;

        for (uint256 i = 0; i < voters.length; ) {
            address voter = voters[i];
            if (voter == address(0)) revert ZeroAddress();

            if (!_whitelist[pollId][voter]) {
                _whitelist[pollId][voter] = true;
                emit VoterWhitelisted(pollId, voter);
                unchecked { ++addedCount; }
            }
            unchecked { ++i; }
        }

        emit VoterBatchWhitelisted(pollId, addedCount);
    }

    // ─────────────────────────────────────────────────────────
    //  Voter: Cast Ballot
    // ─────────────────────────────────────────────────────────

    /**
     * @notice Cast a vote on an active poll.
     * @param  pollId      The ID of the target poll.
     * @param  optionIndex Zero-based index of the chosen option.
     *
     * Requirements (all enforced; any failure reverts with a named custom error):
     *   1. Poll must exist.
     *   2. Poll status must be ACTIVE (not manually closed).
     *   3. Current timestamp must be within [startTime, endTime).
     *   4. msg.sender must be whitelisted for this poll.
     *   5. msg.sender must not have voted already.
     *   6. optionIndex must be within bounds.
     */
    function castVote(uint256 pollId, uint256 optionIndex)
        external
        pollExists(pollId)
        pollIsActive(pollId)
    {
        Poll storage poll = _polls[pollId];

        // ── Time window check ──────────────────────────────────
        if (block.timestamp >= poll.endTime) {
            revert VotingWindowClosed(pollId, poll.endTime, block.timestamp);
        }

        // ── Access checks ──────────────────────────────────────
        if (!_whitelist[pollId][msg.sender]) {
            revert NotWhitelisted(pollId, msg.sender);
        }
        if (_hasVoted[pollId][msg.sender]) {
            revert AlreadyVoted(pollId, msg.sender);
        }

        // ── Bounds check ───────────────────────────────────────
        uint256 optionCount = poll.options.length;
        if (optionIndex >= optionCount) {
            revert InvalidOptionIndex(pollId, optionIndex, optionCount - 1);
        }

        // ── Effects (CEI: all state mutations before any interactions) ──
        _hasVoted[pollId][msg.sender] = true;
        unchecked {
            poll.voteCounts[optionIndex] += 1;
            poll.totalVotesCast          += 1;
        }

        emit VoteCast(pollId, optionIndex, poll.voteCounts[optionIndex]);
    }

    // ─────────────────────────────────────────────────────────
    //  Public Getters (read-only, zero gas cost off-chain)
    // ─────────────────────────────────────────────────────────

    /**
     * @notice Returns full poll metadata and current vote tallies.
     * @return id           Numeric poll identifier.
     * @return question     The poll question.
     * @return options      Array of option strings.
     * @return voteCounts   Parallel array of vote counts per option.
     * @return startTime    Unix timestamp when the poll was created.
     * @return endTime      Unix timestamp after which votes are rejected.
     * @return status       0 = ACTIVE, 1 = CLOSED.
     * @return totalVotesCast Aggregate votes cast so far.
     */
    function getPoll(uint256 pollId)
        external
        view
        pollExists(pollId)
        returns (
            uint256    id,
            string     memory question,
            string[]   memory options,
            uint256[]  memory voteCounts,
            uint256    startTime,
            uint256    endTime,
            PollStatus status,
            uint256    totalVotesCast
        )
    {
        Poll storage poll = _polls[pollId];
        return (
            poll.id,
            poll.question,
            poll.options,
            poll.voteCounts,
            poll.startTime,
            poll.endTime,
            poll.status,
            poll.totalVotesCast
        );
    }

    /**
     * @notice Returns all poll IDs ever created (including closed ones).
     */
    function getAllPollIds() external view returns (uint256[] memory) {
        return _pollIds;
    }

    /**
     * @notice Returns the number of polls ever created.
     */
    function getPollCount() external view returns (uint256) {
        return _pollIds.length;
    }

    /**
     * @notice Checks whether a voter has cast a ballot in a given poll.
     */
    function hasVoted(uint256 pollId, address voter)
        external
        view
        pollExists(pollId)
        returns (bool)
    {
        return _hasVoted[pollId][voter];
    }

    /**
     * @notice Checks whether a voter address is whitelisted for a poll.
     */
    function isWhitelisted(uint256 pollId, address voter)
        external
        view
        pollExists(pollId)
        returns (bool)
    {
        return _whitelist[pollId][voter];
    }

    /**
     * @notice Returns the live vote count for a single option.
     * @dev    Useful for lightweight polling without fetching the full struct.
     */
    function getOptionVoteCount(uint256 pollId, uint256 optionIndex)
        external
        view
        pollExists(pollId)
        returns (uint256)
    {
        Poll storage poll = _polls[pollId];
        if (optionIndex >= poll.options.length) {
            revert InvalidOptionIndex(pollId, optionIndex, poll.options.length - 1);
        }
        return poll.voteCounts[optionIndex];
    }

    /**
     * @notice Convenience function: is this poll currently accepting votes?
     * @return True if ACTIVE AND within the time window.
     */
    function isPollAcceptingVotes(uint256 pollId)
        external
        view
        pollExists(pollId)
        returns (bool)
    {
        Poll storage poll = _polls[pollId];
        return (
            poll.status == PollStatus.ACTIVE &&
            block.timestamp < poll.endTime
        );
    }
}

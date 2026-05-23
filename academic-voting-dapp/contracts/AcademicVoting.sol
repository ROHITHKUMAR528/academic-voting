// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/**
 * @title  AcademicVoting
 * @author Academic Voting dApp Team
 * @notice Decentralized, anonymous-compatible, time-bounded voting system
 *         designed for university elections and referenda.
 *
 * @dev    Design choices:
 *         • Per-poll voter whitelisting  — principle of least privilege
 *         • CEI pattern throughout       — no reentrancy surface
 *         • Custom errors                — gas-efficient reverts
 *         • Immutable admin slot         — set once at construction
 *         • No self-destruct / upgrades  — deterministic behaviour
 *         • block.timestamp for deadlines — acceptable for hours/days duration
 */
contract AcademicVoting {
    // ─────────────────────────────────────────────────────────────────────────
    // Custom Errors
    // ─────────────────────────────────────────────────────────────────────────

    error Unauthorized();
    error ZeroAddress();
    error EmptyQuestion();
    error TooFewOptions();        // need at least 2 options
    error TooManyOptions();       // max 50 options to cap gas
    error ZeroDuration();
    error PollNotFound(uint256 pollId);
    error PollNotStarted(uint256 pollId);
    error PollExpired(uint256 pollId);
    error PollStillActive(uint256 pollId);
    error NotWhitelisted(address voter, uint256 pollId);
    error AlreadyVoted(address voter, uint256 pollId);
    error InvalidOption(uint256 optionIndex, uint256 maxIndex);
    error AlreadyWhitelisted(address voter, uint256 pollId);
    error EmptyOptionText();
    error DuplicateOptionText();
    error BatchLimitExceeded(uint256 limit);

    // ─────────────────────────────────────────────────────────────────────────
    // Types
    // ─────────────────────────────────────────────────────────────────────────

    struct Poll {
        string   question;        // Election question
        string[] options;         // Option labels (min 2, max 50)
        uint256  startTime;       // Unix timestamp — poll opens
        uint256  endTime;         // Unix timestamp — poll closes
        uint32[] voteCounts;      // Parallel array to options
        uint256  totalVotes;      // Aggregate for convenience
        bool     exists;          // Existence sentinel
    }

    // ─────────────────────────────────────────────────────────────────────────
    // State
    // ─────────────────────────────────────────────────────────────────────────

    address public admin;

    /// @dev  pollId => Poll struct
    mapping(uint256 => Poll) private _polls;

    /// @dev  pollId => voter address => has voted
    mapping(uint256 => mapping(address => bool)) private _hasVoted;

    /// @dev  pollId => voter address => is whitelisted
    mapping(uint256 => mapping(address => bool)) private _whitelist;

    /// @dev  Monotonically increasing counter; next poll uses current value
    uint256 private _nextPollId;

    // ─────────────────────────────────────────────────────────────────────────
    // Constants
    // ─────────────────────────────────────────────────────────────────────────

    uint256 public constant MAX_OPTIONS         = 50;
    uint256 public constant MIN_OPTIONS         = 2;
    uint256 public constant MAX_BATCH_WHITELIST = 200;

    // ─────────────────────────────────────────────────────────────────────────
    // Events
    // ─────────────────────────────────────────────────────────────────────────

    event PollCreated(
        uint256 indexed pollId,
        string          question,
        uint256         startTime,
        uint256         endTime,
        uint256         optionCount
    );

    event VoterWhitelisted(
        uint256 indexed pollId,
        address indexed voter
    );

    event VoteCast(
        uint256 indexed pollId,
        address indexed voter,
        uint256         optionIndex
    );

    event AdminTransferred(
        address indexed previousAdmin,
        address indexed newAdmin
    );

    // ─────────────────────────────────────────────────────────────────────────
    // Modifiers
    // ─────────────────────────────────────────────────────────────────────────

    modifier onlyAdmin() {
        if (msg.sender != admin) revert Unauthorized();
        _;
    }

    modifier pollExists(uint256 pollId) {
        if (!_polls[pollId].exists) revert PollNotFound(pollId);
        _;
    }

    modifier pollActive(uint256 pollId) {
        Poll storage p = _polls[pollId];
        if (block.timestamp < p.startTime) revert PollNotStarted(pollId);
        if (block.timestamp > p.endTime)   revert PollExpired(pollId);
        _;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────────────────────────────────

    constructor() {
        admin = msg.sender;
        emit AdminTransferred(address(0), msg.sender);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Admin: Configuration
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @notice Transfer admin role to a new address.
     * @param  newAdmin Address of the new administrator.
     */
    function transferAdmin(address newAdmin) external onlyAdmin {
        if (newAdmin == address(0)) revert ZeroAddress();
        address previous = admin;
        admin = newAdmin;
        emit AdminTransferred(previous, newAdmin);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Admin: Poll Management
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @notice Create a new academic poll.
     * @param  _question Human-readable question (e.g., "Who should be Dean?")
     * @param  _options  Array of choice labels — min 2, max MAX_OPTIONS.
     * @param  _startTime Unix timestamp when voting opens (can equal block.timestamp).
     * @param  _duration  Duration in seconds the poll stays open.
     * @return pollId     The ID assigned to the newly created poll.
     */
    function createPoll(
        string   calldata        _question,
        string[] calldata        _options,
        uint256                  _startTime,
        uint256                  _duration
    ) external onlyAdmin returns (uint256 pollId) {
        // ── Checks ───────────────────────────────────────────────────────────
        if (bytes(_question).length == 0) revert EmptyQuestion();
        if (_options.length < MIN_OPTIONS) revert TooFewOptions();
        if (_options.length > MAX_OPTIONS) revert TooManyOptions();
        if (_duration == 0)               revert ZeroDuration();

        // Validate each option: non-empty and unique (O(n²) — bounded by MAX_OPTIONS)
        for (uint256 i = 0; i < _options.length; i++) {
            if (bytes(_options[i]).length == 0) revert EmptyOptionText();
            for (uint256 j = i + 1; j < _options.length; j++) {
                if (
                    keccak256(bytes(_options[i])) == keccak256(bytes(_options[j]))
                ) revert DuplicateOptionText();
            }
        }

        // Allow startTime == 0 to mean "start now"
        uint256 effectiveStart = (_startTime == 0) ? block.timestamp : _startTime;

        // ── Effects ──────────────────────────────────────────────────────────
        pollId = _nextPollId++;

        Poll storage p = _polls[pollId];
        p.question  = _question;
        p.startTime = effectiveStart;
        p.endTime   = effectiveStart + _duration;
        p.totalVotes = 0;
        p.exists    = true;

        // Copy options + initialise vote counts
        for (uint256 i = 0; i < _options.length; i++) {
            p.options.push(_options[i]);
            p.voteCounts.push(0);
        }

        // ── Interactions (none) ──────────────────────────────────────────────
        emit PollCreated(pollId, _question, effectiveStart, p.endTime, _options.length);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Admin: Voter Whitelisting
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @notice Whitelist a single voter for a specific poll.
     * @param  pollId  Target poll identifier.
     * @param  voter   Ethereum address of the voter.
     */
    function whitelistVoter(
        uint256 pollId,
        address voter
    ) external onlyAdmin pollExists(pollId) {
        if (voter == address(0))                    revert ZeroAddress();
        if (_whitelist[pollId][voter])              revert AlreadyWhitelisted(voter, pollId);

        _whitelist[pollId][voter] = true;
        emit VoterWhitelisted(pollId, voter);
    }

    /**
     * @notice Batch-whitelist multiple voters in a single transaction.
     *         Capped at MAX_BATCH_WHITELIST to prevent block gas limit issues.
     * @param  pollId  Target poll identifier.
     * @param  voters  Array of voter addresses.
     */
    function whitelistVotersBatch(
        uint256          pollId,
        address[] calldata voters
    ) external onlyAdmin pollExists(pollId) {
        if (voters.length > MAX_BATCH_WHITELIST) revert BatchLimitExceeded(MAX_BATCH_WHITELIST);

        for (uint256 i = 0; i < voters.length; i++) {
            address v = voters[i];
            if (v == address(0))           revert ZeroAddress();
            if (_whitelist[pollId][v])     revert AlreadyWhitelisted(v, pollId);

            _whitelist[pollId][v] = true;
            emit VoterWhitelisted(pollId, v);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Voter: Casting a Vote
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @notice Cast a vote on an active poll.
     * @dev    Enforces: whitelisted, poll active, single-vote, valid option.
     *         Follows Checks-Effects-Interactions strictly.
     * @param  pollId       Target poll identifier.
     * @param  optionIndex  Zero-based index into the options array.
     */
    function castVote(
        uint256 pollId,
        uint256 optionIndex
    ) external pollExists(pollId) pollActive(pollId) {
        Poll storage p = _polls[pollId];

        // ── Checks ───────────────────────────────────────────────────────────
        if (!_whitelist[pollId][msg.sender])  revert NotWhitelisted(msg.sender, pollId);
        if (_hasVoted[pollId][msg.sender])    revert AlreadyVoted(msg.sender, pollId);
        if (optionIndex >= p.options.length)  revert InvalidOption(optionIndex, p.options.length - 1);

        // ── Effects ──────────────────────────────────────────────────────────
        _hasVoted[pollId][msg.sender] = true;  // mark BEFORE incrementing
        p.voteCounts[optionIndex]    += 1;
        p.totalVotes                 += 1;

        // ── Interactions (none) ──────────────────────────────────────────────
        emit VoteCast(pollId, msg.sender, optionIndex);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public Getters
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @notice Retrieve full result data for a poll.
     * @param  pollId   Target poll identifier.
     * @return question  The poll question.
     * @return options   Array of option labels.
     * @return counts    Array of vote tallies (parallel to options).
     * @return total     Total votes cast.
     */
    function getResults(uint256 pollId)
        external
        view
        pollExists(pollId)
        returns (
            string memory    question,
            string[] memory  options,
            uint32[] memory  counts,
            uint256          total
        )
    {
        Poll storage p = _polls[pollId];
        return (p.question, p.options, p.voteCounts, p.totalVotes);
    }

    /**
     * @notice Get high-level metadata about a poll.
     * @param  pollId     Target poll identifier.
     * @return question   The poll question.
     * @return startTime  Unix timestamp when voting opens.
     * @return endTime    Unix timestamp when voting closes.
     * @return isActive   True if the poll is currently accepting votes.
     * @return totalVotes Total votes cast so far.
     * @return optionCount Number of voting options.
     */
    function getPollInfo(uint256 pollId)
        external
        view
        pollExists(pollId)
        returns (
            string memory question,
            uint256       startTime,
            uint256       endTime,
            bool          isActive,
            uint256       totalVotes,
            uint256       optionCount
        )
    {
        Poll storage p = _polls[pollId];
        bool active = (block.timestamp >= p.startTime && block.timestamp <= p.endTime);
        return (
            p.question,
            p.startTime,
            p.endTime,
            active,
            p.totalVotes,
            p.options.length
        );
    }

    /**
     * @notice Get vote count for a specific option.
     * @param  pollId      Target poll identifier.
     * @param  optionIndex Zero-based option index.
     * @return count       Number of votes for that option.
     */
    function getVoteCount(uint256 pollId, uint256 optionIndex)
        external
        view
        pollExists(pollId)
        returns (uint32 count)
    {
        Poll storage p = _polls[pollId];
        if (optionIndex >= p.options.length)
            revert InvalidOption(optionIndex, p.options.length - 1);
        return p.voteCounts[optionIndex];
    }

    /**
     * @notice Check whether a voter has already voted in a given poll.
     * @param  pollId  Target poll identifier.
     * @param  voter   Address to query.
     * @return voted   True if the address has cast a vote.
     */
    function hasVoted(uint256 pollId, address voter)
        external
        view
        pollExists(pollId)
        returns (bool voted)
    {
        return _hasVoted[pollId][voter];
    }

    /**
     * @notice Check whether an address is whitelisted for a poll.
     * @param  pollId  Target poll identifier.
     * @param  voter   Address to query.
     * @return listed  True if the address is on the whitelist.
     */
    function isWhitelisted(uint256 pollId, address voter)
        external
        view
        pollExists(pollId)
        returns (bool listed)
    {
        return _whitelist[pollId][voter];
    }

    /**
     * @notice Returns the total number of polls created (next available ID).
     */
    function totalPolls() external view returns (uint256) {
        return _nextPollId;
    }
}

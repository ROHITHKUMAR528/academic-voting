package com.academicvoting.controller;

import com.academicvoting.dto.*;
import com.academicvoting.service.ContractService;
import com.academicvoting.service.PollService;
import com.academicvoting.service.VotingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * REST controller exposing the Academic Voting smart contract over HTTP.
 *
 * <p>Base path: {@code /api}
 *
 * <h2>Poll Management (Admin)</h2>
 * <pre>
 *   POST   /api/polls                             — create poll
 *   POST   /api/polls/{pollId}/whitelist           — whitelist single voter
 *   POST   /api/polls/{pollId}/whitelist/batch     — batch whitelist
 * </pre>
 *
 * <h2>Voting</h2>
 * <pre>
 *   POST   /api/polls/{pollId}/vote               — cast a vote
 * </pre>
 *
 * <h2>Queries (Public)</h2>
 * <pre>
 *   GET    /api/polls/count                        — total polls created
 *   GET    /api/polls/{pollId}/info                — poll metadata
 *   GET    /api/polls/{pollId}/results             — vote results
 *   GET    /api/polls/{pollId}/voted/{address}     — has address voted?
 *   GET    /api/polls/{pollId}/whitelisted/{addr}  — is address whitelisted?
 * </pre>
 *
 * <h2>Admin</h2>
 * <pre>
 *   POST   /api/admin/transfer                     — transfer admin role
 *   GET    /api/admin/info                         — admin & contract info
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class VotingController {

    private final PollService     pollService;
    private final VotingService   votingService;
    private final ContractService contractService;

    // ═════════════════════════════════════════════════════════════════════════
    // POLL MANAGEMENT (Admin)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Create a new poll.
     *
     * <pre>
     * POST /api/polls
     * {
     *   "question": "Who should be Dean?",
     *   "options": ["Alice", "Bob", "Carol"],
     *   "startTime": 0,
     *   "durationSeconds": 604800
     * }
     * </pre>
     */
    @PostMapping("/polls")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createPoll(
            @Valid @RequestBody CreatePollRequest request) {

        log.info("→ POST /api/polls — question: \"{}\"", request.getQuestion());
        BigInteger pollId = pollService.createPoll(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.<Map<String, Object>>builder()
                .status(201)
                .message("Poll created successfully")
                .data(Map.of("pollId", pollId.longValue()))
                .build()
        );
    }

    /**
     * Whitelist a single voter for a poll.
     *
     * <pre>
     * POST /api/polls/{pollId}/whitelist
     * { "voterAddress": "0x70997970C51812dc3A010C7d01b50e0d17dc79C8" }
     * </pre>
     */
    @PostMapping("/polls/{pollId}/whitelist")
    public ResponseEntity<ApiResponse<Map<String, Object>>> whitelistVoter(
            @PathVariable long pollId,
            @Valid @RequestBody WhitelistRequest request) {

        log.info("→ POST /api/polls/{}/whitelist — voter: {}", pollId, request.getVoterAddress());
        String txHash = pollService.whitelistVoter(pollId, request.getVoterAddress());

        return ResponseEntity.ok(ApiResponse.success(
            Map.of("pollId", pollId, "voter", request.getVoterAddress()),
            txHash
        ));
    }

    /**
     * Batch-whitelist multiple voters (max 200).
     *
     * <pre>
     * POST /api/polls/{pollId}/whitelist/batch
     * { "voters": ["0xABCD...", "0x1234..."] }
     * </pre>
     */
    @PostMapping("/polls/{pollId}/whitelist/batch")
    public ResponseEntity<ApiResponse<Map<String, Object>>> whitelistVotersBatch(
            @PathVariable long pollId,
            @RequestBody Map<String, List<String>> body) {

        List<String> voters = body.get("voters");
        log.info("→ POST /api/polls/{}/whitelist/batch — {} voters", pollId,
            voters != null ? voters.size() : 0);
        String txHash = pollService.whitelistVotersBatch(pollId, voters);

        return ResponseEntity.ok(ApiResponse.success(
            Map.of("pollId", pollId, "whitelistedCount", voters.size()),
            txHash
        ));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // VOTING
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Cast a vote on an active poll.
     *
     * <pre>
     * POST /api/polls/{pollId}/vote
     * {
     *   "voterPrivateKey": "0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d",
     *   "optionIndex": 0
     * }
     * </pre>
     */
    @PostMapping("/polls/{pollId}/vote")
    public ResponseEntity<ApiResponse<Map<String, Object>>> castVote(
            @PathVariable long pollId,
            @Valid @RequestBody VoteRequest request) {

        log.info("→ POST /api/polls/{}/vote — option: {}", pollId, request.getOptionIndex());
        String txHash = votingService.castVote(pollId, request);

        return ResponseEntity.ok(ApiResponse.success(
            Map.of("pollId", pollId, "optionIndex", request.getOptionIndex()),
            txHash
        ));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // QUERIES (Public Read)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Get total number of polls ever created.
     *
     * <pre>GET /api/polls/count</pre>
     */
    @GetMapping("/polls/count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getTotalPolls() {
        long count = pollService.getTotalPolls().longValue();
        return ResponseEntity.ok(ApiResponse.success(Map.of("totalPolls", count)));
    }

    /**
     * Get metadata for a specific poll.
     *
     * <pre>GET /api/polls/{pollId}/info</pre>
     */
    @GetMapping("/polls/{pollId}/info")
    public ResponseEntity<ApiResponse<PollInfoResponse>> getPollInfo(
            @PathVariable long pollId) {

        PollInfoResponse info = pollService.getPollInfo(pollId);
        return ResponseEntity.ok(ApiResponse.success(info));
    }

    /**
     * Get vote results for a specific poll.
     *
     * <pre>GET /api/polls/{pollId}/results</pre>
     */
    @GetMapping("/polls/{pollId}/results")
    public ResponseEntity<ApiResponse<PollResultResponse>> getResults(
            @PathVariable long pollId) {

        PollResultResponse results = votingService.getResults(pollId);
        return ResponseEntity.ok(ApiResponse.success(results));
    }

    /**
     * Check whether a given address has voted in a poll.
     *
     * <pre>GET /api/polls/{pollId}/voted/{address}</pre>
     */
    @GetMapping("/polls/{pollId}/voted/{address}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> hasVoted(
            @PathVariable long pollId,
            @PathVariable String address) {

        boolean voted = votingService.hasVoted(pollId, address);
        return ResponseEntity.ok(ApiResponse.success(
            Map.of("pollId", pollId, "address", address, "hasVoted", voted)
        ));
    }

    /**
     * Check whether a given address is whitelisted for a poll.
     *
     * <pre>GET /api/polls/{pollId}/whitelisted/{address}</pre>
     */
    @GetMapping("/polls/{pollId}/whitelisted/{address}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> isWhitelisted(
            @PathVariable long pollId,
            @PathVariable String address) {

        boolean whitelisted = pollService.isWhitelisted(pollId, address);
        return ResponseEntity.ok(ApiResponse.success(
            Map.of("pollId", pollId, "address", address, "isWhitelisted", whitelisted)
        ));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ADMIN OPERATIONS
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Transfer on-chain admin role to a new address.
     *
     * <pre>
     * POST /api/admin/transfer
     * { "newAdmin": "0xABCDE..." }
     * </pre>
     */
    @PostMapping("/admin/transfer")
    public ResponseEntity<ApiResponse<Map<String, Object>>> transferAdmin(
            @RequestBody Map<String, String> body) {

        String newAdmin = body.get("newAdmin");
        if (newAdmin == null || newAdmin.isBlank()) {
            throw new IllegalArgumentException("newAdmin address is required");
        }

        log.info("→ POST /api/admin/transfer — newAdmin: {}", newAdmin);
        String txHash = contractService.transferAdmin(newAdmin);

        return ResponseEntity.ok(ApiResponse.success(
            Map.of("newAdmin", newAdmin),
            txHash
        ));
    }

    /**
     * Get contract and admin info (useful for debugging).
     *
     * <pre>GET /api/admin/info</pre>
     */
    @GetMapping("/admin/info")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAdminInfo() {
        return ResponseEntity.ok(ApiResponse.success(Map.of(
            "contractAddress",  contractService.getContractAddress(),
            "onChainAdmin",     contractService.getAdminAddress(),
            "localAdminWallet", contractService.getLocalAdminAddress(),
            "totalPolls",       contractService.getTotalPolls().longValue()
        )));
    }
}

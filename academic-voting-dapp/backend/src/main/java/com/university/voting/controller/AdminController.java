package com.university.voting.controller;

import com.university.voting.dto.*;
import com.university.voting.service.PollService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Admin REST Controller — poll lifecycle management.
 *
 * All endpoints are prefixed with /api/admin.
 * Sprint 3 will add an authentication filter to gate this prefix.
 *
 * Endpoints:
 *   POST   /api/admin/polls                         → createPoll
 *   POST   /api/admin/polls/{pollId}/whitelist       → whitelistVoter (single)
 *   POST   /api/admin/polls/{pollId}/whitelist/batch → whitelistVoterBatch
 *   POST   /api/admin/polls/{pollId}/close           → closePoll
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final PollService pollService;

    /**
     * Creates a new poll on-chain.
     *
     * POST /api/admin/polls
     * Body: { "question": "...", "options": ["A","B"], "durationSeconds": 86400 }
     */
    @PostMapping("/polls")
    public ResponseEntity<ApiResponse<Map<String, String>>> createPoll(
            @Valid @RequestBody CreatePollRequest request) {

        TransactionReceipt receipt = pollService.createPoll(
                request.getQuestion(),
                request.getOptions(),
                request.getDurationSeconds()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(
                        Map.of("transactionHash", receipt.getTransactionHash(),
                               "blockNumber",     receipt.getBlockNumber().toString(),
                               "gasUsed",         receipt.getGasUsed().toString()),
                        receipt.getTransactionHash()
                ));
    }

    /**
     * Whitelists a single voter.
     *
     * POST /api/admin/polls/{pollId}/whitelist
     * Body: { "voterAddress": "0x..." }
     */
    @PostMapping("/polls/{pollId}/whitelist")
    public ResponseEntity<ApiResponse<Map<String, String>>> whitelistVoter(
            @PathVariable String pollId,
            @RequestBody Map<String, String> body) {

        String voterAddress = body.get("voterAddress");
        if (voterAddress == null || voterAddress.isBlank()) {
            throw new IllegalArgumentException("voterAddress is required");
        }

        TransactionReceipt receipt = pollService.whitelistVoter(
                new BigInteger(pollId), voterAddress);

        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("transactionHash", receipt.getTransactionHash()),
                receipt.getTransactionHash()
        ));
    }

    /**
     * Whitelists multiple voters in a single transaction.
     *
     * POST /api/admin/polls/{pollId}/whitelist/batch
     * Body: { "voterAddresses": ["0x...", "0x..."] }
     */
    @PostMapping("/polls/{pollId}/whitelist/batch")
    public ResponseEntity<ApiResponse<Map<String, String>>> whitelistVoterBatch(
            @PathVariable String pollId,
            @RequestBody Map<String, List<String>> body) {

        List<String> voters = body.get("voterAddresses");
        if (voters == null || voters.isEmpty()) {
            throw new IllegalArgumentException("voterAddresses list is required");
        }

        TransactionReceipt receipt = pollService.whitelistVoterBatch(
                new BigInteger(pollId), voters);

        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("transactionHash", receipt.getTransactionHash(),
                       "count",           String.valueOf(voters.size())),
                receipt.getTransactionHash()
        ));
    }

    /**
     * Closes an active poll early.
     *
     * POST /api/admin/polls/{pollId}/close
     */
    @PostMapping("/polls/{pollId}/close")
    public ResponseEntity<ApiResponse<Map<String, String>>> closePoll(
            @PathVariable String pollId) {

        TransactionReceipt receipt = pollService.closePoll(new BigInteger(pollId));

        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("transactionHash", receipt.getTransactionHash(),
                       "pollId",          pollId),
                receipt.getTransactionHash()
        ));
    }
}

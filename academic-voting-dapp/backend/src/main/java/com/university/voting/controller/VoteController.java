package com.university.voting.controller;

import com.university.voting.dto.ApiResponse;
import com.university.voting.dto.CastVoteRequest;
import com.university.voting.service.PollService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.util.Map;

/**
 * Voting REST Controller.
 *
 * Sprint 2: The request body carries a voterPrivateKey for direct testing.
 *           This is ONLY acceptable on a local testnet.
 *
 * Sprint 3: This controller will be refactored to:
 *           1. Accept only a session token (no private key in payload).
 *           2. Let AnonymityService resolve the ephemeral credentials server-side.
 *           3. The voterPrivateKey field will be removed from CastVoteRequest.
 *
 * Endpoints:
 *   POST /api/votes   → castVote
 */
@Slf4j
@RestController
@RequestMapping("/api/votes")
@RequiredArgsConstructor
public class VoteController {

    private final PollService pollService;

    /**
     * Casts a vote on a poll.
     *
     * POST /api/votes
     * Body:
     * {
     *   "pollId":         "1",
     *   "optionIndex":    0,
     *   "voterAddress":   "0x...",
     *   "voterPrivateKey":"0x..."   ← removed in Sprint 3
     * }
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> castVote(
            @Valid @RequestBody CastVoteRequest request) {

        // Sprint 2: credentials supplied by client for local testing.
        // Sprint 3: Credentials will be resolved from the anonymity layer,
        //           and this line will be replaced by: AnonymityService.resolveCredentials(session)
        Credentials voterCredentials = Credentials.create(request.getVoterPrivateKey());

        TransactionReceipt receipt = pollService.castVote(
                new BigInteger(request.getPollId()),
                BigInteger.valueOf(request.getOptionIndex()),
                voterCredentials
        );

        log.info("Vote cast: pollId={} option={} voter={} tx={}",
                request.getPollId(),
                request.getOptionIndex(),
                request.getVoterAddress(),
                receipt.getTransactionHash()
        );

        return ResponseEntity.ok(ApiResponse.ok(
                Map.of(
                        "transactionHash", receipt.getTransactionHash(),
                        "blockNumber",     receipt.getBlockNumber().toString(),
                        "gasUsed",         receipt.getGasUsed().toString(),
                        "pollId",          request.getPollId(),
                        "optionIndex",     String.valueOf(request.getOptionIndex())
                ),
                receipt.getTransactionHash()
        ));
    }
}

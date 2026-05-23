package com.university.voting.controller;

import com.university.voting.dto.ApiResponse;
import com.university.voting.dto.PollDto;
import com.university.voting.service.PollService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Public Poll REST Controller — read-only poll data.
 *
 * These endpoints are intentionally unauthenticated: anyone can view
 * active polls and real-time results (full transparency).
 *
 * Endpoints:
 *   GET  /api/polls            → all polls
 *   GET  /api/polls/{pollId}   → single poll
 *   GET  /api/polls/{pollId}/whitelist/{address}  → check whitelist status
 *   GET  /api/polls/{pollId}/voted/{address}      → check voted status
 */
@RestController
@RequestMapping("/api/polls")
@RequiredArgsConstructor
public class PollController {

    private final PollService pollService;

    /**
     * Returns all polls with their current vote tallies.
     * GET /api/polls
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<PollDto>>> getAllPolls() {
        return ResponseEntity.ok(ApiResponse.ok(pollService.getAllPolls()));
    }

    /**
     * Returns a single poll by ID.
     * GET /api/polls/{pollId}
     */
    @GetMapping("/{pollId}")
    public ResponseEntity<ApiResponse<PollDto>> getPoll(@PathVariable String pollId) {
        return ResponseEntity.ok(ApiResponse.ok(pollService.getPoll(new BigInteger(pollId))));
    }

    /**
     * Checks if an address is whitelisted for a poll.
     * GET /api/polls/{pollId}/whitelist/{address}
     */
    @GetMapping("/{pollId}/whitelist/{address}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> isWhitelisted(
            @PathVariable String pollId,
            @PathVariable String address) {

        boolean whitelisted = pollService.isWhitelisted(new BigInteger(pollId), address);
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("pollId", pollId, "address", address, "whitelisted", whitelisted)
        ));
    }

    /**
     * Checks if an address has already voted in a poll.
     * GET /api/polls/{pollId}/voted/{address}
     */
    @GetMapping("/{pollId}/voted/{address}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> hasVoted(
            @PathVariable String pollId,
            @PathVariable String address) {

        boolean voted = pollService.hasVoted(new BigInteger(pollId), address);
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("pollId", pollId, "address", address, "hasVoted", voted)
        ));
    }
}

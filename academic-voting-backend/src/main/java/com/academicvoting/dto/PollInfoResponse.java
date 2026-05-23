package com.academicvoting.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Response payload for poll metadata queries ({@code GET /api/polls/{id}/info}).
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PollInfoResponse {

    private long   pollId;
    private String question;

    /** Unix timestamp (seconds) when voting opens. */
    private long   startTime;

    /** Unix timestamp (seconds) when voting closes. */
    private long   endTime;

    /** ISO-8601 representation of startTime (for human readability). */
    private String startTimeIso;

    /** ISO-8601 representation of endTime. */
    private String endTimeIso;

    /** Whether the poll is currently accepting votes. */
    private boolean active;

    private long   totalVotes;
    private long   optionCount;

    // ── Factory ──────────────────────────────────────────────────────────────

    public static PollInfoResponse from(long pollId,
                                         String question,
                                         long startTime,
                                         long endTime,
                                         boolean active,
                                         long totalVotes,
                                         long optionCount) {
        return PollInfoResponse.builder()
            .pollId(pollId)
            .question(question)
            .startTime(startTime)
            .endTime(endTime)
            .startTimeIso(Instant.ofEpochSecond(startTime).toString())
            .endTimeIso(Instant.ofEpochSecond(endTime).toString())
            .active(active)
            .totalVotes(totalVotes)
            .optionCount(optionCount)
            .build();
    }
}

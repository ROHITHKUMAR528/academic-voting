package com.academicvoting.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response payload for vote result queries ({@code GET /api/polls/{id}/results}).
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PollResultResponse {

    private long        pollId;
    private String      question;
    private long        totalVotes;

    /** Per-option breakdown. */
    private List<OptionResult> options;

    // ── Nested ────────────────────────────────────────────────────────────────

    @Data
    @Builder
    public static class OptionResult {
        private int    index;
        private String label;
        private long   voteCount;

        /** Percentage of total votes (rounded to 2 decimal places). */
        private double percentage;
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    public static PollResultResponse from(long pollId,
                                           String question,
                                           List<String> optionLabels,
                                           List<Long> voteCounts,
                                           long totalVotes) {
        List<OptionResult> opts = new java.util.ArrayList<>();
        for (int i = 0; i < optionLabels.size(); i++) {
            long count = voteCounts.get(i);
            double pct = totalVotes == 0 ? 0.0
                : Math.round(count * 10000.0 / totalVotes) / 100.0;

            opts.add(OptionResult.builder()
                .index(i)
                .label(optionLabels.get(i))
                .voteCount(count)
                .percentage(pct)
                .build());
        }

        return PollResultResponse.builder()
            .pollId(pollId)
            .question(question)
            .totalVotes(totalVotes)
            .options(opts)
            .build();
    }
}

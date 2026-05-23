package com.academicvoting.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

/**
 * Request body for {@code POST /api/polls}.
 */
@Data
public class CreatePollRequest {

    /** The poll question shown to voters. */
    @NotBlank(message = "question must not be blank")
    @Size(max = 512, message = "question must be at most 512 characters")
    private String question;

    /** Option labels — at least 2, at most 50, none blank, all unique. */
    @NotNull(message = "options must not be null")
    @Size(min = 2, max = 50, message = "poll must have between 2 and 50 options")
    private List<@NotBlank(message = "option label must not be blank") String> options;

    /**
     * Unix timestamp (seconds) when voting opens.
     * Defaults to {@code 0} = start immediately.
     */
    @Min(value = 0, message = "startTime must be >= 0")
    private long startTime = 0;

    /**
     * Duration the poll stays open, in seconds.
     * Must be at least 60 seconds.
     */
    @Min(value = 60, message = "duration must be at least 60 seconds")
    private long durationSeconds;
}

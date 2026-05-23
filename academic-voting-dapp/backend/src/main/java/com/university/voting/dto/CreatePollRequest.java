package com.university.voting.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

/** Request body for POST /api/admin/polls */
@Data
public class CreatePollRequest {

    @NotBlank(message = "Question must not be blank")
    @Size(max = 500, message = "Question must not exceed 500 characters")
    private String question;

    @NotNull
    @Size(min = 2, max = 10, message = "Must provide between 2 and 10 options")
    private List<@NotBlank(message = "Option text must not be blank") String> options;

    @Positive(message = "Duration must be a positive number of seconds")
    @Min(value = 60,       message = "Duration must be at least 60 seconds")
    @Max(value = 31536000, message = "Duration must not exceed 365 days")
    private long durationSeconds;
}

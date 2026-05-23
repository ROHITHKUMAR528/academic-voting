package com.academicvoting.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Generic API response envelope for all endpoints.
 *
 * @param <T> Payload type
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /** HTTP status code (200, 400, 500…). */
    private int status;

    /** Human-readable result message. */
    private String message;

    /** Response payload — {@code null} on error responses. */
    private T data;

    /** Blockchain transaction hash — present when a tx was sent. */
    private String txHash;

    /** ISO-8601 timestamp of the response. */
    @Builder.Default
    private String timestamp = Instant.now().toString();

    // ── Static factories ──────────────────────────────────────────────────

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
            .status(200)
            .message("Success")
            .data(data)
            .build();
    }

    public static <T> ApiResponse<T> success(T data, String txHash) {
        return ApiResponse.<T>builder()
            .status(200)
            .message("Transaction submitted")
            .data(data)
            .txHash(txHash)
            .build();
    }

    public static <T> ApiResponse<T> error(int status, String message) {
        return ApiResponse.<T>builder()
            .status(status)
            .message(message)
            .build();
    }
}

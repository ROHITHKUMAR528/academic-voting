package com.university.voting.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Strongly-typed configuration properties bound from application.properties.
 *
 * All Web3j and contract settings are centralised here — no scattered
 * @Value annotations across the codebase.
 */
@Component
@ConfigurationProperties(prefix = "web3j")
@Validated
@Getter
@Setter
public class Web3jProperties {

    /** JSON-RPC endpoint (e.g. http://127.0.0.1:8545) */
    @NotBlank
    private String clientAddress;

    /** Hex private key of the admin/operator wallet. Injected from env in prod. */
    @NotBlank
    private String adminPrivateKey;

    /** Wei per gas unit for submitted transactions. */
    @Positive
    private long gasPrice = 20_000_000_000L;

    /** Maximum gas units per transaction. */
    @Positive
    private long gasLimit = 500_000L;

    /** Nested HTTP client pool settings. */
    private HttpClient httpClient = new HttpClient();

    @Getter
    @Setter
    public static class HttpClient {
        private int  maxIdleConnections         = 10;
        private int  keepAliveDurationMinutes   = 5;
        private int  connectionTimeoutSeconds   = 10;
        private int  readTimeoutSeconds         = 30;
    }
}

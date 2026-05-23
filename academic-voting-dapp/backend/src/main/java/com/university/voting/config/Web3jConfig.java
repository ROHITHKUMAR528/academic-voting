package com.university.voting.config;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.StaticGasProvider;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

/**
 * Web3j Spring configuration.
 *
 * Bean hierarchy:
 *   OkHttpClient (pool)
 *     └─► Web3j           (JSON-RPC client)
 *     └─► Credentials     (admin signing key)
 *     └─► ContractGasProvider
 *
 * All beans are singletons; the OkHttpClient pool is shared across all
 * Web3j requests (connection reuse, keep-alive).
 */
@Configuration
public class Web3jConfig {

    private final Web3jProperties props;

    @Value("${app.contract.address}")
    private String contractAddress;

    public Web3jConfig(Web3jProperties props) {
        this.props = props;
    }

    /**
     * Shared OkHttp connection pool.
     *
     * maxIdleConnections : limits idle socket count (memory vs throughput trade-off).
     * keepAliveDuration  : how long an idle connection stays alive before being evicted.
     */
    @Bean
    public OkHttpClient okHttpClient() {
        Web3jProperties.HttpClient hc = props.getHttpClient();

        ConnectionPool pool = new ConnectionPool(
                hc.getMaxIdleConnections(),
                hc.getKeepAliveDurationMinutes(),
                TimeUnit.MINUTES
        );

        return new OkHttpClient.Builder()
                .connectionPool(pool)
                .connectTimeout(hc.getConnectionTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(hc.getReadTimeoutSeconds(), TimeUnit.SECONDS)
                .writeTimeout(hc.getReadTimeoutSeconds(), TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    /**
     * Web3j instance bound to the local EVM node.
     * Uses the pooled OkHttpClient for all JSON-RPC calls.
     */
    @Bean
    public Web3j web3j(OkHttpClient okHttpClient) {
        HttpService httpService = new HttpService(props.getClientAddress(), okHttpClient);
        return Web3j.build(httpService);
    }

    /**
     * Admin credentials loaded from the private key in properties.
     * In production this private key must come from a secrets manager
     * (e.g. AWS Secrets Manager, HashiCorp Vault) — NEVER hardcoded.
     */
    @Bean
    public Credentials adminCredentials() {
        return Credentials.create(props.getAdminPrivateKey());
    }

    /**
     * Static gas provider used for all write transactions.
     * For mainnet use EthGasStationGasProvider or a fee-market-aware provider.
     */
    @Bean
    public ContractGasProvider contractGasProvider() {
        return new StaticGasProvider(
                BigInteger.valueOf(props.getGasPrice()),
                BigInteger.valueOf(props.getGasLimit())
        );
    }

    /**
     * Exposes the deployed contract address for injection into services.
     */
    @Bean("contractAddress")
    public String contractAddress() {
        return contractAddress;
    }
}

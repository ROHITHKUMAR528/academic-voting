package com.academicvoting.config;

import com.academicvoting.contracts.AcademicVoting;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.tx.gas.StaticGasProvider;

import java.math.BigInteger;

/**
 * Spring configuration for all Web3j-related beans.
 *
 * <p>Reads connection details from {@code application.yml} and exposes:
 * <ul>
 *   <li>{@link Web3j}         — JSON-RPC client connected to the EVM node</li>
 *   <li>{@link Credentials}   — signing credentials derived from the admin private key</li>
 *   <li>{@link AcademicVoting} — loaded contract wrapper (auto-deploys if address absent)</li>
 * </ul>
 */
@Slf4j
@Configuration
public class Web3jConfig {

    @Value("${web3j.client-address:http://localhost:8545}")
    private String nodeUrl;

    @Value("${web3j.admin-private-key}")
    private String adminPrivateKey;

    @Value("${web3j.contract-address:}")
    private String contractAddress;

    @Value("${web3j.gas-limit:3000000}")
    private long gasLimit;

    @Value("${web3j.gas-price:20000000000}")
    private long gasPrice;

    // ── Core Web3j Connection ────────────────────────────────────────────────

    /**
     * Creates a Web3j HTTP client connecting to the configured EVM node.
     * Uses polling for log/event subscriptions (suitable for local Hardhat).
     */
    @Bean
    public Web3j web3j() {
        log.info("Connecting to EVM node at: {}", nodeUrl);
        Web3j client = Web3j.build(new HttpService(nodeUrl));
        try {
            String version = client.web3ClientVersion().send().getWeb3ClientVersion();
            log.info("Connected — node client version: {}", version);
            BigInteger blockNumber = client.ethBlockNumber().send().getBlockNumber();
            log.info("Current block number: {}", blockNumber);
        } catch (Exception e) {
            log.warn("Could not verify node connection: {}. Will retry on first use.", e.getMessage());
        }
        return client;
    }

    // ── Signing Credentials ──────────────────────────────────────────────────

    /**
     * Derives an Ethereum {@link Credentials} from the configured private key.
     * In production these should come from a secure vault (not a config file).
     */
    @Bean
    public Credentials adminCredentials() {
        Credentials creds = Credentials.create(adminPrivateKey);
        log.info("Admin wallet address: {}", creds.getAddress());
        return creds;
    }

    // ── Gas Provider ─────────────────────────────────────────────────────────

    /**
     * Static gas provider using configured gas price and limit.
     * Sprint 3 can replace this with an {@code EIP1559} or oracle-based provider.
     */
    @Bean
    public StaticGasProvider gasProvider() {
        log.info("Gas config — price: {} wei, limit: {} units", gasPrice, gasLimit);
        return new StaticGasProvider(
            BigInteger.valueOf(gasPrice),
            BigInteger.valueOf(gasLimit)
        );
    }

    // ── Contract Wrapper ─────────────────────────────────────────────────────

    /**
     * Loads (or deploys) the {@link AcademicVoting} contract.
     *
     * <ul>
     *   <li>If {@code web3j.contract-address} is set → loads existing deployment</li>
     *   <li>If not set → deploys a fresh contract and logs the address</li>
     * </ul>
     */
    @Bean
    public AcademicVoting academicVoting(Web3j web3j,
                                          Credentials adminCredentials,
                                          StaticGasProvider gasProvider) throws Exception {
        if (contractAddress != null && !contractAddress.isBlank()) {
            log.info("Loading AcademicVoting contract at address: {}", contractAddress);
            return AcademicVoting.load(contractAddress, web3j, adminCredentials, gasProvider);
        }

        log.warn("CONTRACT_ADDRESS not configured — deploying a fresh AcademicVoting contract...");
        AcademicVoting deployed = AcademicVoting
            .deploy(web3j, adminCredentials, gasProvider)
            .send();
        String deployedAddress = deployed.getContractAddress();
        log.info("✅ AcademicVoting deployed at: {}", deployedAddress);
        log.info("   Add  web3j.contract-address={}  to application.yml to reuse it.", deployedAddress);
        return deployed;
    }
}

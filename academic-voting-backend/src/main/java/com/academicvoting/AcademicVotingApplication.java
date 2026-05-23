package com.academicvoting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Academic Voting dApp backend.
 *
 * <p>Connects to a local (or remote) EVM node via Web3j and exposes the
 * AcademicVoting smart contract over a REST API.
 */
@SpringBootApplication
public class AcademicVotingApplication {

    public static void main(String[] args) {
        SpringApplication.run(AcademicVotingApplication.class, args);
    }
}

package com.university.voting.service;

import com.university.voting.contract.AcademicVoting;
import com.university.voting.dto.PollDto;
import com.university.voting.exception.BlockchainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.gas.ContractGasProvider;

import java.math.BigInteger;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PollService Unit Tests")
class PollServiceTest {

    @Mock Web3j               web3j;
    @Mock Credentials         adminCredentials;
    @Mock ContractGasProvider gasProvider;
    @Mock AcademicVoting      mockContract;

    private PollService pollService;

    private static final String CONTRACT_ADDR = "0x5FbDB2315678afecb367f032d93F642f64180aa3";

    @BeforeEach
    void setUp() {
        when(adminCredentials.getAddress()).thenReturn("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266");
        pollService = new PollService(web3j, adminCredentials, gasProvider, CONTRACT_ADDR);
    }

    // ── createPoll ───────────────────────────────────────────────────────

    @Test
    @DisplayName("createPoll: returns receipt on successful tx")
    void createPoll_success() throws Exception {
        TransactionReceipt mockReceipt = buildReceipt("0xabc123");

        try (MockedStatic<AcademicVoting> staticMock = mockStatic(AcademicVoting.class)) {
            staticMock.when(() -> AcademicVoting.load(any(), any(), any(Credentials.class), any()))
                    .thenReturn(mockContract);

            RemoteFunctionCall<TransactionReceipt> mockCall = mock(RemoteFunctionCall.class);
            when(mockContract.createPoll(anyString(), anyList(), any(BigInteger.class)))
                    .thenReturn(mockCall);
            when(mockCall.send()).thenReturn(mockReceipt);

            TransactionReceipt result = pollService.createPoll(
                    "Test question", List.of("A", "B"), 86400L);

            assertThat(result.getTransactionHash()).isEqualTo("0xabc123");
        }
    }

    @Test
    @DisplayName("createPoll: wraps exception into BlockchainException")
    void createPoll_throwsBlockchainException() throws Exception {
        try (MockedStatic<AcademicVoting> staticMock = mockStatic(AcademicVoting.class)) {
            staticMock.when(() -> AcademicVoting.load(any(), any(), any(Credentials.class), any()))
                    .thenReturn(mockContract);

            RemoteFunctionCall<TransactionReceipt> mockCall = mock(RemoteFunctionCall.class);
            when(mockContract.createPoll(anyString(), anyList(), any(BigInteger.class)))
                    .thenReturn(mockCall);
            when(mockCall.send()).thenThrow(new RuntimeException("Node unreachable"));

            assertThatThrownBy(() ->
                    pollService.createPoll("Q", List.of("A", "B"), 3600L))
                    .isInstanceOf(BlockchainException.class)
                    .hasMessageContaining("Failed to create poll");
        }
    }

    // ── hasVoted / isWhitelisted ─────────────────────────────────────────

    @Test
    @DisplayName("hasVoted: returns true when contract says true")
    void hasVoted_returnsTrue() throws Exception {
        try (MockedStatic<AcademicVoting> staticMock = mockStatic(AcademicVoting.class)) {
            staticMock.when(() -> AcademicVoting.load(any(), any(), any(Credentials.class), any()))
                    .thenReturn(mockContract);

            RemoteFunctionCall<Boolean> call = mock(RemoteFunctionCall.class);
            when(mockContract.hasVoted(any(BigInteger.class), anyString())).thenReturn(call);
            when(call.send()).thenReturn(Boolean.TRUE);

            boolean result = pollService.hasVoted(BigInteger.ONE, "0xVoter");
            assertThat(result).isTrue();
        }
    }

    @Test
    @DisplayName("isWhitelisted: returns false when contract says false")
    void isWhitelisted_returnsFalse() throws Exception {
        try (MockedStatic<AcademicVoting> staticMock = mockStatic(AcademicVoting.class)) {
            staticMock.when(() -> AcademicVoting.load(any(), any(), any(Credentials.class), any()))
                    .thenReturn(mockContract);

            RemoteFunctionCall<Boolean> call = mock(RemoteFunctionCall.class);
            when(mockContract.isWhitelisted(any(BigInteger.class), anyString())).thenReturn(call);
            when(call.send()).thenReturn(Boolean.FALSE);

            boolean result = pollService.isWhitelisted(BigInteger.ONE, "0xStranger");
            assertThat(result).isFalse();
        }
    }

    // ── getPoll ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getPoll: correctly maps raw tuple to PollDto")
    @SuppressWarnings("unchecked")
    void getPoll_mapsCorrectly() throws Exception {
        List<Type> rawPoll = buildRawPoll(
                BigInteger.ONE,
                "Best language?",
                List.of("Java", "Go"),
                List.of(BigInteger.TWO, BigInteger.ONE),
                BigInteger.valueOf(1_700_000_000L),
                BigInteger.valueOf(1_700_086_400L),
                BigInteger.ZERO,   // ACTIVE
                BigInteger.valueOf(3)
        );

        try (MockedStatic<AcademicVoting> staticMock = mockStatic(AcademicVoting.class)) {
            staticMock.when(() -> AcademicVoting.load(any(), any(), any(Credentials.class), any()))
                    .thenReturn(mockContract);

            RemoteFunctionCall<List<Type>> getPollCall = mock(RemoteFunctionCall.class);
            RemoteFunctionCall<Boolean>   acceptingCall = mock(RemoteFunctionCall.class);

            when(mockContract.getPoll(BigInteger.ONE)).thenReturn(getPollCall);
            when(getPollCall.send()).thenReturn(rawPoll);
            when(mockContract.isPollAcceptingVotes(BigInteger.ONE)).thenReturn(acceptingCall);
            when(acceptingCall.send()).thenReturn(true);

            PollDto dto = pollService.getPoll(BigInteger.ONE);

            assertThat(dto.getPollId()).isEqualTo("1");
            assertThat(dto.getQuestion()).isEqualTo("Best language?");
            assertThat(dto.getOptions()).containsExactly("Java", "Go");
            assertThat(dto.getVoteCounts()).containsExactly(2L, 1L);
            assertThat(dto.getStatus()).isEqualTo("ACTIVE");
            assertThat(dto.getTotalVotesCast()).isEqualTo(3L);
            assertThat(dto.getAcceptingVotes()).isTrue();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private TransactionReceipt buildReceipt(String txHash) {
        TransactionReceipt r = new TransactionReceipt();
        r.setTransactionHash(txHash);
        r.setBlockNumber("12");
        r.setGasUsed("21000");
        return r;
    }

    @SuppressWarnings("unchecked")
    private List<Type> buildRawPoll(
            BigInteger id, String question, List<String> options,
            List<BigInteger> counts, BigInteger startTime,
            BigInteger endTime, BigInteger status, BigInteger total) {

        return List.of(
                new Uint256(id),
                new Utf8String(question),
                new DynamicArray<>(Utf8String.class, options.stream().map(Utf8String::new).toList()),
                new DynamicArray<>(Uint256.class, counts.stream().map(Uint256::new).toList()),
                new Uint256(startTime),
                new Uint256(endTime),
                new Uint256(status),
                new Uint256(total)
        );
    }
}

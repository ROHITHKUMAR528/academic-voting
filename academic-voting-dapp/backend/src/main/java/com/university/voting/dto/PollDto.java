package com.university.voting.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigInteger;
import java.util.List;

/**
 * DTO representing a single poll's full state returned to API consumers.
 * All numeric blockchain values are represented as Strings to avoid
 * JavaScript precision loss for BigInteger / uint256 values.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PollDto {

    private String        pollId;
    private String        question;
    private List<String>  options;
    private List<Long>    voteCounts;
    private String        startTime;       // unix epoch seconds (as string)
    private String        endTime;         // unix epoch seconds (as string)
    private String        status;          // "ACTIVE" | "CLOSED"
    private Long          totalVotesCast;
    private Boolean       acceptingVotes;  // convenience flag from isPollAcceptingVotes()
}

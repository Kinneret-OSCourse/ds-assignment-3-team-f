package com.mulligan.recommender;

import java.util.List;
import java.util.Map;

/**
 * Result of the majority-vote consensus step.
 *
 * @param success true when a majority agreed on exactly the same encoded list
 * @param recommendation encoded recommendation or failure message
 * @param votes raw node votes collected by the leader
 */
public record ConsensusResult(boolean success, String recommendation, Map<String, String> votes) {

    /**
     * Formats the result for CLI and HTTP clients.
     *
     * @return human-readable response
     */
    public String toClientText() {
        if (!success) {
            return "CONSENSUS_FAILURE";
        }
        if ("EMPTY".equals(recommendation)) {
            return "Empty List";
        }
        return recommendation;
    }

    /**
     * Builds a successful result.
     *
     * @param recommendation majority recommendation
     * @param votes raw votes
     * @return success record
     */
    public static ConsensusResult success(String recommendation, Map<String, String> votes) {
        return new ConsensusResult(true, recommendation, votes);
    }

    /**
     * Builds a failed result.
     *
     * @param votes raw votes
     * @return failure record
     */
    public static ConsensusResult failure(Map<String, String> votes) {
        return new ConsensusResult(false, "CONSENSUS_FAILURE", votes);
    }

    /**
     * Encodes multiple options for one vote.
     *
     * @param options recommendation options
     * @return canonical encoded list
     */
    public static String encodeVote(List<RecommendationOption> options) {
        return RecommendationEngine.encode(options);
    }
}

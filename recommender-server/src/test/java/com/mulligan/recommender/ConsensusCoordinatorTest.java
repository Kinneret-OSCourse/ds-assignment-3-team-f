package com.mulligan.recommender;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsensusCoordinatorTest {

    @Test
    void majorityVoteAcceptsTwoMatchingLists() {
        ConsensusResult result = decide(votes("server1", "S003;1", "server2", "S004;1", "server3", "S003;1"));

        assertTrue(result.success());
        assertEquals("S003;1", result.recommendation());
    }

    @Test
    void noAgreementFails() {
        ConsensusResult result = decide(votes("server1", "S003;1", "server2", "S004;1", "server3", "S005;1"));

        assertFalse(result.success());
    }

    @Test
    void missingResponseStillAllowsMajorityOfConfiguredCluster() {
        ConsensusResult result = decide(votes("server1", "S003;1", "server3", "S003;1"));

        assertTrue(result.success());
        assertEquals("S003;1", result.recommendation());
    }

    @Test
    void twoMissingResponsesFail() {
        ConsensusResult result = decide(votes("server1", "S003;1"));

        assertFalse(result.success());
    }

    private static ConsensusResult decide(Map<String, String> votes) {
        int requiredMajority = 2;
        return votes.values().stream()
                .distinct()
                .filter(vote -> votes.values().stream().filter(vote::equals).count() >= requiredMajority)
                .findFirst()
                .map(vote -> ConsensusResult.success(vote, votes))
                .orElseGet(() -> ConsensusResult.failure(votes));
    }

    private static Map<String, String> votes(String k1, String v1) {
        Map<String, String> votes = new LinkedHashMap<>();
        votes.put(k1, v1);
        return votes;
    }

    private static Map<String, String> votes(String k1, String v1, String k2, String v2) {
        Map<String, String> votes = votes(k1, v1);
        votes.put(k2, v2);
        return votes;
    }

    private static Map<String, String> votes(String k1, String v1, String k2, String v2, String k3, String v3) {
        Map<String, String> votes = votes(k1, v1, k2, v2);
        votes.put(k3, v3);
        return votes;
    }
}

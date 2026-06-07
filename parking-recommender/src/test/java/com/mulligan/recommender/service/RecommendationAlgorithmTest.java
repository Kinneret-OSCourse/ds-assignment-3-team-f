package com.mulligan.recommender.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationAlgorithmTest {

    @Test
    void returnsRequestedSpaceWhenItHasMinimumCitationCount() {
        var result = RecommendationAlgorithm.choose("S003", List.of(
                new SpaceSnapshot("S001", 10, true),
                new SpaceSnapshot("S002", 5, true),
                new SpaceSnapshot("S003", 1, true),
                new SpaceSnapshot("S004", 5, true)
        ));

        assertEquals("S003;1", result.get(0).compact());
    }

    @Test
    void returnsNearestTieWhenRequestedSpaceIsNotBest() {
        var result = RecommendationAlgorithm.choose("S003", List.of(
                new SpaceSnapshot("S001", 10, true),
                new SpaceSnapshot("S002", 3, true),
                new SpaceSnapshot("S003", 7, true),
                new SpaceSnapshot("S004", 3, true),
                new SpaceSnapshot("S006", 3, true)
        ));

        assertEquals(List.of("S002;3", "S004;3"), result.stream().map(option -> option.compact()).toList());
    }

    @Test
    void ignoresBusySpacesAndReturnsEmptyWhenAllBusy() {
        var result = RecommendationAlgorithm.choose("S003", List.of(
                new SpaceSnapshot("S001", 2, false),
                new SpaceSnapshot("S002", 1, false)
        ));

        assertTrue(result.isEmpty());
    }
}

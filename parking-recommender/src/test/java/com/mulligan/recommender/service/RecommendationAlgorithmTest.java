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

    @Test
    void matchesAssignmentImageMinimumCitationExamples() {
        assertEquals(List.of("S003;1"), compact(RecommendationAlgorithm.choose("S003", List.of(
                space(1, 10, true), space(2, 5, true), space(3, 1, true),
                space(4, 5, true), space(5, 3, true), space(6, 3, true)
        ))));

        assertEquals(List.of("S003;3"), compact(RecommendationAlgorithm.choose("S003", List.of(
                space(1, 10, true), space(2, 5, true), space(3, 3, true),
                space(4, 5, true), space(5, 3, true), space(6, 3, true)
        ))));

        assertEquals(List.of("S005;3"), compact(RecommendationAlgorithm.choose("S003", List.of(
                space(1, 10, true), space(2, 5, true), space(3, 7, true),
                space(4, 5, true), space(5, 3, true), space(6, 3, true)
        ))));

        assertEquals(List.of("S002;3", "S004;3"), compact(RecommendationAlgorithm.choose("S003", List.of(
                space(1, 10, true), space(2, 3, true), space(3, 7, true),
                space(4, 3, true), space(5, 5, true), space(6, 3, true)
        ))));
    }

    @Test
    void matchesAssignmentImageBusySpaceExamples() {
        assertEquals(List.of("S002;1"), compact(RecommendationAlgorithm.choose("S003", List.of(
                space(1, 2, true), space(2, 1, true), space(3, 2, true),
                space(4, 2, true), space(5, 2, true), space(6, 3, true)
        ))));

        assertEquals(List.of("S003;2"), compact(RecommendationAlgorithm.choose("S003", List.of(
                space(1, 2, true), space(2, 1, false), space(3, 2, true),
                space(4, 2, true), space(5, 2, true), space(6, 3, true)
        ))));

        assertEquals(List.of("S004;2"), compact(RecommendationAlgorithm.choose("S003", List.of(
                space(1, 2, true), space(2, 1, false), space(3, 2, false),
                space(4, 2, true), space(5, 2, true), space(6, 3, true)
        ))));

        assertEquals(List.of("S001;2", "S005;2"), compact(RecommendationAlgorithm.choose("S003", List.of(
                space(1, 2, true), space(2, 1, false), space(3, 2, false),
                space(4, 2, false), space(5, 2, true), space(6, 3, true)
        ))));

        assertEquals(List.of("S001;10"), compact(RecommendationAlgorithm.choose("S003", List.of(
                space(1, 10, true), space(2, 1, false), space(3, 2, false),
                space(4, 2, false), space(5, 2, false), space(6, 3, false)
        ))));
    }

    private static SpaceSnapshot space(int number, int citations, boolean available) {
        return new SpaceSnapshot("S%03d".formatted(number), citations, available);
    }

    private static List<String> compact(List<com.mulligan.recommender.model.RecommendationOption> options) {
        return options.stream().map(option -> option.compact()).toList();
    }
}

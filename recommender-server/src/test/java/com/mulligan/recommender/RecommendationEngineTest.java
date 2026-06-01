package com.mulligan.recommender;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecommendationEngineTest {

    @Test
    void returnsRequestedSpaceWhenItHasMinimumCitations() {
        RecommendationEngine engine = engine(List.of(10, 5, 1, 5, 3, 3), List.of(false, false, false, false, false, false));

        assertEquals("S003;1", RecommendationEngine.encode(engine.recommend("S003")));
    }

    @Test
    void returnsNearestMinimumCitationSpaceWhenRequestedIsWorse() {
        RecommendationEngine engine = engine(List.of(10, 5, 7, 5, 3, 3), List.of(false, false, false, false, false, false));

        assertEquals("S005;3", RecommendationEngine.encode(engine.recommend("S003")));
    }

    @Test
    void returnsBothNearestTies() {
        RecommendationEngine engine = engine(List.of(10, 3, 7, 3, 5, 3), List.of(false, false, false, false, false, false));

        assertEquals("S002;3,S004;3", RecommendationEngine.encode(engine.recommend("S003")));
    }

    @Test
    void ignoresBusySpacesAndReturnsNearestAvailableMinimum() {
        RecommendationEngine engine = engine(List.of(2, 1, 2, 2, 2, 3), List.of(false, true, true, false, false, false));

        assertEquals("S004;2", RecommendationEngine.encode(engine.recommend("S003")));
    }

    @Test
    void returnsEmptyWhenNoSpacesAreAvailable() {
        RecommendationEngine engine = engine(List.of(2, 1, 2, 2, 2, 3), List.of(true, true, true, true, true, true));

        assertEquals("EMPTY", RecommendationEngine.encode(engine.recommend("S003")));
    }

    private static RecommendationEngine engine(List<Integer> citations, List<Boolean> busy) {
        return new RecommendationEngine(() -> {
            java.util.ArrayList<ParkingSpaceSnapshot> spaces = new java.util.ArrayList<>();
            for (int i = 0; i < citations.size(); i++) {
                spaces.add(new ParkingSpaceSnapshot("S%03d".formatted(i + 1), "A", citations.get(i), !busy.get(i)));
            }
            return spaces;
        });
    }
}

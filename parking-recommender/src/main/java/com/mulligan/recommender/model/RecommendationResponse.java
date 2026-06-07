package com.mulligan.recommender.model;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Result returned by the recommender consensus path.
 *
 * @param success true when a majority agreed on the exact recommendation list
 * @param message user-facing status message
 * @param options agreed recommendation list, empty when no spaces or no consensus
 */
public record RecommendationResponse(boolean success, String message, List<RecommendationOption> options) {

    /**
     * Builds a successful response.
     *
     * @param options recommendation options
     * @return success response
     */
    public static RecommendationResponse ok(List<RecommendationOption> options) {
        return new RecommendationResponse(true, "Consensus reached.", List.copyOf(options));
    }

    /**
     * Builds a failure response.
     *
     * @param message reason shown to the caller
     * @return failure response
     */
    public static RecommendationResponse fail(String message) {
        return new RecommendationResponse(false, message, List.of());
    }

    /**
     * Converts options to the assignment examples' text format.
     *
     * @return empty list label or comma-separated compact options
     */
    public String compactOptions() {
        if (options.isEmpty()) {
            return "Empty List";
        }
        return options.stream()
                .map(RecommendationOption::compact)
                .collect(Collectors.joining(", "));
    }
}

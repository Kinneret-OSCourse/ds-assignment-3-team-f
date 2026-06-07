package com.mulligan.recommender.model;

/**
 * Immutable recommendation row containing the suggested parking space and its
 * historical citation count.
 *
 * @param spaceNumber human-visible parking space number
 * @param citationCount number of citations previously issued for the space
 */
public record RecommendationOption(String spaceNumber, int citationCount) implements Comparable<RecommendationOption> {

    /**
     * Orders options consistently for consensus comparisons and UI display.
     *
     * @param other option being compared
     * @return natural ordering by citation count and then space text
     */
    @Override
    public int compareTo(RecommendationOption other) {
        int byCitation = Integer.compare(citationCount, other.citationCount);
        if (byCitation != 0) {
            return byCitation;
        }
        return spaceNumber.compareTo(other.spaceNumber);
    }

    /**
     * Formats a result using the assignment's "space;citations" notation.
     *
     * @return compact assignment-compatible string
     */
    public String compact() {
        return spaceNumber + ";" + citationCount;
    }
}

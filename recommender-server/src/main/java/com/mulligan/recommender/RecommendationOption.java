package com.mulligan.recommender;

/**
 * A parking-space recommendation returned by one recommender node.
 *
 * @param spaceNumber parking space identifier
 * @param citationCount number of citations issued for that space
 */
public record RecommendationOption(String spaceNumber, int citationCount) {

    /**
     * Encodes the option in the assignment format.
     *
     * @return {@code space;citationCount}
     */
    public String encode() {
        return spaceNumber + ";" + citationCount;
    }
}

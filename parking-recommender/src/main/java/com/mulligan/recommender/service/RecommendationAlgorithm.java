package com.mulligan.recommender.service;

import com.mulligan.recommender.model.RecommendationOption;

import java.util.Comparator;
import java.util.List;

/**
 * Pure implementation of Assignment 3's minimum-citation recommendation rule.
 */
public final class RecommendationAlgorithm {

    private RecommendationAlgorithm() {
    }

    /**
     * Chooses the best available spaces in the same zone as the requested
     * space: minimum citation count first, requested space wins ties when it is
     * in that minimum set, otherwise nearest numeric distance wins with ties
     * preserved.
     *
     * @param requestedSpace requested parking space number
     * @param sameZoneSpaces all active spaces in the requested space's zone
     * @return recommended spaces, or an empty list when none are available
     */
    public static List<RecommendationOption> choose(String requestedSpace, List<SpaceSnapshot> sameZoneSpaces) {
        List<SpaceSnapshot> available = sameZoneSpaces.stream()
                .filter(SpaceSnapshot::available)
                .toList();
        if (available.isEmpty()) {
            return List.of();
        }

        int minCitations = available.stream()
                .mapToInt(SpaceSnapshot::citationCount)
                .min()
                .orElse(0);

        List<SpaceSnapshot> minCitationSpaces = available.stream()
                .filter(space -> space.citationCount() == minCitations)
                .toList();

        for (SpaceSnapshot space : minCitationSpaces) {
            if (space.spaceNumber().equalsIgnoreCase(requestedSpace)) {
                return List.of(new RecommendationOption(space.spaceNumber(), space.citationCount()));
            }
        }

        int requestedNumber = numericPart(requestedSpace);
        int nearestDistance = minCitationSpaces.stream()
                .mapToInt(space -> Math.abs(numericPart(space.spaceNumber()) - requestedNumber))
                .min()
                .orElse(0);

        return minCitationSpaces.stream()
                .filter(space -> Math.abs(numericPart(space.spaceNumber()) - requestedNumber) == nearestDistance)
                .map(space -> new RecommendationOption(space.spaceNumber(), space.citationCount()))
                .sorted(Comparator.comparing(RecommendationOption::spaceNumber))
                .toList();
    }

    /**
     * Extracts the numeric portion of a parking space identifier.
     *
     * @param spaceNumber parking space number such as S003
     * @return parsed number, or a stable lexical fallback when no digits exist
     */
    static int numericPart(String spaceNumber) {
        String digits = spaceNumber == null ? "" : spaceNumber.replaceAll("\\D+", "");
        if (digits.isBlank()) {
            return Math.abs(String.valueOf(spaceNumber).hashCode());
        }
        return Integer.parseInt(digits);
    }
}

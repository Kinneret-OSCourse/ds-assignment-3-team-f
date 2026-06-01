package com.mulligan.recommender;

import com.mulligan.common.validation.InputValidator;
import com.mulligan.common.validation.ValidationException;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Implements Assignment 3's minimum-citation, nearest-distance recommendation rule.
 */
public class RecommendationEngine {

    private final ParkingSnapshotProvider snapshotProvider;

    /**
     * Creates a recommendation engine over the supplied snapshot provider.
     *
     * @param snapshotProvider source of parking-space state
     */
    public RecommendationEngine(ParkingSnapshotProvider snapshotProvider) {
        this.snapshotProvider = Objects.requireNonNull(snapshotProvider);
    }

    /**
     * Recommends the best available spaces in the requested space's zone.
     *
     * @param requestedSpaceNumber customer's desired parking space
     * @return zero, one, or more recommended spaces
     */
    public List<RecommendationOption> recommend(String requestedSpaceNumber) {
        String requested = validateSpace(requestedSpaceNumber);
        List<ParkingSpaceSnapshot> allSpaces = snapshotProvider.loadSpaces();
        ParkingSpaceSnapshot requestedSpace = allSpaces.stream()
                .filter(space -> space.spaceNumber().equalsIgnoreCase(requested))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid parking space."));

        List<ParkingSpaceSnapshot> availableInZone = allSpaces.stream()
                .filter(ParkingSpaceSnapshot::available)
                .filter(space -> space.zoneCode().equals(requestedSpace.zoneCode()))
                .toList();
        if (availableInZone.isEmpty()) {
            return List.of();
        }

        int minimumCitations = availableInZone.stream()
                .mapToInt(ParkingSpaceSnapshot::citationCount)
                .min()
                .orElseThrow();
        List<ParkingSpaceSnapshot> minimumCitationSpaces = availableInZone.stream()
                .filter(space -> space.citationCount() == minimumCitations)
                .toList();

        if (requestedSpace.available() && requestedSpace.citationCount() == minimumCitations) {
            return List.of(toOption(requestedSpace));
        }

        int requestedOrdinal = numericOrdinal(requested);
        int nearestDistance = minimumCitationSpaces.stream()
                .mapToInt(space -> Math.abs(numericOrdinal(space.spaceNumber()) - requestedOrdinal))
                .min()
                .orElse(0);

        return minimumCitationSpaces.stream()
                .filter(space -> Math.abs(numericOrdinal(space.spaceNumber()) - requestedOrdinal) == nearestDistance)
                .sorted(Comparator.comparingInt(space -> numericOrdinal(space.spaceNumber())))
                .map(RecommendationEngine::toOption)
                .toList();
    }

    /**
     * Encodes a recommendation list in the assignment's comparison format.
     *
     * @param options recommendation list
     * @return comma-separated {@code space;citationCount} list, or {@code EMPTY}
     */
    public static String encode(List<RecommendationOption> options) {
        if (options == null || options.isEmpty()) {
            return "EMPTY";
        }
        return options.stream()
                .map(RecommendationOption::encode)
                .reduce((left, right) -> left + "," + right)
                .orElse("EMPTY");
    }

    /**
     * Produces a deterministic incorrect answer for malicious-mode testing.
     *
     * @param honestResult result that would have been returned normally
     * @return intentionally wrong result
     */
    public static String maliciousEncode(List<RecommendationOption> honestResult) {
        if (honestResult == null || honestResult.isEmpty()) {
            return "MALICIOUS;999";
        }
        RecommendationOption first = honestResult.get(0);
        return first.spaceNumber() + ";" + (first.citationCount() + 999);
    }

    private static RecommendationOption toOption(ParkingSpaceSnapshot space) {
        return new RecommendationOption(space.spaceNumber(), space.citationCount());
    }

    private static String validateSpace(String requestedSpaceNumber) {
        try {
            return InputValidator.requireSpace(requestedSpaceNumber);
        } catch (ValidationException e) {
            throw new IllegalArgumentException("Invalid parking space.");
        }
    }

    private static int numericOrdinal(String value) {
        String digits = value.replaceAll("\\D", "");
        if (digits.isBlank()) {
            return Math.abs(value.toUpperCase().hashCode());
        }
        return Integer.parseInt(digits);
    }
}

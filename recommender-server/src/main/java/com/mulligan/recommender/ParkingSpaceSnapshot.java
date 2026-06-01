package com.mulligan.recommender;

/**
 * Read model used by the recommendation algorithm.
 *
 * @param spaceNumber parking space identifier
 * @param zoneCode zone containing the space
 * @param citationCount number of issued citations
 * @param available true when the space is active and not currently occupied
 */
public record ParkingSpaceSnapshot(String spaceNumber, String zoneCode, int citationCount, boolean available) {
}

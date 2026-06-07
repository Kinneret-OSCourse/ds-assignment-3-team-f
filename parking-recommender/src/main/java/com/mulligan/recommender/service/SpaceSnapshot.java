package com.mulligan.recommender.service;

/**
 * Snapshot of one parking space used by the pure recommendation algorithm.
 *
 * @param spaceNumber human-visible parking space number
 * @param citationCount citation count for the space
 * @param available true when no active parking event currently occupies it
 */
public record SpaceSnapshot(String spaceNumber, int citationCount, boolean available) {
}

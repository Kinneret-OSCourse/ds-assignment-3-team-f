package com.mulligan.recommender;

import java.util.List;

/**
 * Supplies the current parking-space state to a recommender node.
 */
public interface ParkingSnapshotProvider {

    /**
     * Loads all spaces in the same logical dataset.
     *
     * @return current parking spaces, citation counts, and availability
     */
    List<ParkingSpaceSnapshot> loadSpaces();
}

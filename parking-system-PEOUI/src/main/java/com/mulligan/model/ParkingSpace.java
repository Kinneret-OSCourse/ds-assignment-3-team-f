package com.mulligan.model;

/**
 * Main type definition for ParkingSpace.
 */
public class ParkingSpace {
    private String spaceId;
    private String zoneId;
    private boolean active;

    /**
     * Creates a new ParkingSpace instance.
     * @param spaceId input value for the constructor
     * @param zoneId input value for the constructor
     * @param active input value for the constructor
     */
    public ParkingSpace(String spaceId, String zoneId, boolean active) {
        this.spaceId = spaceId;
        this.zoneId = zoneId;
        this.active = active;
    }

    /**
     * Executes the getSpaceId operation.
     * @return operation result
     */
    public String getSpaceId() {
        return spaceId;
    }

    /**
     * Executes the setSpaceId operation.
     * @param spaceId input value used by the operation
     */
    public void setSpaceId(String spaceId) {
        this.spaceId = spaceId;
    }

    /**
     * Executes the getZoneId operation.
     * @return operation result
     */
    public String getZoneId() {
        return zoneId;
    }

    /**
     * Executes the setZoneId operation.
     * @param zoneId input value used by the operation
     */
    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    /**
     * Executes the isActive operation.
     * @return operation result
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Executes the setActive operation.
     * @param active input value used by the operation
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    /**
     * Executes the toString operation.
     * @return operation result
     */
    public String toString() {
        return "ParkingSpace{" +
                "spaceId='" + spaceId + '\'' +
                ", zoneId='" + zoneId + '\'' +
                ", active=" + active +
                '}';
    }
}

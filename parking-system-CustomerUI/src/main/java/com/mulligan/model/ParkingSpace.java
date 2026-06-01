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

    public String getSpaceId() { return spaceId; }
    public void setSpaceId(String spaceId) { this.spaceId = spaceId; }

    public String getZoneId() { return zoneId; }
    public void setZoneId(String zoneId) { this.zoneId = zoneId; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

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

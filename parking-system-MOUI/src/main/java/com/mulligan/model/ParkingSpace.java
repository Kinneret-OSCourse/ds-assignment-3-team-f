package com.mulligan.model;

public class ParkingSpace {
    private String spaceId;
    private String zoneId;
    private boolean active;

    public ParkingSpace(String spaceId, String zoneId, boolean active) {
        this.spaceId = spaceId;
        this.zoneId = zoneId;
        this.active = active;
    }

    public String getSpaceId() {
        return spaceId;
    }

    public void setSpaceId(String spaceId) {
        this.spaceId = spaceId;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public String toString() {
        return "ParkingSpace{" +
                "spaceId='" + spaceId + '\'' +
                ", zoneId='" + zoneId + '\'' +
                ", active=" + active +
                '}';
    }
}
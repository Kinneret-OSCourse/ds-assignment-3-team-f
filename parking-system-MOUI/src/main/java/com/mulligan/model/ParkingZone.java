package com.mulligan.model;

public class ParkingZone {
    private String zoneId;
    private String zoneName;
    private double pricePerHour;
    private int maxAllowedMinutes;

    public ParkingZone(String zoneId, String zoneName, double pricePerHour, int maxAllowedMinutes) {
        this.zoneId = zoneId;
        this.zoneName = zoneName;
        this.pricePerHour = pricePerHour;
        this.maxAllowedMinutes = maxAllowedMinutes;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public String getZoneName() {
        return zoneName;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    public double getPricePerHour() {
        return pricePerHour;
    }

    public void setPricePerHour(double pricePerHour) {
        this.pricePerHour = pricePerHour;
    }

    public int getMaxAllowedMinutes() {
        return maxAllowedMinutes;
    }

    public void setMaxAllowedMinutes(int maxAllowedMinutes) {
        this.maxAllowedMinutes = maxAllowedMinutes;
    }

    @Override
    public String toString() {
        return "ParkingZone{" +
                "zoneId='" + zoneId + '\'' +
                ", zoneName='" + zoneName + '\'' +
                ", pricePerHour=" + pricePerHour +
                ", maxAllowedMinutes=" + maxAllowedMinutes +
                '}';
    }
}
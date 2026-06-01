package com.mulligan.model;

/**
 * Main type definition for ParkingZone.
 */
public class ParkingZone {
    private String zoneId;
    private String zoneName;
    private double pricePerHour;
    private int maxAllowedMinutes;

    /**
     * Creates a new ParkingZone instance.
     * @param zoneId input value for the constructor
     * @param zoneName input value for the constructor
     * @param pricePerHour input value for the constructor
     * @param maxAllowedMinutes input value for the constructor
     */
    public ParkingZone(String zoneId, String zoneName, double pricePerHour, int maxAllowedMinutes) {
        this.zoneId = zoneId;
        this.zoneName = zoneName;
        this.pricePerHour = pricePerHour;
        this.maxAllowedMinutes = maxAllowedMinutes;
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
     * Executes the getZoneName operation.
     * @return operation result
     */
    public String getZoneName() {
        return zoneName;
    }

    /**
     * Executes the setZoneName operation.
     * @param zoneName input value used by the operation
     */
    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    /**
     * Executes the getPricePerHour operation.
     * @return operation result
     */
    public double getPricePerHour() {
        return pricePerHour;
    }

    /**
     * Executes the setPricePerHour operation.
     * @param pricePerHour input value used by the operation
     */
    public void setPricePerHour(double pricePerHour) {
        this.pricePerHour = pricePerHour;
    }

    /**
     * Executes the getMaxAllowedMinutes operation.
     * @return operation result
     */
    public int getMaxAllowedMinutes() {
        return maxAllowedMinutes;
    }

    /**
     * Executes the setMaxAllowedMinutes operation.
     * @param maxAllowedMinutes input value used by the operation
     */
    public void setMaxAllowedMinutes(int maxAllowedMinutes) {
        this.maxAllowedMinutes = maxAllowedMinutes;
    }

    @Override
    /**
     * Executes the toString operation.
     * @return operation result
     */
    public String toString() {
        return "ParkingZone{" +
                "zoneId='" + zoneId + '\'' +
                ", zoneName='" + zoneName + '\'' +
                ", pricePerHour=" + pricePerHour +
                ", maxAllowedMinutes=" + maxAllowedMinutes +
                '}';
    }
}

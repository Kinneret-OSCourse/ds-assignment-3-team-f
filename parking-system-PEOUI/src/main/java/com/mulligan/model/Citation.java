package com.mulligan.model;

import java.time.LocalDateTime;

/**
 * Main type definition for Citation.
 */
public class Citation {
    private String citationId;
    private String vehicleNumber;
    private String parkingSpaceId;
    private String parkingZoneId;
    private LocalDateTime inspectionTime;
    private double amount;

    public Citation(String citationId, String vehicleNumber, String parkingSpaceId,
                    String parkingZoneId, LocalDateTime inspectionTime, double amount) {
        this.citationId = citationId;
        this.vehicleNumber = vehicleNumber;
        this.parkingSpaceId = parkingSpaceId;
        this.parkingZoneId = parkingZoneId;
        this.inspectionTime = inspectionTime;
        this.amount = amount;
    }

    /**
     * Executes the getCitationId operation.
     * @return operation result
     */
    public String getCitationId() {
        return citationId;
    }

    /**
     * Executes the setCitationId operation.
     * @param citationId input value used by the operation
     */
    public void setCitationId(String citationId) {
        this.citationId = citationId;
    }

    /**
     * Executes the getVehicleNumber operation.
     * @return operation result
     */
    public String getVehicleNumber() {
        return vehicleNumber;
    }

    /**
     * Executes the setVehicleNumber operation.
     * @param vehicleNumber input value used by the operation
     */
    public void setVehicleNumber(String vehicleNumber) {
        this.vehicleNumber = vehicleNumber;
    }

    /**
     * Executes the getParkingSpaceId operation.
     * @return operation result
     */
    public String getParkingSpaceId() {
        return parkingSpaceId;
    }

    /**
     * Executes the setParkingSpaceId operation.
     * @param parkingSpaceId input value used by the operation
     */
    public void setParkingSpaceId(String parkingSpaceId) {
        this.parkingSpaceId = parkingSpaceId;
    }

    /**
     * Executes the getParkingZoneId operation.
     * @return operation result
     */
    public String getParkingZoneId() {
        return parkingZoneId;
    }

    /**
     * Executes the setParkingZoneId operation.
     * @param parkingZoneId input value used by the operation
     */
    public void setParkingZoneId(String parkingZoneId) {
        this.parkingZoneId = parkingZoneId;
    }

    /**
     * Executes the getInspectionTime operation.
     * @return operation result
     */
    public LocalDateTime getInspectionTime() {
        return inspectionTime;
    }

    /**
     * Executes the setInspectionTime operation.
     * @param inspectionTime input value used by the operation
     */
    public void setInspectionTime(LocalDateTime inspectionTime) {
        this.inspectionTime = inspectionTime;
    }

    /**
     * Executes the getAmount operation.
     * @return operation result
     */
    public double getAmount() {
        return amount;
    }

    /**
     * Executes the setAmount operation.
     * @param amount input value used by the operation
     */
    public void setAmount(double amount) {
        this.amount = amount;
    }

    @Override
    /**
     * Executes the toString operation.
     * @return operation result
     */
    public String toString() {
        return "Citation{" +
                "citationId='" + citationId + '\'' +
                ", vehicleNumber='" + vehicleNumber + '\'' +
                ", parkingSpaceId='" + parkingSpaceId + '\'' +
                ", parkingZoneId='" + parkingZoneId + '\'' +
                ", inspectionTime=" + inspectionTime +
                ", amount=" + amount +
                '}';
    }
}

package com.mulligan.model;

import java.time.LocalDateTime;

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

    public String getCitationId() {
        return citationId;
    }

    public void setCitationId(String citationId) {
        this.citationId = citationId;
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }

    public void setVehicleNumber(String vehicleNumber) {
        this.vehicleNumber = vehicleNumber;
    }

    public String getParkingSpaceId() {
        return parkingSpaceId;
    }

    public void setParkingSpaceId(String parkingSpaceId) {
        this.parkingSpaceId = parkingSpaceId;
    }

    public String getParkingZoneId() {
        return parkingZoneId;
    }

    public void setParkingZoneId(String parkingZoneId) {
        this.parkingZoneId = parkingZoneId;
    }

    public LocalDateTime getInspectionTime() {
        return inspectionTime;
    }

    public void setInspectionTime(LocalDateTime inspectionTime) {
        this.inspectionTime = inspectionTime;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    @Override
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

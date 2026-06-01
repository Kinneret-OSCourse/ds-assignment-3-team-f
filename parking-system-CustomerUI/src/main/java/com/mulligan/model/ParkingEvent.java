package com.mulligan.model;

import java.time.LocalDateTime;

/**
 * Main type definition for ParkingEvent.
 */
public class ParkingEvent {
    private String eventId;
    private String customerId;
    private String vehicleNumber;
    private String parkingSpaceId;
    private String parkingZoneId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private double amount;

    public ParkingEvent(String eventId, String customerId, String vehicleNumber,
                        String parkingSpaceId, String parkingZoneId,
                        LocalDateTime startTime, LocalDateTime endTime, String status) {
        this(eventId, customerId, vehicleNumber, parkingSpaceId, parkingZoneId, startTime, endTime, status, 0);
    }

    public ParkingEvent(String eventId, String customerId, String vehicleNumber,
                        String parkingSpaceId, String parkingZoneId,
                        LocalDateTime startTime, LocalDateTime endTime, String status, double amount) {
        this.eventId = eventId;
        this.customerId = customerId;
        this.vehicleNumber = vehicleNumber;
        this.parkingSpaceId = parkingSpaceId;
        this.parkingZoneId = parkingZoneId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.amount = amount;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getVehicleNumber() { return vehicleNumber; }
    public void setVehicleNumber(String vehicleNumber) { this.vehicleNumber = vehicleNumber; }

    public String getParkingSpaceId() { return parkingSpaceId; }
    public void setParkingSpaceId(String parkingSpaceId) { this.parkingSpaceId = parkingSpaceId; }

    public String getParkingZoneId() { return parkingZoneId; }
    public void setParkingZoneId(String parkingZoneId) { this.parkingZoneId = parkingZoneId; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    /**
     * Executes the isActive operation.
     * @return operation result
     */
    public boolean isActive() {
        return endTime == null && "STARTED".equalsIgnoreCase(status);
    }

    @Override
    /**
     * Executes the toString operation.
     * @return operation result
     */
    public String toString() {
        return "ParkingEvent{" +
                "eventId='" + eventId + '\'' +
                ", customerId='" + customerId + '\'' +
                ", vehicleNumber='" + vehicleNumber + '\'' +
                ", parkingSpaceId='" + parkingSpaceId + '\'' +
                ", parkingZoneId='" + parkingZoneId + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", status='" + status + '\'' +
                ", amount=" + amount +
                '}';
    }
}

package com.mulligan.mo.model;

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

    public ParkingEvent(String eventId, String customerId, String vehicleNumber,
                        String parkingSpaceId, String parkingZoneId,
                        LocalDateTime startTime, LocalDateTime endTime, String status) {
        this.eventId = eventId;
        this.customerId = customerId;
        this.vehicleNumber = vehicleNumber;
        this.parkingSpaceId = parkingSpaceId;
        this.parkingZoneId = parkingZoneId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
    }

    /**
     * Executes the getEventId operation.
     * @return operation result
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * Executes the setEventId operation.
     * @param eventId input value used by the operation
     */
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    /**
     * Executes the getCustomerId operation.
     * @return operation result
     */
    public String getCustomerId() {
        return customerId;
    }

    /**
     * Executes the setCustomerId operation.
     * @param customerId input value used by the operation
     */
    public void setCustomerId(String customerId) {
        this.customerId = customerId;
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
     * Executes the getStartTime operation.
     * @return operation result
     */
    public LocalDateTime getStartTime() {
        return startTime;
    }

    /**
     * Executes the setStartTime operation.
     * @param startTime input value used by the operation
     */
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    /**
     * Executes the getEndTime operation.
     * @return operation result
     */
    public LocalDateTime getEndTime() {
        return endTime;
    }

    /**
     * Executes the setEndTime operation.
     * @param endTime input value used by the operation
     */
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    /**
     * Executes the getStatus operation.
     * @return operation result
     */
    public String getStatus() {
        return status;
    }

    /**
     * Executes the setStatus operation.
     * @param status input value used by the operation
     */
    public void setStatus(String status) {
        this.status = status;
    }

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
                '}';
    }
}

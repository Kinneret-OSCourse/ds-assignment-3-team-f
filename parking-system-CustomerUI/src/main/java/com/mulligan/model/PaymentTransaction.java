package com.mulligan.model;

import java.time.LocalDateTime;

/**
 * Main type definition for PaymentTransaction.
 */
public class PaymentTransaction {
    private String transactionId;
    private String vehicleNumber;
    private String parkingSpaceId;
    private String parkingZoneId;
    private LocalDateTime startTime;
    private LocalDateTime stopTime;
    private double amount;

    public PaymentTransaction(String transactionId, String vehicleNumber, String parkingSpaceId,
                              String parkingZoneId, LocalDateTime startTime,
                              LocalDateTime stopTime, double amount) {
        this.transactionId = transactionId;
        this.vehicleNumber = vehicleNumber;
        this.parkingSpaceId = parkingSpaceId;
        this.parkingZoneId = parkingZoneId;
        this.startTime = startTime;
        this.stopTime = stopTime;
        this.amount = amount;
    }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getVehicleNumber() { return vehicleNumber; }
    public void setVehicleNumber(String vehicleNumber) { this.vehicleNumber = vehicleNumber; }

    public String getParkingSpaceId() { return parkingSpaceId; }
    public void setParkingSpaceId(String parkingSpaceId) { this.parkingSpaceId = parkingSpaceId; }

    public String getParkingZoneId() { return parkingZoneId; }
    public void setParkingZoneId(String parkingZoneId) { this.parkingZoneId = parkingZoneId; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getStopTime() { return stopTime; }
    public void setStopTime(LocalDateTime stopTime) { this.stopTime = stopTime; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    @Override
    /**
     * Executes the toString operation.
     * @return operation result
     */
    public String toString() {
        return "PaymentTransaction{" +
                "transactionId='" + transactionId + '\'' +
                ", vehicleNumber='" + vehicleNumber + '\'' +
                ", parkingSpaceId='" + parkingSpaceId + '\'' +
                ", parkingZoneId='" + parkingZoneId + '\'' +
                ", startTime=" + startTime +
                ", stopTime=" + stopTime +
                ", amount=" + amount +
                '}';
    }
}

package com.mulligan.mo.model;

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

    /**
     * Executes the getTransactionId operation.
     * @return operation result
     */
    public String getTransactionId() {
        return transactionId;
    }

    /**
     * Executes the setTransactionId operation.
     * @param transactionId input value used by the operation
     */
    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
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
     * Executes the getStopTime operation.
     * @return operation result
     */
    public LocalDateTime getStopTime() {
        return stopTime;
    }

    /**
     * Executes the setStopTime operation.
     * @param stopTime input value used by the operation
     */
    public void setStopTime(LocalDateTime stopTime) {
        this.stopTime = stopTime;
    }

    /**
     * Executes the getAmount operation.
     * @return operation result
     */
    public double getAmount() {
        return amount;
    }


    public String getDisplayStopTime() {
        if (this.stopTime == null) {
            return "";
        }
        return this.stopTime.toString();
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

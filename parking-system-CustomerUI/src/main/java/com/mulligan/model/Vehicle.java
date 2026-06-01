package com.mulligan.model;

/**
 * Main type definition for Vehicle.
 */
public class Vehicle {
    private String vehicleNumber;
    private String customerId;

    /**
     * Creates a new Vehicle instance.
     * @param vehicleNumber input value for the constructor
     * @param customerId input value for the constructor
     */
    public Vehicle(String vehicleNumber, String customerId) {
        this.vehicleNumber = vehicleNumber;
        this.customerId = customerId;
    }

    public String getVehicleNumber() { return vehicleNumber; }
    public void setVehicleNumber(String vehicleNumber) { this.vehicleNumber = vehicleNumber; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    @Override
    /**
     * Executes the toString operation.
     * @return operation result
     */
    public String toString() {
        return "Vehicle{" +
                "vehicleNumber='" + vehicleNumber + '\'' +
                ", customerId='" + customerId + '\'' +
                '}';
    }
}

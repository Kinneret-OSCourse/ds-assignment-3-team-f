package com.mulligan.mo.model;

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

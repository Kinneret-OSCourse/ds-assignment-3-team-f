package com.mulligan.customer.controller;

import com.mulligan.model.ParkingEvent;
import com.mulligan.model.PaymentTransaction;
import com.mulligan.service.CustomerAuthService;
import com.mulligan.service.ParkingService;

import java.util.List;

/**
 * Purpose: Acts as the primary Controller for the Customer UI module. 
 * Orchestrates the communication between the UI layer and the backend Service layer 
 * to manage parking lifecycles, user authentication, and transaction history.
 */
public class CustomerController {

    private final ParkingService parkingService;
    private final CustomerAuthService customerAuthService;

    /**
     * Purpose: Dependency injection constructor for CustomerController.
     * @param parkingService The service layer instance required for parking operations.
     */
    public CustomerController(ParkingService parkingService) {
        this.parkingService = parkingService;
        this.customerAuthService = new CustomerAuthService();
    }

    CustomerController(ParkingService parkingService, CustomerAuthService customerAuthService) {
        this.parkingService = parkingService;
        this.customerAuthService = customerAuthService;
    }

    /**
     * Purpose: Validates customer credentials and initiates a secure session.
     * @param customerId The unique identifier provided by the user.
     * @param password The secret credential for authentication.
     * @return String Returns a success message or a detailed validation error.
     */
    public String login(String customerId, String password) {
        if (customerId == null || customerId.isBlank() || password == null || password.isBlank()) {
            return "Error: Please enter Customer ID and Password.";
        }

        try {
            if (customerAuthService.authenticate(customerId.trim(), password)) {
                if (customerAuthService.getAssignedVehicleNumber(customerId.trim()) == null) {
                    return "Error: No vehicle is assigned to this customer.";
                }
                return "Login successful.";
            }

            return "Error: Invalid Customer ID or Password.";
        } catch (RuntimeException e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Purpose: Orchestrates the 'Start Parking' sequence. Validates inputs and triggers 
     * the creation of a new parking event in the system.
     * @param customerId The ID of the authenticated customer.
     * @param vehicleNumber The license plate of the vehicle being parked.
     * @param spaceId The designated space ID for the parking session.
     * @return String Formatted details of the started event or a caught exception message.
     */
    public String startParking(String customerId, String vehicleNumber, String spaceId) {
        try {
            ParkingEvent event = parkingService.startParking(customerId, vehicleNumber, spaceId);
            return "Parking started successfully.\nEvent ID: " + event.getEventId()
                    + "\nZone: " + event.getParkingZoneId()
                    + "\nStart time: " + event.getStartTime();
        } catch (IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        } catch (RuntimeException e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Purpose: Requests a consensus-backed parking recommendation for the selected space.
     * @param spaceId The desired parking space identifier.
     * @return String Recommendation text or a caught exception message.
     */
    public String recommendParking(String spaceId) {
        try {
            String recommendation = parkingService.recommendParking(spaceId);
            return "Recommended parking: " + recommendation;
        } catch (IllegalArgumentException | IllegalStateException e) {
            return "Error: " + e.getMessage();
        }
    }

    public String getAssignedVehicleNumber(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return null;
        }

        return customerAuthService.getAssignedVehicleNumber(customerId.trim());
    }

    /**
     * Purpose: Finalizes a parking session, calculating final costs and generating 
     * a payment transaction record.
     * @param customerId The ID of the customer ending the session.
     * @param vehicleNumber The license plate associated with the session.
     * @return String Transaction summary including ID and total amount paid.
     */
    public String stopParking(String customerId, String vehicleNumber) {
        try {
            PaymentTransaction transaction = parkingService.stopParking(customerId, vehicleNumber);
            return "Parking stopped successfully.\nTransaction ID: " + transaction.getTransactionId()
                    + "\nAmount paid: " + transaction.getAmount()
                    + "\nStop time: " + transaction.getStopTime();
        } catch (IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        } catch (RuntimeException e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Purpose: Retrieves a historical log of all parking events for audit or UI display.
     * @param customerId The ID of the requesting customer.
     * @param vehicleNumber The specific vehicle to filter the history by.
     * @return List<ParkingEvent> A collection of parking event data objects.
     */
    public List<ParkingEvent> getParkingEvents(String customerId, String vehicleNumber) {
        return parkingService.getParkingEvents(customerId, vehicleNumber);
    }

    /**
     * Purpose: Aggregates total expenditure for a specific vehicle across all sessions.
     * @param customerId The ID of the customer.
     * @param vehicleNumber The vehicle license plate.
     * @return double The cumulative sum of all paid transactions.
     */
    public double getTotalAmountPaid(String customerId, String vehicleNumber) {
        return parkingService.getTotalAmountPaid(customerId, vehicleNumber);
    }
}

package com.mulligan.peo.controller;

import com.mulligan.model.Citation;
import com.mulligan.service.PEOAuthService;
import com.mulligan.service.ParkingService;

/**
 * Controller for the Parking Enforcement Officer (PEO) UI. Sits between the
 * CLI/Swing view and the service layer: validates that the caller has the
 * minimum input set, calls the right {@link ParkingService} method, and
 * translates exceptions into user-facing strings so internal driver/JDBC
 * messages never reach the operator (Defense.md §V11).
 *
 * <p>Citation issuance also triggers a signed AMQP publish through the
 * shared {@code SecurePublisher}, so any forged or replayed citation message
 * is rejected server-side by {@code MessageValidator}.
 */
public class PEOController {

    private final ParkingService parkingService;
    private final PEOAuthService authService;

    /**
     * Production constructor. Constructs a default {@link PEOAuthService};
     * tests should use the package-private overload to inject a stub.
     *
     * @param parkingService service layer used for parking checks and citations
     */
    public PEOController(ParkingService parkingService) {
        this.parkingService = parkingService;
        this.authService = new PEOAuthService();
    }

    PEOController(ParkingService parkingService, PEOAuthService authService) {
        this.parkingService = parkingService;
        this.authService = authService;
    }

    /**
     * Authenticates a PEO user against the credential store.
     *
     * @param userId   PEO username (trimmed)
     * @param password PEO password
     * @return {@code "Login successful."} on a valid credential pair, or a
     *         short user-facing error message otherwise
     */
    public String login(String userId, String password) {
        if (userId == null || userId.isBlank() || password == null || password.isBlank()) {
            return "Error: Please enter User ID and Password.";
        }

        try {
            return authService.authenticate(userId.trim(), password)
                    ? "Login successful."
                    : "Error: Invalid User ID or Password.";
        } catch (RuntimeException e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Inspects whether a vehicle is parked correctly in the supplied space —
     * "correctly" meaning the DB has an open parking event for the vehicle
     * in that space and (if a paid session is required) it has been paid.
     *
     * @param vehicleNumber license plate to look up
     * @param spaceId       parking space identifier
     * @return {@code "Parking Ok"} or {@code "Parking Not Ok"} on success;
     *         {@code "Error: ..."} on a validation or runtime failure
     */
    public String checkVehicle(String vehicleNumber, String spaceId) {
        try {
            boolean ok = parkingService.isParkingOk(vehicleNumber, spaceId);
            return ok ? "Parking Ok" : "Parking Not Ok";
        } catch (IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        } catch (RuntimeException e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Issues a citation for the supplied amount. The service layer validates
     * the vehicle/space pair, persists the citation row, and publishes a
     * signed message on the {@code Citations} queue so the validation server
     * can audit it.
     *
     * @param vehicleNumber license plate to cite
     * @param spaceId       parking space identifier
     * @param amount        citation amount in dollars; must be positive
     *                      (the service layer enforces the upper bound)
     * @return human-readable confirmation including the new citation ID,
     *         zone, inspection time, and amount; or a short error message
     */
    public String issueCitation(String vehicleNumber, String spaceId, double amount) {
        try {
            Citation citation = parkingService.issueCitation(vehicleNumber, spaceId, amount);
            return "Citation issued successfully.\nCitation ID: " + citation.getCitationId()
                    + "\nZone: " + citation.getParkingZoneId()
                    + "\nInspection time: " + citation.getInspectionTime()
                    + "\nAmount: " + citation.getAmount();

        } catch (IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        } catch (RuntimeException e) {
            return "Error: " + e.getMessage();
        }
    }
}

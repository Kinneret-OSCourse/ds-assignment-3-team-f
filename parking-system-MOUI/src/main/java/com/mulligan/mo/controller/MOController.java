package com.mulligan.mo.controller;

import com.mulligan.mo.model.Citation;
import com.mulligan.mo.model.PaymentTransaction;
import com.mulligan.mo.service.MOAuthService;
import com.mulligan.mo.service.ParkingService;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Controller for the Municipal Officer (MO) UI. Brokers calls between the
 * CLI/Swing view and the service layer that materialises the transaction
 * and citation reports.
 *
 * <p>The MO is read-only as far as parking events are concerned. Its
 * RabbitMQ user ({@code mulligan_mo}) has the narrowest set of permissions
 * in {@code definitions.json} — read on {@code Transactions} and
 * {@code Citations} only — so a compromised MO process cannot publish
 * forged messages.
 *
 * <p>Reports are read from the durable Postgres report tables rather than
 * drained from the queues; this is intentional and documented in the
 * implementation note next to TESTING.md §SUC-6.
 */
public class MOController {

    private final ParkingService parkingService;
    private final MOAuthService authService;

    /**
     * Production constructor. Constructs a default {@link MOAuthService};
     * tests should use the package-private overload to inject a stub.
     *
     * @param parkingService service layer used to materialise reports
     */
    public MOController(ParkingService parkingService) {
        this.parkingService = parkingService;
        this.authService = new MOAuthService();
    }

    MOController(ParkingService parkingService, MOAuthService authService) {
        this.parkingService = parkingService;
        this.authService = authService;
    }

    /**
     * Authenticates an MO user against the credential store.
     *
     * @param userId   MO username (trimmed)
     * @param password MO password
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
     * Returns every payment transaction from the report table. The list is
     * empty if no transactions have been committed yet.
     *
     * @return all payment transactions, in service-defined order
     * @throws IOException      if the backing channel cannot be opened
     * @throws TimeoutException if the broker times out during channel setup
     */
    public List<PaymentTransaction> getTransactionReport() throws IOException, TimeoutException {
        return parkingService.getTransactionReport();
    }

    /**
     * Returns every citation from the report table. The list is empty if no
     * citations have been issued yet.
     *
     * @return all citations, in service-defined order
     * @throws IOException      if the backing channel cannot be opened
     * @throws TimeoutException if the broker times out during channel setup
     */
    public List<Citation> getCitationReport() throws IOException, TimeoutException {
        return parkingService.getCitationReport();
    }
}

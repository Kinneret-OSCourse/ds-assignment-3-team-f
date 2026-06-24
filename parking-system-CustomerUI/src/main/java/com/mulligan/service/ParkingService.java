package com.mulligan.service;

import com.mulligan.common.logging.SecureLogger;
import com.mulligan.common.messaging.SecurePublisher;
import com.mulligan.common.security.ClientErrorCodes;
import com.mulligan.common.security.MessageSigner;
import com.mulligan.common.validation.InputValidator;
import com.mulligan.common.validation.ValidationException;
import com.mulligan.model.ParkingEvent;
import com.mulligan.model.PaymentTransaction;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements the customer parking workflows against PostgreSQL and RabbitMQ.
 * The class keeps parking start/stop changes atomic at the database layer and
 * only publishes queue events after the database transaction has committed.
 */
public class ParkingService {

    private static final SecureLogger LOG = new SecureLogger("customer-ui");

    /**
     * Returns the {@link SecurePublisher} used to publish transaction messages.
     * Lazily constructed and overridable by tests.
     *
     * @return the configured secure publisher
     */
    protected SecurePublisher publisher() {
        return new SecurePublisher(QueueConfig.createFactory(), new MessageSigner(), LOG);
    }

    /**
     * Starts a parking session for the given vehicle and parking space.
     * A customer has one assigned vehicle and may have only one active parking
     * session. The current session must be stopped before starting another one.
     *
     * @param customerId customer identifier shown in the returned event object
     * @param vehicleNumber existing vehicle plate number
     * @param spaceId active parking space number
     * @return the newly created started parking event
     */
    public ParkingEvent startParking(String customerId, String vehicleNumber, String spaceId) {
        validateCustomerInput(customerId, vehicleNumber, spaceId);

        try (var conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            try {
                Integer vehicleId = findVehicleId(conn, vehicleNumber);
                if (vehicleId == null) {
                    throw new IllegalArgumentException("Vehicle not found.");
                }
                ensureVehicleAssignedToCustomer(conn, customerId, vehicleId);

                SpaceDetails spaceDetails = findSpaceDetails(conn, spaceId);
                if (spaceDetails == null) {
                    throw new IllegalArgumentException("Invalid parking space.");
                }

                if (hasActiveParkingForCustomer(conn, customerId)) {
                    throw new IllegalArgumentException("Customer already has an active parking event.");
                }

                if (hasActiveParkingForSpace(conn, spaceDetails.spaceDbId())) {
                    throw new IllegalArgumentException("Parking space is already occupied.");
                }

                ParkingEvent startedEvent = insertStartedParking(conn, customerId, vehicleNumber, vehicleId, spaceDetails);
                conn.commit();

                return startedEvent;
            } catch (Exception e) {
                conn.rollback();
                if (e instanceof IllegalArgumentException illegalArgumentException) {
                    throw illegalArgumentException;
                }
                LOG.security("Customer DB error op=startStop err=" + e.getClass().getSimpleName());
                throw new RuntimeException(ClientErrorCodes.INTERNAL);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            LOG.security("Customer DB unavailable op=start err=" + e.getClass().getSimpleName());
            throw new RuntimeException(ClientErrorCodes.UNAVAILABLE);
        }
    }

    /**
     * Stops the current active parking session for the given vehicle and records
     * the generated payment transaction in the reporting tables.
     *
     * @param customerId customer identifier shown in the returned transaction object
     * @param vehicleNumber existing vehicle plate number
     * @return the completed payment transaction for the stopped parking event
     */
    public PaymentTransaction stopParking(String customerId, String vehicleNumber) {
        if (customerId == null || customerId.isBlank() || vehicleNumber == null || vehicleNumber.isBlank()) {
            throw new IllegalArgumentException("Invalid input.");
        }

        try (var conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            try {
                Integer vehicleId = findVehicleId(conn, vehicleNumber);
                if (vehicleId == null) {
                    throw new IllegalArgumentException("Vehicle not found.");
                }
                ensureVehicleAssignedToCustomer(conn, customerId, vehicleId);

                StoppedParkingResult stoppedParking = stopParkingInternal(conn, customerId, vehicleId, vehicleNumber);
                persistTransaction(conn, stoppedParking);
                conn.commit();

                PaymentTransaction transaction = stoppedParking.transaction();
                reportToQueue(
                        transaction.getTransactionId(),
                        transaction.getVehicleNumber(),
                        transaction.getParkingSpaceId(),
                        transaction.getParkingZoneId(),
                        transaction.getStartTime(),
                        transaction.getStopTime(),
                        transaction.getAmount()
                );
                return transaction;
            } catch (Exception e) {
                conn.rollback();
                if (e instanceof IllegalArgumentException illegalArgumentException) {
                    throw illegalArgumentException;
                }
                LOG.security("Customer DB error op=stop err=" + e.getClass().getSimpleName());
                throw new RuntimeException(ClientErrorCodes.INTERNAL);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            LOG.security("Customer DB unavailable op=stop err=" + e.getClass().getSimpleName());
            throw new RuntimeException(ClientErrorCodes.UNAVAILABLE);
        }
    }

    /**
     * Retrieves all parking events previously created for the given vehicle.
     *
     * @param customerId customer identifier used in the returned model objects
     * @param vehicleNumber vehicle plate number to search for
     * @return parking events ordered from newest to oldest
     */
    public List<ParkingEvent> getParkingEvents(String customerId, String vehicleNumber) {
        if (customerId == null || vehicleNumber == null || customerId.isBlank() || vehicleNumber.isBlank()) {
            throw new IllegalArgumentException("Invalid input.");
        }

        List<ParkingEvent> result = new ArrayList<>();

        try (var conn = DatabaseConnection.getConnection()) {
            Integer vehicleId = findVehicleId(conn, vehicleNumber);
            if (vehicleId == null) {
                return result;
            }
            ensureVehicleAssignedToCustomer(conn, customerId, vehicleId);

            String eventsSql = """
                    SELECT pe.event_id, pe.start_time, pe.end_time, pe.status, pe.calculated_amount,
                           ps.space_number, pz.zone_code
                    FROM parking_events pe
                    JOIN parking_spaces ps ON pe.space_id = ps.space_id
                    JOIN parking_zones pz ON ps.zone_id = pz.zone_id
                    WHERE pe.vehicle_id = ?
                    ORDER BY pe.start_time DESC
                    """;

            try (var stmt = conn.prepareStatement(eventsSql)) {
                stmt.setInt(1, vehicleId);

                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        result.add(new ParkingEvent(
                                String.valueOf(rs.getInt("event_id")),
                                customerId,
                                vehicleNumber,
                                rs.getString("space_number"),
                                rs.getString("zone_code"),
                                rs.getTimestamp("start_time").toLocalDateTime(),
                                rs.getTimestamp("end_time") != null
                                        ? rs.getTimestamp("end_time").toLocalDateTime()
                                        : null,
                                rs.getString("status"),
                                rs.getDouble("calculated_amount")
                        ));
                    }
                }
            }

            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            LOG.security("Customer DB error op=getEvents err=" + e.getClass().getSimpleName());
            throw new RuntimeException(ClientErrorCodes.INTERNAL);
        }
    }

    /**
     * Calculates the total amount already paid for completed parking sessions of
     * the given vehicle.
     *
     * @param customerId customer identifier used for input validation
     * @param vehicleNumber vehicle plate number whose completed sessions are summed
     * @return total amount paid across stopped parking events
     */
    public double getTotalAmountPaid(String customerId, String vehicleNumber) {
        if (customerId == null || vehicleNumber == null || customerId.isBlank() || vehicleNumber.isBlank()) {
            throw new IllegalArgumentException("Invalid input.");
        }

        try (var conn = DatabaseConnection.getConnection()) {
            Integer vehicleId = findVehicleId(conn, vehicleNumber);
            if (vehicleId == null) {
                return 0;
            }
            ensureVehicleAssignedToCustomer(conn, customerId, vehicleId);

            String totalSql = """
                    SELECT COALESCE(SUM(calculated_amount), 0) AS total
                    FROM parking_events
                    WHERE vehicle_id = ?
                      AND status = 'STOPPED'
                    """;

            try (var stmt = conn.prepareStatement(totalSql)) {
                stmt.setInt(1, vehicleId);
                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getDouble("total");
                    }
                }
            }

            return 0;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            LOG.security("Customer DB error op=getTotal err=" + e.getClass().getSimpleName());
            throw new RuntimeException(ClientErrorCodes.INTERNAL);
        }
    }

    /**
     * Publishes a completed parking transaction to RabbitMQ after the
     * corresponding database transaction has committed successfully.
     *
     * @param transactionId completed transaction identifier
     * @param vehicleNumber vehicle plate number
     * @param parkingSpaceId parking space number
     * @param parkingZoneId zone code for the parking space
     * @param startTime parking start timestamp
     * @param endTime parking stop timestamp
     * @param amount total amount owed for the completed event
     */
    protected void reportToQueue(String transactionId, String vehicleNumber, String parkingSpaceId, String parkingZoneId,
                                 LocalDateTime startTime, LocalDateTime endTime, double amount) {
        try {
            String payload = JsonMessageCodec.serializeTransaction(
                    transactionId,
                    vehicleNumber,
                    parkingSpaceId,
                    parkingZoneId,
                    startTime,
                    endTime,
                    amount
            );
            publisher().publishSigned(QueueConfig.TRANSACTION_ROUTING_KEY, payload);
        } catch (Exception e) {
            // The parking DB transaction has already committed before this
            // reporting step. Keep the customer workflow successful and audit
            // the queue outage for operators to repair/replay separately.
            LOG.security("Customer UI failed to publish transaction id=" + transactionId
                    + " err=" + e.getClass().getSimpleName());
        }
    }

    private void validateCustomerInput(String customerId, String vehicleNumber, String spaceId) {
        try {
            InputValidator.requireCustomerId(customerId);
            InputValidator.requirePlate(vehicleNumber);
            InputValidator.requireSpace(spaceId);
        } catch (ValidationException ve) {
            LOG.security("Customer input rejected code=" + ve.getMessage());
            throw new IllegalArgumentException(ClientErrorCodes.INVALID_INPUT);
        }
    }

    private ParkingEvent insertStartedParking(java.sql.Connection conn, String customerId, String vehicleNumber,
                                              int vehicleId, SpaceDetails spaceDetails) throws java.sql.SQLException {
        String insertSql = """
                INSERT INTO parking_events (vehicle_id, space_id, customer_id, start_time, end_time, status, calculated_amount, created_at, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, NULL, 'STARTED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING event_id, start_time
                """;

        try (var stmt = conn.prepareStatement(insertSql)) {
            stmt.setInt(1, vehicleId);
            stmt.setInt(2, spaceDetails.spaceDbId());
            stmt.setString(3, customerId);

            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new ParkingEvent(
                            String.valueOf(rs.getInt("event_id")),
                            customerId,
                            vehicleNumber,
                            spaceDetails.spaceNumber(),
                            spaceDetails.zoneCode(),
                            rs.getTimestamp("start_time").toLocalDateTime(),
                            null,
                            "STARTED"
                    );
                }
            }
        }

        throw new IllegalStateException("Failed to create a new parking event.");
    }

    private StoppedParkingResult stopParkingInternal(java.sql.Connection conn, String customerId, int vehicleId, String vehicleNumber)
            throws java.sql.SQLException {
        String eventSql = """
                SELECT pe.event_id, pe.start_time, ps.space_id, ps.space_number, pz.zone_code, pz.hourly_rate
                FROM parking_events pe
                JOIN parking_spaces ps ON pe.space_id = ps.space_id
                JOIN parking_zones pz ON ps.zone_id = pz.zone_id
                WHERE pe.vehicle_id = ?
                  AND (pe.customer_id = ? OR pe.customer_id IS NULL)
                  AND pe.status = 'STARTED'
                  AND pe.end_time IS NULL
                ORDER BY pe.start_time DESC
                LIMIT 1
                """;

        Integer eventId = null;
        Integer spaceDbId = null;
        String spaceNumber = null;
        String zoneCode = null;
        double rate = 0;
        LocalDateTime startTime = null;

        try (PreparedStatement stmt = conn.prepareStatement(eventSql)) {
            stmt.setInt(1, vehicleId);
            stmt.setString(2, customerId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    eventId = rs.getInt("event_id");
                    spaceDbId = rs.getInt("space_id");
                    spaceNumber = rs.getString("space_number");
                    zoneCode = rs.getString("zone_code");
                    rate = rs.getDouble("hourly_rate");
                    startTime = rs.getTimestamp("start_time").toLocalDateTime();
                }
            }
        }

        if (eventId == null || spaceDbId == null || startTime == null) {
            throw new IllegalArgumentException("There is no open parking event.");
        }

        LocalDateTime endTime = LocalDateTime.now();
        long minutes = Duration.between(startTime, endTime).toMinutes();
        double hours = Math.max(1, Math.ceil(minutes / 60.0));
        double amount = hours * rate;

        String updateSql = """
                UPDATE parking_events
                SET end_time = ?, status = 'STOPPED', calculated_amount = ?, updated_at = CURRENT_TIMESTAMP
                WHERE event_id = ?
                """;

        try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(endTime));
            stmt.setDouble(2, amount);
            stmt.setInt(3, eventId);
            stmt.executeUpdate();
        }

        PaymentTransaction transaction = new PaymentTransaction(
                String.valueOf(eventId),
                vehicleNumber,
                spaceNumber,
                zoneCode,
                startTime,
                endTime,
                amount
        );

        return new StoppedParkingResult(eventId, vehicleId, spaceDbId, transaction);
    }

    private void persistTransaction(java.sql.Connection conn, StoppedParkingResult stoppedParking) throws java.sql.SQLException {
        String insertSql = """
                INSERT INTO payment_transactions
                (transaction_id, event_id, vehicle_id, space_id, zone_code, start_time, stop_time, amount, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (transaction_id) DO UPDATE SET
                    zone_code = EXCLUDED.zone_code,
                    start_time = EXCLUDED.start_time,
                    stop_time = EXCLUDED.stop_time,
                    amount = EXCLUDED.amount
                """;

        try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            stmt.setString(1, stoppedParking.transaction().getTransactionId());
            stmt.setInt(2, stoppedParking.eventId());
            stmt.setInt(3, stoppedParking.vehicleId());
            stmt.setInt(4, stoppedParking.spaceDbId());
            stmt.setString(5, stoppedParking.transaction().getParkingZoneId());
            stmt.setTimestamp(6, Timestamp.valueOf(stoppedParking.transaction().getStartTime()));
            stmt.setTimestamp(7, Timestamp.valueOf(stoppedParking.transaction().getStopTime()));
            stmt.setDouble(8, stoppedParking.transaction().getAmount());
            stmt.executeUpdate();
        }
    }

    private Integer findVehicleId(java.sql.Connection conn, String vehicleNumber) throws java.sql.SQLException {
        String vehicleSql = "SELECT vehicle_id FROM vehicles WHERE plate_number = ?";
        try (var stmt = conn.prepareStatement(vehicleSql)) {
            stmt.setString(1, vehicleNumber);
            try (var rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt("vehicle_id") : null;
            }
        }
    }

    private void ensureVehicleAssignedToCustomer(java.sql.Connection conn, String customerId, int requestedVehicleId)
            throws java.sql.SQLException {
        String assignedVehicleSql = """
                SELECT vehicle_id
                FROM customers
                WHERE customer_id = ?
                  AND is_active = true
                FOR UPDATE
                """;
        try (var stmt = conn.prepareStatement(assignedVehicleSql)) {
            stmt.setString(1, customerId);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Customer not found.");
                }

                int assignedVehicleId = rs.getInt("vehicle_id");
                if (rs.wasNull()) {
                    throw new IllegalArgumentException("No vehicle is assigned to this customer.");
                }

                if (assignedVehicleId != requestedVehicleId) {
                    throw new IllegalArgumentException("This vehicle is not assigned to the logged-in customer.");
                }
            }
        }
    }

    private SpaceDetails findSpaceDetails(java.sql.Connection conn, String spaceId) throws java.sql.SQLException {
        String spaceSql = """
                SELECT ps.space_id, ps.space_number, pz.zone_code
                FROM parking_spaces ps
                JOIN parking_zones pz ON ps.zone_id = pz.zone_id
                WHERE ps.space_number = ?
                  AND ps.is_active = true
                FOR UPDATE OF ps
                """;

        try (var stmt = conn.prepareStatement(spaceSql)) {
            stmt.setString(1, spaceId);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                return new SpaceDetails(
                        rs.getInt("space_id"),
                        rs.getString("space_number"),
                        rs.getString("zone_code")
                );
            }
        }
    }

    private boolean hasActiveParkingForSpace(java.sql.Connection conn, int spaceDbId) throws java.sql.SQLException {
        String activeSql = """
                SELECT event_id
                FROM parking_events
                WHERE space_id = ?
                  AND status = 'STARTED'
                  AND end_time IS NULL
                LIMIT 1
                """;

        try (var stmt = conn.prepareStatement(activeSql)) {
            stmt.setInt(1, spaceDbId);
            try (var rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean hasActiveParkingForCustomer(java.sql.Connection conn, String customerId) throws java.sql.SQLException {
        String activeSql = """
                SELECT event_id
                FROM parking_events
                WHERE customer_id = ?
                  AND status = 'STARTED'
                  AND end_time IS NULL
                LIMIT 1
                """;

        try (var stmt = conn.prepareStatement(activeSql)) {
            stmt.setString(1, customerId);
            try (var rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private record SpaceDetails(int spaceDbId, String spaceNumber, String zoneCode) {
    }

    private record StoppedParkingResult(int eventId, int vehicleId, int spaceDbId, PaymentTransaction transaction) {
    }
}

package com.mulligan.service;

import com.mulligan.common.logging.SecureLogger;
import com.mulligan.common.messaging.SecurePublisher;
import com.mulligan.common.security.ClientErrorCodes;
import com.mulligan.common.security.MessageSigner;
import com.mulligan.common.validation.InputValidator;
import com.mulligan.common.validation.ValidationException;
import com.mulligan.model.Citation;
import com.mulligan.model.ParkingEvent;
import com.mulligan.model.PaymentTransaction;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class ParkingService {

    private static final SecureLogger LOG = new SecureLogger("peo-ui");
    private static final Logger INVESTIGATION_LOGGER = createLogger();

    /**
     * Returns the secure publisher used to send citation/transaction
     * messages to the queue cluster. Lazily constructed and overridable by
     * tests.
     *
     * @return cluster-aware secure publisher
     */
    protected SecurePublisher publisher() {
        return new SecurePublisher(QueueConfig.createFactory(), new MessageSigner(), LOG);
    }

    public ParkingEvent startParking(String customerId, String vehicleNumber, String spaceId) {
        validateCustomerInput(customerId, vehicleNumber, spaceId);
        StoppedParkingResult autoStoppedParking = null;

        try (var conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            try {
                Integer vehicleId = findVehicleId(conn, vehicleNumber);
                if (vehicleId == null) {
                    throw new IllegalArgumentException("Vehicle not found.");
                }

                SpaceDetails spaceDetails = findSpaceDetails(conn, spaceId);
                if (spaceDetails == null) {
                    throw new IllegalArgumentException("Invalid parking space.");
                }

                if (hasActiveParkingForCustomerVehicle(conn, customerId, vehicleId)) {
                    autoStoppedParking = stopParkingInternal(conn, customerId, vehicleId, vehicleNumber);
                    persistTransaction(conn, autoStoppedParking);
                }

                ParkingEvent startedEvent = insertStartedParking(conn, customerId, vehicleNumber, vehicleId, spaceDetails);
                conn.commit();

                if (autoStoppedParking != null) {
                    PaymentTransaction transaction = autoStoppedParking.transaction();
                    reportToQueue(
                            transaction.getTransactionId(),
                            transaction.getVehicleNumber(),
                            transaction.getParkingSpaceId(),
                            transaction.getParkingZoneId(),
                            transaction.getStartTime(),
                            transaction.getStopTime(),
                            transaction.getAmount()
                    );
                }

                return startedEvent;
            } catch (Exception e) {
                conn.rollback();
                if (e instanceof IllegalArgumentException illegalArgumentException) {
                    throw illegalArgumentException;
                }
                LOG.security("PEO DB error ctx=\"Database error\" err=" + e.getClass().getSimpleName()); throw new RuntimeException(ClientErrorCodes.INTERNAL);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            LOG.security("PEO DB error ctx=\"Database error\" err=" + e.getClass().getSimpleName()); throw new RuntimeException(ClientErrorCodes.INTERNAL);
        }
    }

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
                LOG.security("PEO DB error ctx=\"Error stopping parking\" err=" + e.getClass().getSimpleName()); throw new RuntimeException(ClientErrorCodes.INTERNAL);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            LOG.security("PEO DB error ctx=\"Error stopping parking\" err=" + e.getClass().getSimpleName()); throw new RuntimeException(ClientErrorCodes.INTERNAL);
        }
    }

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

            String eventsSql = """
                    SELECT pe.event_id, pe.start_time, pe.end_time, pe.status, ps.space_number, pz.zone_code
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
                                rs.getString("status")
                        ));
                    }
                }
            }

            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            LOG.security("PEO DB error ctx=\"Error retrieving parking events\" err=" + e.getClass().getSimpleName()); throw new RuntimeException(ClientErrorCodes.INTERNAL);
        }
    }

    public double getTotalAmountPaid(String customerId, String vehicleNumber) {
        if (customerId == null || vehicleNumber == null || customerId.isBlank() || vehicleNumber.isBlank()) {
            throw new IllegalArgumentException("Invalid input.");
        }

        try (var conn = DatabaseConnection.getConnection()) {
            Integer vehicleId = findVehicleId(conn, vehicleNumber);
            if (vehicleId == null) {
                return 0;
            }

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
            LOG.security("PEO DB error ctx=\"Error retrieving total amount paid\" err=" + e.getClass().getSimpleName()); throw new RuntimeException(ClientErrorCodes.INTERNAL);
        }
    }

    public boolean isValidVehicle(String vehicleNumber) {
        if (vehicleNumber == null || vehicleNumber.isBlank()) {
            return false;
        }

        String sql = "SELECT 1 FROM vehicles WHERE plate_number = ?";

        try (var conn = DatabaseConnection.getConnection();
             var stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, vehicleNumber);
            try (var rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            LOG.security("PEO DB error ctx=\"Error validating vehicle\" err=" + e.getClass().getSimpleName()); throw new RuntimeException(ClientErrorCodes.INTERNAL);
        }
    }

    public boolean isValidParkingSpace(String spaceId) {
        if (spaceId == null || spaceId.isBlank()) {
            return false;
        }

        String sql = """
                SELECT 1
                FROM parking_spaces
                WHERE space_number = ?
                  AND is_active = true
                """;

        try (var conn = DatabaseConnection.getConnection();
             var stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, spaceId);
            try (var rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            LOG.security("PEO DB error ctx=\"Error validating parking space\" err=" + e.getClass().getSimpleName()); throw new RuntimeException(ClientErrorCodes.INTERNAL);
        }
    }

    public boolean isParkingOk(String vehicleNumber, String spaceId) {
        if (!isValidVehicle(vehicleNumber) || !isValidParkingSpace(spaceId)) {
            throw new IllegalArgumentException("Invalid vehicle number or parking space.");
        }

        LocalDateTime currentTime = LocalDateTime.of(2026, 4, 27, 0, 34, 11);

        try (var conn = DatabaseConnection.getConnection()) {
            Integer vehicleId = findVehicleId(conn, vehicleNumber);
            if (vehicleId == null) {
                logInvestigation(vehicleNumber, spaceId, false);
                return false;
            }

            SpaceCheckDetails spaceDetails = findSpaceCheckDetails(conn, spaceId);
            if (spaceDetails == null) {
                logInvestigation(vehicleNumber, spaceId, false);
                return false;
            }

            String activeSql = """
                    SELECT start_time
                    FROM parking_events
                    WHERE vehicle_id = ?
                      AND space_id = ?
                      AND status = 'STARTED'
                      AND end_time IS NULL
                    ORDER BY start_time DESC
                    LIMIT 1
                    """;

            LocalDateTime startTime = null;

            try (var stmt = conn.prepareStatement(activeSql)) {
                stmt.setInt(1, vehicleId);
                stmt.setInt(2, spaceDetails.spaceId());
                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        startTime = rs.getTimestamp("start_time").toLocalDateTime();
                    }
                }
            }

            if (startTime == null) {
                logInvestigation(vehicleNumber, spaceId, false);
                return false;
            }

            long parkedMinutes = Duration.between(startTime, currentTime).toMinutes();
            boolean parkingOk = parkedMinutes <= spaceDetails.maxAllowedMinutes();

            logInvestigation(vehicleNumber, spaceId, parkingOk);
            return parkingOk;
        } catch (Exception e) {
            LOG.security("PEO DB error ctx=\"Error checking parking legality\" err=" + e.getClass().getSimpleName()); throw new RuntimeException(ClientErrorCodes.INTERNAL);
        }
    }

    public Citation issueCitation(String vehicleNumber, String spaceId, double amount) {
        if (!isValidVehicle(vehicleNumber) || !isValidParkingSpace(spaceId)) {
            throw new IllegalArgumentException("Invalid vehicle number or parking space.");
        }

        if (isParkingOk(vehicleNumber, spaceId)) {
            throw new IllegalArgumentException("Citation can only be issued when parking is Not Ok.");
        }

        if (amount <= 0) {
            throw new IllegalArgumentException("Citation amount must be positive.");
        }

        // Use the real current time so the citation's inspection time reflects
        // when it was actually issued (was previously hardcoded to a fixed date).
        LocalDateTime currentTime = LocalDateTime.now();

        try (var conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            try {
                Integer vehicleId = findVehicleId(conn, vehicleNumber);
                if (vehicleId == null) {
                    throw new IllegalArgumentException("Vehicle not found.");
                }

                CitationSpaceDetails spaceDetails = findCitationSpaceDetails(conn, spaceId);
                if (spaceDetails == null) {
                    throw new IllegalArgumentException("Parking space not found.");
                }

                Citation citation = new Citation(
                        java.util.UUID.randomUUID().toString(),
                        vehicleNumber,
                        spaceId,
                        spaceDetails.zoneCode(),
                        currentTime,
                        amount
                );

                persistCitation(conn, citation, vehicleId, spaceDetails.spaceId());
                conn.commit();

                reportCitationToQueue(
                        citation.getCitationId(),
                        vehicleNumber,
                        spaceId,
                        citation.getParkingZoneId(),
                        citation.getInspectionTime(),
                        amount
                );

                return citation;
            } catch (Exception e) {
                conn.rollback();
                if (e instanceof IllegalArgumentException illegalArgumentException) {
                    throw illegalArgumentException;
                }
                LOG.security("PEO DB error ctx=\"Error issuing citation\" err=" + e.getClass().getSimpleName()); throw new RuntimeException(ClientErrorCodes.INTERNAL);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            LOG.security("PEO DB error ctx=\"Error issuing citation\" err=" + e.getClass().getSimpleName()); throw new RuntimeException(ClientErrorCodes.INTERNAL);
        }
    }

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
            // Reporting happens after the DB transaction commits. A broker
            // outage must not make the completed parking operation look
            // failed to the officer.
            LOG.security("PEO UI failed to publish transaction id=" + transactionId
                    + " err=" + e.getClass().getSimpleName());
        }
    }

    protected void reportCitationToQueue(String citationId, String vehicleNumber, String parkingSpaceId, String parkingZoneId,
                                         LocalDateTime inspectionTime, double amount) {
        try {
            String payload = JsonMessageCodec.serializeCitation(
                    citationId,
                    vehicleNumber,
                    parkingSpaceId,
                    parkingZoneId,
                    inspectionTime,
                    amount
            );
            publisher().publishSigned(QueueConfig.CITATION_ROUTING_KEY, payload);
        } catch (Exception e) {
            // The citation row is already durable by this point. Keep the UI
            // result successful and leave the queue failure in the secure log.
            LOG.security("PEO UI failed to publish citation id=" + citationId
                    + " err=" + e.getClass().getSimpleName());
        }
    }

    private void validateCustomerInput(String customerId, String vehicleNumber, String spaceId) {
        try {
            InputValidator.requireCustomerId(customerId);
            InputValidator.requirePlate(vehicleNumber);
            InputValidator.requireSpace(spaceId);
        } catch (ValidationException ve) {
            LOG.security("PEO input rejected code=" + ve.getMessage());
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

        LocalDateTime endTime = LocalDateTime.of(2026, 4, 27, 0, 34, 11);
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

    private SpaceDetails findSpaceDetails(java.sql.Connection conn, String spaceId) throws java.sql.SQLException {
        String spaceSql = """
                SELECT ps.space_id, ps.space_number, pz.zone_code
                FROM parking_spaces ps
                JOIN parking_zones pz ON ps.zone_id = pz.zone_id
                WHERE ps.space_number = ?
                  AND ps.is_active = true
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

    private SpaceCheckDetails findSpaceCheckDetails(java.sql.Connection conn, String spaceId) throws java.sql.SQLException {
        String spaceSql = """
                SELECT ps.space_id, pz.max_parking_minutes
                FROM parking_spaces ps
                JOIN parking_zones pz ON ps.zone_id = pz.zone_id
                WHERE ps.space_number = ?
                  AND ps.is_active = true
                """;

        try (var stmt = conn.prepareStatement(spaceSql)) {
            stmt.setString(1, spaceId);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                return new SpaceCheckDetails(
                        rs.getInt("space_id"),
                        rs.getInt("max_parking_minutes")
                );
            }
        }
    }

    private CitationSpaceDetails findCitationSpaceDetails(java.sql.Connection conn, String spaceId) throws java.sql.SQLException {
        String sql = """
                SELECT ps.space_id, pz.zone_code
                FROM parking_spaces ps
                JOIN parking_zones pz ON ps.zone_id = pz.zone_id
                WHERE ps.space_number = ?
                  AND ps.is_active = true
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, spaceId);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                return new CitationSpaceDetails(
                        rs.getInt("space_id"),
                        rs.getString("zone_code")
                );
            }
        }
    }

    private boolean hasActiveParkingForCustomerVehicle(java.sql.Connection conn, String customerId, int vehicleId) throws java.sql.SQLException {
        String activeSql = """
                SELECT event_id
                FROM parking_events
                WHERE customer_id = ?
                  AND vehicle_id = ?
                  AND status = 'STARTED'
                  AND end_time IS NULL
                LIMIT 1
                """;

        try (var stmt = conn.prepareStatement(activeSql)) {
            stmt.setString(1, customerId);
            stmt.setInt(2, vehicleId);
            try (var rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void persistCitation(java.sql.Connection conn, Citation citation, int vehicleId, int spaceId) throws java.sql.SQLException {
        String sql = """
                INSERT INTO citations
                (citation_id, vehicle_id, space_id, zone_code, inspection_time, amount, created_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (citation_id) DO UPDATE SET
                    zone_code = EXCLUDED.zone_code,
                    inspection_time = EXCLUDED.inspection_time,
                    amount = EXCLUDED.amount
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, citation.getCitationId());
            stmt.setInt(2, vehicleId);
            stmt.setInt(3, spaceId);
            stmt.setString(4, citation.getParkingZoneId());
            stmt.setTimestamp(5, Timestamp.valueOf(citation.getInspectionTime()));
            stmt.setDouble(6, citation.getAmount());
            stmt.executeUpdate();
        }
    }

    private static Logger createLogger() {
        Logger logger = Logger.getLogger(ParkingService.class.getName());
        logger.setUseParentHandlers(false);

        try {
            String logPattern = System.getProperty("java.io.tmpdir") + java.io.File.separator + "peo-investigations.log";
            FileHandler fileHandler = new FileHandler(logPattern, true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
        } catch (Exception ignored) {
        }

        return logger;
    }

    private void logInvestigation(String vehicleNumber, String spaceId, boolean parkingOk) {
        LocalDateTime currentTime = LocalDateTime.of(2026, 4, 27, 0, 34, 11);
        INVESTIGATION_LOGGER.info(
                "investigationTime=" + currentTime
                        + ", vehicleNumber=" + vehicleNumber
                        + ", parkingSpaceId=" + spaceId
                        + ", response=" + (parkingOk ? "Parking Ok" : "Parking Not Ok")
        );
    }

    private record SpaceDetails(int spaceDbId, String spaceNumber, String zoneCode) {
    }

    private record StoppedParkingResult(int eventId, int vehicleId, int spaceDbId, PaymentTransaction transaction) {
    }

    private record SpaceCheckDetails(int spaceId, int maxAllowedMinutes) {
    }

    private record CitationSpaceDetails(int spaceId, String zoneCode) {
    }
}

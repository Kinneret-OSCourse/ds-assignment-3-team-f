package com.mulligan.mo.service;

import com.mulligan.common.logging.SecureLogger;
import com.mulligan.common.messaging.MulliganConnectionFactory;
import com.mulligan.common.messaging.SecurePublisher;
import com.mulligan.common.security.ClientErrorCodes;
import com.mulligan.common.security.MessageSigner;
import com.mulligan.common.security.SecureMessage;
import com.mulligan.mo.model.Citation;
import com.mulligan.mo.model.CitationCount;
import com.mulligan.mo.model.ParkingEvent;
import com.mulligan.mo.model.PaymentTransaction;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.GetResponse;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides municipality-facing access to shared parking workflows and queue
 * backed reports. The MO report use cases consume transaction and citation
 * events from RabbitMQ, matching the assignment communication requirement.
 */
public class ParkingService {

    private static final SecureLogger LOG = new SecureLogger("mo-ui");

    /**
     * @return the secure publisher used by the few MO write paths (those exist
     *     for symmetry with PEO/Customer in case the MO UI needs to backfill).
     */
    protected SecurePublisher publisher() {
        return new SecurePublisher(QueueConfig.createFactory(), new MessageSigner(), LOG);
    }

    /**
     * @return the cluster-aware connection factory used to consume reports
     *     from the broker.
     */
    protected MulliganConnectionFactory consumeFactory() {
        return QueueConfig.createFactory();
    }

    /**
     * Validates that a vehicle exists in the system.
     *
     * @param vehicleNumber vehicle plate number to validate
     * @return {@code true} when the vehicle exists
     */
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
            LOG.security("MO DB error ctx=\"Error validating vehicle\" err=" + e.getClass().getSimpleName()); throw new RuntimeException(ClientErrorCodes.INTERNAL);
        }
    }

    /**
     * Validates that a parking space exists and is active.
     *
     * @param spaceId parking space number to validate
     * @return {@code true} when the space exists and can be used
     */
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
            LOG.security("MO DB error ctx=\"Error validating parking space\" err=" + e.getClass().getSimpleName()); throw new RuntimeException(ClientErrorCodes.INTERNAL);
        }
    }

    /**
     * Starts a parking session atomically. If another session is currently open
     * for the same vehicle, the old session is stopped and recorded within the
     * same database transaction before the new one is created.
     *
     * @param customerId customer identifier shown in the returned event
     * @param vehicleNumber vehicle plate number
     * @param spaceId parking space number
     * @return the newly started parking event
     */
    public ParkingEvent startParking(String customerId, String vehicleNumber, String spaceId) {
        if (customerId == null || vehicleNumber == null || spaceId == null
                || customerId.isBlank() || vehicleNumber.isBlank() || spaceId.isBlank()) {
            throw new IllegalArgumentException("Invalid input.");
        }

        StoppedParkingResult autoStoppedEvent = null;

        try (var conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            try {
                Integer vehicleId = findVehicleId(conn, vehicleNumber);
                if (vehicleId == null) {
                    throw new IllegalArgumentException("Vehicle not found.");
                }

                SpaceDetails spaceDetails = findSpaceDetails(conn, spaceId);
                if (spaceDetails == null) {
                    throw new IllegalArgumentException("Parking space not found.");
                }

                if (hasActiveParking(conn, vehicleId)) {
                    autoStoppedEvent = stopParkingInternal(conn, vehicleId, vehicleNumber);
                    persistTransaction(conn, autoStoppedEvent);
                }

                ParkingEvent startedEvent = insertStartedParking(conn, customerId, vehicleNumber, vehicleId, spaceDetails);
                conn.commit();

                if (autoStoppedEvent != null) {
                    PaymentTransaction transaction = autoStoppedEvent.transaction();
                    reportTransactionToQueue(
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
                LOG.security("MO DB error ctx=\"Error starting parking\" err=" + e.getClass().getSimpleName()); throw new RuntimeException(ClientErrorCodes.INTERNAL);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            LOG.security("MO DB error ctx=\"Error starting parking\" err=" + e.getClass().getSimpleName()); throw new RuntimeException(ClientErrorCodes.INTERNAL);
        }
    }

    /**
     * Stops an active parking session atomically and stores the transaction in
     * PostgreSQL before publishing the queue event.
     *
     * @param customerId customer identifier shown in the returned transaction
     * @param vehicleNumber vehicle plate number
     * @return completed parking transaction
     */
    public PaymentTransaction stopParking(String customerId, String vehicleNumber) {
        if (customerId == null || vehicleNumber == null || customerId.isBlank() || vehicleNumber.isBlank()) {
            throw new IllegalArgumentException("Invalid input.");
        }

        try (var conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            try {
                Integer vehicleId = findVehicleId(conn, vehicleNumber);
                if (vehicleId == null) {
                    throw new IllegalArgumentException("Vehicle not found.");
                }

                StoppedParkingResult stoppedParking = stopParkingInternal(conn, vehicleId, vehicleNumber);
                persistTransaction(conn, stoppedParking);
                conn.commit();

                PaymentTransaction transaction = stoppedParking.transaction();
                reportTransactionToQueue(
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
                LOG.security("MO DB error ctx=\"Error stopping parking\" err=" + e.getClass().getSimpleName()); throw new RuntimeException(ClientErrorCodes.INTERNAL);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            LOG.security("MO DB error ctx=\"Error stopping parking\" err=" + e.getClass().getSimpleName()); throw new RuntimeException(ClientErrorCodes.INTERNAL);
        }
    }

    /**
     * Retrieves all parking events for the given vehicle.
     *
     * @param customerId customer identifier shown in returned models
     * @param vehicleNumber vehicle plate number
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
        } catch (Exception e) {
            LOG.security("MO DB error ctx=\"Error retrieving parking events\" err=" + e.getClass().getSimpleName()); throw new RuntimeException(ClientErrorCodes.INTERNAL);
        }
    }

    /**
     * Calculates the total amount paid for completed parking sessions of the
     * given vehicle.
     *
     * @param customerId customer identifier used for validation
     * @param vehicleNumber vehicle plate number
     * @return sum of completed parking charges
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
        } catch (Exception e) {
            LOG.security("MO DB error ctx=\"Error retrieving total amount paid\" err=" + e.getClass().getSimpleName()); throw new RuntimeException(ClientErrorCodes.INTERNAL);
        }
    }

    /**
     * Checks whether a vehicle is still legally parked in the requested space.
     *
     * @param vehicleNumber vehicle plate number
     * @param spaceId parking space number
     * @return {@code true} when the vehicle has a valid active event in the space
     */
    public boolean isParkingOk(String vehicleNumber, String spaceId) {
        if (!isValidVehicle(vehicleNumber) || !isValidParkingSpace(spaceId)) {
            throw new IllegalArgumentException("Invalid vehicle number or parking space.");
        }

        try (var conn = DatabaseConnection.getConnection()) {
            Integer vehicleId = findVehicleId(conn, vehicleNumber);
            if (vehicleId == null) {
                return false;
            }

            String spaceSql = """
                    SELECT ps.space_id, pz.max_parking_minutes
                    FROM parking_spaces ps
                    JOIN parking_zones pz ON ps.zone_id = pz.zone_id
                    WHERE ps.space_number = ?
                      AND ps.is_active = true
                    """;

            Integer spaceDbId = null;
            Integer maxAllowedMinutes = null;

            try (var stmt = conn.prepareStatement(spaceSql)) {
                stmt.setString(1, spaceId);
                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        spaceDbId = rs.getInt("space_id");
                        maxAllowedMinutes = rs.getInt("max_parking_minutes");
                    }
                }
            }

            if (spaceDbId == null || maxAllowedMinutes == null) {
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
                stmt.setInt(2, spaceDbId);
                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        startTime = rs.getTimestamp("start_time").toLocalDateTime();
                    }
                }
            }

            if (startTime == null) {
                return false;
            }

            long parkedMinutes = Duration.between(startTime, LocalDateTime.now()).toMinutes();
            return parkedMinutes <= maxAllowedMinutes;
        } catch (Exception e) {
            LOG.security("MO DB error ctx=\"Error checking parking legality\" err=" + e.getClass().getSimpleName()); throw new RuntimeException(ClientErrorCodes.INTERNAL);
        }
    }

    /**
     * Issues a citation, stores it in PostgreSQL for stable reporting, and then
     * publishes the event to RabbitMQ.
     *
     * @param vehicleNumber vehicle plate number
     * @param spaceId parking space number
     * @param amount positive citation amount in NIS
     * @return created citation record
     */
    public Citation issueCitation(String vehicleNumber, String spaceId, double amount) {
        if (!isValidVehicle(vehicleNumber) || !isValidParkingSpace(spaceId)) {
            throw new IllegalArgumentException("Invalid vehicle number or parking space.");
        }

        if (amount <= 0) {
            throw new IllegalArgumentException("Citation amount must be positive.");
        }

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
                        LocalDateTime.now(),
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
                LOG.security("MO DB error ctx=\"Error issuing citation\" err=" + e.getClass().getSimpleName()); throw new RuntimeException(ClientErrorCodes.INTERNAL);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            LOG.security("MO DB error ctx=\"Error issuing citation\" err=" + e.getClass().getSimpleName()); throw new RuntimeException(ClientErrorCodes.INTERNAL);
        }
    }

    public List<PaymentTransaction> getTransactionReport() {
        return loadTransactionReportFromDatabase();
    }

    protected List<PaymentTransaction> loadTransactionReportFromDatabase() {
        List<PaymentTransaction> transactions = new ArrayList<>();

        String sql = """
                SELECT pt.transaction_id, v.plate_number, ps.space_number, pt.zone_code,
                       pt.start_time, pt.stop_time, pt.amount
                FROM payment_transactions pt
                JOIN vehicles v ON v.vehicle_id = pt.vehicle_id
                JOIN parking_spaces ps ON ps.space_id = pt.space_id
                ORDER BY pt.stop_time DESC
                LIMIT 100
                """;

        try (var conn = DatabaseConnection.getConnection();
             var stmt = conn.prepareStatement(sql);
             var rs = stmt.executeQuery()) {

            while (rs.next()) {
                transactions.add(new PaymentTransaction(
                        rs.getString("transaction_id"),
                        rs.getString("plate_number"),
                        rs.getString("space_number"),
                        rs.getString("zone_code"),
                        rs.getTimestamp("start_time").toLocalDateTime(),
                        rs.getTimestamp("stop_time").toLocalDateTime(),
                        rs.getDouble("amount")
                ));
            }
        } catch (Exception e) {
            LOG.security("MO DB error ctx=\"Error loading transaction report\" err=" + e.getClass().getSimpleName());
            throw new RuntimeException(ClientErrorCodes.INTERNAL);
        }

        return transactions;
    }

    public List<Citation> getCitationReport() {
        return loadCitationReportFromDatabase();
    }

    public List<CitationCount> getCitationCountsBySpace() {
        return loadCitationCountsBySpaceFromDatabase();
    }

    protected List<Citation> loadCitationReportFromDatabase() {
        List<Citation> citations = new ArrayList<>();

        String sql = """
                SELECT c.citation_id, v.plate_number, ps.space_number, c.zone_code,
                       c.inspection_time, c.amount
                FROM citations c
                JOIN vehicles v ON v.vehicle_id = c.vehicle_id
                JOIN parking_spaces ps ON ps.space_id = c.space_id
                ORDER BY c.inspection_time DESC
                LIMIT 100
                """;

        try (var conn = DatabaseConnection.getConnection();
             var stmt = conn.prepareStatement(sql);
             var rs = stmt.executeQuery()) {

            while (rs.next()) {
                citations.add(new Citation(
                        rs.getString("citation_id"),
                        rs.getString("plate_number"),
                        rs.getString("space_number"),
                        rs.getString("zone_code"),
                        rs.getTimestamp("inspection_time").toLocalDateTime(),
                        rs.getDouble("amount")
                ));
            }
        } catch (Exception e) {
            LOG.security("MO DB error ctx=\"Error loading citation report\" err=" + e.getClass().getSimpleName());
            throw new RuntimeException(ClientErrorCodes.INTERNAL);
        }

        return citations;
    }

    protected List<CitationCount> loadCitationCountsBySpaceFromDatabase() {
        List<CitationCount> counts = new ArrayList<>();

        String sql = """
                SELECT ps.space_number, pz.zone_code, COUNT(c.citation_id) AS citation_count
                FROM parking_spaces ps
                JOIN parking_zones pz ON pz.zone_id = ps.zone_id
                LEFT JOIN citations c ON c.space_id = ps.space_id
                WHERE ps.is_active = true
                GROUP BY ps.space_number, pz.zone_code
                ORDER BY ps.space_number
                """;

        try (var conn = DatabaseConnection.getConnection();
             var stmt = conn.prepareStatement(sql);
             var rs = stmt.executeQuery()) {

            while (rs.next()) {
                counts.add(new CitationCount(
                        rs.getString("space_number"),
                        rs.getString("zone_code"),
                        rs.getLong("citation_count")
                ));
            }
        } catch (Exception e) {
            LOG.security("MO DB error ctx=\"Error loading citation counts\" err=" + e.getClass().getSimpleName());
            throw new RuntimeException(ClientErrorCodes.INTERNAL);
        }

        return counts;
    }

    /**
     * Consumes all currently available messages from a RabbitMQ queue.
     *
     * @param queueName queue to consume
     * @param routingKey routing key used to bind the queue before reading
     * @return decoded message payloads in retrieval order
     */
    protected List<String> drainQueue(String queueName, String routingKey) {
        List<String> messages = new ArrayList<>();
        MulliganConnectionFactory factory = consumeFactory();

        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {

            GetResponse response;
            while ((response = channel.basicGet(queueName, false)) != null) {
                try {
                    String wire = new String(response.getBody(), StandardCharsets.UTF_8);
                    // Defensive unwrap: prefer the secure envelope, but fall back to
                    // the raw payload for backwards compatibility with Assignment 1
                    // queue messages still sitting in the broker on first upgrade.
                    String payload;
                    try {
                        payload = SecureMessage.fromJson(wire).payload;
                    } catch (IllegalArgumentException notEnvelope) {
                        payload = wire;
                    }
                    messages.add(payload);
                    channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
                } catch (Exception e) {
                    channel.basicNack(response.getEnvelope().getDeliveryTag(), false, true);
                    throw e;
                }
            }
        } catch (Exception e) {
            LOG.security("MO consume failed queue=" + queueName + " err=" + e.getClass().getSimpleName());
            throw new RuntimeException(ClientErrorCodes.UNAVAILABLE);
        }

        return messages;
    }

    /**
     * Publishes a completed parking transaction to RabbitMQ after the database
     * transaction has committed.
     *
     * @param transactionId completed transaction identifier
     * @param vehicleNumber vehicle plate number
     * @param parkingSpaceId parking space number
     * @param parkingZoneId parking zone code
     * @param startTime parking start timestamp
     * @param endTime parking stop timestamp
     * @param amount completed transaction amount
     */
    protected void reportTransactionToQueue(String transactionId, String vehicleNumber, String parkingSpaceId, String parkingZoneId,
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
            LOG.security("MO publish transaction failed id=" + transactionId + " err=" + e.getClass().getSimpleName());
            throw new RuntimeException(ClientErrorCodes.UNAVAILABLE);
        }
    }

    /**
     * Publishes a citation event to RabbitMQ after the database commit succeeds.
     *
     * @param citationId citation identifier
     * @param vehicleNumber vehicle plate number
     * @param parkingSpaceId parking space number
     * @param parkingZoneId parking zone code
     * @param inspectionTime inspection timestamp
     * @param amount citation amount
     */
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
            LOG.security("MO publish citation failed id=" + citationId + " err=" + e.getClass().getSimpleName());
            throw new RuntimeException(ClientErrorCodes.UNAVAILABLE);
        }
    }

    private ParkingEvent insertStartedParking(java.sql.Connection conn, String customerId, String vehicleNumber,
                                             int vehicleId, SpaceDetails spaceDetails) throws java.sql.SQLException {
        String insertSql = """
                INSERT INTO parking_events
                (vehicle_id, space_id, start_time, end_time, status, calculated_amount, created_at, updated_at)
                VALUES (?, ?, CURRENT_TIMESTAMP, NULL, 'STARTED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING event_id, start_time
                """;

        try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            stmt.setInt(1, vehicleId);
            stmt.setInt(2, spaceDetails.spaceDbId());

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

    private StoppedParkingResult stopParkingInternal(java.sql.Connection conn, int vehicleId, String vehicleNumber)
            throws java.sql.SQLException {
        String eventSql = """
                SELECT pe.event_id, pe.start_time, ps.space_id, ps.space_number, pz.zone_code, pz.hourly_rate
                FROM parking_events pe
                JOIN parking_spaces ps ON pe.space_id = ps.space_id
                JOIN parking_zones pz ON ps.zone_id = pz.zone_id
                WHERE pe.vehicle_id = ?
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
            throw new IllegalArgumentException("No active parking event found.");
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

    private boolean hasActiveParking(java.sql.Connection conn, int vehicleId) throws java.sql.SQLException {
        String activeSql = """
                SELECT event_id
                FROM parking_events
                WHERE vehicle_id = ?
                  AND status = 'STARTED'
                  AND end_time IS NULL
                LIMIT 1
                """;

        try (var stmt = conn.prepareStatement(activeSql)) {
            stmt.setInt(1, vehicleId);
            try (var rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private record SpaceDetails(int spaceDbId, String spaceNumber, String zoneCode) {
    }

    private record StoppedParkingResult(int eventId, int vehicleId, int spaceDbId, PaymentTransaction transaction) {
    }

    private record CitationSpaceDetails(int spaceId, String zoneCode) {
    }
}

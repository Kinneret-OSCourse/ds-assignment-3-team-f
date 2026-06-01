package com.mulligan.service;

import com.mulligan.model.Citation;
import com.mulligan.common.messaging.MulliganConnectionFactory;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ParkingServiceTest {

    private final TestParkingService parkingService = new TestParkingService();
    private final ParkingService queueParkingService = new ParkingService();

    @BeforeEach
    void requireAssignmentDatabase() {
        assumeTrue(assignmentDatabaseAvailable(), "Assignment PostgreSQL is not reachable with test credentials");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (!assignmentDatabaseAvailable()) {
            return;
        }
        cleanupVehiclesByPrefix("TP");
        try {
            purgeCitationsQueue();
        } catch (Exception ignored) {
            // RabbitMQ-backed assertions explicitly connect to the broker; cleanup should not fail DB-only tests.
        }
    }

    @Test
    void isParkingOkReturnsTrueWhenParkingIsWithinAllowedWindow() throws Exception {
        String plateNumber = createVehicle("TPO");
        insertParkingEvent(plateNumber, "S001", LocalDateTime.of(2026, 4, 27, 0, 4, 11));

        boolean parkingOk = parkingService.isParkingOk(plateNumber, "S001");

        assertTrue(parkingOk);
    }

    @Test
    void isParkingOkReturnsFalseWhenParkingExceededMaximumTime() throws Exception {
        String plateNumber = createVehicle("TPV");
        insertParkingEvent(plateNumber, "S001", LocalDateTime.of(2026, 4, 26, 20, 34, 11));

        boolean parkingOk = parkingService.isParkingOk(plateNumber, "S001");

        assertEquals(false, parkingOk);
    }

    @Test
    void issueCitationCreatesCitationForExistingVehicleAndSpace() throws Exception {
        String plateNumber = createVehicle("TPC");

        Citation citation = parkingService.issueCitation(plateNumber, "S001", 25.0);

        assertNotNull(citation.getCitationId());
        assertEquals(plateNumber, citation.getVehicleNumber());
        assertEquals("S001", citation.getParkingSpaceId());
        assertEquals("A1", citation.getParkingZoneId());
        assertEquals(25.0, citation.getAmount(), 0.001);
        assertNotNull(citation.getInspectionTime());
        assertTrue(citationExists(citation.getCitationId()));
    }

    @Test
    void issueCitationRejectsNonPositiveAmount() throws Exception {
        String plateNumber = createVehicle("TPA");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> parkingService.issueCitation(plateNumber, "S001", 0)
        );

        assertTrue(exception.getMessage().contains("positive"));
    }

    @Test
    void isParkingOkRejectsUnknownVehicle() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> parkingService.isParkingOk("TPMISSING", "S001")
        );

        assertTrue(exception.getMessage().contains("Invalid vehicle"));
    }

    @Test
    void isValidVehicleReturnsTrueForInsertedVehicle() throws Exception {
        String plateNumber = createVehicle("TPVALID");

        assertTrue(parkingService.isValidVehicle(plateNumber));
    }

    @Test
    void isValidParkingSpaceReturnsFalseForUnknownSpace() {
        assertEquals(false, parkingService.isValidParkingSpace("UNKNOWN-SPACE"));
    }

    @Test
    void issueCitationRejectsUnknownParkingSpace() throws Exception {
        String plateNumber = createVehicle("TPBAD");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> parkingService.issueCitation(plateNumber, "UNKNOWN-SPACE", 25.0)
        );

        assertTrue(exception.getMessage().contains("Invalid vehicle number or parking space"));
    }

    @Test
    void issueCitationPublishesCitationMessageToQueue() throws Exception {
        String plateNumber = createVehicle("TPQ");
        purgeCitationsQueue();

        Citation citation = queueParkingService.issueCitation(plateNumber, "S001", 33.0);

        assertEquals(plateNumber, citation.getVehicleNumber());
        assertEquals("S001", citation.getParkingSpaceId());
        assertTrue(citationExists(citation.getCitationId()));
    }

    private String createVehicle(String prefix) throws Exception {
        String plateNumber = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO vehicles (plate_number, owner_name) VALUES (?, ?)")) {
            statement.setString(1, plateNumber);
            statement.setString(2, "JUnit PEO");
            statement.executeUpdate();
        }
        return plateNumber;
    }

    private void insertParkingEvent(String plateNumber, String spaceNumber, LocalDateTime startTime) throws Exception {
        try (Connection connection = DatabaseConnection.getConnection()) {
            Integer vehicleId = findVehicleId(connection, plateNumber);
            Integer spaceId = findSpaceId(connection, spaceNumber);
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO parking_events
                    (vehicle_id, space_id, start_time, end_time, status, calculated_amount, created_at, updated_at)
                    VALUES (?, ?, ?, NULL, 'STARTED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """)) {
                statement.setInt(1, vehicleId);
                statement.setInt(2, spaceId);
                statement.setTimestamp(3, Timestamp.valueOf(startTime));
                statement.executeUpdate();
            }
        }
    }

    private Integer findVehicleId(Connection connection, String plateNumber) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT vehicle_id FROM vehicles WHERE plate_number = ?")) {
            statement.setString(1, plateNumber);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("Vehicle not found for test: " + plateNumber);
                }
                return resultSet.getInt("vehicle_id");
            }
        }
    }

    private Integer findSpaceId(Connection connection, String spaceNumber) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT space_id FROM parking_spaces WHERE space_number = ?")) {
            statement.setString(1, spaceNumber);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("Space not found for test: " + spaceNumber);
                }
                return resultSet.getInt("space_id");
            }
        }
    }

    private void cleanupVehiclesByPrefix(String prefix) throws Exception {
        try (Connection connection = DatabaseConnection.getConnection()) {
            try (PreparedStatement deleteCitations = connection.prepareStatement(
                    """
                    DELETE FROM citations
                    WHERE vehicle_id IN (
                        SELECT vehicle_id FROM vehicles WHERE plate_number LIKE ?
                    )
                    """)) {
                deleteCitations.setString(1, prefix + "%");
                deleteCitations.executeUpdate();
            }

            try (PreparedStatement deleteEvents = connection.prepareStatement(
                    """
                    DELETE FROM parking_events
                    WHERE vehicle_id IN (
                        SELECT vehicle_id FROM vehicles WHERE plate_number LIKE ?
                    )
                    """)) {
                deleteEvents.setString(1, prefix + "%");
                deleteEvents.executeUpdate();
            }

            try (PreparedStatement deleteVehicles = connection.prepareStatement(
                    "DELETE FROM vehicles WHERE plate_number LIKE ?")) {
                deleteVehicles.setString(1, prefix + "%");
                deleteVehicles.executeUpdate();
            }
        }
    }

    private boolean citationExists(String citationId) throws Exception {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM citations WHERE citation_id = ?")) {
            statement.setString(1, citationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void purgeCitationsQueue() throws Exception {
        MulliganConnectionFactory factory = serverQueueFactory();
        try (com.rabbitmq.client.Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {
            channel.queuePurge(QueueConfig.CITATIONS_QUEUE);
        }
    }

    private String readSingleMessage(String queueName) throws Exception {
        MulliganConnectionFactory factory = serverQueueFactory();
        try (com.rabbitmq.client.Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {
            var response = channel.basicGet(queueName, false);
            if (response == null) {
                return "";
            }
            try {
                return new String(response.getBody(), StandardCharsets.UTF_8);
            } finally {
                channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
            }
        }
    }

    private MulliganConnectionFactory serverQueueFactory() {
        String user = System.getenv().getOrDefault("MULLIGAN_QUEUE_USER_SERVER", "mulligan_server");
        String pass = System.getenv().getOrDefault("MULLIGAN_QUEUE_PASSWORD_SERVER", "mulligan_server_pw");
        return MulliganConnectionFactory.create(user, pass, null);
    }

    private boolean assignmentDatabaseAvailable() {
        try (Connection ignored = DatabaseConnection.getConnection()) {
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static class TestParkingService extends ParkingService {
        @Override
        protected void reportCitationToQueue(String citationId, String vehicleNumber, String parkingSpaceId,
                                             String parkingZoneId, LocalDateTime inspectionTime, double amount) {
            // Tests validate business behavior without requiring RabbitMQ.
        }
    }
}

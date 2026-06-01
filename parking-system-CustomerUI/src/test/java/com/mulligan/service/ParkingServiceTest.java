package com.mulligan.service;

import com.mulligan.model.ParkingEvent;
import com.mulligan.model.PaymentTransaction;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        cleanupCustomersByPrefix("TC");
        cleanupVehiclesByPrefix("TC");
        try {
            purgeTransactionsQueue();
        } catch (Exception ignored) {
            // RabbitMQ-backed assertions explicitly connect to the broker; cleanup should not fail DB-only tests.
        }
    }

    @Test
    void startParkingCreatesStartedEvent() throws Exception {
        String plateNumber = createVehicle("TCS");
        createCustomerWithAssignedVehicle("TCCUSTSTART", plateNumber);

        ParkingEvent event = parkingService.startParking("TCCUSTSTART", plateNumber, "S001");

        assertNotNull(event.getEventId());
        assertEquals("TCCUSTSTART", event.getCustomerId());
        assertEquals(plateNumber, event.getVehicleNumber());
        assertEquals("S001", event.getParkingSpaceId());
        assertEquals("STARTED", event.getStatus());
        assertNull(event.getEndTime());

        assertTrue(hasActiveParking(plateNumber));
    }

    @Test
    void startParkingAutoStopsExistingActiveParkingForSameCustomerVehicle() throws Exception {
        String plateNumber = createVehicle("TCA");
        createCustomerWithAssignedVehicle("TCCUSTAUTO", plateNumber);
        insertParkingEvent("TCCUSTAUTO", plateNumber, "S001", "STARTED", LocalDateTime.now().minusMinutes(150), null, 0);

        ParkingEvent newEvent = parkingService.startParking("TCCUSTAUTO", plateNumber, "S002");

        assertEquals("STARTED", newEvent.getStatus());
        assertEquals("S002", newEvent.getParkingSpaceId());
        assertEquals(1, countActiveParkingEvents(plateNumber));
        assertEquals(1, countStoppedParkingEvents(plateNumber));
        assertEquals(1, countPaymentTransactions(plateNumber));
        assertTrue(getStoppedAmountTotal(plateNumber) > 0);
    }

    @Test
    void startParkingAllowsDifferentSpaceAfterCustomerStops() throws Exception {
        String plateNumber = createVehicle("TCR");
        createCustomerWithAssignedVehicle("TCCUSTREPARK", plateNumber);
        parkingService.startParking("TCCUSTREPARK", plateNumber, "S001");

        parkingService.stopParking("TCCUSTREPARK", plateNumber);
        ParkingEvent newEvent = parkingService.startParking("TCCUSTREPARK", plateNumber, "S002");

        assertEquals("STARTED", newEvent.getStatus());
        assertEquals("S002", newEvent.getParkingSpaceId());
        assertEquals(1, countActiveParkingEvents(plateNumber));
        assertEquals(1, countStoppedParkingEvents(plateNumber));
    }

    @Test
    void stopParkingStopsEventAndCalculatesAmount() throws Exception {
        String plateNumber = createVehicle("TCX");
        createCustomerWithAssignedVehicle("TCCUSTSTOP", plateNumber);
        insertParkingEvent("TCCUSTSTOP", plateNumber, "S003", "STARTED", LocalDateTime.now().minusMinutes(61), null, 0);

        PaymentTransaction transaction = parkingService.stopParking("TCCUSTSTOP", plateNumber);

        assertEquals(plateNumber, transaction.getVehicleNumber());
        assertEquals("S003", transaction.getParkingSpaceId());
        assertNotNull(transaction.getStopTime());
        assertTrue(transaction.getAmount() >= 8.0);
        assertEquals(0, countActiveParkingEvents(plateNumber));
        assertEquals(1, countStoppedParkingEvents(plateNumber));
        assertEquals(1, countPaymentTransactions(plateNumber));
    }

    @Test
    void getParkingEventsReturnsStoredEvents() throws Exception {
        String plateNumber = createVehicle("TCE");
        insertParkingEvent(plateNumber, "S004", "STOPPED", LocalDateTime.now().minusHours(3), LocalDateTime.now().minusHours(2), 8.0);
        insertParkingEvent(plateNumber, "S005", "STARTED", LocalDateTime.now().minusMinutes(15), null, 0);

        List<ParkingEvent> events = parkingService.getParkingEvents("customer-4", plateNumber);

        assertEquals(2, events.size());
        assertEquals("S005", events.get(0).getParkingSpaceId());
        assertEquals("STARTED", events.get(0).getStatus());
        assertEquals("S004", events.get(1).getParkingSpaceId());
        assertEquals("STOPPED", events.get(1).getStatus());
    }

    @Test
    void getTotalAmountPaidSumsStoppedEventsOnly() throws Exception {
        String plateNumber = createVehicle("TCT");
        insertParkingEvent(plateNumber, "S006", "STOPPED", LocalDateTime.now().minusHours(5), LocalDateTime.now().minusHours(4), 5.50);
        insertParkingEvent(plateNumber, "S007", "STOPPED", LocalDateTime.now().minusHours(3), LocalDateTime.now().minusHours(2), 7.25);
        insertParkingEvent(plateNumber, "S008", "STARTED", LocalDateTime.now().minusMinutes(20), null, 0);

        double total = parkingService.getTotalAmountPaid("customer-5", plateNumber);

        assertEquals(12.75, total, 0.001);
    }

    @Test
    void startParkingRejectsUnknownParkingSpace() throws Exception {
        String plateNumber = createVehicle("TCSPACE");
        createCustomerWithAssignedVehicle("TCCUSTSPACE", plateNumber);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> parkingService.startParking("TCCUSTSPACE", plateNumber, "NOSUCHSPACE")
        );

        assertNotNull(exception.getMessage());
    }

    @Test
    void startParkingRejectsVehicleNotAssignedToKnownCustomer() throws Exception {
        String assignedPlateNumber = createVehicle("TCASSIGN");
        String requestedPlateNumber = createVehicle("TCOTHER");
        createCustomerWithAssignedVehicle("TC-CUST-1", assignedPlateNumber);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> parkingService.startParking("TC-CUST-1", requestedPlateNumber, "S001")
        );

        assertTrue(exception.getMessage().contains("not assigned"));
    }

    @Test
    void getParkingEventsReturnsEmptyListForUnknownVehicle() {
        List<ParkingEvent> events = parkingService.getParkingEvents("customer-9", "UNKNOWN-VEHICLE");

        assertTrue(events.isEmpty());
    }

    @Test
    void getTotalAmountPaidReturnsZeroForUnknownVehicle() {
        double total = parkingService.getTotalAmountPaid("customer-10", "UNKNOWN-VEHICLE");

        assertEquals(0.0, total, 0.001);
    }

    @Test
    void stopParkingFailsWhenNoActiveParkingExists() throws Exception {
        String plateNumber = createVehicle("TCN");
        createCustomerWithAssignedVehicle("TCCUSTNONE", plateNumber);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> parkingService.stopParking("TCCUSTNONE", plateNumber)
        );

        assertTrue(exception.getMessage().contains("no open parking event"));
    }

    @Test
    void stopParkingPublishesTransactionMessageToQueue() throws Exception {
        String plateNumber = createVehicle("TCQ");
        createCustomerWithAssignedVehicle("TCCUSTQUEUE", plateNumber);
        insertParkingEvent("TCCUSTQUEUE", plateNumber, "S009", "STARTED", LocalDateTime.now().minusMinutes(65), null, 0);

        PaymentTransaction transaction = queueParkingService.stopParking("TCCUSTQUEUE", plateNumber);

        assertEquals(plateNumber, transaction.getVehicleNumber());
        assertEquals("S009", transaction.getParkingSpaceId());
        assertEquals(1, countPaymentTransactions(plateNumber));
    }

    private String createVehicle(String prefix) throws Exception {
        String plateNumber = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO vehicles (plate_number, owner_name) VALUES (?, ?)")) {
            statement.setString(1, plateNumber);
            statement.setString(2, "JUnit Customer");
            statement.executeUpdate();
        }
        return plateNumber;
    }

    private void createCustomerWithAssignedVehicle(String customerId, String plateNumber) throws Exception {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                     INSERT INTO customers
                     (customer_id, vehicle_id, display_name, password_hash, password_salt, password_iterations, is_active)
                     VALUES (?, (SELECT vehicle_id FROM vehicles WHERE plate_number = ?), ?, ?, ?, ?, true)
                     ON CONFLICT (customer_id) DO UPDATE SET vehicle_id = EXCLUDED.vehicle_id
                     """)) {
            statement.setString(1, customerId);
            statement.setString(2, plateNumber);
            statement.setString(3, "JUnit Customer");
            statement.setString(4, "00");
            statement.setString(5, "00");
            statement.setInt(6, 1);
            statement.executeUpdate();
        }
    }

    private void insertParkingEvent(String plateNumber, String spaceNumber, String status,
                                    LocalDateTime startTime, LocalDateTime endTime, double amount) throws Exception {
        insertParkingEvent(null, plateNumber, spaceNumber, status, startTime, endTime, amount);
    }

    private void insertParkingEvent(String customerId, String plateNumber, String spaceNumber, String status,
                                    LocalDateTime startTime, LocalDateTime endTime, double amount) throws Exception {
        try (Connection connection = DatabaseConnection.getConnection()) {
            Integer vehicleId = findVehicleId(connection, plateNumber);
            Integer spaceId = findSpaceId(connection, spaceNumber);
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO parking_events
                    (vehicle_id, space_id, customer_id, start_time, end_time, status, calculated_amount, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """)) {
                statement.setInt(1, vehicleId);
                statement.setInt(2, spaceId);
                statement.setString(3, customerId);
                statement.setTimestamp(4, Timestamp.valueOf(startTime));
                statement.setTimestamp(5, endTime == null ? null : Timestamp.valueOf(endTime));
                statement.setString(6, status);
                statement.setDouble(7, amount);
                statement.executeUpdate();
            }
        }
    }

    private boolean hasActiveParking(String plateNumber) throws Exception {
        return countActiveParkingEvents(plateNumber) == 1;
    }

    private int countActiveParkingEvents(String plateNumber) throws Exception {
        return countEvents(plateNumber, "STARTED", true);
    }

    private int countStoppedParkingEvents(String plateNumber) throws Exception {
        return countEvents(plateNumber, "STOPPED", false);
    }

    private int countPaymentTransactions(String plateNumber) throws Exception {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                     SELECT COUNT(*)
                     FROM payment_transactions pt
                     JOIN vehicles v ON pt.vehicle_id = v.vehicle_id
                     WHERE v.plate_number = ?
                     """)) {
            statement.setString(1, plateNumber);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private int countEvents(String plateNumber, String status, boolean endTimeNull) throws Exception {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                     SELECT COUNT(*)
                     FROM parking_events pe
                     JOIN vehicles v ON pe.vehicle_id = v.vehicle_id
                     WHERE v.plate_number = ?
                       AND pe.status = ?
                       AND ((? = true AND pe.end_time IS NULL) OR (? = false AND pe.end_time IS NOT NULL))
                     """)) {
            statement.setString(1, plateNumber);
            statement.setString(2, status);
            statement.setBoolean(3, endTimeNull);
            statement.setBoolean(4, endTimeNull);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private double getStoppedAmountTotal(String plateNumber) throws Exception {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                     SELECT COALESCE(SUM(pe.calculated_amount), 0)
                     FROM parking_events pe
                     JOIN vehicles v ON pe.vehicle_id = v.vehicle_id
                     WHERE v.plate_number = ?
                       AND pe.status = 'STOPPED'
                     """)) {
            statement.setString(1, plateNumber);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getDouble(1);
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
            try (PreparedStatement deleteTransactions = connection.prepareStatement(
                    """
                    DELETE FROM payment_transactions
                    WHERE vehicle_id IN (
                        SELECT vehicle_id FROM vehicles WHERE plate_number LIKE ?
                    )
                    """)) {
                deleteTransactions.setString(1, prefix + "%");
                deleteTransactions.executeUpdate();
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

    private void cleanupCustomersByPrefix(String prefix) throws Exception {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM customers WHERE customer_id LIKE ?")) {
            statement.setString(1, prefix + "%");
            statement.executeUpdate();
        }
    }

    private void purgeTransactionsQueue() throws Exception {
        MulliganConnectionFactory factory = serverQueueFactory();
        try (com.rabbitmq.client.Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {
            channel.queuePurge(QueueConfig.TRANSACTIONS_QUEUE);
        }
    }

    private boolean assignmentDatabaseAvailable() {
        try (Connection ignored = DatabaseConnection.getConnection()) {
            return true;
        } catch (Exception ignored) {
            return false;
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

    private static class TestParkingService extends ParkingService {
        @Override
        protected void reportToQueue(String transactionId, String vehicleNumber, String parkingSpaceId, String parkingZoneId,
                                     LocalDateTime startTime, LocalDateTime endTime, double amount) {
            // Tests validate DB behavior without depending on a live RabbitMQ broker.
        }
    }

    private MulliganConnectionFactory serverQueueFactory() {
        String user = System.getenv().getOrDefault("MULLIGAN_QUEUE_USER_SERVER", "mulligan_server");
        String pass = System.getenv().getOrDefault("MULLIGAN_QUEUE_PASSWORD_SERVER", "mulligan_server_pw");
        return MulliganConnectionFactory.create(user, pass, null);
    }
}

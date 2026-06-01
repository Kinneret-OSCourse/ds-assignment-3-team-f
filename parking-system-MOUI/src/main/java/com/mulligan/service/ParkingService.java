package com.mulligan.service;

import com.mulligan.model.ParkingEvent;
import com.mulligan.model.ParkingSpace;
import com.mulligan.model.ParkingZone;
import com.mulligan.model.PaymentTransaction;
import com.mulligan.model.Vehicle;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ParkingService {

    private static final String DEFAULT_DB_URL = "jdbc:postgresql://localhost:5432/mulligan_db";
    private static final String DEFAULT_DB_USER = "postgres";
    private static final String DEFAULT_DB_PASSWORD = "pass159357";

    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;

    public ParkingService() {
        dbUrl = getConfiguredValue("DB_URL", DEFAULT_DB_URL);
        dbUser = getConfiguredValue("DB_USER", DEFAULT_DB_USER);
        dbPassword = getConfiguredValue("DB_PASSWORD", DEFAULT_DB_PASSWORD);
        validateDatabaseConnection();
    }

    public ParkingEvent startParking(String customerId, String vehicleNumber, String spaceId) {
        if (vehicleNumber == null || spaceId == null) {
            throw new IllegalArgumentException("Invalid input.");
        }

        Vehicle vehicle = getVehicle(vehicleNumber);
        String vehicleId = getVehicleId(vehicleNumber);
        ParkingSpace parkingSpace = getParkingSpace(spaceId);

        if (vehicle == null || vehicleId == null || parkingSpace == null || !parkingSpace.isActive()) {
            throw new IllegalArgumentException("Invalid input.");
        }

        if (customerId != null && !customerId.trim().isEmpty() && !customerId.equals(vehicle.getCustomerId())) {
            throw new IllegalArgumentException("Invalid input.");
        }

        if (findActiveParkingEvent(vehicleNumber) != null) {
            throw new IllegalArgumentException("Parking already active for this vehicle.");
        }

        LocalDateTime now = LocalDateTime.now();
        ParkingEvent event = new ParkingEvent(
                UUID.randomUUID().toString(),
                vehicle.getCustomerId(),
                vehicle.getVehicleNumber(),
                parkingSpace.getSpaceId(),
                parkingSpace.getZoneId(),
                now,
                null,
                "STARTED"
        );

        String sql = "INSERT INTO parking_events " +
                "(event_id, vehicle_id, space_id, start_time, end_time, status, calculated_amount, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (java.sql.Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, event.getEventId());
            statement.setString(2, vehicleId);
            statement.setString(3, event.getParkingSpaceId());
            statement.setTimestamp(4, Timestamp.valueOf(event.getStartTime()));
            statement.setNull(5, Types.TIMESTAMP);
            statement.setString(6, event.getStatus());
            statement.setNull(7, Types.DOUBLE);
            statement.setTimestamp(8, Timestamp.valueOf(now));
            statement.setTimestamp(9, Timestamp.valueOf(now));
            statement.executeUpdate();
            return event;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to start parking.", e);
        }
    }

    public PaymentTransaction stopParking(String customerId, String vehicleNumber) {
        ParkingEvent event = findActiveParkingEvent(vehicleNumber);
        if (event == null) {
            throw new IllegalArgumentException("No active parking found.");
        }

        if (customerId != null && !customerId.trim().isEmpty() && !customerId.equals(event.getCustomerId())) {
            throw new IllegalArgumentException("No active parking found.");
        }

        LocalDateTime stopTime = LocalDateTime.now();
        double amount = calculatePayment(event.getParkingZoneId(), event.getStartTime(), stopTime);

        String sql = "UPDATE parking_events SET end_time = ?, status = ?, calculated_amount = ?, updated_at = ? WHERE event_id = ?";

        try (java.sql.Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.valueOf(stopTime));
            statement.setString(2, "STOPPED");
            statement.setDouble(3, amount);
            statement.setTimestamp(4, Timestamp.valueOf(stopTime));
            statement.setString(5, event.getEventId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to stop parking.", e);
        }

        PaymentTransaction transaction = new PaymentTransaction(
                event.getEventId(),
                vehicleNumber,
                event.getParkingSpaceId(),
                event.getParkingZoneId(),
                event.getStartTime(),
                stopTime,
                amount
        );

        reportToQueue(vehicleNumber, event.getParkingSpaceId(), event.getParkingZoneId(), event.getStartTime(), stopTime, amount);
        return transaction;
    }

    public void reportToQueue(String vehicleNumber, String parkingspaceid, String parkingZoneID, LocalDateTime starttime, LocalDateTime endtime, double amount) {
        String EXCHANGE_NAME = "mulligan.exchange";
        String ROUTING_KEY = "transaction.completed";

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("5.tcp.eu.ngrok.io");
        factory.setPort(13483);
        factory.setUsername("fares");
        factory.setPassword("fares123");

        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {
            channel.exchangeDeclare(EXCHANGE_NAME, "direct", true);
            String message = String.format(
                    "{\"vehicleNumber\":\"%s\", \"spaceId\":\"%s\", \"zone\":\"%s\", " +
                            "\"date\":\"%s\", \"startTime\":\"%s\", \"endTime\":\"%s\", \"amount\":%.2f}",
                    vehicleNumber,
                    parkingspaceid,
                    parkingZoneID,
                    endtime.toLocalDate(),
                    starttime,
                    endtime,
                    amount
            );
            channel.basicPublish(EXCHANGE_NAME, ROUTING_KEY, null, message.getBytes());

            System.out.println("Citation event sent to exchange: " + message);
        } catch (Exception e) {
            System.err.println("Failed to send citation to queue: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public List<ParkingEvent> getParkingEvents(String customerId, String vehicleNumber) {
        List<ParkingEvent> result = new ArrayList<ParkingEvent>();
        String sql = "SELECT pe.event_id, v.owner_name AS customer_id, v.plate_number AS vehicle_number, " +
                "pe.space_id AS parking_space_id, ps.zone_id AS parking_zone_id, pe.start_time, pe.end_time, pe.status " +
                "FROM parking_events pe " +
                "JOIN vehicles v ON v.vehicle_id = pe.vehicle_id " +
                "JOIN parking_spaces ps ON ps.space_id = pe.space_id " +
                "WHERE v.plate_number = ? " +
                "AND (? IS NULL OR ? = '' OR v.owner_name = ?) " +
                "ORDER BY pe.start_time";

        try (java.sql.Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, vehicleNumber);
            statement.setString(2, customerId);
            statement.setString(3, customerId);
            statement.setString(4, customerId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(mapParkingEvent(resultSet));
                }
            }

            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load parking events.", e);
        }
    }

    public double getTotalAmountPaid(String customerId, String vehicleNumber) {
        String sql = "SELECT pe.start_time, pe.end_time, pe.calculated_amount, pz.hourly_rate " +
                "FROM parking_events pe " +
                "JOIN vehicles v ON v.vehicle_id = pe.vehicle_id " +
                "JOIN parking_spaces ps ON ps.space_id = pe.space_id " +
                "JOIN parking_zones pz ON pz.zone_id = ps.zone_id " +
                "WHERE v.plate_number = ? " +
                "AND (? IS NULL OR ? = '' OR v.owner_name = ?) " +
                "AND pe.end_time IS NOT NULL AND pe.status = 'STOPPED'";

        double total = 0.0;

        try (java.sql.Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, vehicleNumber);
            statement.setString(2, customerId);
            statement.setString(3, customerId);
            statement.setString(4, customerId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    double amount = resultSet.getDouble("calculated_amount");
                    if (resultSet.wasNull()) {
                        LocalDateTime startTime = resultSet.getTimestamp("start_time").toLocalDateTime();
                        LocalDateTime endTime = resultSet.getTimestamp("end_time").toLocalDateTime();
                        amount = calculatePayment(resultSet.getDouble("hourly_rate"), startTime, endTime);
                    }
                    total += amount;
                }
            }

            return total;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to calculate total amount paid.", e);
        }
    }

    public List<PaymentTransaction> getTransactionReport() {
        List<PaymentTransaction> result = new ArrayList<PaymentTransaction>();
        String sql = "SELECT pe.event_id AS transaction_id, v.plate_number, ps.space_number, pz.zone_name, " +
                "pe.start_time, pe.end_time, pe.calculated_amount " +
                "FROM parking_events pe " +
                "JOIN vehicles v ON v.vehicle_id = pe.vehicle_id " +
                "JOIN parking_spaces ps ON ps.space_id = pe.space_id " +
                "JOIN parking_zones pz ON pz.zone_id = ps.zone_id " +
                "WHERE pe.end_time IS NOT NULL AND pe.status = 'STOPPED' " +
                "ORDER BY pe.end_time";

        try (java.sql.Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                LocalDateTime startTime = resultSet.getTimestamp("start_time").toLocalDateTime();
                LocalDateTime stopTime = resultSet.getTimestamp("end_time").toLocalDateTime();

                result.add(new PaymentTransaction(
                        resultSet.getString("transaction_id"),
                        resultSet.getString("plate_number"),
                        resultSet.getString("space_number"),
                        resultSet.getString("zone_name"),
                        startTime,
                        stopTime,
                        resultSet.getDouble("calculated_amount")
                ));
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load transaction report.", e);
        }
    }

    private void validateDatabaseConnection() {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("PostgreSQL JDBC driver not found.", e);
        }

        try (java.sql.Connection connection = getConnection()) {
            if (connection == null || connection.isClosed()) {
                throw new SQLException("Database connection is not available.");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to connect to PostgreSQL database.", e);
        }
    }

    private java.sql.Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    private String getVehicleId(String vehicleNumber) {
        String sql = "SELECT vehicle_id FROM vehicles WHERE plate_number = ?";

        try (java.sql.Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, vehicleNumber);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString("vehicle_id") : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load vehicle id.", e);
        }
    }

    private Vehicle getVehicle(String vehicleNumber) {
        String sql = "SELECT plate_number, owner_name FROM vehicles WHERE plate_number = ?";

        try (java.sql.Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, vehicleNumber);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }

                return new Vehicle(
                        resultSet.getString("plate_number"),
                        resultSet.getString("owner_name")
                );
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load vehicle.", e);
        }
    }

    private ParkingSpace getParkingSpace(String spaceId) {
        String sql = "SELECT space_id, zone_id, is_active FROM parking_spaces WHERE space_id = ?";

        try (java.sql.Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, spaceId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }

                return new ParkingSpace(
                        resultSet.getString("space_id"),
                        resultSet.getString("zone_id"),
                        resultSet.getBoolean("is_active")
                );
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load parking space.", e);
        }
    }

    private ParkingZone getParkingZone(String zoneId) {
        String sql = "SELECT zone_id, zone_name, hourly_rate, max_parking_minutes FROM parking_zones WHERE zone_id = ?";

        try (java.sql.Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, zoneId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }

                return new ParkingZone(
                        resultSet.getString("zone_id"),
                        resultSet.getString("zone_name"),
                        resultSet.getDouble("hourly_rate"),
                        resultSet.getInt("max_parking_minutes")
                );
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load parking zone.", e);
        }
    }

    private ParkingEvent findActiveParkingEvent(String vehicleNumber) {
        String sql = "SELECT pe.event_id, v.owner_name AS customer_id, v.plate_number AS vehicle_number, " +
                "pe.space_id AS parking_space_id, ps.zone_id AS parking_zone_id, pe.start_time, pe.end_time, pe.status " +
                "FROM parking_events pe " +
                "JOIN vehicles v ON v.vehicle_id = pe.vehicle_id " +
                "JOIN parking_spaces ps ON ps.space_id = pe.space_id " +
                "WHERE v.plate_number = ? AND pe.end_time IS NULL AND pe.status = 'STARTED' " +
                "ORDER BY pe.start_time DESC LIMIT 1";

        try (java.sql.Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, vehicleNumber);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapParkingEvent(resultSet) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find active parking event.", e);
        }
    }

    private ParkingEvent mapParkingEvent(ResultSet resultSet) throws SQLException {
        Timestamp endTime = resultSet.getTimestamp("end_time");
        return new ParkingEvent(
                resultSet.getString("event_id"),
                resultSet.getString("customer_id"),
                resultSet.getString("vehicle_number"),
                resultSet.getString("parking_space_id"),
                resultSet.getString("parking_zone_id"),
                resultSet.getTimestamp("start_time").toLocalDateTime(),
                endTime == null ? null : endTime.toLocalDateTime(),
                resultSet.getString("status")
        );
    }

    private double calculatePayment(String zoneId, LocalDateTime startTime, LocalDateTime stopTime) {
        ParkingZone zone = getParkingZone(zoneId);
        if (zone == null) {
            return 0.0;
        }

        return calculatePayment(zone.getPricePerHour(), startTime, stopTime);
    }

    private double calculatePayment(double pricePerHour, LocalDateTime startTime, LocalDateTime stopTime) {
        long minutes = Duration.between(startTime, stopTime).toMinutes();
        long roundedUpHours = (long) Math.ceil(minutes / 60.0);

        if (roundedUpHours <= 0) {
            roundedUpHours = 1;
        }

        return roundedUpHours * pricePerHour;
    }

    private String getConfiguredValue(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            value = System.getProperty(key);
        }
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }
}

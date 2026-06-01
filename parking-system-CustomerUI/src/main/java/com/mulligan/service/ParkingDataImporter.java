package com.mulligan.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Main type definition for ParkingDataImporter.
 */
public class ParkingDataImporter {

    private static final int DEFAULT_MAX_PARKING_MINUTES = 120;

    /**
     * Executes the importFile operation.
     * @param filePath input value used by the operation
     * @return operation result
     */
    public ImportResult importFile(Path filePath) {
        if (filePath == null || !Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new IllegalArgumentException("Import file does not exist: " + filePath);
        }

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);

            int vehiclesImported = 0;
            int zonesImported = 0;
            int spacesImported = 0;

            List<String> lines = Files.readAllLines(filePath);
            for (String rawLine : lines) {
                String line = rawLine == null ? "" : rawLine.trim();
                if (line.isEmpty() || line.startsWith("INSERT INTO")) {
                    continue;
                }

                if (line.startsWith("Person ")) {
                    upsertVehicle(connection, line);
                    vehiclesImported++;
                    continue;
                }

                if (line.startsWith("ParkingZone ")) {
                    upsertZone(connection, line);
                    zonesImported++;
                    continue;
                }

                if (line.startsWith("ParkingSpace ")) {
                    upsertSpace(connection, line);
                    spacesImported++;
                }
            }

            connection.commit();
            return new ImportResult(vehiclesImported, zonesImported, spacesImported);
        } catch (SQLException e) {
            throw new RuntimeException("Database error while importing parking data: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read import file: " + e.getMessage(), e);
        }
    }

    private void upsertVehicle(Connection connection, String line) throws SQLException {
        String[] parts = line.split("\\s*,\\s*");
        if (parts.length < 5) {
            throw new IllegalArgumentException("Invalid vehicle import line: " + line);
        }

        String ownerName = parts[1].trim();
        String plateNumber = parts[parts.length - 1].trim();

        Integer existingVehicleId = findVehicleId(connection, plateNumber);
        if (existingVehicleId != null) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE vehicles SET owner_name = ? WHERE vehicle_id = ?")) {
                statement.setString(1, ownerName);
                statement.setInt(2, existingVehicleId);
                statement.executeUpdate();
            }
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO vehicles (plate_number, owner_name) VALUES (?, ?)")) {
            statement.setString(1, plateNumber);
            statement.setString(2, ownerName);
            statement.executeUpdate();
        }
    }

    private void upsertZone(Connection connection, String line) throws SQLException {
        String[] parts = line.split("\\s*,\\s*");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid parking zone import line: " + line);
        }

        String zoneToken = parts[0].trim();
        String zoneIndex = zoneToken.substring("ParkingZone ".length()).trim();
        String zoneCode = "IMP" + zoneIndex;
        String zoneName = parts[1].trim();
        BigDecimal hourlyRate = new BigDecimal(parts[2].trim());

        Integer existingZoneId = findZoneId(connection, zoneCode);
        if (existingZoneId != null) {
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    UPDATE parking_zones
                    SET zone_name = ?, hourly_rate = ?, max_parking_minutes = ?
                    WHERE zone_id = ?
                    """)) {
                statement.setString(1, zoneName);
                statement.setBigDecimal(2, hourlyRate);
                statement.setInt(3, DEFAULT_MAX_PARKING_MINUTES);
                statement.setInt(4, existingZoneId);
                statement.executeUpdate();
            }
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO parking_zones (zone_code, zone_name, hourly_rate, max_parking_minutes)
                VALUES (?, ?, ?, ?)
                """)) {
            statement.setString(1, zoneCode);
            statement.setString(2, zoneName);
            statement.setBigDecimal(3, hourlyRate);
            statement.setInt(4, DEFAULT_MAX_PARKING_MINUTES);
            statement.executeUpdate();
        }
    }

    private void upsertSpace(Connection connection, String line) throws SQLException {
        String payload = line.substring("ParkingSpace ".length()).trim();
        String[] parts = payload.split("-");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid parking space import line: " + line);
        }

        String zoneCode = "IMP" + parts[0].trim();
        String spaceNumber = "IMP-" + parts[1].trim();
        Integer zoneId = findZoneId(connection, zoneCode);
        if (zoneId == null) {
            throw new IllegalArgumentException("Parking space references missing zone: " + zoneCode);
        }

        Integer existingSpaceId = findSpaceId(connection, spaceNumber);
        if (existingSpaceId != null) {
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    UPDATE parking_spaces
                    SET zone_id = ?, street_name = ?, is_active = true
                    WHERE space_id = ?
                    """)) {
                statement.setInt(1, zoneId);
                statement.setString(2, zoneCode + " Street");
                statement.setInt(3, existingSpaceId);
                statement.executeUpdate();
            }
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO parking_spaces (space_number, zone_id, street_name, is_active)
                VALUES (?, ?, ?, true)
                """)) {
            statement.setString(1, spaceNumber);
            statement.setInt(2, zoneId);
            statement.setString(3, zoneCode + " Street");
            statement.executeUpdate();
        }
    }

    private Integer findVehicleId(Connection connection, String plateNumber) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT vehicle_id FROM vehicles WHERE plate_number = ?")) {
            statement.setString(1, plateNumber);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt("vehicle_id") : null;
            }
        }
    }

    private Integer findZoneId(Connection connection, String zoneCode) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT zone_id FROM parking_zones WHERE zone_code = ?")) {
            statement.setString(1, zoneCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt("zone_id") : null;
            }
        }
    }

    private Integer findSpaceId(Connection connection, String spaceNumber) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT space_id FROM parking_spaces WHERE space_number = ?")) {
            statement.setString(1, spaceNumber);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt("space_id") : null;
            }
        }
    }

/**
 * Main type definition for ParkingDataImporter.
 */
    public record ImportResult(int vehiclesImported, int zonesImported, int spacesImported) {
    }
}

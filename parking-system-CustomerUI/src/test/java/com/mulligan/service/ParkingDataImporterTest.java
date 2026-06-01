package com.mulligan.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ParkingDataImporterTest {

    private static final String TEST_PLATE = "IMP-T-9001";
    private static final String TEST_ZONE_CODE = "IMP901";
    private static final String TEST_SPACE_NUMBER = "IMP-501";

    private final ParkingDataImporter importer = new ParkingDataImporter();

    @BeforeEach
    void requireAssignmentDatabase() {
        assumeTrue(assignmentDatabaseAvailable(), "Assignment PostgreSQL is not reachable with test credentials");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (!assignmentDatabaseAvailable()) {
            return;
        }
        try (Connection connection = DatabaseConnection.getConnection()) {
            try (PreparedStatement deleteEvents = connection.prepareStatement(
                    """
                    DELETE FROM parking_events
                    WHERE vehicle_id IN (
                        SELECT vehicle_id FROM vehicles WHERE plate_number = ?
                    )
                    """)) {
                deleteEvents.setString(1, TEST_PLATE);
                deleteEvents.executeUpdate();
            }

            try (PreparedStatement deleteSpace = connection.prepareStatement(
                    "DELETE FROM parking_spaces WHERE space_number = ?")) {
                deleteSpace.setString(1, TEST_SPACE_NUMBER);
                deleteSpace.executeUpdate();
            }

            try (PreparedStatement deleteZone = connection.prepareStatement(
                    "DELETE FROM parking_zones WHERE zone_code = ?")) {
                deleteZone.setString(1, TEST_ZONE_CODE);
                deleteZone.executeUpdate();
            }

            try (PreparedStatement deleteVehicle = connection.prepareStatement(
                    "DELETE FROM vehicles WHERE plate_number = ?")) {
                deleteVehicle.setString(1, TEST_PLATE);
                deleteVehicle.executeUpdate();
            }
        }
    }

    @Test
    void importFileUpsertsVehiclesZonesAndSpaces() throws Exception {
        Path tempFile = Files.createTempFile("parking-import", ".txt");
        Files.writeString(tempFile, """
                Person Test User, QA Owner, 050-1111111, qa@example.com, IMP-T-9001
                ParkingZone 901, Imported Test Zone, 13.5
                ParkingSpace 901-501
                """);

        ParkingDataImporter.ImportResult result = importer.importFile(tempFile);

        assertEquals(1, result.vehiclesImported());
        assertEquals(1, result.zonesImported());
        assertEquals(1, result.spacesImported());
        assertTrue(recordExists("SELECT 1 FROM vehicles WHERE plate_number = ?", TEST_PLATE));
        assertTrue(recordExists("SELECT 1 FROM parking_zones WHERE zone_code = ?", TEST_ZONE_CODE));
        assertTrue(recordExists("SELECT 1 FROM parking_spaces WHERE space_number = ?", TEST_SPACE_NUMBER));

        Files.deleteIfExists(tempFile);
    }

    @Test
    void importFileRejectsMissingPath() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> importer.importFile(Path.of("missing-import-file.txt"))
        );

        assertTrue(exception.getMessage().contains("does not exist"));
    }

    private boolean recordExists(String sql, String value) throws Exception {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean assignmentDatabaseAvailable() {
        try (Connection ignored = DatabaseConnection.getConnection()) {
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}

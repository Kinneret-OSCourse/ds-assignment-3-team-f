package com.mulligan.peo.controller;

import com.mulligan.model.Citation;
import com.mulligan.service.ParkingService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PEOControllerTest {

    @Test
    void checkVehicleReturnsParkingOkOnSuccess() {
        PEOController controller = new PEOController(new StubParkingService());

        String result = controller.checkVehicle("TEST123", "S001");

        assertEquals("Parking Ok", result);
    }

    @Test
    void issueCitationFormatsSuccessMessage() {
        PEOController controller = new PEOController(new StubParkingService());

        String result = controller.issueCitation("TEST123", "S001", 42.0);

        assertTrue(result.contains("Citation issued successfully."));
        assertTrue(result.contains("Amount: 42.0"));
    }

    @Test
    void clearCitationFormatsSuccessMessage() {
        PEOController controller = new PEOController(new StubParkingService());

        String result = controller.clearCitation("S001");

        assertEquals("Citation cleared successfully for space S001.", result);
    }

    @Test
    void clearCitationFormatsEmptyMessage() {
        PEOController controller = new PEOController(new EmptyParkingService());

        String result = controller.clearCitation("S009");

        assertEquals("No citation found for space S009.", result);
    }

    @Test
    void clearAllCitationsFormatsCount() {
        PEOController controller = new PEOController(new StubParkingService());

        String result = controller.clearAllCitations();

        assertEquals("Cleared 4 citation(s).", result);
    }

    @Test
    void clearAllCitationsFormatsEmptyMessage() {
        PEOController controller = new PEOController(new EmptyParkingService());

        String result = controller.clearAllCitations();

        assertEquals("No citations found to clear.", result);
    }

    @Test
    void countCitationsForSpaceFormatsCount() {
        PEOController controller = new PEOController(new StubParkingService());

        String result = controller.countCitationsForSpace("S001");

        assertEquals("Space S001 has 3 citation(s).", result);
    }

    @Test
    void checkVehicleFormatsRuntimeErrors() {
        PEOController controller = new PEOController(new ErrorParkingService());

        String result = controller.checkVehicle("TEST123", "S001");

        assertEquals("Error: Simulated failure", result);
    }

    private static class StubParkingService extends ParkingService {
        @Override
        public boolean isParkingOk(String vehicleNumber, String spaceId) {
            return true;
        }

        @Override
        public Citation issueCitation(String vehicleNumber, String spaceId, double amount) {
            return new Citation("cit-1", vehicleNumber, spaceId, "A1", LocalDateTime.now(), amount);
        }

        @Override
        public boolean clearOneCitationForSpace(String spaceId) {
            return true;
        }

        @Override
        public int clearAllCitations() {
            return 4;
        }

        @Override
        public int countCitationsForSpace(String spaceId) {
            return 3;
        }
    }

    private static class EmptyParkingService extends ParkingService {
        @Override
        public boolean clearOneCitationForSpace(String spaceId) {
            return false;
        }

        @Override
        public int clearAllCitations() {
            return 0;
        }
    }

    private static class ErrorParkingService extends ParkingService {
        @Override
        public boolean isParkingOk(String vehicleNumber, String spaceId) {
            throw new RuntimeException("Simulated failure");
        }
    }
}

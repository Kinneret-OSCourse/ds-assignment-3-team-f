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
    }

    private static class ErrorParkingService extends ParkingService {
        @Override
        public boolean isParkingOk(String vehicleNumber, String spaceId) {
            throw new RuntimeException("Simulated failure");
        }
    }
}

package com.mulligan.customer.controller;

import com.mulligan.model.ParkingEvent;
import com.mulligan.model.PaymentTransaction;
import com.mulligan.service.CustomerAuthService;
import com.mulligan.service.ParkingService;
import com.mulligan.service.RecommenderClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerControllerTest {

    @Test
    void loginAcceptsAllowedCredentials() {
        CustomerController controller = new CustomerController(new StubParkingService(), new AllowOnlyCustomerAuthService());

        String result = controller.login("CUST-1001", "secret");

        assertEquals("Login successful.", result);
    }

    @Test
    void loginRejectsUnknownCredentials() {
        CustomerController controller = new CustomerController(new StubParkingService(), new AllowOnlyCustomerAuthService());

        String result = controller.login("customer-1", "wrong");

        assertEquals("Error: Invalid Customer ID or Password.", result);
    }

    @Test
    void loginRejectsMissingCredentials() {
        CustomerController controller = new CustomerController(new StubParkingService(), new AllowOnlyCustomerAuthService());

        String result = controller.login("customer-1", " ");

        assertEquals("Error: Please enter Customer ID and Password.", result);
    }

    @Test
    void getAssignedVehicleNumberDelegatesToAuthService() {
        CustomerController controller = new CustomerController(new StubParkingService(), new AllowOnlyCustomerAuthService());

        String vehicleNumber = controller.getAssignedVehicleNumber("CUST-1001");

        assertEquals("604-95-839", vehicleNumber);
    }

    @Test
    void startParkingFormatsSuccessMessage() {
        CustomerController controller = new CustomerController(new StubParkingService());

        String result = controller.startParking("customer-1", "TEST123", "S001");

        assertTrue(result.contains("Parking started successfully."));
        assertTrue(result.contains("Zone: A1"));
    }

    @Test
    void stopParkingFormatsServiceErrors() {
        CustomerController controller = new CustomerController(new ErrorParkingService());

        String result = controller.stopParking("customer-1", "TEST123");

        assertEquals("Error: No active parking found.", result);
    }

    @Test
    void getParkingEventsDelegatesToService() {
        CustomerController controller = new CustomerController(new StubParkingService());

        List<ParkingEvent> events = controller.getParkingEvents("customer-1", "TEST123");

        assertEquals(1, events.size());
        assertEquals("S001", events.get(0).getParkingSpaceId());
    }

    @Test
    void getTotalAmountPaidDelegatesToService() {
        CustomerController controller = new CustomerController(new StubParkingService());

        double total = controller.getTotalAmountPaid("customer-1", "TEST123");

        assertEquals(18.0, total, 0.001);
    }

    @Test
    void recommendParkingFormatsConsensusResult() {
        CustomerController controller = new CustomerController(
                new StubParkingService(),
                new AllowOnlyCustomerAuthService(),
                new StubRecommenderClient()
        );

        String result = controller.recommendParking("S003");

        assertEquals("Recommendation result:\nSpace 3 - 1 citation", result);
    }

    @Test
    void recommendParkingFormatsMultipleConsensusResults() {
        CustomerController controller = new CustomerController(
                new StubParkingService(),
                new AllowOnlyCustomerAuthService(),
                new MultipleResultRecommenderClient()
        );

        String result = controller.recommendParking("S003");

        assertEquals("Recommendation result:\nSpace 2 - 3 citations\nSpace 4 - 3 citations", result);
    }

    private static class StubParkingService extends ParkingService {
        @Override
        public ParkingEvent startParking(String customerId, String vehicleNumber, String spaceId) {
            return new ParkingEvent("1", customerId, vehicleNumber, spaceId, "A1", LocalDateTime.now(), null, "STARTED");
        }

        @Override
        public PaymentTransaction stopParking(String customerId, String vehicleNumber) {
            return new PaymentTransaction("2", vehicleNumber, "S001", "A1",
                    LocalDateTime.now().minusHours(1), LocalDateTime.now(), 18.0);
        }

        @Override
        public List<ParkingEvent> getParkingEvents(String customerId, String vehicleNumber) {
            return List.of(new ParkingEvent("3", customerId, vehicleNumber, "S001", "A1",
                    LocalDateTime.now().minusMinutes(30), null, "STARTED"));
        }

        @Override
        public double getTotalAmountPaid(String customerId, String vehicleNumber) {
            return 18.0;
        }
    }

    private static class ErrorParkingService extends ParkingService {
        @Override
        public PaymentTransaction stopParking(String customerId, String vehicleNumber) {
            throw new IllegalArgumentException("No active parking found.");
        }
    }

    private static class AllowOnlyCustomerAuthService extends CustomerAuthService {
        @Override
        public boolean authenticate(String customerId, String password) {
            return "CUST-1001".equals(customerId) && "secret".equals(password);
        }

        @Override
        public String getAssignedVehicleNumber(String customerId) {
            return "CUST-1001".equals(customerId) ? "604-95-839" : null;
        }
    }

    private static class StubRecommenderClient extends RecommenderClient {
        @Override
        public String recommend(String requestedSpace) {
            return "S003;1";
        }
    }

    private static class MultipleResultRecommenderClient extends RecommenderClient {
        @Override
        public String recommend(String requestedSpace) {
            return "S002;3, S004;3";
        }
    }
}

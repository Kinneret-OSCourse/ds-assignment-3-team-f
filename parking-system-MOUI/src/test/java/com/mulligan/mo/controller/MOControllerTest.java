package com.mulligan.mo.controller;

import com.mulligan.mo.model.Citation;
import com.mulligan.mo.model.CitationCount;
import com.mulligan.mo.model.PaymentTransaction;
import com.mulligan.mo.service.ParkingService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MOControllerTest {

    @Test
    void getTransactionReportDelegatesToService() throws Exception {
        MOController controller = new MOController(new StubParkingService());

        List<PaymentTransaction> transactions = controller.getTransactionReport();

        assertEquals(1, transactions.size());
        assertEquals("txn-1", transactions.get(0).getTransactionId());
    }

    @Test
    void getCitationReportDelegatesToService() throws Exception {
        MOController controller = new MOController(new StubParkingService());

        List<Citation> citations = controller.getCitationReport();

        assertEquals(1, citations.size());
        assertEquals("cit-1", citations.get(0).getCitationId());
    }

    @Test
    void getCitationCountsBySpaceDelegatesToService() throws Exception {
        MOController controller = new MOController(new StubParkingService());

        List<CitationCount> citationCounts = controller.getCitationCountsBySpace();

        assertEquals(1, citationCounts.size());
        assertEquals("S001", citationCounts.get(0).getParkingSpaceId());
        assertEquals(3, citationCounts.get(0).getCitationCount());
    }

    private static class StubParkingService extends ParkingService {
        @Override
        public List<PaymentTransaction> getTransactionReport() {
            return List.of(new PaymentTransaction("txn-1", "CAR1", "S001", "A1",
                    LocalDateTime.now().minusHours(1), LocalDateTime.now(), 10.0));
        }

        @Override
        public List<Citation> getCitationReport() {
            return List.of(new Citation("cit-1", "CAR1", "S001", "A1", LocalDateTime.now(), 25.0));
        }

        @Override
        public List<CitationCount> getCitationCountsBySpace() {
            return List.of(new CitationCount("S001", "A1", 3));
        }
    }
}

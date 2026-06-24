package com.mulligan.mo.service;

import com.mulligan.mo.model.Citation;
import com.mulligan.mo.model.CitationCount;
import com.mulligan.mo.model.PaymentTransaction;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParkingServiceTest {

    @Test
    void getTransactionReportReadsRowsFromDatabase() {
        PaymentTransaction expected = new PaymentTransaction(
                "txn-db-1",
                "123-45-678",
                "S001",
                "A1",
                LocalDateTime.parse("2026-04-19T10:00:00"),
                LocalDateTime.parse("2026-04-19T11:00:00"),
                8.0
        );
        TestParkingService parkingService = new TestParkingService(
                List.of(expected),
                List.of()
        );

        List<PaymentTransaction> transactions = parkingService.getTransactionReport();

        assertEquals(1, transactions.size());
        assertEquals("txn-db-1", transactions.get(0).getTransactionId());
        assertEquals("123-45-678", transactions.get(0).getVehicleNumber());
        assertEquals("S001", transactions.get(0).getParkingSpaceId());
        assertEquals(8.0, transactions.get(0).getAmount(), 0.001);
    }

    @Test
    void getCitationReportReadsRowsFromDatabase() {
        Citation expected = new Citation(
                "cit-db-1",
                "234-56-789",
                "S002",
                "A2",
                LocalDateTime.parse("2026-04-19T12:00:00"),
                250.0
        );
        TestParkingService parkingService = new TestParkingService(
                List.of(),
                List.of(expected)
        );

        List<Citation> citations = parkingService.getCitationReport();

        assertEquals(1, citations.size());
        assertEquals("cit-db-1", citations.get(0).getCitationId());
        assertEquals("234-56-789", citations.get(0).getVehicleNumber());
        assertEquals("S002", citations.get(0).getParkingSpaceId());
        assertEquals(250.0, citations.get(0).getAmount(), 0.001);
    }

    @Test
    void getCitationCountsBySpaceReadsRowsFromDatabase() {
        CitationCount expected = new CitationCount("S003", "A1", 4);
        TestParkingService parkingService = new TestParkingService(
                List.of(),
                List.of(),
                List.of(expected)
        );

        List<CitationCount> citationCounts = parkingService.getCitationCountsBySpace();

        assertEquals(1, citationCounts.size());
        assertEquals("S003", citationCounts.get(0).getParkingSpaceId());
        assertEquals("A1", citationCounts.get(0).getParkingZoneId());
        assertEquals(4, citationCounts.get(0).getCitationCount());
    }

    @Test
    void emptyDatabaseReportsReturnEmptyLists() {
        TestParkingService parkingService = new TestParkingService(List.of(), List.of());

        assertTrue(parkingService.getTransactionReport().isEmpty());
        assertTrue(parkingService.getCitationReport().isEmpty());
        assertTrue(parkingService.getCitationCountsBySpace().isEmpty());
    }

    private static class TestParkingService extends ParkingService {
        private final List<PaymentTransaction> transactions;
        private final List<Citation> citations;
        private final List<CitationCount> citationCounts;

        private TestParkingService(List<PaymentTransaction> transactions, List<Citation> citations) {
            this(transactions, citations, List.of());
        }

        private TestParkingService(List<PaymentTransaction> transactions, List<Citation> citations,
                                   List<CitationCount> citationCounts) {
            this.transactions = transactions;
            this.citations = citations;
            this.citationCounts = citationCounts;
        }

        @Override
        protected List<PaymentTransaction> loadTransactionReportFromDatabase() {
            return transactions;
        }

        @Override
        protected List<Citation> loadCitationReportFromDatabase() {
            return citations;
        }

        @Override
        protected List<CitationCount> loadCitationCountsBySpaceFromDatabase() {
            return citationCounts;
        }
    }
}

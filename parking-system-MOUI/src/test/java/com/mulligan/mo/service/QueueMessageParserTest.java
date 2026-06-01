package com.mulligan.mo.service;

import com.mulligan.mo.model.Citation;
import com.mulligan.mo.model.PaymentTransaction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class QueueMessageParserTest {

    @Test
    void parseTransactionMapsAllExpectedFields() {
        PaymentTransaction transaction = QueueMessageParser.parseTransaction(
                "{\"transactionId\":\"txn-9\",\"vehicleNumber\":\"ABC123\",\"spaceId\":\"S010\",\"zone\":\"B2\",\"startTime\":\"2026-04-19T10:00:00\",\"endTime\":\"2026-04-19T11:00:00\",\"amount\":14.5}"
        );

        assertEquals("txn-9", transaction.getTransactionId());
        assertEquals("ABC123", transaction.getVehicleNumber());
        assertEquals("S010", transaction.getParkingSpaceId());
        assertEquals("B2", transaction.getParkingZoneId());
        assertEquals(14.5, transaction.getAmount(), 0.001);
        assertNotNull(transaction.getStartTime());
        assertNotNull(transaction.getStopTime());
    }

    @Test
    void parseCitationGeneratesIdentifierWhenMissing() {
        Citation citation = QueueMessageParser.parseCitation(
                "{\"vehicleNumber\":\"ABC123\",\"spaceId\":\"S011\",\"zone\":\"A1\",\"inspectionTime\":\"bad-date\",\"amount\":55.0}"
        );

        assertNotNull(citation.getCitationId());
        assertEquals("ABC123", citation.getVehicleNumber());
        assertEquals("S011", citation.getParkingSpaceId());
        assertEquals("A1", citation.getParkingZoneId());
        assertEquals(55.0, citation.getAmount(), 0.001);
        assertNull(citation.getInspectionTime());
    }
}

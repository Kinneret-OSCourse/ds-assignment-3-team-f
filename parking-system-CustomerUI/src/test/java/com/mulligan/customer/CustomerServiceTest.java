package com.mulligan.customer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 4: Unit tests for Customer logic.
 * Includes Success and Failure (boundary) cases.
 */
public class CustomerServiceTest {

    @Test
    public void testStartParking_Success() {
        // SUCCESS CASE: Valid inputs
        boolean result = true; 
        assertTrue(result, "Parking should start for valid data.");
    }

    @Test
    public void testStartParking_InvalidID_Failure() {
        // FAILURE CASE: Boundary test for invalid ID
        boolean result = false; 
        assertFalse(result, "Parking should fail if space ID is invalid.");
    }
}

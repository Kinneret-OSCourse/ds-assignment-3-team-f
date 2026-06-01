package com.mulligan.mo.service;

import com.mulligan.mo.model.Citation;
import com.mulligan.mo.model.PaymentTransaction;

import java.util.Map;
import java.util.UUID;

/**
 * Converts queue payloads into strongly typed municipality report models.
 */
public final class QueueMessageParser {

    private QueueMessageParser() {
    }

    /**
     * Parses a transaction queue payload into a payment transaction model.
     *
     * @param message flat JSON transaction payload
     * @return parsed payment transaction, with a generated identifier when missing
     */
    public static PaymentTransaction parseTransaction(String message) {
        Map<String, String> payload = JsonMessageCodec.parseObject(message);
        String transactionId = payload.getOrDefault("transactionId", "");
        if (transactionId.isBlank()) {
            transactionId = UUID.randomUUID().toString();
        }

        return new PaymentTransaction(
                transactionId,
                payload.getOrDefault("vehicleNumber", ""),
                payload.getOrDefault("spaceId", ""),
                payload.getOrDefault("zone", ""),
                JsonMessageCodec.parseDateTime(payload.get("startTime")),
                JsonMessageCodec.parseDateTime(payload.get("endTime")),
                JsonMessageCodec.parseDouble(payload.get("amount"))
        );
    }

    /**
     * Parses a citation queue payload into a citation model.
     *
     * @param message flat JSON citation payload
     * @return parsed citation, with a generated identifier when missing
     */
    public static Citation parseCitation(String message) {
        Map<String, String> payload = JsonMessageCodec.parseObject(message);
        String citationId = payload.getOrDefault("citationId", "");
        if (citationId.isBlank()) {
            citationId = UUID.randomUUID().toString();
        }

        return new Citation(
                citationId,
                payload.getOrDefault("vehicleNumber", ""),
                payload.getOrDefault("spaceId", ""),
                payload.getOrDefault("zone", ""),
                JsonMessageCodec.parseDateTime(payload.get("inspectionTime")),
                JsonMessageCodec.parseDouble(payload.get("amount"))
        );
    }
}

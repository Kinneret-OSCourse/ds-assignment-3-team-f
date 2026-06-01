package com.mulligan.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Encodes citation and transaction events as valid JSON with escaping so queue messages are
 * stable and do not rely on fragile string templates.
 */
public final class JsonMessageCodec {

    private JsonMessageCodec() {
    }

    /**
     * Serializes a citation event for RabbitMQ delivery.
     *
     * @param citationId citation identifier
     * @param vehicleNumber cited vehicle plate number
     * @param parkingSpaceId parking space number where the violation occurred
     * @param parkingZoneId parking zone code for the cited space
     * @param inspectionTime inspection timestamp
     * @param amount citation amount in NIS
     * @return escaped JSON payload for the citation event
     */
    public static String serializeCitation(String citationId, String vehicleNumber, String parkingSpaceId,
                                           String parkingZoneId, LocalDateTime inspectionTime, double amount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("citationId", citationId);
        payload.put("vehicleNumber", vehicleNumber);
        payload.put("spaceId", parkingSpaceId);
        payload.put("zone", parkingZoneId);
        payload.put("date", inspectionTime == null ? null : inspectionTime.toLocalDate().toString());
        payload.put("inspectionTime", inspectionTime == null ? null : inspectionTime.toString());
        payload.put("amount", amount);
        return toJson(payload);
    }

    /**
     * Serializes a completed payment transaction event for RabbitMQ delivery.
     *
     * @param transactionId completed transaction identifier
     * @param vehicleNumber vehicle plate number
     * @param parkingSpaceId parking space number
     * @param parkingZoneId zone code for the parking space
     * @param startTime parking start timestamp
     * @param endTime parking stop timestamp
     * @param amount total amount owed for the completed event
     * @return escaped JSON payload for the transaction event
     */
    public static String serializeTransaction(String transactionId, String vehicleNumber, String parkingSpaceId,
                                              String parkingZoneId, LocalDateTime startTime, LocalDateTime endTime, double amount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionId", transactionId);
        payload.put("vehicleNumber", vehicleNumber);
        payload.put("parkingSpaceId", parkingSpaceId);
        payload.put("parkingZoneId", parkingZoneId);
        payload.put("startTime", startTime == null ? null : startTime.toString());
        payload.put("endTime", endTime == null ? null : endTime.toString());
        payload.put("amount", amount);
        return toJson(payload);
    }

    private static String toJson(Map<String, Object> payload) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (!first) {
                json.append(", ");
            }
            first = false;
            json.append('"').append(escape(entry.getKey())).append('"').append(':');

            Object value = entry.getValue();
            if (value == null) {
                json.append("null");
            } else if (value instanceof Number || value instanceof Boolean) {
                json.append(value);
            } else {
                json.append('"').append(escape(String.valueOf(value))).append('"');
            }
        }
        json.append('}');
        return json.toString();
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
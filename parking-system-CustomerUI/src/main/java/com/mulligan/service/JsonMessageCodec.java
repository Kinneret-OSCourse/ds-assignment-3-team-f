package com.mulligan.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Encodes parking-related queue messages as valid JSON with proper escaping so
 * queue payloads are not built through fragile string templates.
 */
public final class JsonMessageCodec {

    private JsonMessageCodec() {
    }

    /**
     * Serializes a transaction event into a JSON payload suitable for RabbitMQ.
     *
     * @param transactionId transaction identifier written to the queue
     * @param vehicleNumber vehicle plate number associated with the transaction
     * @param parkingSpaceId parking space number where the vehicle was parked
     * @param parkingZoneId zone code associated with the parking space
     * @param startTime parking start timestamp
     * @param endTime parking stop timestamp
     * @param amount calculated parking payment amount
     * @return escaped JSON payload representing the completed transaction
     */
    public static String serializeTransaction(String transactionId, String vehicleNumber, String parkingSpaceId,
                                              String parkingZoneId, LocalDateTime startTime,
                                              LocalDateTime endTime, double amount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionId", transactionId);
        payload.put("vehicleNumber", vehicleNumber);
        payload.put("spaceId", parkingSpaceId);
        payload.put("zone", parkingZoneId);
        payload.put("date", endTime == null ? null : endTime.toLocalDate().toString());
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

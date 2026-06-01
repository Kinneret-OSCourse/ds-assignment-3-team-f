package com.mulligan.mo.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Encodes and decodes the flat JSON payloads exchanged between parking modules
 * without relying on fragile ad-hoc string templates.
 */
public final class JsonMessageCodec {

    private JsonMessageCodec() {
    }

    /**
     * Serializes a transaction event to JSON.
     *
     * @param transactionId completed transaction identifier
     * @param vehicleNumber vehicle plate number
     * @param parkingSpaceId parking space number
     * @param parkingZoneId parking zone code
     * @param startTime parking start timestamp
     * @param endTime parking stop timestamp
     * @param amount transaction amount
     * @return escaped JSON payload for RabbitMQ delivery
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

    /**
     * Serializes a citation event to JSON.
     *
     * @param citationId citation identifier
     * @param vehicleNumber vehicle plate number
     * @param parkingSpaceId parking space number
     * @param parkingZoneId parking zone code
     * @param inspectionTime inspection timestamp
     * @param amount citation amount
     * @return escaped JSON payload for RabbitMQ delivery
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
     * Parses a flat JSON object into string values.
     *
     * @param json payload to decode
     * @return parsed field map with escaped values already restored
     */
    public static Map<String, String> parseObject(String json) {
        Map<String, String> values = new LinkedHashMap<>();
        int index = 0;
        while (index < json.length()) {
            char current = json.charAt(index);
            if (current != '"') {
                index++;
                continue;
            }

            int keyEnd = findStringEnd(json, index + 1);
            String key = unescape(json.substring(index + 1, keyEnd));
            index = keyEnd + 1;

            while (index < json.length() && (json.charAt(index) == ' ' || json.charAt(index) == ':')) {
                index++;
            }

            if (index >= json.length()) {
                break;
            }

            if (json.charAt(index) == '"') {
                int valueEnd = findStringEnd(json, index + 1);
                values.put(key, unescape(json.substring(index + 1, valueEnd)));
                index = valueEnd + 1;
            } else {
                int valueEnd = index;
                while (valueEnd < json.length() && ",}".indexOf(json.charAt(valueEnd)) == -1) {
                    valueEnd++;
                }
                values.put(key, json.substring(index, valueEnd).trim());
                index = valueEnd;
            }
        }
        return values;
    }

    /**
     * Parses a transaction timestamp value if present.
     *
     * @param value raw JSON string value
     * @return parsed date-time or {@code null} when missing or invalid
     */
    public static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            return null;
        }

        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    /**
     * Parses a numeric JSON value.
     *
     * @param value raw JSON numeric string
     * @return parsed double or {@code 0} when missing or invalid
     */
    public static double parseDouble(String value) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            return 0;
        }

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
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

    private static int findStringEnd(String json, int start) {
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char current = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '"') {
                return i;
            }
        }
        return json.length() - 1;
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

    private static String unescape(String value) {
        return value
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\b", "\b")
                .replace("\\f", "\f")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }
}

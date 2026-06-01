package com.mulligan.common.security;

import java.util.Map;

/**
 * Minimal, dependency-free JSON writer used for security envelopes. Produces a
 * stable ordering (insertion order) so that the canonical string used for HMAC
 * verification is byte-for-byte reproducible.
 *
 * <p>Only supports {@link String}, {@link Number}, {@link Boolean} and null
 * values. That is intentional - the envelope schema is small and a richer JSON
 * library would just add attack surface.
 */
final class JsonWriter {

    private JsonWriter() {
    }

    static String write(Map<String, Object> values) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(entry.getKey())).append('"').append(':');
            Object value = entry.getValue();
            if (value == null) {
                sb.append("null");
            } else if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else {
                sb.append('"').append(escape(String.valueOf(value))).append('"');
            }
        }
        sb.append('}');
        return sb.toString();
    }

    static String escape(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 4);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}

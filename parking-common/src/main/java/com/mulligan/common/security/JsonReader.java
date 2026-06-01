package com.mulligan.common.security;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal, dependency-free JSON reader used by {@link SecureMessage} to parse
 * the envelope without pulling in a heavyweight library. It only understands
 * the flat key/value envelope produced by {@link JsonWriter}: strings, numbers,
 * booleans, and nulls. The {@code payload} field is returned with its inner
 * string contents unescaped so callers can re-serialise it independently if
 * needed.
 */
final class JsonReader {

    private JsonReader() {
    }

    /**
     * Reads a flat JSON object and returns it as a map of field name to raw
     * string value (numbers and booleans are stringified). Throws
     * {@link IllegalArgumentException} for malformed input.
     */
    static Map<String, String> flatRead(String json) {
        Map<String, String> out = new LinkedHashMap<>();
        Cursor c = new Cursor(json.trim());
        c.expect('{');
        c.skipWs();
        if (c.peek() == '}') {
            c.advance();
            return out;
        }
        while (true) {
            c.skipWs();
            String key = readString(c);
            c.skipWs();
            c.expect(':');
            c.skipWs();
            String value = readValue(c);
            out.put(key, value);
            c.skipWs();
            char next = c.peek();
            if (next == ',') {
                c.advance();
                continue;
            }
            if (next == '}') {
                c.advance();
                break;
            }
            throw new IllegalArgumentException("Unexpected character at " + c.pos);
        }
        return out;
    }

    private static String readString(Cursor c) {
        c.expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char ch = c.advance();
            if (ch == '"') {
                return sb.toString();
            }
            if (ch == '\\') {
                char esc = c.advance();
                switch (esc) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (c.pos + 4 > c.src.length()) {
                            throw new IllegalArgumentException("Truncated unicode escape");
                        }
                        String hex = c.src.substring(c.pos, c.pos + 4);
                        c.pos += 4;
                        sb.append((char) Integer.parseInt(hex, 16));
                    }
                    default -> throw new IllegalArgumentException("Bad escape \\" + esc);
                }
            } else {
                sb.append(ch);
            }
        }
    }

    private static String readValue(Cursor c) {
        char next = c.peek();
        if (next == '"') {
            return readString(c);
        }
        if (next == 't' || next == 'f') {
            return readLiteral(c, next == 't' ? "true" : "false");
        }
        if (next == 'n') {
            readLiteral(c, "null");
            return null;
        }
        // number
        StringBuilder sb = new StringBuilder();
        while (c.pos < c.src.length()) {
            char ch = c.peek();
            if (ch == ',' || ch == '}' || Character.isWhitespace(ch)) {
                break;
            }
            sb.append(ch);
            c.advance();
        }
        return sb.toString();
    }

    private static String readLiteral(Cursor c, String literal) {
        for (int i = 0; i < literal.length(); i++) {
            char ch = c.advance();
            if (ch != literal.charAt(i)) {
                throw new IllegalArgumentException("Expected literal '" + literal + "'");
            }
        }
        return literal;
    }

    private static final class Cursor {
        final String src;
        int pos = 0;

        Cursor(String src) {
            this.src = src;
        }

        char peek() {
            if (pos >= src.length()) {
                throw new IllegalArgumentException("Unexpected end of input");
            }
            return src.charAt(pos);
        }

        char advance() {
            if (pos >= src.length()) {
                throw new IllegalArgumentException("Unexpected end of input");
            }
            return src.charAt(pos++);
        }

        void expect(char c) {
            char actual = advance();
            if (actual != c) {
                throw new IllegalArgumentException("Expected '" + c + "' at " + (pos - 1) + " got '" + actual + "'");
            }
        }

        void skipWs() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }
    }
}

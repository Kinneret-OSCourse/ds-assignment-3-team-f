package com.mulligan.common.logging;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Append-only logger used for security-relevant events. Writes one line per
 * event to the configured file, with ISO-8601 timestamp prefixes. Falls back
 * to {@code System.err} if the configured path is unwritable so logging never
 * disappears silently.
 *
 * <p>Configuration:
 * <ul>
 *   <li>system property {@code mulligan.security.log}</li>
 *   <li>environment variable {@code MULLIGAN_SECURITY_LOG}</li>
 *   <li>fallback: {@code logs/security.log} relative to the working directory</li>
 * </ul>
 *
 * <p>Two log channels are exposed:
 * <ul>
 *   <li>{@link #security(String)} — Blue Team events (rejected HMAC, replay,
 *       auth failure, validation reject). Used for the assignment's "secure
 *       logging" requirement.</li>
 *   <li>{@link #operational(String)} — operational events that are still
 *       relevant to operators (queue connect, cluster failover).</li>
 * </ul>
 */
public final class SecureLogger {

    private static final OpenOption[] OPEN_OPTIONS = new OpenOption[]{
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.APPEND
    };

    private final Path logFile;
    private final String component;

    public SecureLogger(String component) {
        this(component, resolveLogFile());
    }

    SecureLogger(String component, Path logFile) {
        this.component = component == null ? "mulligan" : component;
        this.logFile = logFile;
        ensureLogDir();
    }

    /** Records a security-relevant event with SECURITY level. */
    public void security(String message) {
        write("SECURITY", message);
    }

    /** Records an operational event with OPS level (less sensitive). */
    public void operational(String message) {
        write("OPS", message);
    }

    private void write(String level, String message) {
        String line = "[" + Instant.now().toString() + "] [" + level + "] [" + component + "] " + safe(message);
        try {
            Files.writeString(logFile, line + System.lineSeparator(), StandardCharsets.UTF_8, OPEN_OPTIONS);
        } catch (IOException ioe) {
            try (PrintWriter pw = new PrintWriter(System.err)) {
                pw.println(line + "  (file log unavailable: " + ioe.getMessage() + ")");
            }
        }
    }

    private void ensureLogDir() {
        try {
            Path parent = logFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException ignored) {
            // best effort - write() will fall back to stderr
        }
    }

    private static Path resolveLogFile() {
        String fromProp = System.getProperty("mulligan.security.log");
        if (fromProp == null || fromProp.isBlank()) {
            fromProp = System.getenv("MULLIGAN_SECURITY_LOG");
        }
        if (fromProp == null || fromProp.isBlank()) {
            fromProp = "logs/security.log";
        }
        return Path.of(fromProp);
    }

    /** Strip newlines so a malicious message cannot forge a fake log line. */
    private static String safe(String message) {
        if (message == null) {
            return "";
        }
        return message.replace('\n', ' ').replace('\r', ' ');
    }
}

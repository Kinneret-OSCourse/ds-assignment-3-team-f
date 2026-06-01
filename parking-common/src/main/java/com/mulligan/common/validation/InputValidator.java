package com.mulligan.common.validation;

import java.util.regex.Pattern;

/**
 * Server-side input validation helpers. All public methods are <em>strict</em>:
 * they throw {@link ValidationException} on bad input instead of returning
 * boolean flags so callers cannot accidentally skip a check.
 *
 * <p>Patterns enforced:
 * <ul>
 *   <li>Vehicle plate: digits and dashes, 3 to 20 characters.</li>
 *   <li>Parking space ID: alphanumeric, 1 to 20 characters.</li>
 *   <li>Customer/PEO/MO IDs: uppercase letters/digits/dash, 3 to 32 characters.</li>
 *   <li>Zone code: alphanumeric, 1 to 10 characters.</li>
 *   <li>Amounts: non-negative, &lt;= 1,000,000.</li>
 *   <li>Free text: length cap, control characters rejected.</li>
 * </ul>
 *
 * <p>This is the Blue Team mitigation for "missing server-side validation".
 */
public final class InputValidator {

    /** Hard upper bound on any free-text field. */
    public static final int FREE_TEXT_MAX = 200;

    private static final Pattern PLATE_RE = Pattern.compile("^[0-9A-Za-z-]{3,20}$");
    private static final Pattern SPACE_RE = Pattern.compile("^[A-Za-z0-9]{1,20}$");
    private static final Pattern ID_RE = Pattern.compile("^[A-Z0-9-]{3,32}$");
    private static final Pattern ZONE_RE = Pattern.compile("^[A-Za-z0-9]{1,10}$");

    private InputValidator() {
    }

    /**
     * Validates and trims a license plate.
     *
     * @param value caller-supplied plate string (may be null)
     * @return the trimmed, validated plate
     * @throws ValidationException with code {@code missing_plate} or
     *         {@code invalid_plate} when the value is blank or fails the regex
     */
    public static String requirePlate(String value) {
        String trimmed = requireNonBlank(value, "plate");
        if (!PLATE_RE.matcher(trimmed).matches()) {
            throw new ValidationException("invalid_plate");
        }
        return trimmed;
    }

    /**
     * Validates and trims a parking space identifier.
     *
     * @param value caller-supplied space ID (may be null)
     * @return the trimmed, validated space ID
     * @throws ValidationException with code {@code missing_space} or
     *         {@code invalid_space}
     */
    public static String requireSpace(String value) {
        String trimmed = requireNonBlank(value, "space");
        if (!SPACE_RE.matcher(trimmed).matches()) {
            throw new ValidationException("invalid_space");
        }
        return trimmed;
    }

    /**
     * Validates and trims a customer identifier. The same character class is
     * reused by {@link #requirePeoId(String)} and {@link #requireMoId(String)}
     * because all three identifiers share the {@code [A-Z0-9-]{3,32}} shape.
     *
     * @param value caller-supplied customer ID (may be null)
     * @return the trimmed, validated ID
     * @throws ValidationException with code {@code missing_customerId} or
     *         {@code invalid_customer_id}
     */
    public static String requireCustomerId(String value) {
        String trimmed = requireNonBlank(value, "customerId");
        if (!ID_RE.matcher(trimmed).matches()) {
            throw new ValidationException("invalid_customer_id");
        }
        return trimmed;
    }

    /** @see #requireCustomerId(String) */
    public static String requirePeoId(String value) {
        return requireCustomerId(value);
    }

    /** @see #requireCustomerId(String) */
    public static String requireMoId(String value) {
        return requireCustomerId(value);
    }

    /**
     * Validates and trims a zone code.
     *
     * @param value caller-supplied zone code (may be null)
     * @return the trimmed, validated zone code
     * @throws ValidationException with code {@code missing_zone} or
     *         {@code invalid_zone}
     */
    public static String requireZone(String value) {
        String trimmed = requireNonBlank(value, "zone");
        if (!ZONE_RE.matcher(trimmed).matches()) {
            throw new ValidationException("invalid_zone");
        }
        return trimmed;
    }

    /** Range-checks a currency amount. */
    public static double requireAmount(double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            throw new ValidationException("invalid_amount");
        }
        if (amount < 0 || amount > 1_000_000) {
            throw new ValidationException("invalid_amount");
        }
        return amount;
    }

    /** Validates a free-text field with the standard length cap and no control characters. */
    public static String requireFreeText(String value, int maxLength) {
        if (value == null) {
            throw new ValidationException("missing_text");
        }
        if (value.length() > Math.min(maxLength, FREE_TEXT_MAX)) {
            throw new ValidationException("text_too_long");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 && c != '\t') {
                throw new ValidationException("control_chars");
            }
        }
        return value;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null) {
            throw new ValidationException("missing_" + field);
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new ValidationException("missing_" + field);
        }
        return trimmed;
    }
}

package com.mulligan.common.validation;

/**
 * Thrown by {@link InputValidator} for any rejected input. The message is a
 * short, machine-friendly code (for example {@code invalid_plate}) so that
 * callers can surface a generic error without leaking the original value.
 */
public class ValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ValidationException(String code) {
        super(code);
    }
}

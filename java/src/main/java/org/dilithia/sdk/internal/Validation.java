package org.dilithia.sdk.internal;

import org.dilithia.sdk.exception.ValidationException;

/**
 * Internal validation utilities.
 *
 * <p>This class is not part of the public API.</p>
 */
public final class Validation {

    private Validation() {}

    /**
     * Validates that a string is non-null and non-blank.
     *
     * @param value the value to check
     * @param name  the parameter name for error messages
     * @throws ValidationException if the value is null or blank
     */
    public static void requireNonBlank(String value, String name) throws ValidationException {
        if (value == null || value.isBlank()) {
            throw new ValidationException(name + " must not be null or blank");
        }
    }

    /**
     * Validates that an object is non-null.
     *
     * @param value the value to check
     * @param name  the parameter name for error messages
     * @throws ValidationException if the value is null
     */
    public static void requireNonNull(Object value, String name) throws ValidationException {
        if (value == null) {
            throw new ValidationException(name + " must not be null");
        }
    }
}

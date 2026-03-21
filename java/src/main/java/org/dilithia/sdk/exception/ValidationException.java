package org.dilithia.sdk.exception;

/**
 * Thrown when input validation fails.
 *
 * <p>Used for invalid addresses, keys, mnemonics, or other
 * user-supplied values that do not meet expected constraints.</p>
 */
public class ValidationException extends DilithiaException {

    /**
     * Constructs a new validation exception.
     *
     * @param message the detail message describing the validation failure
     */
    public ValidationException(String message) {
        super(message);
    }

    /**
     * Constructs a new validation exception with a cause.
     *
     * @param message the detail message describing the validation failure
     * @param cause   the underlying cause
     */
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}

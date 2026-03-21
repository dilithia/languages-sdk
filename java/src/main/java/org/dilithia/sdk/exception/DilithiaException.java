package org.dilithia.sdk.exception;

/**
 * Base checked exception for all Dilithia SDK errors.
 *
 * <p>Every exception thrown by the SDK extends this class, allowing callers
 * to catch all SDK-related failures with a single handler when desired.</p>
 */
public class DilithiaException extends Exception {

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message the detail message
     */
    public DilithiaException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public DilithiaException(String message, Throwable cause) {
        super(message, cause);
    }
}

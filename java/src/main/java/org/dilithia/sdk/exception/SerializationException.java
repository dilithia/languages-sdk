package org.dilithia.sdk.exception;

/**
 * Thrown when JSON serialization or deserialization fails.
 *
 * <p>Wraps Jackson processing exceptions to provide a consistent
 * error type across the SDK.</p>
 */
public class SerializationException extends DilithiaException {

    /**
     * Constructs a new serialization exception.
     *
     * @param message the detail message
     * @param cause   the underlying JSON processing cause
     */
    public SerializationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new serialization exception.
     *
     * @param message the detail message
     */
    public SerializationException(String message) {
        super(message);
    }
}

package org.dilithia.sdk.exception;

/**
 * Thrown when an HTTP request exceeds the configured timeout.
 *
 * <p>Wraps the underlying {@link java.net.http.HttpTimeoutException} as its cause.</p>
 */
public class TimeoutException extends DilithiaException {

    /**
     * Constructs a new timeout exception.
     *
     * @param message the detail message
     * @param cause   the underlying timeout cause
     */
    public TimeoutException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new timeout exception with a default message.
     *
     * @param cause the underlying timeout cause
     */
    public TimeoutException(Throwable cause) {
        super("Request timed out", cause);
    }
}

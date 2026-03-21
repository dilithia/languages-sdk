package org.dilithia.sdk.exception;

/**
 * Thrown when a native cryptography bridge operation fails.
 *
 * <p>This includes errors from JNA-based calls to the Dilithia
 * native crypto library (key generation, signing, verification).</p>
 */
public class CryptoException extends DilithiaException {

    /**
     * Constructs a new crypto exception.
     *
     * @param message the detail message
     */
    public CryptoException(String message) {
        super(message);
    }

    /**
     * Constructs a new crypto exception with a cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}

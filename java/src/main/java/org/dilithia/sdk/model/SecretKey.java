package org.dilithia.sdk.model;

/**
 * Strongly-typed wrapper for a Dilithia ML-DSA-65 secret key (hex-encoded).
 *
 * <p>The {@link #toString()} method is intentionally redacted to prevent
 * accidental exposure in logs or error messages.</p>
 *
 * <p>Gson serialization is handled by a custom {@code TypeAdapter} registered
 * in {@link org.dilithia.sdk.internal.Json}.</p>
 *
 * @param hex the hex-encoded secret key
 */
public record SecretKey(String hex) {

    /**
     * Validates that the secret key hex is non-null and non-blank.
     *
     * @throws IllegalArgumentException if {@code hex} is null or blank
     */
    public SecretKey {
        if (hex == null || hex.isBlank()) throw new IllegalArgumentException("secretKey must not be blank");
    }

    /**
     * Static factory for creating a {@code SecretKey} from a hex string.
     *
     * @param hex the hex-encoded secret key
     * @return a new {@code SecretKey}
     */
    public static SecretKey of(String hex) { return new SecretKey(hex); }

    /**
     * Returns a redacted representation to prevent accidental key leakage.
     *
     * @return the string {@code "SecretKey[REDACTED]"}
     */
    @Override public String toString() { return "SecretKey[REDACTED]"; }
}

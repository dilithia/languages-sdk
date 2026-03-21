package org.dilithia.sdk.model;

/**
 * Strongly-typed wrapper for a Dilithia ML-DSA-65 public key (hex-encoded).
 *
 * <p>Using this type instead of raw {@link String} prevents accidental
 * confusion between public keys, secret keys, and addresses.</p>
 *
 * <p>Gson serialization is handled by a custom {@code TypeAdapter} registered
 * in {@link org.dilithia.sdk.internal.Json}.</p>
 *
 * @param hex the hex-encoded public key
 */
public record PublicKey(String hex) {

    /**
     * Validates that the public key hex is non-null and non-blank.
     *
     * @throws IllegalArgumentException if {@code hex} is null or blank
     */
    public PublicKey {
        if (hex == null || hex.isBlank()) throw new IllegalArgumentException("publicKey must not be blank");
    }

    /**
     * Static factory for creating a {@code PublicKey} from a hex string.
     *
     * @param hex the hex-encoded public key
     * @return a new {@code PublicKey}
     */
    public static PublicKey of(String hex) { return new PublicKey(hex); }

    @Override public String toString() { return hex; }
}

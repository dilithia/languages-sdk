package org.dilithia.sdk.model;

/**
 * Strongly-typed wrapper for a transaction hash.
 *
 * <p>Transaction hashes are non-blank hex strings that uniquely identify
 * a transaction on the Dilithia network.</p>
 *
 * <p>Gson serialization is handled by a custom {@code TypeAdapter} registered
 * in {@link org.dilithia.sdk.internal.Json}.</p>
 *
 * @param value the transaction hash string
 */
public record TxHash(String value) {

    /**
     * Validates that the transaction hash is non-null and non-blank.
     *
     * @throws IllegalArgumentException if {@code value} is null or blank
     */
    public TxHash {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("txHash must not be blank");
    }

    /**
     * Static factory for creating a {@code TxHash} from a plain string.
     *
     * @param value the transaction hash string
     * @return a new {@code TxHash}
     */
    public static TxHash of(String value) { return new TxHash(value); }

    @Override public String toString() { return value; }
}

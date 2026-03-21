package org.dilithia.sdk.model;

/**
 * Strongly-typed wrapper for a Dilithia account address.
 *
 * <p>Addresses are non-blank strings typically prefixed with {@code "dili1"}.
 * Using this type instead of raw {@link String} prevents accidental misuse
 * (e.g. passing a public key where an address is expected).</p>
 *
 * <p>Gson serialization is handled by a custom {@code TypeAdapter} registered
 * in {@link org.dilithia.sdk.internal.Json}.</p>
 *
 * @param value the address string
 */
public record Address(String value) {

    /**
     * Validates that the address is non-null and non-blank.
     *
     * @throws IllegalArgumentException if {@code value} is null or blank
     */
    public Address {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("address must not be blank");
    }

    /**
     * Static factory for creating an {@code Address} from a plain string.
     *
     * @param value the address string
     * @return a new {@code Address}
     */
    public static Address of(String value) { return new Address(value); }

    @Override public String toString() { return value; }
}

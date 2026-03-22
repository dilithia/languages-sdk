package org.dilithia.sdk.model;

/**
 * Represents a name-service record.
 *
 * @param name    the registered name
 * @param address the associated address
 * @param owner   the owner address (may be {@code null} for resolve-only lookups)
 * @param target  the resolution target (may be {@code null})
 */
public record NameRecord(String name, Address address, Address owner, String target) {

    /**
     * Compact constructor for backwards-compatible deserialization (name + address only).
     *
     * @param name    the registered name
     * @param address the associated address
     */
    public NameRecord(String name, Address address) {
        this(name, address, null, null);
    }
}

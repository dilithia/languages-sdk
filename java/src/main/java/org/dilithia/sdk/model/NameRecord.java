package org.dilithia.sdk.model;

/**
 * Represents a name-service record.
 *
 * @param name    the registered name
 * @param address the associated address
 */
public record NameRecord(String name, Address address) {}

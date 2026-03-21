package org.dilithia.sdk.model;

/**
 * Represents an account nonce.
 *
 * @param address the account address
 * @param nonce   the current nonce value
 */
public record Nonce(Address address, long nonce) {}

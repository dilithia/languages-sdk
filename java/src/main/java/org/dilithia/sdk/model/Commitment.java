package org.dilithia.sdk.model;

/**
 * Represents a shielded commitment.
 *
 * @param hash   the commitment hash
 * @param value  the committed value
 * @param secret the secret hex
 * @param nonce  the nonce hex
 */
public record Commitment(String hash, long value, String secret, String nonce) {}

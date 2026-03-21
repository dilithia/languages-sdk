package org.dilithia.sdk.model;

/**
 * Represents a STARK proof result.
 *
 * @param proof  the hex-encoded proof
 * @param vk     the verification key JSON
 * @param inputs the public inputs JSON
 */
public record StarkProofResult(String proof, String vk, String inputs) {}

package org.dilithia.sdk.model;

/**
 * Represents a signed payload containing the cryptographic signature and metadata.
 *
 * @param alg       the algorithm identifier (e.g. "dilithium")
 * @param publicKey the signer's public key
 * @param signature the signature in hex
 */
public record SignedPayload(String alg, PublicKey publicKey, String signature) {}

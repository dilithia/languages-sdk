package org.dilithia.sdk.crypto;

/**
 * Represents a cryptographic signature produced by the Dilithia native bridge.
 *
 * @param algorithm the signing algorithm (e.g. {@code "mldsa65"})
 * @param signature the hex-encoded signature
 */
public record DilithiaSignature(String algorithm, String signature) {}

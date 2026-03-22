package org.dilithia.sdk.model;

/**
 * Result of a credential verification call.
 *
 * @param valid      whether the credential proof is valid
 * @param commitment the credential commitment that was verified
 * @param reason     a human-readable reason if verification failed (may be {@code null})
 */
public record VerificationResult(boolean valid, String commitment, String reason) {}

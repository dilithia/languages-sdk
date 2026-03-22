package org.dilithia.sdk.model;

/**
 * Represents an issued credential stored on-chain.
 *
 * @param commitment the credential commitment hash
 * @param issuer     the issuer address
 * @param holder     the holder address
 * @param schemaHash the schema hash this credential conforms to
 * @param status     the credential status (e.g. "active", "revoked")
 * @param revoked    whether the credential has been revoked
 */
public record Credential(
        String commitment,
        String issuer,
        String holder,
        String schemaHash,
        String status,
        boolean revoked
) {}

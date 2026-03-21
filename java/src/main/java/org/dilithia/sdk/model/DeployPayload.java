package org.dilithia.sdk.model;

/**
 * Represents a contract deployment or upgrade payload.
 *
 * @param name      the contract name
 * @param bytecode  the hex-encoded bytecode
 * @param from      the sender address
 * @param alg       the signing algorithm
 * @param pk        the public key
 * @param sig       the signature in hex
 * @param nonce     the sender's nonce
 * @param chainId   the chain identifier
 * @param version   the contract version
 */
public record DeployPayload(
        String name,
        String bytecode,
        Address from,
        String alg,
        PublicKey pk,
        String sig,
        long nonce,
        String chainId,
        int version
) {}

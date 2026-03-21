package org.dilithia.sdk.zk;

import org.dilithia.sdk.exception.CryptoException;
import org.dilithia.sdk.model.Commitment;
import org.dilithia.sdk.model.Nullifier;
import org.dilithia.sdk.model.StarkProofResult;

/**
 * Zero-knowledge proof adapter using post-quantum STARKs.
 *
 * <p>Provides Poseidon hashing, commitment/nullifier computation,
 * and STARK proof generation/verification for the Dilithia shielded pool.</p>
 *
 * <p>Requires the {@code @dilithia/sdk-zk} native bridge.</p>
 */
public interface DilithiaZkAdapter {

    /**
     * Compute a Poseidon hash over an array of field elements.
     * Poseidon is a STARK-friendly algebraic hash function.
     *
     * @param inputs field elements to hash
     * @return hex-encoded Poseidon hash
     * @throws CryptoException if the native bridge fails
     */
    String poseidonHash(long[] inputs) throws CryptoException;

    /**
     * Compute a shielded commitment: {@code Poseidon(value || secret || nonce)}.
     *
     * <p>The commitment binds the depositor to a specific value without revealing
     * it on-chain. The secret and nonce must be retained to later prove ownership
     * during withdrawal.</p>
     *
     * @param value     the amount to commit
     * @param secretHex hex-encoded 32-byte secret
     * @param nonceHex  hex-encoded 32-byte nonce
     * @return a {@link Commitment} containing the hash, value, secret, and nonce
     * @throws CryptoException if hashing or serialization fails
     */
    Commitment computeCommitment(long value, String secretHex, String nonceHex) throws CryptoException;

    /**
     * Compute a nullifier: {@code Poseidon(secret || nonce)}.
     *
     * <p>The nullifier uniquely identifies a commitment without revealing its
     * value. Publishing a nullifier during withdrawal prevents double-spending.</p>
     *
     * @param secretHex hex-encoded 32-byte secret used in the original commitment
     * @param nonceHex  hex-encoded 32-byte nonce used in the original commitment
     * @return a {@link Nullifier} containing the hex-encoded nullifier hash
     * @throws CryptoException if hashing or serialization fails
     */
    Nullifier computeNullifier(String secretHex, String nonceHex) throws CryptoException;

    /**
     * Generate a STARK proof of knowledge of a hash preimage.
     *
     * <p>Proves that the prover knows field elements whose Poseidon hash equals a
     * public output, without revealing the elements themselves.</p>
     *
     * @param values the preimage field elements
     * @return a {@link StarkProofResult} containing the proof bytes, verification key, and public inputs
     * @throws CryptoException if proof generation fails
     */
    StarkProofResult generatePreimageProof(long[] values) throws CryptoException;

    /**
     * Verify a STARK preimage proof.
     *
     * @param proofHex   hex-encoded proof bytes
     * @param vkJson     JSON-encoded verification key
     * @param inputsJson JSON-encoded public inputs
     * @return {@code true} if the proof is valid, {@code false} otherwise
     * @throws CryptoException if verification fails due to malformed inputs or a native bridge error
     */
    boolean verifyPreimageProof(String proofHex, String vkJson, String inputsJson) throws CryptoException;

    /**
     * Generate a STARK range proof proving that {@code value} lies within
     * {@code [min, max]} without revealing the actual value.
     *
     * @param value the secret value to prove is in range
     * @param min   the inclusive lower bound
     * @param max   the inclusive upper bound
     * @return a {@link StarkProofResult} containing the proof bytes, verification key, and public inputs
     * @throws CryptoException if proof generation fails
     */
    StarkProofResult generateRangeProof(long value, long min, long max) throws CryptoException;

    /**
     * Verify a STARK range proof.
     *
     * @param proofHex   hex-encoded proof bytes
     * @param vkJson     JSON-encoded verification key
     * @param inputsJson JSON-encoded public inputs
     * @return {@code true} if the proof is valid, {@code false} otherwise
     * @throws CryptoException if verification fails due to malformed inputs or a native bridge error
     */
    boolean verifyRangeProof(String proofHex, String vkJson, String inputsJson) throws CryptoException;
}

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

    /**
     * Generate a commitment proof with a domain tag.
     *
     * @param value     the committed value
     * @param blinding  the blinding factor
     * @param domainTag the domain tag
     * @return a {@link StarkProofResult} containing the proof, public inputs, and verification key
     * @throws CryptoException if proof generation fails
     */
    StarkProofResult generateCommitmentProof(long value, long blinding, long domainTag) throws CryptoException;

    /**
     * Verify a commitment proof.
     *
     * @param proofHex   hex-encoded proof bytes
     * @param vkJson     JSON-encoded verification key
     * @param inputsJson JSON-encoded public inputs
     * @return {@code true} if the proof is valid, {@code false} otherwise
     * @throws CryptoException if verification fails
     */
    boolean verifyCommitmentProof(String proofHex, String vkJson, String inputsJson) throws CryptoException;

    /**
     * Prove a predicate (value in [min, max] with domain tag).
     *
     * @param value     the secret value
     * @param blinding  the blinding factor
     * @param domainTag the domain tag
     * @param min       the inclusive lower bound
     * @param max       the inclusive upper bound
     * @return a JSON string containing proof, commitment, min, max, and domain_tag
     * @throws CryptoException if proof generation fails
     */
    String provePredicate(long value, long blinding, long domainTag, long min, long max) throws CryptoException;

    /**
     * Prove that the subject's age is at least {@code minAge}.
     *
     * @param birthYear   the birth year
     * @param currentYear the current year
     * @param minAge      the minimum age to prove
     * @param blinding    the blinding factor
     * @return a JSON string containing proof, commitment, min, max, and domain_tag
     * @throws CryptoException if proof generation fails
     */
    String proveAgeOver(long birthYear, long currentYear, long minAge, long blinding) throws CryptoException;

    /**
     * Verify an age-over proof.
     *
     * @param proofHex      hex-encoded proof bytes
     * @param commitmentHex hex-encoded commitment
     * @param minAge        the minimum age that was proved
     * @return {@code true} if the proof is valid, {@code false} otherwise
     * @throws CryptoException if verification fails
     */
    boolean verifyAgeOver(String proofHex, String commitmentHex, long minAge) throws CryptoException;

    /**
     * Prove that the balance is above a threshold.
     *
     * @param balance    the actual balance
     * @param blinding   the blinding factor
     * @param minBalance the minimum balance to prove
     * @param maxBalance the maximum balance bound
     * @return a JSON string containing proof, commitment, min, max, and domain_tag
     * @throws CryptoException if proof generation fails
     */
    String proveBalanceAbove(long balance, long blinding, long minBalance, long maxBalance) throws CryptoException;

    /**
     * Verify a balance-above proof.
     *
     * @param proofHex      hex-encoded proof bytes
     * @param commitmentHex hex-encoded commitment
     * @param minBalance    the minimum balance that was proved
     * @param maxBalance    the maximum balance bound
     * @return {@code true} if the proof is valid, {@code false} otherwise
     * @throws CryptoException if verification fails
     */
    boolean verifyBalanceAbove(String proofHex, String commitmentHex, long minBalance, long maxBalance) throws CryptoException;

    /**
     * Prove a balance transfer between sender and receiver.
     *
     * @param senderPre   the sender's pre-transfer balance
     * @param receiverPre the receiver's pre-transfer balance
     * @param amount      the transfer amount
     * @return a JSON string containing proof, sender_pre, receiver_pre, sender_post, receiver_post
     * @throws CryptoException if proof generation fails
     */
    String proveTransfer(long senderPre, long receiverPre, long amount) throws CryptoException;

    /**
     * Verify a transfer proof.
     *
     * @param proofHex   hex-encoded proof bytes
     * @param inputsJson JSON-encoded public inputs
     * @return {@code true} if the proof is valid, {@code false} otherwise
     * @throws CryptoException if verification fails
     */
    boolean verifyTransfer(String proofHex, String inputsJson) throws CryptoException;

    /**
     * Prove Merkle tree membership for a leaf.
     *
     * @param leafHashHex hex-encoded leaf hash
     * @param pathJson    JSON-encoded Merkle path (array of {sibling, is_left} objects)
     * @return a JSON string containing proof, leaf_hash, root, and depth
     * @throws CryptoException if proof generation fails
     */
    String proveMerkleVerify(String leafHashHex, String pathJson) throws CryptoException;

    /**
     * Verify a Merkle membership proof.
     *
     * @param proofHex   hex-encoded proof bytes
     * @param inputsJson JSON-encoded public inputs (leaf_hash, root, depth)
     * @return {@code true} if the proof is valid, {@code false} otherwise
     * @throws CryptoException if verification fails
     */
    boolean verifyMerkleProof(String proofHex, String inputsJson) throws CryptoException;
}

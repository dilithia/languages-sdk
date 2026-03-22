namespace Dilithia.Sdk.Zk;

/// <summary>
/// A commitment in the shielded pool.
/// </summary>
public record Commitment(string Hash, long Value, string Secret, string Nonce);

/// <summary>
/// A nullifier used to prevent double-spending.
/// </summary>
public record Nullifier(string Hash);

/// <summary>
/// A STARK proof with verification key and public inputs.
/// </summary>
public record StarkProof(string Proof, string Vk, string Inputs);

/// <summary>
/// Result of a predicate proof (age, balance — 0.5.0).
/// </summary>
public record PredicateProofResult(string Proof, string Commitment, long Min, long Max, long DomainTag);

/// <summary>
/// Result of a transfer proof (0.5.0).
/// </summary>
public record TransferProofResult(string Proof, long SenderPre, long ReceiverPre, long SenderPost, long ReceiverPost);

/// <summary>
/// Result of a Merkle verification proof (0.5.0).
/// </summary>
public record MerkleProofResult(string Proof, string LeafHash, string Root, long Depth);

/// <summary>
/// Interface for zero-knowledge proof operations backed by the native ZK library.
/// </summary>
public interface IDilithiaZkAdapter
{
    /// <summary>Compute a Poseidon hash over the given inputs.</summary>
    string PoseidonHash(long[] inputs);

    /// <summary>Compute a shielded-pool commitment.</summary>
    Commitment ComputeCommitment(long value, string secretHex, string nonceHex);

    /// <summary>Compute a nullifier from a secret and nonce.</summary>
    Nullifier ComputeNullifier(string secretHex, string nonceHex);

    /// <summary>Generate a STARK preimage proof.</summary>
    StarkProof GeneratePreimageProof(long[] values);

    /// <summary>Verify a STARK preimage proof.</summary>
    bool VerifyPreimageProof(string proofHex, string vkJson, string inputsJson);

    /// <summary>Generate a STARK range proof.</summary>
    StarkProof GenerateRangeProof(long value, long min, long max);

    /// <summary>Verify a STARK range proof.</summary>
    bool VerifyRangeProof(string proofHex, string vkJson, string inputsJson);

    /// <summary>Generate a commitment proof with domain tag (0.5.0).</summary>
    StarkProof GenerateCommitmentProof(long value, long blinding, long domainTag);

    /// <summary>Verify a commitment proof (0.5.0).</summary>
    bool VerifyCommitmentProof(string proofHex, string vkJson, string inputsJson);

    /// <summary>Prove a predicate (value in [min, max] with domain tag) (0.5.0).</summary>
    PredicateProofResult ProvePredicate(long value, long blinding, long domainTag, long min, long max);

    /// <summary>Prove that age is at least minAge (0.5.0).</summary>
    PredicateProofResult ProveAgeOver(long birthYear, long currentYear, long minAge, long blinding);

    /// <summary>Verify an age-over proof (0.5.0).</summary>
    bool VerifyAgeOver(string proofHex, string commitmentHex, long minAge);

    /// <summary>Prove that balance is above a threshold (0.5.0).</summary>
    PredicateProofResult ProveBalanceAbove(long balance, long blinding, long minBalance, long maxBalance);

    /// <summary>Verify a balance-above proof (0.5.0).</summary>
    bool VerifyBalanceAbove(string proofHex, string commitmentHex, long minBalance, long maxBalance);

    /// <summary>Prove a transfer between sender and receiver (0.5.0).</summary>
    TransferProofResult ProveTransfer(long senderPre, long receiverPre, long amount);

    /// <summary>Verify a transfer proof (0.5.0).</summary>
    bool VerifyTransfer(string proofHex, string inputsJson);

    /// <summary>Prove Merkle tree membership (0.5.0).</summary>
    MerkleProofResult ProveMerkleVerify(string leafHashHex, string pathJson);

    /// <summary>Verify a Merkle membership proof (0.5.0).</summary>
    bool VerifyMerkleProof(string proofHex, string inputsJson);
}

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
}

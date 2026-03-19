package org.dilithia.sdk;

public interface DilithiaZkAdapter {
    String poseidonHash(long[] inputs);
    Commitment computeCommitment(long value, String secretHex, String nonceHex);
    Nullifier computeNullifier(String secretHex, String nonceHex);
    StarkProofResult generatePreimageProof(long[] values);
    boolean verifyPreimageProof(String proofHex, String vkJson, String inputsJson);
    StarkProofResult generateRangeProof(long value, long min, long max);
    boolean verifyRangeProof(String proofHex, String vkJson, String inputsJson);
}

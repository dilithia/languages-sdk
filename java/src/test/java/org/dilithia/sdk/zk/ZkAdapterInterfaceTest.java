package org.dilithia.sdk.zk;

import org.dilithia.sdk.exception.CryptoException;
import org.dilithia.sdk.model.Commitment;
import org.dilithia.sdk.model.Nullifier;
import org.dilithia.sdk.model.StarkProofResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the DilithiaZkAdapter interface contract and the 0.5.0 ZK primitives.
 * Uses a mock implementation to verify method signatures and return types.
 */
class ZkAdapterInterfaceTest {

    /** Mock ZkAdapter that returns deterministic results. */
    static class MockZkAdapter implements DilithiaZkAdapter {

        @Override
        public String poseidonHash(long[] inputs) {
            return "hash_mock";
        }

        @Override
        public Commitment computeCommitment(long value, String secretHex, String nonceHex) {
            return new Commitment("cm_hash", value, secretHex, nonceHex);
        }

        @Override
        public Nullifier computeNullifier(String secretHex, String nonceHex) {
            return new Nullifier("null_hash");
        }

        @Override
        public StarkProofResult generatePreimageProof(long[] values) {
            return new StarkProofResult("preimage_proof", "pvk", "pi");
        }

        @Override
        public boolean verifyPreimageProof(String proofHex, String vkJson, String inputsJson) {
            return "preimage_proof".equals(proofHex);
        }

        @Override
        public StarkProofResult generateRangeProof(long value, long min, long max) {
            return new StarkProofResult("range_proof", "rvk", "ri");
        }

        @Override
        public boolean verifyRangeProof(String proofHex, String vkJson, String inputsJson) {
            return "range_proof".equals(proofHex);
        }

        @Override
        public StarkProofResult generateCommitmentProof(long value, long blinding, long domainTag) {
            return new StarkProofResult("cp_hex", "cpvk", "cpi");
        }

        @Override
        public boolean verifyCommitmentProof(String proofHex, String vkJson, String inputsJson) {
            return "cp_hex".equals(proofHex);
        }

        @Override
        public String provePredicate(long value, long blinding, long domainTag, long min, long max) {
            return "{\"proof\":\"pred_proof\",\"commitment\":\"pred_cm\",\"min\":" + min + ",\"max\":" + max + ",\"domain_tag\":" + domainTag + "}";
        }

        @Override
        public String proveAgeOver(long birthYear, long currentYear, long minAge, long blinding) {
            return "{\"proof\":\"age_proof\",\"commitment\":\"age_cm\",\"min\":" + minAge + ",\"max\":200,\"domain_tag\":1}";
        }

        @Override
        public boolean verifyAgeOver(String proofHex, String commitmentHex, long minAge) {
            return "age_proof".equals(proofHex);
        }

        @Override
        public String proveBalanceAbove(long balance, long blinding, long minBalance, long maxBalance) {
            return "{\"proof\":\"bal_proof\",\"commitment\":\"bal_cm\",\"min\":" + minBalance + ",\"max\":" + maxBalance + ",\"domain_tag\":2}";
        }

        @Override
        public boolean verifyBalanceAbove(String proofHex, String commitmentHex, long minBalance, long maxBalance) {
            return "bal_proof".equals(proofHex);
        }

        @Override
        public String proveTransfer(long senderPre, long receiverPre, long amount) {
            return "{\"proof\":\"xfer_proof\",\"sender_pre\":" + senderPre + ",\"receiver_pre\":" + receiverPre + "}";
        }

        @Override
        public boolean verifyTransfer(String proofHex, String inputsJson) {
            return "xfer_proof".equals(proofHex);
        }

        @Override
        public String proveMerkleVerify(String leafHashHex, String pathJson) {
            return "{\"proof\":\"merkle_proof\",\"leaf_hash\":\"" + leafHashHex + "\",\"root\":\"root_hex\",\"depth\":3}";
        }

        @Override
        public boolean verifyMerkleProof(String proofHex, String inputsJson) {
            return "merkle_proof".equals(proofHex);
        }
    }

    private final DilithiaZkAdapter zk = new MockZkAdapter();

    // ── Commitment proof (0.5.0) ────────────────────────────────────────

    @Nested
    class CommitmentProofTests {

        @Test
        void generateCommitmentProof() throws CryptoException {
            var result = zk.generateCommitmentProof(100, 42, 1);
            assertEquals("cp_hex", result.proof());
            assertEquals("cpvk", result.vk());
            assertEquals("cpi", result.inputs());
        }

        @Test
        void verifyCommitmentProofValid() throws CryptoException {
            assertTrue(zk.verifyCommitmentProof("cp_hex", "vk", "inputs"));
        }

        @Test
        void verifyCommitmentProofInvalid() throws CryptoException {
            assertFalse(zk.verifyCommitmentProof("bad", "vk", "inputs"));
        }
    }

    // ── Predicate proofs (0.5.0) ────────────────────────────────────────

    @Nested
    class PredicateProofTests {

        @Test
        void provePredicate() throws CryptoException {
            String result = zk.provePredicate(25, 42, 1, 18, 200);
            assertNotNull(result);
            assertTrue(result.contains("\"proof\":\"pred_proof\""));
            assertTrue(result.contains("\"min\":18"));
            assertTrue(result.contains("\"max\":200"));
        }

        @Test
        void proveAgeOver() throws CryptoException {
            String result = zk.proveAgeOver(2000, 2026, 18, 99);
            assertNotNull(result);
            assertTrue(result.contains("\"proof\":\"age_proof\""));
            assertTrue(result.contains("\"min\":18"));
        }

        @Test
        void verifyAgeOverValid() throws CryptoException {
            assertTrue(zk.verifyAgeOver("age_proof", "cm", 18));
        }

        @Test
        void verifyAgeOverInvalid() throws CryptoException {
            assertFalse(zk.verifyAgeOver("bad", "cm", 18));
        }

        @Test
        void proveBalanceAbove() throws CryptoException {
            String result = zk.proveBalanceAbove(5000, 42, 1000, 100000);
            assertNotNull(result);
            assertTrue(result.contains("\"proof\":\"bal_proof\""));
            assertTrue(result.contains("\"min\":1000"));
            assertTrue(result.contains("\"max\":100000"));
        }

        @Test
        void verifyBalanceAboveValid() throws CryptoException {
            assertTrue(zk.verifyBalanceAbove("bal_proof", "cm", 1000, 100000));
        }

        @Test
        void verifyBalanceAboveInvalid() throws CryptoException {
            assertFalse(zk.verifyBalanceAbove("bad", "cm", 1000, 100000));
        }
    }

    // ── Transfer proof (0.5.0) ──────────────────────────────────────────

    @Nested
    class TransferProofTests {

        @Test
        void proveTransfer() throws CryptoException {
            String result = zk.proveTransfer(1000, 500, 200);
            assertNotNull(result);
            assertTrue(result.contains("\"proof\":\"xfer_proof\""));
            assertTrue(result.contains("\"sender_pre\":1000"));
        }

        @Test
        void verifyTransferValid() throws CryptoException {
            assertTrue(zk.verifyTransfer("xfer_proof", "{}"));
        }

        @Test
        void verifyTransferInvalid() throws CryptoException {
            assertFalse(zk.verifyTransfer("bad", "{}"));
        }
    }

    // ── Merkle proof (0.5.0) ────────────────────────────────────────────

    @Nested
    class MerkleProofTests {

        @Test
        void proveMerkleVerify() throws CryptoException {
            String result = zk.proveMerkleVerify("0xleaf", "[]");
            assertNotNull(result);
            assertTrue(result.contains("\"proof\":\"merkle_proof\""));
            assertTrue(result.contains("\"leaf_hash\":\"0xleaf\""));
            assertTrue(result.contains("\"root\":\"root_hex\""));
        }

        @Test
        void verifyMerkleProofValid() throws CryptoException {
            assertTrue(zk.verifyMerkleProof("merkle_proof", "{}"));
        }

        @Test
        void verifyMerkleProofInvalid() throws CryptoException {
            assertFalse(zk.verifyMerkleProof("bad", "{}"));
        }
    }
}

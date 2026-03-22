package sdk

import (
	"context"
	"encoding/json"
	"testing"
)

// mockZkAdapter implements ZkAdapter with deterministic fake results.
type mockZkAdapter struct{}

func (m *mockZkAdapter) PoseidonHash(_ context.Context, inputs []uint64) (string, error) {
	return "hash_mock", nil
}

func (m *mockZkAdapter) ComputeCommitment(_ context.Context, value uint64, secretHex, nonceHex string) (Commitment, error) {
	return Commitment{Hash: "cm_hash", Value: value, Secret: secretHex, Nonce: nonceHex}, nil
}

func (m *mockZkAdapter) ComputeNullifier(_ context.Context, secretHex, nonceHex string) (Nullifier, error) {
	return Nullifier{Hash: "null_hash"}, nil
}

func (m *mockZkAdapter) GeneratePreimageProof(_ context.Context, values []uint64) (StarkProofResult, error) {
	return StarkProofResult{Proof: "preimage_proof", VK: "pvk", Inputs: "pi"}, nil
}

func (m *mockZkAdapter) VerifyPreimageProof(_ context.Context, proofHex, vkJSON, inputsJSON string) (bool, error) {
	return proofHex == "preimage_proof", nil
}

func (m *mockZkAdapter) GenerateRangeProof(_ context.Context, value, min, max uint64) (StarkProofResult, error) {
	return StarkProofResult{Proof: "range_proof", VK: "rvk", Inputs: "ri"}, nil
}

func (m *mockZkAdapter) VerifyRangeProof(_ context.Context, proofHex, vkJSON, inputsJSON string) (bool, error) {
	return proofHex == "range_proof", nil
}

func (m *mockZkAdapter) GenerateCommitmentProof(_ context.Context, value, blinding, domainTag uint64) (CommitmentProofResult, error) {
	return CommitmentProofResult{Proof: "cp_hex", PublicInputs: "pi_hex", VerificationKey: "vk_hex"}, nil
}

func (m *mockZkAdapter) VerifyCommitmentProof(_ context.Context, proofHex, vkJSON, inputsJSON string) (bool, error) {
	return proofHex == "cp_hex", nil
}

func (m *mockZkAdapter) ProvePredicate(_ context.Context, value, blinding, domainTag, min, max uint64) (PredicateProofResult, error) {
	return PredicateProofResult{Proof: "pred_proof", Commitment: "pred_cm", Min: min, Max: max, DomainTag: domainTag}, nil
}

func (m *mockZkAdapter) ProveAgeOver(_ context.Context, birthYear, currentYear, minAge, blinding uint64) (PredicateProofResult, error) {
	return PredicateProofResult{Proof: "age_proof", Commitment: "age_cm", Min: minAge, Max: 200, DomainTag: 1}, nil
}

func (m *mockZkAdapter) VerifyAgeOver(_ context.Context, proofHex, commitmentHex string, minAge uint64) (bool, error) {
	return proofHex == "age_proof", nil
}

func (m *mockZkAdapter) ProveBalanceAbove(_ context.Context, balance, blinding, minBalance, maxBalance uint64) (PredicateProofResult, error) {
	return PredicateProofResult{Proof: "bal_proof", Commitment: "bal_cm", Min: minBalance, Max: maxBalance, DomainTag: 2}, nil
}

func (m *mockZkAdapter) VerifyBalanceAbove(_ context.Context, proofHex, commitmentHex string, minBalance, maxBalance uint64) (bool, error) {
	return proofHex == "bal_proof", nil
}

func (m *mockZkAdapter) ProveTransfer(_ context.Context, senderPre, receiverPre, amount uint64) (TransferProofResult, error) {
	return TransferProofResult{
		Proof:        "xfer_proof",
		SenderPre:    senderPre,
		ReceiverPre:  receiverPre,
		SenderPost:   senderPre - amount,
		ReceiverPost: receiverPre + amount,
	}, nil
}

func (m *mockZkAdapter) VerifyTransfer(_ context.Context, proofHex, inputsJSON string) (bool, error) {
	return proofHex == "xfer_proof", nil
}

func (m *mockZkAdapter) ProveMerkleVerify(_ context.Context, leafHashHex, pathJSON string) (MerkleProofResult, error) {
	return MerkleProofResult{Proof: "merkle_proof", LeafHash: leafHashHex, Root: "root_hex", Depth: 3}, nil
}

func (m *mockZkAdapter) VerifyMerkleProof(_ context.Context, proofHex, inputsJSON string) (bool, error) {
	return proofHex == "merkle_proof", nil
}

// Compile-time check: mockZkAdapter must implement ZkAdapter.
var _ ZkAdapter = (*mockZkAdapter)(nil)

// ---------------------------------------------------------------------------
// Type construction tests (0.5.0 result types)
// ---------------------------------------------------------------------------

func TestCommitmentProofResultFields(t *testing.T) {
	r := CommitmentProofResult{Proof: "p", PublicInputs: "pi", VerificationKey: "vk"}
	if r.Proof != "p" || r.PublicInputs != "pi" || r.VerificationKey != "vk" {
		t.Fatalf("unexpected fields: %+v", r)
	}
}

func TestPredicateProofResultFields(t *testing.T) {
	r := PredicateProofResult{Proof: "p", Commitment: "c", Min: 18, Max: 200, DomainTag: 1}
	if r.Min != 18 || r.Max != 200 || r.DomainTag != 1 {
		t.Fatalf("unexpected fields: %+v", r)
	}
}

func TestTransferProofResultFields(t *testing.T) {
	r := TransferProofResult{Proof: "p", SenderPre: 1000, ReceiverPre: 500, SenderPost: 800, ReceiverPost: 700}
	if r.SenderPost != 800 || r.ReceiverPost != 700 {
		t.Fatalf("unexpected fields: %+v", r)
	}
}

func TestMerkleProofResultFields(t *testing.T) {
	r := MerkleProofResult{Proof: "p", LeafHash: "leaf", Root: "root", Depth: 4}
	if r.Depth != 4 || r.Root != "root" {
		t.Fatalf("unexpected fields: %+v", r)
	}
}

// ---------------------------------------------------------------------------
// ZkAdapter interface contract tests via mock
// ---------------------------------------------------------------------------

func TestMockZkAdapter_GenerateCommitmentProof(t *testing.T) {
	zk := &mockZkAdapter{}
	r, err := zk.GenerateCommitmentProof(context.Background(), 100, 42, 1)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if r.Proof != "cp_hex" {
		t.Fatalf("unexpected proof: %s", r.Proof)
	}
}

func TestMockZkAdapter_VerifyCommitmentProof(t *testing.T) {
	zk := &mockZkAdapter{}
	valid, err := zk.VerifyCommitmentProof(context.Background(), "cp_hex", "vk", "inputs")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !valid {
		t.Fatal("expected valid")
	}
	invalid, _ := zk.VerifyCommitmentProof(context.Background(), "bad", "vk", "inputs")
	if invalid {
		t.Fatal("expected invalid")
	}
}

func TestMockZkAdapter_ProvePredicate(t *testing.T) {
	zk := &mockZkAdapter{}
	r, err := zk.ProvePredicate(context.Background(), 25, 42, 1, 18, 200)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if r.Proof != "pred_proof" || r.Min != 18 || r.Max != 200 {
		t.Fatalf("unexpected result: %+v", r)
	}
}

func TestMockZkAdapter_ProveAgeOver(t *testing.T) {
	zk := &mockZkAdapter{}
	r, err := zk.ProveAgeOver(context.Background(), 2000, 2026, 18, 99)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if r.Proof != "age_proof" || r.Min != 18 {
		t.Fatalf("unexpected result: %+v", r)
	}
}

func TestMockZkAdapter_VerifyAgeOver(t *testing.T) {
	zk := &mockZkAdapter{}
	valid, _ := zk.VerifyAgeOver(context.Background(), "age_proof", "cm", 18)
	if !valid {
		t.Fatal("expected valid")
	}
	invalid, _ := zk.VerifyAgeOver(context.Background(), "bad", "cm", 18)
	if invalid {
		t.Fatal("expected invalid")
	}
}

func TestMockZkAdapter_ProveBalanceAbove(t *testing.T) {
	zk := &mockZkAdapter{}
	r, err := zk.ProveBalanceAbove(context.Background(), 5000, 42, 1000, 100000)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if r.Proof != "bal_proof" || r.Min != 1000 || r.Max != 100000 {
		t.Fatalf("unexpected result: %+v", r)
	}
}

func TestMockZkAdapter_VerifyBalanceAbove(t *testing.T) {
	zk := &mockZkAdapter{}
	valid, _ := zk.VerifyBalanceAbove(context.Background(), "bal_proof", "cm", 1000, 100000)
	if !valid {
		t.Fatal("expected valid")
	}
	invalid, _ := zk.VerifyBalanceAbove(context.Background(), "bad", "cm", 1000, 100000)
	if invalid {
		t.Fatal("expected invalid")
	}
}

func TestMockZkAdapter_ProveTransfer(t *testing.T) {
	zk := &mockZkAdapter{}
	r, err := zk.ProveTransfer(context.Background(), 1000, 500, 200)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if r.Proof != "xfer_proof" || r.SenderPost != 800 || r.ReceiverPost != 700 {
		t.Fatalf("unexpected result: %+v", r)
	}
}

func TestMockZkAdapter_VerifyTransfer(t *testing.T) {
	zk := &mockZkAdapter{}
	valid, _ := zk.VerifyTransfer(context.Background(), "xfer_proof", "{}")
	if !valid {
		t.Fatal("expected valid")
	}
	invalid, _ := zk.VerifyTransfer(context.Background(), "bad", "{}")
	if invalid {
		t.Fatal("expected invalid")
	}
}

func TestMockZkAdapter_ProveMerkleVerify(t *testing.T) {
	zk := &mockZkAdapter{}
	r, err := zk.ProveMerkleVerify(context.Background(), "0xleaf", "[]")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if r.Proof != "merkle_proof" || r.LeafHash != "0xleaf" || r.Root != "root_hex" || r.Depth != 3 {
		t.Fatalf("unexpected result: %+v", r)
	}
}

func TestMockZkAdapter_VerifyMerkleProof(t *testing.T) {
	zk := &mockZkAdapter{}
	valid, _ := zk.VerifyMerkleProof(context.Background(), "merkle_proof", "{}")
	if !valid {
		t.Fatal("expected valid")
	}
	invalid, _ := zk.VerifyMerkleProof(context.Background(), "bad", "{}")
	if invalid {
		t.Fatal("expected invalid")
	}
}

// ---------------------------------------------------------------------------
// Edge cases: zero values, empty strings, boundary conditions
// ---------------------------------------------------------------------------

func TestMockZkAdapter_PoseidonHash_EmptyInputs(t *testing.T) {
	zk := &mockZkAdapter{}
	result, err := zk.PoseidonHash(context.Background(), []uint64{})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if result != "hash_mock" {
		t.Fatalf("unexpected result: %s", result)
	}
}

func TestMockZkAdapter_PoseidonHash_NilInputs(t *testing.T) {
	zk := &mockZkAdapter{}
	result, err := zk.PoseidonHash(context.Background(), nil)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if result != "hash_mock" {
		t.Fatalf("unexpected result: %s", result)
	}
}

func TestMockZkAdapter_ComputeCommitment_ZeroValue(t *testing.T) {
	zk := &mockZkAdapter{}
	r, err := zk.ComputeCommitment(context.Background(), 0, "", "")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if r.Value != 0 {
		t.Fatalf("expected zero value, got %d", r.Value)
	}
	if r.Secret != "" {
		t.Fatalf("expected empty secret, got %q", r.Secret)
	}
	if r.Hash != "cm_hash" {
		t.Fatalf("unexpected hash: %s", r.Hash)
	}
}

func TestMockZkAdapter_ComputeNullifier_EmptyStrings(t *testing.T) {
	zk := &mockZkAdapter{}
	r, err := zk.ComputeNullifier(context.Background(), "", "")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if r.Hash != "null_hash" {
		t.Fatalf("unexpected hash: %s", r.Hash)
	}
}

func TestMockZkAdapter_GeneratePreimageProof_EmptyValues(t *testing.T) {
	zk := &mockZkAdapter{}
	r, err := zk.GeneratePreimageProof(context.Background(), []uint64{})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if r.Proof != "preimage_proof" {
		t.Fatalf("unexpected proof: %s", r.Proof)
	}
}

func TestMockZkAdapter_GenerateRangeProof_ZeroBounds(t *testing.T) {
	zk := &mockZkAdapter{}
	r, err := zk.GenerateRangeProof(context.Background(), 0, 0, 0)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if r.Proof != "range_proof" {
		t.Fatalf("unexpected proof: %s", r.Proof)
	}
}

func TestMockZkAdapter_GenerateCommitmentProof_ZeroValues(t *testing.T) {
	zk := &mockZkAdapter{}
	r, err := zk.GenerateCommitmentProof(context.Background(), 0, 0, 0)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if r.Proof != "cp_hex" || r.PublicInputs != "pi_hex" || r.VerificationKey != "vk_hex" {
		t.Fatalf("unexpected result: %+v", r)
	}
}

func TestMockZkAdapter_ProvePredicate_ZeroBounds(t *testing.T) {
	zk := &mockZkAdapter{}
	r, err := zk.ProvePredicate(context.Background(), 0, 0, 0, 0, 0)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if r.Min != 0 || r.Max != 0 || r.DomainTag != 0 {
		t.Fatalf("unexpected result: %+v", r)
	}
}

func TestMockZkAdapter_ProveAgeOver_ZeroBlinding(t *testing.T) {
	zk := &mockZkAdapter{}
	r, err := zk.ProveAgeOver(context.Background(), 1990, 2026, 21, 0)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if r.Min != 21 {
		t.Fatalf("expected min=21, got %d", r.Min)
	}
}

func TestMockZkAdapter_ProveBalanceAbove_EqualBounds(t *testing.T) {
	zk := &mockZkAdapter{}
	r, err := zk.ProveBalanceAbove(context.Background(), 1000, 0, 1000, 1000)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if r.Min != 1000 || r.Max != 1000 {
		t.Fatalf("expected equal bounds, got min=%d max=%d", r.Min, r.Max)
	}
}

func TestMockZkAdapter_ProveTransfer_ZeroAmount(t *testing.T) {
	zk := &mockZkAdapter{}
	r, err := zk.ProveTransfer(context.Background(), 500, 300, 0)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if r.SenderPost != 500 || r.ReceiverPost != 300 {
		t.Fatalf("expected unchanged balances: sender=%d receiver=%d", r.SenderPost, r.ReceiverPost)
	}
}

func TestMockZkAdapter_ProveTransfer_FullAmount(t *testing.T) {
	zk := &mockZkAdapter{}
	r, err := zk.ProveTransfer(context.Background(), 1000, 0, 1000)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if r.SenderPost != 0 || r.ReceiverPost != 1000 {
		t.Fatalf("expected full transfer: sender=%d receiver=%d", r.SenderPost, r.ReceiverPost)
	}
}

func TestMockZkAdapter_ProveMerkleVerify_EmptyPath(t *testing.T) {
	zk := &mockZkAdapter{}
	r, err := zk.ProveMerkleVerify(context.Background(), "", "[]")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if r.LeafHash != "" {
		t.Fatalf("expected empty leaf hash, got %q", r.LeafHash)
	}
	if r.Root != "root_hex" || r.Depth != 3 {
		t.Fatalf("unexpected result: %+v", r)
	}
}

func TestMockZkAdapter_VerifyCommitmentProof_EmptyStrings(t *testing.T) {
	zk := &mockZkAdapter{}
	valid, err := zk.VerifyCommitmentProof(context.Background(), "", "", "")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if valid {
		t.Fatal("expected invalid for empty proof")
	}
}

func TestMockZkAdapter_VerifyAgeOver_EmptyProof(t *testing.T) {
	zk := &mockZkAdapter{}
	valid, err := zk.VerifyAgeOver(context.Background(), "", "", 0)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if valid {
		t.Fatal("expected invalid for empty proof")
	}
}

func TestMockZkAdapter_VerifyBalanceAbove_EmptyProof(t *testing.T) {
	zk := &mockZkAdapter{}
	valid, err := zk.VerifyBalanceAbove(context.Background(), "", "", 0, 0)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if valid {
		t.Fatal("expected invalid for empty proof")
	}
}

func TestMockZkAdapter_VerifyTransfer_EmptyProof(t *testing.T) {
	zk := &mockZkAdapter{}
	valid, err := zk.VerifyTransfer(context.Background(), "", "{}")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if valid {
		t.Fatal("expected invalid for empty proof")
	}
}

func TestMockZkAdapter_VerifyMerkleProof_EmptyProof(t *testing.T) {
	zk := &mockZkAdapter{}
	valid, err := zk.VerifyMerkleProof(context.Background(), "", "{}")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if valid {
		t.Fatal("expected invalid for empty proof")
	}
}

func TestMockZkAdapter_VerifyPreimageProof_EmptyProof(t *testing.T) {
	zk := &mockZkAdapter{}
	valid, err := zk.VerifyPreimageProof(context.Background(), "", "", "")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if valid {
		t.Fatal("expected invalid for empty proof")
	}
}

func TestMockZkAdapter_VerifyRangeProof_EmptyProof(t *testing.T) {
	zk := &mockZkAdapter{}
	valid, err := zk.VerifyRangeProof(context.Background(), "", "", "")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if valid {
		t.Fatal("expected invalid for empty proof")
	}
}

// ---------------------------------------------------------------------------
// Type construction: zero-value / default structs
// ---------------------------------------------------------------------------

func TestCommitmentProofResult_ZeroValue(t *testing.T) {
	var r CommitmentProofResult
	if r.Proof != "" || r.PublicInputs != "" || r.VerificationKey != "" {
		t.Fatalf("expected zero-value struct, got %+v", r)
	}
}

func TestPredicateProofResult_ZeroValue(t *testing.T) {
	var r PredicateProofResult
	if r.Proof != "" || r.Commitment != "" || r.Min != 0 || r.Max != 0 || r.DomainTag != 0 {
		t.Fatalf("expected zero-value struct, got %+v", r)
	}
}

func TestTransferProofResult_ZeroValue(t *testing.T) {
	var r TransferProofResult
	if r.Proof != "" || r.SenderPre != 0 || r.ReceiverPre != 0 || r.SenderPost != 0 || r.ReceiverPost != 0 {
		t.Fatalf("expected zero-value struct, got %+v", r)
	}
}

func TestMerkleProofResult_ZeroValue(t *testing.T) {
	var r MerkleProofResult
	if r.Proof != "" || r.LeafHash != "" || r.Root != "" || r.Depth != 0 {
		t.Fatalf("expected zero-value struct, got %+v", r)
	}
}

func TestStarkProofResult_ZeroValue(t *testing.T) {
	var r StarkProofResult
	if r.Proof != "" || r.VK != "" || r.Inputs != "" {
		t.Fatalf("expected zero-value struct, got %+v", r)
	}
}

func TestCommitment_ZeroValue(t *testing.T) {
	var c Commitment
	if c.Hash != "" || c.Value != 0 || c.Secret != "" || c.Nonce != "" {
		t.Fatalf("expected zero-value struct, got %+v", c)
	}
}

func TestNullifier_ZeroValue(t *testing.T) {
	var n Nullifier
	if n.Hash != "" {
		t.Fatalf("expected zero-value struct, got %+v", n)
	}
}

// ---------------------------------------------------------------------------
// JSON round-trip for ZK result types
// ---------------------------------------------------------------------------

func TestCommitmentProofResult_JSON(t *testing.T) {
	r := CommitmentProofResult{Proof: "p1", PublicInputs: "pi1", VerificationKey: "vk1"}
	data, err := json.Marshal(r)
	if err != nil {
		t.Fatalf("marshal error: %v", err)
	}
	var r2 CommitmentProofResult
	if err := json.Unmarshal(data, &r2); err != nil {
		t.Fatalf("unmarshal error: %v", err)
	}
	if r2.Proof != "p1" || r2.PublicInputs != "pi1" || r2.VerificationKey != "vk1" {
		t.Fatalf("round-trip mismatch: %+v", r2)
	}
}

func TestPredicateProofResult_JSON(t *testing.T) {
	r := PredicateProofResult{Proof: "p", Commitment: "c", Min: 18, Max: 200, DomainTag: 1}
	data, err := json.Marshal(r)
	if err != nil {
		t.Fatalf("marshal error: %v", err)
	}
	var r2 PredicateProofResult
	if err := json.Unmarshal(data, &r2); err != nil {
		t.Fatalf("unmarshal error: %v", err)
	}
	if r2 != r {
		t.Fatalf("round-trip mismatch: got %+v want %+v", r2, r)
	}
}

func TestTransferProofResult_JSON(t *testing.T) {
	r := TransferProofResult{Proof: "p", SenderPre: 1000, ReceiverPre: 500, SenderPost: 800, ReceiverPost: 700}
	data, err := json.Marshal(r)
	if err != nil {
		t.Fatalf("marshal error: %v", err)
	}
	var r2 TransferProofResult
	if err := json.Unmarshal(data, &r2); err != nil {
		t.Fatalf("unmarshal error: %v", err)
	}
	if r2 != r {
		t.Fatalf("round-trip mismatch: got %+v want %+v", r2, r)
	}
}

func TestMerkleProofResult_JSON(t *testing.T) {
	r := MerkleProofResult{Proof: "p", LeafHash: "lh", Root: "rt", Depth: 5}
	data, err := json.Marshal(r)
	if err != nil {
		t.Fatalf("marshal error: %v", err)
	}
	var r2 MerkleProofResult
	if err := json.Unmarshal(data, &r2); err != nil {
		t.Fatalf("unmarshal error: %v", err)
	}
	if r2 != r {
		t.Fatalf("round-trip mismatch: got %+v want %+v", r2, r)
	}
}

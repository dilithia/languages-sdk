package sdk

import (
	"context"
	"encoding/json"
)

// ShieldedDeposit submits a shielded deposit transaction. The commitment,
// value, and STARK proof are sent to the shielded pool contract.
func (c *Client) ShieldedDeposit(ctx context.Context, commitment string, value uint64, proofHex string) (*SubmitResult, error) {
	body := c.BuildContractCall("shielded", "deposit", map[string]any{
		"commitment": commitment,
		"value":      value,
		"proof":      proofHex,
	}, "")
	raw, err := c.postJSON(ctx, "/call", body)
	if err != nil {
		return nil, &DilithiaError{Message: "shielded deposit failed", Cause: err}
	}
	return parseSubmitResult(raw)
}

// ShieldedWithdraw submits a shielded withdrawal transaction.
func (c *Client) ShieldedWithdraw(ctx context.Context, nullifier string, amount uint64, recipient, proofHex, commitmentRoot string) (*SubmitResult, error) {
	body := c.BuildContractCall("shielded", "withdraw", map[string]any{
		"nullifier":       nullifier,
		"amount":          amount,
		"recipient":       recipient,
		"proof":           proofHex,
		"commitment_root": commitmentRoot,
	}, "")
	raw, err := c.postJSON(ctx, "/call", body)
	if err != nil {
		return nil, &DilithiaError{Message: "shielded withdraw failed", Cause: err}
	}
	return parseSubmitResult(raw)
}

// GetCommitmentRoot queries the current Merkle commitment root from the
// shielded pool.
func (c *Client) GetCommitmentRoot(ctx context.Context) (string, error) {
	body := c.BuildContractCall("shielded", "get_commitment_root", nil, "")
	raw, err := c.postJSON(ctx, "/call", body)
	if err != nil {
		return "", &DilithiaError{Message: "get commitment root failed", Cause: err}
	}
	if v, ok := raw["value"]; ok {
		if s, ok := v.(string); ok {
			return s, nil
		}
		b, _ := json.Marshal(v)
		return string(b), nil
	}
	b, _ := json.Marshal(raw)
	return string(b), nil
}

// IsNullifierSpent checks whether a nullifier has already been used.
func (c *Client) IsNullifierSpent(ctx context.Context, nullifier string) (bool, error) {
	body := c.BuildContractCall("shielded", "is_nullifier_spent", map[string]any{
		"nullifier": nullifier,
	}, "")
	raw, err := c.postJSON(ctx, "/call", body)
	if err != nil {
		return false, &DilithiaError{Message: "is nullifier spent check failed", Cause: err}
	}
	if v, ok := raw["value"].(bool); ok {
		return v, nil
	}
	return false, nil
}

// ShieldedDepositBody returns the request body for a shielded deposit.
// Deprecated: Use ShieldedDeposit instead, which also submits the request.
func (c *Client) ShieldedDepositBody(commitment string, value uint64, proofHex string) map[string]interface{} {
	return c.BuildContractCall("shielded", "deposit", map[string]any{
		"commitment": commitment,
		"value":      value,
		"proof":      proofHex,
	}, "")
}

// ShieldedWithdrawBody returns the request body for a shielded withdrawal.
// Deprecated: Use ShieldedWithdraw instead, which also submits the request.
func (c *Client) ShieldedWithdrawBody(nullifier string, amount uint64, recipient, proofHex, commitmentRoot string) map[string]interface{} {
	return c.BuildContractCall("shielded", "withdraw", map[string]any{
		"nullifier":       nullifier,
		"amount":          amount,
		"recipient":       recipient,
		"proof":           proofHex,
		"commitment_root": commitmentRoot,
	}, "")
}

// GetCommitmentRootBody returns the request body for querying the commitment root.
// Deprecated: Use GetCommitmentRoot instead.
func (c *Client) GetCommitmentRootBody() map[string]interface{} {
	return c.BuildContractCall("shielded", "get_commitment_root", nil, "")
}

// IsNullifierSpentBody returns the request body for checking nullifier status.
// Deprecated: Use IsNullifierSpent instead.
func (c *Client) IsNullifierSpentBody(nullifier string) map[string]interface{} {
	return c.BuildContractCall("shielded", "is_nullifier_spent", map[string]any{
		"nullifier": nullifier,
	}, "")
}

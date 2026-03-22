package sdk

import (
	"context"
	"encoding/json"
)

// multisigContract is the well-known contract identifier for multisig wallets.
const multisigContract = "multisig"

// ---------------------------------------------------------------------------
// Multisig mutations (submitted as contract calls via POST /call)
// ---------------------------------------------------------------------------

// CreateMultisig creates a new multisig wallet with the given signers and threshold.
func (c *Client) CreateMultisig(ctx context.Context, walletId string, signers []string, threshold int) (*SubmitResult, error) {
	call := c.BuildContractCall(multisigContract, "create", map[string]any{
		"wallet_id": walletId,
		"signers":   signers,
		"threshold": threshold,
	}, "")
	return c.SendCall(ctx, call)
}

// ProposeTx proposes a new transaction on a multisig wallet.
func (c *Client) ProposeTx(ctx context.Context, walletId, contract, method string, args map[string]any) (*SubmitResult, error) {
	call := c.BuildContractCall(multisigContract, "propose_tx", map[string]any{
		"wallet_id": walletId,
		"contract":  contract,
		"method":    method,
		"args":      args,
	}, "")
	return c.SendCall(ctx, call)
}

// ApproveMultisigTx approves a pending multisig transaction.
func (c *Client) ApproveMultisigTx(ctx context.Context, walletId, txId string) (*SubmitResult, error) {
	call := c.BuildContractCall(multisigContract, "approve", map[string]any{
		"wallet_id": walletId,
		"tx_id":     txId,
	}, "")
	return c.SendCall(ctx, call)
}

// ExecuteMultisigTx executes a multisig transaction that has reached threshold.
func (c *Client) ExecuteMultisigTx(ctx context.Context, walletId, txId string) (*SubmitResult, error) {
	call := c.BuildContractCall(multisigContract, "execute", map[string]any{
		"wallet_id": walletId,
		"tx_id":     txId,
	}, "")
	return c.SendCall(ctx, call)
}

// RevokeMultisigApproval revokes a previously given approval on a multisig transaction.
func (c *Client) RevokeMultisigApproval(ctx context.Context, walletId, txId string) (*SubmitResult, error) {
	call := c.BuildContractCall(multisigContract, "revoke", map[string]any{
		"wallet_id": walletId,
		"tx_id":     txId,
	}, "")
	return c.SendCall(ctx, call)
}

// AddMultisigSigner adds a signer to a multisig wallet.
func (c *Client) AddMultisigSigner(ctx context.Context, walletId, signer string) (*SubmitResult, error) {
	call := c.BuildContractCall(multisigContract, "add_signer", map[string]any{
		"wallet_id": walletId,
		"signer":    signer,
	}, "")
	return c.SendCall(ctx, call)
}

// RemoveMultisigSigner removes a signer from a multisig wallet.
func (c *Client) RemoveMultisigSigner(ctx context.Context, walletId, signer string) (*SubmitResult, error) {
	call := c.BuildContractCall(multisigContract, "remove_signer", map[string]any{
		"wallet_id": walletId,
		"signer":    signer,
	}, "")
	return c.SendCall(ctx, call)
}

// ---------------------------------------------------------------------------
// Multisig queries
// ---------------------------------------------------------------------------

// GetMultisigWallet fetches multisig wallet details.
func (c *Client) GetMultisigWallet(ctx context.Context, walletId string) (*MultisigWallet, error) {
	qr, err := c.QueryContract(ctx, multisigContract, "wallet", map[string]any{
		"wallet_id": walletId,
	})
	if err != nil {
		return nil, err
	}
	raw, ok := qr.Value.(map[string]any)
	if !ok {
		return nil, &DilithiaError{Message: "unexpected query result type"}
	}
	b, err := json.Marshal(raw)
	if err != nil {
		return nil, err
	}
	var wallet MultisigWallet
	if err := json.Unmarshal(b, &wallet); err != nil {
		return nil, err
	}
	return &wallet, nil
}

// GetMultisigTx fetches a single pending multisig transaction.
func (c *Client) GetMultisigTx(ctx context.Context, walletId, txId string) (*MultisigTx, error) {
	qr, err := c.QueryContract(ctx, multisigContract, "pending_tx", map[string]any{
		"wallet_id": walletId,
		"tx_id":     txId,
	})
	if err != nil {
		return nil, err
	}
	raw, ok := qr.Value.(map[string]any)
	if !ok {
		return nil, &DilithiaError{Message: "unexpected query result type"}
	}
	b, err := json.Marshal(raw)
	if err != nil {
		return nil, err
	}
	var tx MultisigTx
	if err := json.Unmarshal(b, &tx); err != nil {
		return nil, err
	}
	return &tx, nil
}

// ListMultisigPendingTxs lists all pending transactions for a multisig wallet.
func (c *Client) ListMultisigPendingTxs(ctx context.Context, walletId string) ([]MultisigTx, error) {
	qr, err := c.QueryContract(ctx, multisigContract, "pending_txs", map[string]any{
		"wallet_id": walletId,
	})
	if err != nil {
		return nil, err
	}
	raw, ok := qr.Value.(map[string]any)
	if !ok {
		return nil, &DilithiaError{Message: "unexpected query result type"}
	}
	arr, ok := raw["pending_txs"].([]any)
	if !ok {
		return nil, nil
	}
	var txs []MultisigTx
	for _, item := range arr {
		b, err := json.Marshal(item)
		if err != nil {
			continue
		}
		var tx MultisigTx
		if err := json.Unmarshal(b, &tx); err != nil {
			continue
		}
		txs = append(txs, tx)
	}
	return txs, nil
}

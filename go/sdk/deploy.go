package sdk

import (
	"context"
	"encoding/hex"
	"os"
	"sort"
)

// DeployContract submits a contract deployment transaction to the network.
func (c *Client) DeployContract(ctx context.Context, payload DeployPayload) (*SubmitResult, error) {
	body := deployBody(payload)
	raw, err := c.postAbsoluteJSON(ctx, c.baseURL+"/deploy", body)
	if err != nil {
		return nil, &DilithiaError{Message: "deploy failed", Cause: err}
	}
	return parseSubmitResult(raw)
}

// UpgradeContract submits a contract upgrade transaction to the network.
func (c *Client) UpgradeContract(ctx context.Context, payload DeployPayload) (*SubmitResult, error) {
	body := deployBody(payload)
	raw, err := c.postAbsoluteJSON(ctx, c.baseURL+"/upgrade", body)
	if err != nil {
		return nil, &DilithiaError{Message: "upgrade failed", Cause: err}
	}
	return parseSubmitResult(raw)
}

// DeployContractPath returns the full URL for the deploy endpoint.
func (c *Client) DeployContractPath() string {
	return c.baseURL + "/deploy"
}

// UpgradeContractPath returns the full URL for the upgrade endpoint.
func (c *Client) UpgradeContractPath() string {
	return c.baseURL + "/upgrade"
}

// DeployContractBody returns the JSON body for a deploy request.
// Deprecated: Use DeployContract instead, which also submits the request.
func (c *Client) DeployContractBody(payload DeployPayload) map[string]interface{} {
	return deployBody(payload)
}

// UpgradeContractBody returns the JSON body for an upgrade request.
// Deprecated: Use UpgradeContract instead, which also submits the request.
func (c *Client) UpgradeContractBody(payload DeployPayload) map[string]interface{} {
	return deployBody(payload)
}

// QueryContractAbiBody returns a JSON-RPC request body for fetching a
// contract ABI.
// Deprecated: Use GetContractAbi instead.
func (c *Client) QueryContractAbiBody(contract string) map[string]interface{} {
	return c.BuildJSONRPCRequest("qsc_getAbi", map[string]any{"contract": contract}, 1)
}

// BuildDeployCanonicalPayload builds the canonical map used for signing
// deploy transactions. Keys are sorted alphabetically.
func (c *Client) BuildDeployCanonicalPayload(from, name, bytecodeHash string, nonce uint64, chainID string) map[string]interface{} {
	m := map[string]interface{}{
		"bytecode_hash": bytecodeHash,
		"chain_id":      chainID,
		"from":          from,
		"name":          name,
		"nonce":         nonce,
	}
	keys := make([]string, 0, len(m))
	for k := range m {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	sorted := make(map[string]interface{}, len(m))
	for _, k := range keys {
		sorted[k] = m[k]
	}
	return sorted
}

// ReadWasmFileHex reads a Wasm binary from disk and returns it as a hex
// string suitable for use in DeployPayload.Bytecode.
func ReadWasmFileHex(path string) (string, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return "", err
	}
	return hex.EncodeToString(data), nil
}

// deployBody converts a DeployPayload to the JSON map expected by the
// deploy/upgrade REST endpoints.
func deployBody(payload DeployPayload) map[string]interface{} {
	return map[string]interface{}{
		"name":     payload.Name,
		"bytecode": payload.Bytecode,
		"from":     payload.From,
		"alg":      payload.Alg,
		"pk":       payload.PK,
		"sig":      payload.Sig,
		"nonce":    payload.Nonce,
		"chain_id": payload.ChainID,
		"version":  payload.Version,
	}
}

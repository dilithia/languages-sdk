package sdk

import (
	"context"
	"encoding/json"
	"fmt"
	"net/url"
)

// QueryContract performs a read-only query against a deployed contract.
func (c *Client) QueryContract(ctx context.Context, contract, method string, args map[string]any) (*QueryResult, error) {
	if args == nil {
		args = map[string]any{}
	}
	rawArgs, err := json.Marshal(args)
	if err != nil {
		return nil, &DilithiaError{Message: "failed to marshal query args", Cause: err}
	}
	raw, err := c.getAbsoluteJSON(
		ctx,
		fmt.Sprintf(
			"%s/query?contract=%s&method=%s&args=%s",
			c.baseURL,
			url.QueryEscape(contract),
			url.QueryEscape(method),
			url.QueryEscape(string(rawArgs)),
		),
	)
	if err != nil {
		return nil, err
	}
	return &QueryResult{Value: raw}, nil
}

// Simulate performs a dry-run of a contract call without committing state.
func (c *Client) Simulate(ctx context.Context, call map[string]any) (map[string]any, error) {
	return c.postJSON(ctx, "/simulate", call)
}

// SendCall submits a signed contract call to the network.
func (c *Client) SendCall(ctx context.Context, call map[string]any) (*SubmitResult, error) {
	raw, err := c.postJSON(ctx, "/call", call)
	if err != nil {
		return nil, err
	}
	return parseSubmitResult(raw)
}

// GetContractAbi fetches the ABI of a deployed contract.
func (c *Client) GetContractAbi(ctx context.Context, contract string) (*ContractAbi, error) {
	var abi ContractAbi
	if err := c.jsonRPCResultAs(ctx, "qsc_getAbi", map[string]any{"contract": contract}, 1, &abi); err != nil {
		return nil, &DilithiaError{Message: "failed to get contract ABI", Cause: err}
	}
	return &abi, nil
}

// WithPaymaster returns a copy of the call map with the paymaster field set.
func (c *Client) WithPaymaster(call map[string]any, paymaster string) map[string]any {
	out := cloneMap(call)
	out["paymaster"] = paymaster
	return out
}

// BuildContractCall constructs a contract call map suitable for SendCall or
// Simulate. If paymaster is non-empty it is attached to the call.
func (c *Client) BuildContractCall(contract, method string, args map[string]any, paymaster string) map[string]any {
	if args == nil {
		args = map[string]any{}
	}
	call := map[string]any{
		"contract": contract,
		"method":   method,
		"args":     args,
	}
	if paymaster != "" {
		return c.WithPaymaster(call, paymaster)
	}
	return call
}

// BuildForwarderCall constructs a meta-transaction forwarding call.
func (c *Client) BuildForwarderCall(forwarderContract string, args map[string]any, paymaster string) map[string]any {
	return c.BuildContractCall(forwarderContract, "forward", args, paymaster)
}

// parseSubmitResult converts a raw JSON map into a typed SubmitResult.
func parseSubmitResult(raw map[string]any) (*SubmitResult, error) {
	result := &SubmitResult{}
	if v, ok := raw["accepted"].(bool); ok {
		result.Accepted = v
	}
	if v, ok := raw["tx_hash"].(string); ok {
		result.TxHash = TxHash(v)
	}
	return result, nil
}

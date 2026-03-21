package sdk

import (
	"context"
	"encoding/json"
	"fmt"
	"strconv"
)

// BuildJSONRPCRequest constructs a JSON-RPC 2.0 request envelope.
func (c *Client) BuildJSONRPCRequest(method string, params map[string]any, id int) map[string]any {
	if params == nil {
		params = map[string]any{}
	}
	if id == 0 {
		id = 1
	}
	return map[string]any{
		"jsonrpc": "2.0",
		"id":      id,
		"method":  method,
		"params":  params,
	}
}

// BuildWSRequest constructs a JSON-RPC request suitable for sending over a
// WebSocket connection (identical format to BuildJSONRPCRequest).
func (c *Client) BuildWSRequest(method string, params map[string]any, id int) map[string]any {
	return c.BuildJSONRPCRequest(method, params, id)
}

// JSONRPC sends a JSON-RPC 2.0 request to the RPC endpoint and returns the
// raw result map. If the response contains a JSON-RPC error object an
// *RpcError is returned.
func (c *Client) JSONRPC(ctx context.Context, method string, params map[string]any, id int) (map[string]any, error) {
	result, err := c.postJSON(ctx, "", c.BuildJSONRPCRequest(method, params, id))
	if err != nil {
		return nil, err
	}
	// Check for JSON-RPC level error.
	if errObj, ok := result["error"]; ok {
		if errMap, ok := errObj.(map[string]any); ok {
			code := 0
			switch c := errMap["code"].(type) {
			case json.Number:
				if n, err := c.Int64(); err == nil {
					code = int(n)
				}
			case string:
				if n, err := strconv.Atoi(c); err == nil {
					code = n
				}
			}
			msg := fmt.Sprint(errMap["message"])
			return nil, &DilithiaError{
				Message: "rpc call failed",
				Cause:   &RpcError{Code: code, RpcMessage: msg},
			}
		}
	}
	return result, nil
}

// RawRPC is an alias for JSONRPC, kept for backward compatibility.
func (c *Client) RawRPC(ctx context.Context, method string, params map[string]any, id int) (map[string]any, error) {
	return c.JSONRPC(ctx, method, params, id)
}

// RawGet performs a GET request against the RPC or chain-base URL.
// If useChainBase is true the path is resolved relative to the chain base URL,
// otherwise relative to the RPC URL.
func (c *Client) RawGet(ctx context.Context, path string, useChainBase bool) (map[string]any, error) {
	root := c.rpcURL
	if useChainBase {
		root = c.baseURL
	}
	if len(path) == 0 || path[0] != '/' {
		path = "/" + path
	}
	return c.getAbsoluteJSON(ctx, root+path)
}

// RawPost performs a POST request against the RPC or chain-base URL.
func (c *Client) RawPost(ctx context.Context, path string, body map[string]any, useChainBase bool) (map[string]any, error) {
	root := c.rpcURL
	if useChainBase {
		root = c.baseURL
	}
	if len(path) == 0 || path[0] != '/' {
		path = "/" + path
	}
	return c.postAbsoluteJSON(ctx, root+path, body)
}

// jsonRPCResultAs is a helper that executes a JSON-RPC call and unmarshals
// the "result" field into the value pointed to by dst.
func (c *Client) jsonRPCResultAs(ctx context.Context, method string, params map[string]any, id int, dst any) error {
	raw, err := c.JSONRPC(ctx, method, params, id)
	if err != nil {
		return err
	}
	resultVal, ok := raw["result"]
	if !ok {
		return &DilithiaError{Message: "rpc response missing result field"}
	}
	b, err := json.Marshal(resultVal)
	if err != nil {
		return err
	}
	return json.Unmarshal(b, dst)
}

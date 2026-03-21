// Package sdk provides a Go client for the Dilithia/QSC blockchain.
//
// Create a client with functional options:
//
//	client := sdk.NewClient("http://localhost:9070/rpc",
//	    sdk.WithTimeout(5 * time.Second),
//	    sdk.WithJWT("my-token"),
//	)
//
// All network methods accept a context.Context for cancellation and return
// typed response structs along with wrapped errors suitable for inspection
// via errors.Is and errors.As.
package sdk

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"strconv"
	"math/big"
	"net/http"
	"net/url"
	"strings"
	"time"
)

// SDKVersion is the current version of this SDK.
const SDKVersion = "0.3.0"

// RPCLineVersion is the JSON-RPC protocol version this SDK targets.
const RPCLineVersion = "0.3.0"

// clientConfig holds the internal configuration assembled from Option calls.
type clientConfig struct {
	rpcURL     string
	baseURL    string
	indexerURL string
	oracleURL  string
	wsURL      string
	jwt        string
	headers    map[string]string
	timeout    time.Duration
	httpClient *http.Client
}

// Option is a functional option for configuring a Client.
type Option func(*clientConfig)

// WithTimeout sets the HTTP client timeout. The default is 10 seconds.
func WithTimeout(d time.Duration) Option {
	return func(c *clientConfig) { c.timeout = d }
}

// WithJWT sets the Bearer token used in the Authorization header.
func WithJWT(jwt string) Option {
	return func(c *clientConfig) { c.jwt = jwt }
}

// WithHeader adds a custom header that will be sent with every request.
func WithHeader(key, value string) Option {
	return func(c *clientConfig) {
		if c.headers == nil {
			c.headers = map[string]string{}
		}
		c.headers[key] = value
	}
}

// WithChainBaseURL overrides the chain REST base URL. By default it is
// derived from the RPC URL by stripping the "/rpc" suffix.
func WithChainBaseURL(url string) Option {
	return func(c *clientConfig) { c.baseURL = url }
}

// WithIndexerURL sets the indexer service URL.
func WithIndexerURL(url string) Option {
	return func(c *clientConfig) { c.indexerURL = url }
}

// WithOracleURL sets the oracle service URL.
func WithOracleURL(url string) Option {
	return func(c *clientConfig) { c.oracleURL = url }
}

// WithWSURL overrides the WebSocket URL. By default it is derived from
// the chain base URL by replacing http(s) with ws(s).
func WithWSURL(url string) Option {
	return func(c *clientConfig) { c.wsURL = url }
}

// WithHTTPClient supplies a pre-configured *http.Client. When set, the
// WithTimeout option is ignored (the caller owns the client's transport
// and timeout settings).
func WithHTTPClient(client *http.Client) Option {
	return func(c *clientConfig) { c.httpClient = client }
}

// Client is a Dilithia blockchain client. It provides typed methods for
// querying balances, nonces, receipts, and network info, as well as
// submitting transactions and contract calls.
type Client struct {
	rpcURL     string
	baseURL    string
	indexerURL string
	oracleURL  string
	wsURL      string
	jwt        string
	headers    map[string]string
	timeout    time.Duration
	http       *http.Client
}

// ClientConfig is the legacy struct-based configuration.
// Deprecated: Use NewClient with functional options instead.
type ClientConfig struct {
	RPCURL       string
	ChainBaseURL string
	IndexerURL   string
	OracleURL    string
	WSURL        string
	JWT          string
	Headers      map[string]string
	Timeout      time.Duration
}

// NewClient creates a new Client for the given RPC URL. Configuration is
// applied through functional options.
//
//	client := sdk.NewClient("http://localhost:9070/rpc",
//	    sdk.WithTimeout(5 * time.Second),
//	)
func NewClient(rpcURL string, opts ...Option) *Client {
	cfg := &clientConfig{
		rpcURL:  strings.TrimRight(rpcURL, "/"),
		timeout: 10 * time.Second,
	}
	for _, opt := range opts {
		opt(cfg)
	}

	baseURL := strings.TrimRight(cfg.baseURL, "/")
	if baseURL == "" {
		baseURL = strings.TrimSuffix(cfg.rpcURL, "/rpc")
	}

	wsURL := deriveWSURL(baseURL, strings.TrimRight(cfg.wsURL, "/"))

	httpClient := cfg.httpClient
	if httpClient == nil {
		httpClient = &http.Client{Timeout: cfg.timeout}
	}

	return &Client{
		rpcURL:     cfg.rpcURL,
		baseURL:    baseURL,
		indexerURL: strings.TrimRight(cfg.indexerURL, "/"),
		oracleURL:  strings.TrimRight(cfg.oracleURL, "/"),
		wsURL:      wsURL,
		jwt:        cfg.jwt,
		headers:    cloneStringMap(cfg.headers),
		timeout:    cfg.timeout,
		http:       httpClient,
	}
}

// NewClientWithConfig creates a Client from a legacy ClientConfig struct.
// Deprecated: Use NewClient with functional options instead.
func NewClientWithConfig(config ClientConfig) *Client {
	opts := []Option{}
	if config.Timeout > 0 {
		opts = append(opts, WithTimeout(config.Timeout))
	}
	if config.JWT != "" {
		opts = append(opts, WithJWT(config.JWT))
	}
	if config.ChainBaseURL != "" {
		opts = append(opts, WithChainBaseURL(config.ChainBaseURL))
	}
	if config.IndexerURL != "" {
		opts = append(opts, WithIndexerURL(config.IndexerURL))
	}
	if config.OracleURL != "" {
		opts = append(opts, WithOracleURL(config.OracleURL))
	}
	if config.WSURL != "" {
		opts = append(opts, WithWSURL(config.WSURL))
	}
	for k, v := range config.Headers {
		opts = append(opts, WithHeader(k, v))
	}
	return NewClient(config.RPCURL, opts...)
}

// ---------------------------------------------------------------------------
// Accessor methods
// ---------------------------------------------------------------------------

// WSURL returns the derived or configured WebSocket URL.
func (c *Client) WSURL() string {
	return c.wsURL
}

// BuildAuthHeaders returns a copy of the authentication and custom headers
// merged with any extra headers provided.
func (c *Client) BuildAuthHeaders(extra map[string]string) map[string]string {
	out := map[string]string{}
	if c.jwt != "" {
		out["Authorization"] = "Bearer " + c.jwt
	}
	for key, value := range c.headers {
		out[key] = value
	}
	for key, value := range extra {
		out[key] = value
	}
	return out
}

// WSConnectionInfo returns the WebSocket URL and auth headers as a map,
// suitable for establishing a WebSocket connection.
func (c *Client) WSConnectionInfo() map[string]any {
	return map[string]any{
		"url":     c.wsURL,
		"headers": c.BuildAuthHeaders(nil),
	}
}

// ---------------------------------------------------------------------------
// Typed query methods
// ---------------------------------------------------------------------------

// GetBalance returns the token balance for the given address.
func (c *Client) GetBalance(ctx context.Context, address Address) (*Balance, error) {
	raw, err := c.getJSON(ctx, "/balance/"+url.PathEscape(string(address)))
	if err != nil {
		return nil, err
	}
	bal := &Balance{}
	if v, ok := raw["address"].(string); ok {
		bal.Address = Address(v)
	} else {
		bal.Address = address
	}
	// Parse balance — json.Number (via UseNumber) or string. No float64.
	switch bv := raw["balance"].(type) {
	case json.Number:
		bal.RawValue = bv.String()
		if v, ok := new(big.Int).SetString(bv.String(), 10); ok {
			bal.Value = DiliAmount(v)
		}
	case string:
		bal.RawValue = bv
		if v, ok := new(big.Int).SetString(bv, 10); ok {
			bal.Value = DiliAmount(v)
		}
	}
	return bal, nil
}

// GetNonce returns the next nonce for the given address.
func (c *Client) GetNonce(ctx context.Context, address Address) (*Nonce, error) {
	raw, err := c.getJSON(ctx, "/nonce/"+url.PathEscape(string(address)))
	if err != nil {
		return nil, err
	}
	nonce := &Nonce{Address: address}
	switch v := raw["next_nonce"].(type) {
	case json.Number:
		if n, err := v.Int64(); err == nil {
			nonce.NextNonce = uint64(n)
		}
	case string:
		if n, err := strconv.ParseUint(v, 10, 64); err == nil {
			nonce.NextNonce = n
		}
	}
	return nonce, nil
}

// GetReceipt fetches the transaction receipt for the given hash.
func (c *Client) GetReceipt(ctx context.Context, txHash TxHash) (*Receipt, error) {
	raw, err := c.getJSON(ctx, "/receipt/"+url.PathEscape(string(txHash)))
	if err != nil {
		return nil, err
	}
	receipt := &Receipt{}
	if v, ok := raw["tx_hash"].(string); ok {
		receipt.TxHash = TxHash(v)
	} else {
		receipt.TxHash = txHash
	}
	receipt.BlockHeight = jsonNumberToUint64(raw["block_height"])
	if v, ok := raw["status"].(string); ok {
		receipt.Status = v
	}
	receipt.Result = raw["result"]
	if v, ok := raw["error"].(string); ok {
		receipt.Error = v
	}
	receipt.GasUsed = jsonNumberToUint64(raw["gas_used"])
	receipt.FeePaid = jsonNumberToUint64(raw["fee_paid"])
	return receipt, nil
}

// WaitForReceipt polls for a transaction receipt up to maxAttempts times,
// sleeping for delay between each attempt. If maxAttempts is <= 0, 12 is
// used. If delay is <= 0, one second is used.
func (c *Client) WaitForReceipt(ctx context.Context, txHash TxHash, maxAttempts int, delay time.Duration) (*Receipt, error) {
	if maxAttempts <= 0 {
		maxAttempts = 12
	}
	if delay <= 0 {
		delay = time.Second
	}
	for attempt := 0; attempt < maxAttempts; attempt++ {
		receipt, err := c.GetReceipt(ctx, txHash)
		if err == nil {
			return receipt, nil
		}
		// If it's not a 404, propagate the error immediately.
		var httpErr *HttpError
		if ok := isHttpError(err, &httpErr); ok && httpErr.StatusCode == http.StatusNotFound {
			// Receipt not ready yet, wait and retry.
		} else if err != nil && !strings.Contains(err.Error(), "HTTP 404") {
			return nil, err
		}
		timer := time.NewTimer(delay)
		select {
		case <-ctx.Done():
			timer.Stop()
			return nil, ctx.Err()
		case <-timer.C:
		}
	}
	return nil, &TimeoutError{Operation: fmt.Sprintf("receipt for %s not available after %d attempts", txHash, maxAttempts)}
}

// GetAddressSummary returns a summary for the given address via JSON-RPC.
func (c *Client) GetAddressSummary(ctx context.Context, address Address) (map[string]any, error) {
	return c.JSONRPC(ctx, "qsc_addressSummary", map[string]any{"address": string(address)}, 1)
}

// GetGasEstimate returns the current gas estimate via JSON-RPC.
func (c *Client) GetGasEstimate(ctx context.Context) (*GasEstimate, error) {
	var est GasEstimate
	if err := c.jsonRPCResultAs(ctx, "qsc_gasEstimate", map[string]any{}, 1, &est); err != nil {
		// Fallback: try raw map approach for nodes that return flat response.
		raw, rawErr := c.JSONRPC(ctx, "qsc_gasEstimate", map[string]any{}, 1)
		if rawErr != nil {
			return nil, err
		}
		est.GasLimit = jsonNumberToUint64(raw["gas_limit"])
		est.BaseFee = jsonNumberToUint64(raw["base_fee"])
		est.EstimatedCost = jsonNumberToUint64(raw["estimated_cost"])
	}
	return &est, nil
}

// GetBaseFee returns the current base fee via JSON-RPC.
func (c *Client) GetBaseFee(ctx context.Context) (map[string]any, error) {
	return c.JSONRPC(ctx, "qsc_baseFee", map[string]any{}, 1)
}

// GetNetworkInfo returns the chain ID, latest block height, and base fee.
func (c *Client) GetNetworkInfo(ctx context.Context) (*NetworkInfo, error) {
	var info NetworkInfo
	if err := c.jsonRPCResultAs(ctx, "qsc_networkInfo", map[string]any{}, 1, &info); err != nil {
		raw, rawErr := c.JSONRPC(ctx, "qsc_networkInfo", map[string]any{}, 1)
		if rawErr != nil {
			return nil, err
		}
		if v, ok := raw["chain_id"].(string); ok {
			info.ChainID = v
		}
		info.BlockHeight = jsonNumberToUint64(raw["height"])
		info.BaseFee = jsonNumberToUint64(raw["base_fee"])
	}
	return &info, nil
}

// ---------------------------------------------------------------------------
// Internal HTTP helpers
// ---------------------------------------------------------------------------

func (c *Client) getJSON(ctx context.Context, pathname string) (map[string]any, error) {
	return c.getAbsoluteJSON(ctx, c.rpcURL+pathname)
}

func (c *Client) getAbsoluteJSON(ctx context.Context, rawURL string) (map[string]any, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, rawURL, nil)
	if err != nil {
		return nil, &DilithiaError{Message: "failed to create request", Cause: err}
	}
	for key, value := range c.BuildAuthHeaders(map[string]string{"accept": "application/json"}) {
		req.Header.Set(key, value)
	}
	return c.doJSON(req)
}

func (c *Client) postJSON(ctx context.Context, pathname string, body map[string]any) (map[string]any, error) {
	return c.postAbsoluteJSON(ctx, c.rpcURL+pathname, body)
}

func (c *Client) postAbsoluteJSON(ctx context.Context, rawURL string, body map[string]any) (map[string]any, error) {
	payload, err := json.Marshal(body)
	if err != nil {
		return nil, &DilithiaError{Message: "failed to marshal request body", Cause: err}
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, rawURL, bytes.NewReader(payload))
	if err != nil {
		return nil, &DilithiaError{Message: "failed to create request", Cause: err}
	}
	for key, value := range c.BuildAuthHeaders(map[string]string{
		"accept":       "application/json",
		"content-type": "application/json",
	}) {
		req.Header.Set(key, value)
	}
	return c.doJSON(req)
}

func (c *Client) doJSON(req *http.Request) (map[string]any, error) {
	resp, err := c.http.Do(req)
	if err != nil {
		return nil, &DilithiaError{Message: "request failed", Cause: err}
	}
	defer resp.Body.Close()
	raw, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, &DilithiaError{Message: "failed to read response body", Cause: err}
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		body := string(raw)
		if len(body) > 512 {
			body = body[:512]
		}
		return nil, &DilithiaError{
			Message: "HTTP request failed",
			Cause:   &HttpError{StatusCode: resp.StatusCode, Body: body},
		}
	}
	var result map[string]any
	dec := json.NewDecoder(bytes.NewReader(raw))
	dec.UseNumber()
	if err := dec.Decode(&result); err != nil {
		return nil, &DilithiaError{Message: "failed to parse response JSON", Cause: err}
	}
	return result, nil
}

// ---------------------------------------------------------------------------
// Utility functions
// ---------------------------------------------------------------------------

func deriveWSURL(baseURL, explicit string) string {
	if explicit != "" {
		return explicit
	}
	if strings.HasPrefix(baseURL, "https://") {
		return "wss://" + strings.TrimPrefix(baseURL, "https://")
	}
	if strings.HasPrefix(baseURL, "http://") {
		return "ws://" + strings.TrimPrefix(baseURL, "http://")
	}
	return ""
}

// jsonNumberToUint64 extracts a uint64 from a json.Number or string value.
// Returns 0 if the value is nil or not a recognized numeric type.
func jsonNumberToUint64(v any) uint64 {
	switch n := v.(type) {
	case json.Number:
		if i, err := n.Int64(); err == nil {
			return uint64(i)
		}
	case string:
		if i, err := strconv.ParseUint(n, 10, 64); err == nil {
			return i
		}
	}
	return 0
}

func cloneMap(in map[string]any) map[string]any {
	out := make(map[string]any, len(in)+1)
	for key, value := range in {
		out[key] = value
	}
	return out
}

func cloneStringMap(in map[string]string) map[string]string {
	if in == nil {
		return map[string]string{}
	}
	out := make(map[string]string, len(in))
	for key, value := range in {
		out[key] = value
	}
	return out
}

// isHttpError checks whether err (or any error in its chain) is an *HttpError
// and assigns it to target. Returns true if found.
func isHttpError(err error, target **HttpError) bool {
	if err == nil {
		return false
	}
	type unwrapper interface{ Unwrap() error }
	for e := err; e != nil; {
		if he, ok := e.(*HttpError); ok {
			*target = he
			return true
		}
		if u, ok := e.(unwrapper); ok {
			e = u.Unwrap()
		} else {
			break
		}
	}
	return false
}

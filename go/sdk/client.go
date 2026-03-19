package sdk

import (
	"bytes"
	"context"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"sort"
	"strings"
	"time"
)

const SDKVersion = "0.2.0"
const RPCLineVersion = "0.2.0"

type WalletFile map[string]any

type Account struct {
	Address      string     `json:"address"`
	PublicKey    string     `json:"public_key"`
	SecretKey    string     `json:"secret_key"`
	AccountIndex int        `json:"account_index"`
	WalletFile   WalletFile `json:"wallet_file"`
}

type Signature struct {
	Algorithm string `json:"algorithm"`
	Signature string `json:"signature"`
}

type Keypair struct {
	SecretKey string `json:"secret_key"`
	PublicKey string `json:"public_key"`
	Address   string `json:"address"`
}

type Commitment struct {
	Hash   string `json:"hash"`
	Value  uint64 `json:"value"`
	Secret string `json:"secret"`
	Nonce  string `json:"nonce"`
}

type Nullifier struct {
	Hash string `json:"hash"`
}

type StarkProofResult struct {
	Proof  string `json:"proof"`
	VK     string `json:"vk"`
	Inputs string `json:"inputs"`
}

type DeployPayload struct {
	Name     string `json:"name"`
	Bytecode string `json:"bytecode"`
	From     string `json:"from"`
	Alg      string `json:"alg"`
	PK       string `json:"pk"`
	Sig      string `json:"sig"`
	Nonce    uint64 `json:"nonce"`
	ChainID  string `json:"chain_id"`
	Version  uint8  `json:"version"`
}

type ZkAdapter interface {
	PoseidonHash(ctx context.Context, inputs []uint64) (string, error)
	ComputeCommitment(ctx context.Context, value uint64, secretHex, nonceHex string) (Commitment, error)
	ComputeNullifier(ctx context.Context, secretHex, nonceHex string) (Nullifier, error)
	GeneratePreimageProof(ctx context.Context, values []uint64) (StarkProofResult, error)
	VerifyPreimageProof(ctx context.Context, proofHex, vkJSON, inputsJSON string) (bool, error)
	GenerateRangeProof(ctx context.Context, value, min, max uint64) (StarkProofResult, error)
	VerifyRangeProof(ctx context.Context, proofHex, vkJSON, inputsJSON string) (bool, error)
}

type CryptoAdapter interface {
	GenerateMnemonic(ctx context.Context) (string, error)
	ValidateMnemonic(ctx context.Context, mnemonic string) error
	RecoverHDWallet(ctx context.Context, mnemonic string) (Account, error)
	RecoverHDWalletAccount(ctx context.Context, mnemonic string, accountIndex int) (Account, error)
	CreateHDWalletFileFromMnemonic(ctx context.Context, mnemonic, password string) (Account, error)
	CreateHDWalletAccountFromMnemonic(ctx context.Context, mnemonic, password string, accountIndex int) (Account, error)
	RecoverWalletFile(ctx context.Context, walletFile WalletFile, mnemonic, password string) (Account, error)
	AddressFromPublicKey(ctx context.Context, publicKeyHex string) (string, error)
	SignMessage(ctx context.Context, secretKeyHex, message string) (Signature, error)
	VerifyMessage(ctx context.Context, publicKeyHex, message, signatureHex string) (bool, error)
	ValidateAddress(ctx context.Context, addr string) (string, error)
	AddressFromPKChecksummed(ctx context.Context, publicKeyHex string) (string, error)
	AddressWithChecksum(ctx context.Context, rawAddr string) (string, error)
	ValidatePublicKey(ctx context.Context, publicKeyHex string) error
	ValidateSecretKey(ctx context.Context, secretKeyHex string) error
	ValidateSignature(ctx context.Context, signatureHex string) error
	Keygen(ctx context.Context) (Keypair, error)
	KeygenFromSeed(ctx context.Context, seedHex string) (Keypair, error)
	SeedFromMnemonic(ctx context.Context, mnemonic string) (string, error)
	DeriveChildSeed(ctx context.Context, parentSeedHex string, index int) (string, error)
	ConstantTimeEq(ctx context.Context, aHex string, bHex string) (bool, error)
	HashHex(ctx context.Context, dataHex string) (string, error)
	SetHashAlg(ctx context.Context, alg string) error
	CurrentHashAlg(ctx context.Context) (string, error)
	HashLenHex(ctx context.Context) (int, error)
}

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

func NewClient(rpcURL string, timeout time.Duration) *Client {
	return NewClientWithConfig(ClientConfig{RPCURL: rpcURL, Timeout: timeout})
}

func NewClientWithConfig(config ClientConfig) *Client {
	timeout := config.Timeout
	if timeout <= 0 {
		timeout = 10 * time.Second
	}
	rpcURL := strings.TrimRight(config.RPCURL, "/")
	baseURL := strings.TrimRight(config.ChainBaseURL, "/")
	if baseURL == "" {
		baseURL = strings.TrimSuffix(rpcURL, "/rpc")
	}
	return &Client{
		rpcURL:     rpcURL,
		baseURL:    baseURL,
		indexerURL: strings.TrimRight(config.IndexerURL, "/"),
		oracleURL:  strings.TrimRight(config.OracleURL, "/"),
		wsURL:      deriveWSURL(baseURL, strings.TrimRight(config.WSURL, "/")),
		jwt:        config.JWT,
		headers:    cloneStringMap(config.Headers),
		timeout:    timeout,
		http:       &http.Client{Timeout: timeout},
	}
}

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

func (c *Client) WSURL() string {
	return c.wsURL
}

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

func (c *Client) WSConnectionInfo() map[string]any {
	return map[string]any{
		"url":     c.wsURL,
		"headers": c.BuildAuthHeaders(nil),
	}
}

func (c *Client) GetBalance(ctx context.Context, address string) (map[string]any, error) {
	return c.getJSON(ctx, "/balance/"+url.PathEscape(address))
}

func (c *Client) GetNonce(ctx context.Context, address string) (map[string]any, error) {
	return c.getJSON(ctx, "/nonce/"+url.PathEscape(address))
}

func (c *Client) GetReceipt(ctx context.Context, txHash string) (map[string]any, error) {
	return c.getJSON(ctx, "/receipt/"+url.PathEscape(txHash))
}

func (c *Client) GetAddressSummary(ctx context.Context, address string) (map[string]any, error) {
	return c.JSONRPC(ctx, "qsc_addressSummary", map[string]any{"address": address}, 1)
}

func (c *Client) GetGasEstimate(ctx context.Context) (map[string]any, error) {
	return c.JSONRPC(ctx, "qsc_gasEstimate", map[string]any{}, 1)
}

func (c *Client) GetBaseFee(ctx context.Context) (map[string]any, error) {
	return c.JSONRPC(ctx, "qsc_baseFee", map[string]any{}, 1)
}

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

func (c *Client) BuildWSRequest(method string, params map[string]any, id int) map[string]any {
	return c.BuildJSONRPCRequest(method, params, id)
}

func (c *Client) JSONRPC(ctx context.Context, method string, params map[string]any, id int) (map[string]any, error) {
	return c.postJSON(ctx, "", c.BuildJSONRPCRequest(method, params, id))
}

func (c *Client) RawRPC(ctx context.Context, method string, params map[string]any, id int) (map[string]any, error) {
	return c.JSONRPC(ctx, method, params, id)
}

func (c *Client) RawGet(ctx context.Context, path string, useChainBase bool) (map[string]any, error) {
	root := c.rpcURL
	if useChainBase {
		root = c.baseURL
	}
	if !strings.HasPrefix(path, "/") {
		path = "/" + path
	}
	return c.getAbsoluteJSON(ctx, root+path)
}

func (c *Client) RawPost(ctx context.Context, path string, body map[string]any, useChainBase bool) (map[string]any, error) {
	root := c.rpcURL
	if useChainBase {
		root = c.baseURL
	}
	if !strings.HasPrefix(path, "/") {
		path = "/" + path
	}
	return c.postAbsoluteJSON(ctx, root+path, body)
}

func (c *Client) ResolveName(ctx context.Context, name string) (map[string]any, error) {
	return c.getAbsoluteJSON(ctx, c.baseURL+"/names/resolve/"+url.PathEscape(name))
}

func (c *Client) ReverseResolveName(ctx context.Context, address string) (map[string]any, error) {
	return c.getAbsoluteJSON(ctx, c.baseURL+"/names/reverse/"+url.PathEscape(address))
}

func (c *Client) QueryContract(ctx context.Context, contract, method string, args map[string]any) (map[string]any, error) {
	if args == nil {
		args = map[string]any{}
	}
	rawArgs, err := json.Marshal(args)
	if err != nil {
		return nil, err
	}
	return c.getAbsoluteJSON(
		ctx,
		fmt.Sprintf(
			"%s/query?contract=%s&method=%s&args=%s",
			c.baseURL,
			url.QueryEscape(contract),
			url.QueryEscape(method),
			url.QueryEscape(string(rawArgs)),
		),
	)
}

func (c *Client) Simulate(ctx context.Context, call map[string]any) (map[string]any, error) {
	return c.postJSON(ctx, "/simulate", call)
}

func (c *Client) SendCall(ctx context.Context, call map[string]any) (map[string]any, error) {
	return c.postJSON(ctx, "/call", call)
}

func (c *Client) WithPaymaster(call map[string]any, paymaster string) map[string]any {
	out := cloneMap(call)
	out["paymaster"] = paymaster
	return out
}

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

func (c *Client) BuildForwarderCall(forwarderContract string, args map[string]any, paymaster string) map[string]any {
	return c.BuildContractCall(forwarderContract, "forward", args, paymaster)
}

func (c *Client) WaitForReceipt(ctx context.Context, txHash string, maxAttempts int, delay time.Duration) (map[string]any, error) {
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
		if !strings.Contains(err.Error(), "HTTP 404") {
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
	return nil, fmt.Errorf("receipt not available yet")
}

func (c *Client) getJSON(ctx context.Context, pathname string) (map[string]any, error) {
	return c.getAbsoluteJSON(ctx, c.rpcURL+pathname)
}

func (c *Client) getAbsoluteJSON(ctx context.Context, rawURL string) (map[string]any, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, rawURL, nil)
	if err != nil {
		return nil, err
	}
	for key, value := range c.BuildAuthHeaders(map[string]string{"accept": "application/json"}) {
		req.Header.Set(key, value)
	}
	return c.doJSON(req)
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

type GasSponsorConnector struct {
	Client          *Client
	SponsorContract string
	Paymaster       string
}

func NewGasSponsorConnector(client *Client, sponsorContract, paymaster string) *GasSponsorConnector {
	return &GasSponsorConnector{Client: client, SponsorContract: sponsorContract, Paymaster: paymaster}
}

func (c *GasSponsorConnector) BuildAcceptQuery(user, contract, method string) map[string]any {
	return map[string]any{
		"contract": c.SponsorContract,
		"method":   "accept",
		"args":     map[string]any{"user": user, "contract": contract, "method": method},
	}
}

func (c *GasSponsorConnector) BuildRemainingQuotaQuery(user string) map[string]any {
	return map[string]any{
		"contract": c.SponsorContract,
		"method":   "remaining_quota",
		"args":     map[string]any{"user": user},
	}
}

func (c *GasSponsorConnector) ApplyPaymaster(call map[string]any) map[string]any {
	if c.Paymaster == "" {
		return cloneMap(call)
	}
	return c.Client.WithPaymaster(call, c.Paymaster)
}

type MessagingConnector struct {
	Client            *Client
	MessagingContract string
	Paymaster         string
}

func NewMessagingConnector(client *Client, messagingContract, paymaster string) *MessagingConnector {
	return &MessagingConnector{Client: client, MessagingContract: messagingContract, Paymaster: paymaster}
}

func (c *MessagingConnector) BuildSendMessageCall(destChain string, payload any) map[string]any {
	return c.applyPaymaster(map[string]any{
		"contract": c.MessagingContract,
		"method":   "send_message",
		"args":     map[string]any{"dest_chain": destChain, "payload": payload},
	})
}

func (c *MessagingConnector) BuildReceiveMessageCall(sourceChain, sourceContract string, payload any) map[string]any {
	return c.applyPaymaster(map[string]any{
		"contract": c.MessagingContract,
		"method":   "receive_message",
		"args": map[string]any{
			"source_chain":    sourceChain,
			"source_contract": sourceContract,
			"payload":         payload,
		},
	})
}

func (c *MessagingConnector) applyPaymaster(call map[string]any) map[string]any {
	if c.Paymaster == "" {
		return cloneMap(call)
	}
	return c.Client.WithPaymaster(call, c.Paymaster)
}

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

func (c *Client) DeployContractPath() string {
	return c.baseURL + "/deploy"
}

func (c *Client) UpgradeContractPath() string {
	return c.baseURL + "/upgrade"
}

func (c *Client) DeployContractBody(payload DeployPayload) map[string]interface{} {
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

func (c *Client) UpgradeContractBody(payload DeployPayload) map[string]interface{} {
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

func (c *Client) QueryContractAbiBody(contract string) map[string]interface{} {
	return c.BuildJSONRPCRequest("qsc_getAbi", map[string]any{"contract": contract}, 1)
}

func ReadWasmFileHex(path string) (string, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return "", err
	}
	return hex.EncodeToString(data), nil
}

func (c *Client) postJSON(ctx context.Context, pathname string, body map[string]any) (map[string]any, error) {
	return c.postAbsoluteJSON(ctx, c.rpcURL+pathname, body)
}

func (c *Client) postAbsoluteJSON(ctx context.Context, rawURL string, body map[string]any) (map[string]any, error) {
	payload, err := json.Marshal(body)
	if err != nil {
		return nil, err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, rawURL, bytes.NewReader(payload))
	if err != nil {
		return nil, err
	}
	for key, value := range c.BuildAuthHeaders(map[string]string{
		"accept":       "application/json",
		"content-type": "application/json",
	}) {
		req.Header.Set(key, value)
	}
	return c.doJSON(req)
}

func (c *Client) ShieldedDepositBody(commitment string, value uint64, proofHex string) map[string]interface{} {
	return c.BuildContractCall("shielded", "deposit", map[string]any{
		"commitment": commitment,
		"value":      value,
		"proof":      proofHex,
	}, "")
}

func (c *Client) ShieldedWithdrawBody(nullifier string, amount uint64, recipient, proofHex, commitmentRoot string) map[string]interface{} {
	return c.BuildContractCall("shielded", "withdraw", map[string]any{
		"nullifier":       nullifier,
		"amount":          amount,
		"recipient":       recipient,
		"proof":           proofHex,
		"commitment_root": commitmentRoot,
	}, "")
}

func (c *Client) GetCommitmentRootBody() map[string]interface{} {
	return c.BuildContractCall("shielded", "get_commitment_root", nil, "")
}

func (c *Client) IsNullifierSpentBody(nullifier string) map[string]interface{} {
	return c.BuildContractCall("shielded", "is_nullifier_spent", map[string]any{
		"nullifier": nullifier,
	}, "")
}

func (c *Client) doJSON(req *http.Request) (map[string]any, error) {
	resp, err := c.http.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	raw, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}
	var result map[string]any
	if err := json.Unmarshal(raw, &result); err != nil {
		return nil, err
	}
	return result, nil
}
